# Creates the function app that runs the serverless version of the service, and sets the
# environment variables it reads.
#
# The functions use the same Cosmos DB database as the web app in cc-proj, so the two
# deployments can be compared on the same data. Normally cc-proj creates that database and
# this script only looks it up:
#
#   (in cc-proj)      terraform apply
#   (in cc-proj-fun)  terraform apply
#                     mvn clean package azure-functions:deploy
#
# If you want the functions without the web app, set create_shared_resources = true and this
# script creates the resource group and the database itself. Then do not run "terraform apply"
# in cc-proj for the same suffix and region: it would try to create them again and fail with
# "already exists".
#
# The function app goes in a resource group of its own, cc26-rg-fun-<region>-<suffix> - see
# below for why - with a storage account, which is a runtime requirement of Azure Functions,
# not media storage. "terraform destroy" here deletes that group and leaves the shared one.
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

# These names must be the same as in cc-proj/main.tf.
locals {
  rg_name      = "cc26-rg-${var.region}-${var.suffix}"
  cosmos_name  = "cc26${var.suffix}"
  cosmos_db    = "cc26db${var.suffix}"
  create_group = var.create_shared_resources && var.create_resource_group
}

####################################  Resource group  #######################################

resource "azurerm_resource_group" "rg" {
  count    = local.create_group ? 1 : 0
  name     = local.rg_name
  location = var.region
}

data "azurerm_resource_group" "existing" {
  count = local.create_group ? 0 : 1
  name  = local.rg_name
}

locals {
  rg_location = local.create_group ? azurerm_resource_group.rg[0].location : data.azurerm_resource_group.existing[0].location
}

########################################  Cosmos DB  ########################################

# Created here only when create_shared_resources is true. The same definitions as in
# cc-proj/main.tf - if you change one, change the other.

resource "azurerm_cosmosdb_account" "db" {
  count               = var.create_shared_resources ? 1 : 0
  name                = local.cosmos_name
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
  count               = var.create_shared_resources ? 1 : 0
  name                = local.cosmos_db
  resource_group_name = local.rg_name
  account_name        = azurerm_cosmosdb_account.db[0].name

  # L6: shared by every container, and low enough that load tests hit 429s.
  throughput = var.cosmos_throughput
}

# Partition keys have to match what the DAOs return from getPartKey().
resource "azurerm_cosmosdb_sql_container" "containers" {
  for_each = var.create_shared_resources ? {
    users     = "/id"
    media     = "/id"
    auctions  = "/id"
    bids      = "/auctionId"
    questions = "/auctionId"
    sessions  = "/id"
  } : {}

  name                = each.key
  resource_group_name = local.rg_name
  account_name        = azurerm_cosmosdb_account.db[0].name
  database_name       = azurerm_cosmosdb_sql_database.db[0].name
  partition_key_paths = [each.value]
}

# Otherwise, the ones cc-proj created.
data "azurerm_cosmosdb_account" "db" {
  count               = var.create_shared_resources ? 0 : 1
  name                = local.cosmos_name
  resource_group_name = local.rg_name
}

data "azurerm_cosmosdb_sql_database" "db" {
  count               = var.create_shared_resources ? 0 : 1
  name                = local.cosmos_db
  resource_group_name = local.rg_name
  account_name        = data.azurerm_cosmosdb_account.db[0].name
}

# The environment variables the functions read with System.getenv - see
# cc.utils.AzureProperties. The same ones the web app gets.
locals {
  app_settings = var.create_shared_resources ? {
    COSMOSDB_URL      = azurerm_cosmosdb_account.db[0].endpoint
    COSMOSDB_KEY      = azurerm_cosmosdb_account.db[0].primary_key
    COSMOSDB_DATABASE = azurerm_cosmosdb_sql_database.db[0].name
    } : {
    COSMOSDB_URL      = data.azurerm_cosmosdb_account.db[0].endpoint
    COSMOSDB_KEY      = data.azurerm_cosmosdb_account.db[0].primary_key
    COSMOSDB_DATABASE = data.azurerm_cosmosdb_sql_database.db[0].name
  }
}

