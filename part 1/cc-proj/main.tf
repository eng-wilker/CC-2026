# Creates the Azure resources the application needs, and sets the environment variables it
# reads.
#
# Only what the first lab needs: a Cosmos DB account with the six containers, and an App
# Service to run the WAR. Everything else arrives with the lab that introduces it - storage
# and a blob container in the blob lab, Redis in the caching lab, AI Search in the search
# lab. Adding them here early would leave resources costing money and doing nothing.
#
# Deploying the application itself is a separate step:
#
#   terraform apply
#   mvn clean package azure-webapp:deploy
#
# Terraform creates the server; Maven puts the WAR on it. Infrastructure is created once, the
# WAR is redeployed many times.
#
# NOTE: terraform.tfstate holds the database and storage keys in clear. Do not commit it.

terraform {
  required_version = ">= 1.5"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

provider "azurerm" {
  features {}
}

locals {
  rg_name = "cc26-rg-${var.region}-${var.suffix}"
}

resource "azurerm_resource_group" "rg" {
  count    = var.create_resource_group ? 1 : 0
  name     = local.rg_name
  location = var.region
}

data "azurerm_resource_group" "existing" {
  count = var.create_resource_group ? 0 : 1
  name  = local.rg_name
}

locals {
  rg_location = var.create_resource_group ? azurerm_resource_group.rg[0].location : data.azurerm_resource_group.existing[0].location
}

########################################  Cosmos DB  ########################################

# Cosmos DB accounts from an earlier deployment of this project - for instance, one with another
# suffix whose terraform.tfstate is gone, so "terraform destroy" no longer knows about it. It still
# holds the free tier, and creating the account below would fail halfway through the apply.
data "azurerm_resources" "cosmos" {
  type = "Microsoft.DocumentDB/databaseAccounts"
}

locals {
  old_cosmos_accounts = [
    for r in data.azurerm_resources.cosmos.resources : r
    if startswith(r.name, "cc26") && lower(r.resource_group_name) != lower(local.rg_name)
  ]
}

resource "azurerm_cosmosdb_account" "db" {
  lifecycle {
    precondition {
      condition = length(local.old_cosmos_accounts) == 0
      error_message = join("\n", concat(
        ["A Cosmos DB account from an earlier deployment still exists:"],
        [for r in local.old_cosmos_accounts : "  ${r.name} (resource group ${r.resource_group_name})"],
        ["Only one free-tier Cosmos DB account is allowed per subscription.",
        "If you no longer need it, delete its group and run \"terraform apply\" again:"],
        [for g in distinct([for r in local.old_cosmos_accounts : r.resource_group_name]) : "  az group delete -n ${g}"],
      ))
    }
  }

  name                = "cc26${var.suffix}"
  resource_group_name = local.rg_name
  location            = local.rg_location
  offer_type          = "Standard"
  kind                = "GlobalDocumentDB"

  # First 1000 RU/s and 25GB are free. Only one account per subscription can use this.
  free_tier_enabled = true

  consistency_policy {
    consistency_level = "Session"
  }

  geo_location {
    location          = var.region
    failover_priority = 0
  }
}

resource "azurerm_cosmosdb_sql_database" "db" {
  name                = "cc26db${var.suffix}"
  resource_group_name = local.rg_name
  account_name        = azurerm_cosmosdb_account.db.name

  # L6: shared by every container, and low enough that load tests hit 429s.
  throughput = var.cosmos_throughput
}

# Partition keys have to match what the DAOs return from getPartKey().
resource "azurerm_cosmosdb_sql_container" "containers" {
  for_each = {
    users     = "/id"
    media     = "/id"
    auctions  = "/id"
    bids      = "/auctionId"
    questions = "/auctionId"
    sessions  = "/id"
  }

  name                = each.key
  resource_group_name = local.rg_name
  account_name        = azurerm_cosmosdb_account.db.name
  database_name       = azurerm_cosmosdb_sql_database.db.name
  partition_key_paths = [each.value]
}

######################################  App Service  ########################################

resource "azurerm_service_plan" "plan" {
  name                = "cc26plan${var.region}${var.suffix}"
  resource_group_name = local.rg_name
  location            = local.rg_location
  os_type             = "Linux"
  sku_name            = var.app_service_sku
}

resource "azurerm_linux_web_app" "app" {
  name                = "cc26app${var.region}${var.suffix}"
  resource_group_name = local.rg_name
  location            = azurerm_service_plan.plan.location
  service_plan_id     = azurerm_service_plan.plan.id

  site_config {
    # F1 does not allow always_on, so the app is unloaded when idle and the next request is slow.
    always_on = var.app_service_sku != "F1"

    application_stack {
      java_server         = "TOMCAT"
      java_server_version = "10.1"
      java_version        = "21"
    }
  }

  # The keys reach the application only this way. They are read with System.getenv - see
  # cc.utils.AzureProperties. There is no properties file.
  #
  # BlobStoreConnection is not set: there is no storage account yet. The application reads
  # that variable but never uses it, and the blob storage lab adds both the account and the
  # setting.
  app_settings = {
    COSMOSDB_URL      = azurerm_cosmosdb_account.db.endpoint
    COSMOSDB_KEY      = azurerm_cosmosdb_account.db.primary_key
    COSMOSDB_DATABASE = azurerm_cosmosdb_sql_database.db.name
  }
}

#########################################  Maven  ###########################################

# The names Maven needs, written where it reads them on every run: .mvn/maven.config holds
# command-line options, and each -D here overrides the property of the same name in pom.xml.
# So after "terraform apply", "mvn clean package azure-webapp:deploy" deploys to the app
# created above, without copying anything into pom.xml. "terraform destroy" deletes the file,
# and pom.xml falls back to its placeholder names. The file is gitignored.
resource "local_file" "maven_config" {
  filename             = "${path.module}/.mvn/maven.config"
  file_permission      = "0644"
  directory_permission = "0755"
  content = join("\n", [
    "-DwebAppName=${azurerm_linux_web_app.app.name}",
    "-DwebAppResourceGroup=${local.rg_name}",
    "-DwebAppRegion=${var.region}",
    "",
  ])
}
