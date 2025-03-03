variable "aws-region" {
  type    = string
  default = "eu-central-1"
}

variable "codename" {
  type    = string
  default = "jehlomat"
}

# Internal domain, its not visible publicly, but it should be domain we own
# to comply with best practices => it ensures the internal domain names are globally unique.
variable "codename-domain" {
  type    = string
  default = "jehlomat.cz"
}

variable "development-frontend-bucket-name" {
  type    = string
}

variable "development-domain-certificate-arn" {
  type    = string
}

variable "development-public-domain" {
  type    = string
}

variable "database-username" {
  type = string
}

variable "database-password" {
  type = string
}