#####################################  Function App  ########################################

# The function app has a resource group of its own. A resource group is tied to one App Service
# deployment unit when its first plan is created - here, the web app's plan from cc-proj - and
# that unit may not offer the Linux consumption plan. Azure then refuses the plan below with
# "Requested features 'Dynamic SKU, Linux Worker' not available in resource group". A new group
# avoids that. The database stays where it is, in the shared group.
resource "azurerm_resource_group" "fun" {
  name     = "cc26-rg-fun-${var.region}-${var.suffix}"
  location = var.region
}

# The Functions runtime keeps its state and the deployed package here. At most 24 lowercase
# letters and digits, so anything else in the suffix is dropped and long region names are cut -
# the suffix goes first to keep it.
resource "azurerm_storage_account" "fun" {
  name                     = substr(replace(lower("cc26fn${var.suffix}${var.region}"), "/[^a-z0-9]/", ""), 0, 24)
  resource_group_name      = azurerm_resource_group.fun.name
  location                 = azurerm_resource_group.fun.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

# Y1 is the consumption plan: billed per execution, with a free monthly grant, and the app is
# unloaded when idle - the cold start the function lab asks you to measure.
resource "azurerm_service_plan" "fun" {
  name                = "cc26funplan${var.region}${var.suffix}"
  resource_group_name = azurerm_resource_group.fun.name
  location            = azurerm_resource_group.fun.location
  os_type             = "Linux"
  sku_name            = "Y1"
}

# Where context.getLogger() output ends up. Application Insights needs a Log Analytics
# workspace behind it.
resource "azurerm_log_analytics_workspace" "fun" {
  name                = "cc26log${var.region}${var.suffix}"
  resource_group_name = azurerm_resource_group.fun.name
  location            = azurerm_resource_group.fun.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
}

resource "azurerm_application_insights" "fun" {
  name                = "cc26fun${var.region}${var.suffix}"
  resource_group_name = azurerm_resource_group.fun.name
  location            = azurerm_resource_group.fun.location
  workspace_id        = azurerm_log_analytics_workspace.fun.id
  application_type    = "java"
}

resource "azurerm_linux_function_app" "fun" {
  # Must match functionAppName in pom.xml, so that azure-functions:deploy deploys to this app
  # instead of creating another one.
  name                        = "cc26fun${var.region}${var.suffix}"
  resource_group_name         = azurerm_resource_group.fun.name
  location                    = azurerm_resource_group.fun.location
  service_plan_id             = azurerm_service_plan.fun.id
  storage_account_name        = azurerm_storage_account.fun.name
  storage_account_access_key  = azurerm_storage_account.fun.primary_access_key
  functions_extension_version = "~4"

  site_config {
    application_insights_connection_string = azurerm_application_insights.fun.connection_string

    application_stack {
      java_version = "21"
    }
  }

  # The keys reach the functions only this way.
  app_settings = local.app_settings

  # "mvn azure-functions:deploy" uploads the package and points this setting at it. Without
  # ignoring it, the next "terraform apply" would remove it and the functions would vanish.
  lifecycle {
    ignore_changes = [app_settings["WEBSITE_RUN_FROM_PACKAGE"]]
  }
}

#########################################  Maven  ###########################################

# The names Maven needs, written where it reads them on every run: .mvn/maven.config holds
# command-line options, and each -D here overrides the property of the same name in pom.xml.
# So after "terraform apply", "mvn clean package azure-functions:deploy" deploys to the function app
# created above, without copying anything into pom.xml. "terraform destroy" deletes the file,
# and pom.xml falls back to its placeholder names. The file is gitignored.
resource "local_file" "maven_config" {
  filename             = "${path.module}/.mvn/maven.config"
  file_permission      = "0644"
  directory_permission = "0755"
  content = join("\n", [
    "-DfunctionAppName=${azurerm_linux_function_app.fun.name}",
    "-DfunctionResourceGroup=${azurerm_resource_group.fun.name}",
    "-DfunctionAppRegion=${var.region}",
    "",
  ])
}
