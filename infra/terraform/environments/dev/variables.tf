variable "aws_region" {
  description = "AWS region to deploy resources into"
  type        = string
  default     = "us-east-1"
}

variable "db_password" {
  description = "Master password for the RDS PostgreSQL instance"
  type        = string
  sensitive   = true
}

variable "redis_auth_token" {
  description = "Auth token for ElastiCache Redis transit encryption"
  type        = string
  sensitive   = true
}
