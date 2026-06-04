variable "bucket_name" {
  description = "Name of the S3 bucket for Terraform state storage"
  type        = string
  default     = "vnshop-terraform-state"
}

variable "lock_table_name" {
  description = "Name of the DynamoDB table for Terraform state locking"
  type        = string
  default     = "vnshop-terraform-locks"
}
