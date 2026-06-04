provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "vnshop"
      Environment = "prod"
      ManagedBy   = "terraform"
    }
  }
}
