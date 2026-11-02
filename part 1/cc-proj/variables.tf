variable "create_resource_group" {
  description = <<-EOT
    Whether to create the resource group. Set it to false if the group already exists - for
    instance because you made it in the portal - and Terraform will use it instead of trying
    to create it again.

    Terraform has no "create it if it is not there": a resource block means "this is mine, I
    made it", so pointing one at a group that already exists fails with "already exists - to
    be managed via Terraform this resource needs to be imported". The alternative to setting
    this to false is to adopt the group instead:

      terraform import azurerm_resource_group.rg[0] /subscriptions/<id>/resourceGroups/<name>

    With false, "terraform destroy" also leaves the group alone, which is usually what you
    want for a group you did not create.
  EOT
  type        = bool
  default     = true
}

variable "suffix" {
  description = "Your personal suffix. Every resource name derives from it, so pick one nobody else is using."
  type        = string
  default     = "4204"
}

variable "region" {
  description = "Azure region. Must be one word with no separators, since resource names are built from it."
  type        = string
  default     = "francecentral"
}

variable "cosmos_throughput" {
  description = "Provisioned RU/s for the database, shared by all containers. Deliberately low (L6)."
  type        = number
  default     = 400
}

variable "app_service_sku" {
  description = <<-EOT
    App Service plan tier. F1 is free and fine for checking that the application runs, but it is
    shared and throttled, so do not measure anything on it - the project statement says to avoid
    it for evaluation. Use B1 when you start collecting numbers.
  EOT
  type        = string
  default     = "F1"
}
