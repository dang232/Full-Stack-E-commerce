variable "project" {
  description = "Project name used for resource naming"
  type        = string
  default     = "vnshop"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "enable_nat_gateway" {
  description = "Whether to create a NAT gateway for private subnets"
  type        = bool
  default     = true
}

variable "aws_region" {
  description = "AWS region for availability zone construction"
  type        = string
  default     = "us-east-1"
}
