variable "project" {
  description = "Project name used for resource naming"
  type        = string
  default     = "vnshop"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC where MSK will be deployed"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for MSK broker nodes (one per broker)"
  type        = list(string)
}

variable "allowed_security_groups" {
  description = "List of security group IDs allowed to connect to MSK brokers"
  type        = list(string)
  default     = []
}

variable "kafka_version" {
  description = "Apache Kafka version for the MSK cluster"
  type        = string
  default     = "3.6.0"
}

variable "broker_count" {
  description = "Number of broker nodes in the MSK cluster"
  type        = number
  default     = 3
}

variable "broker_instance_type" {
  description = "EC2 instance type for MSK broker nodes"
  type        = string
  default     = "kafka.m5.large"
}

variable "broker_ebs_volume_size" {
  description = "EBS volume size in GiB for each MSK broker"
  type        = number
  default     = 100
}
