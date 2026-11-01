variable "create_shared_resources" {
  description = <<-EOT
    Whether to create the resources the functions share with the web app in cc-proj: the
    resource group and the Cosmos DB account, database and containers.

    Leave it false if you have run "terraform apply" in cc-proj - this script then looks them up
    by name, and "terraform destroy" here leaves them alone. Set it to true only if you want the
    functions without the web app; after that, do not run "terraform apply" in cc-proj for the
    same suffix and region, since it would try to create them again.
  EOT
  type        = bool
  default     = false
}

variable "create_resource_group" {
  description = <<-EOT
    Only used when create_shared_resources is true. Set it to false if the resource group
    already exists - for instance because you made it in the portal - and only the database is
    created. See the same variable in cc-proj for why Terraform cannot work this out by itself.
  EOT
  type        = bool
  default     = true
}

variable "suffix" {
  description = "Your personal suffix. Must be the same as in cc-proj, since the names of the shared resources derive from it."
  type        = string
  default     = "4204"
}

variable "region" {
  description = "Azure region. Must be the same as in cc-proj, and one word with no separators, since resource names are built from it."
  type        = string
  default     = "francecentral"
}

variable "cosmos_throughput" {
  description = "Only used when create_shared_resources is true. Provisioned RU/s for the database, shared by all containers. Deliberately low (L6)."
  type        = number
  default     = 400
}
