terraform {
  backend "s3" {
    bucket         = "vnshop-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "vnshop-terraform-locks"
    encrypt        = true
  }
}
