variable "project" {
  description = "Project name used for resource naming"
  type        = string
  default     = "vnshop"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
}

variable "service_names" {
  description = "List of microservice names for which ECR repositories will be created"
  type        = list(string)
  default = [
    "api-gateway",
    "auth-service",
    "user-service",
    "product-service",
    "inventory-service",
    "order-service",
    "payment-service",
    "cart-service",
    "notification-service",
    "search-service",
    "review-service",
    "recommendation-service",
    "shipping-service",
    "analytics-service",
    "messaging-service",
    "admin-service"
  ]
}
