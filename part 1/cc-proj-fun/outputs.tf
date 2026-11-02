# Nothing here is secret. The keys stay in the app settings; read them from the portal or with
# "az functionapp config appsettings list" if you ever need them.

output "function_url" {
  description = "Base URL of the functions. The /rest prefix is routePrefix in host.json; the paths are the same as in cc-proj."
  value       = "https://${azurerm_linux_function_app.fun.default_hostname}/rest"
}

output "resource_group" {
  description = "Written to .mvn/maven.config as functionResourceGroup - nothing to copy into pom.xml."
  value       = azurerm_resource_group.fun.name
}

output "function_app_name" {
  description = "Written to .mvn/maven.config as functionAppName - nothing to copy into pom.xml."
  value       = azurerm_linux_function_app.fun.name
}
