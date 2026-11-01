# Nothing here is secret. The keys stay in the app settings; read them from the portal or with
# "az webapp config appsettings list" if you ever need them.

output "app_url" {
  description = "Base URL of the REST service."
  value       = "https://${azurerm_linux_web_app.app.default_hostname}/rest"
}

output "resource_group" {
  description = "Written to .mvn/maven.config for the azure-webapp plugin - nothing to copy into pom.xml."
  value       = local.rg_name
}

output "app_name" {
  description = "Written to .mvn/maven.config for the azure-webapp plugin - nothing to copy into pom.xml."
  value       = azurerm_linux_web_app.app.name
}

output "cosmosdb_account" {
  description = "Cosmos DB account, for the portal."
  value       = azurerm_cosmosdb_account.db.name
}
