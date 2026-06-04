module "vpc" {
  source = "../../modules/vpc"

  project            = "vnshop"
  environment        = "prod"
  vpc_cidr           = "10.1.0.0/16"
  enable_nat_gateway = true
  aws_region         = var.aws_region
}

module "eks" {
  source = "../../modules/eks"

  project                = "vnshop"
  environment            = "prod"
  subnet_ids             = module.vpc.private_subnet_ids
  cluster_version        = "1.30"
  endpoint_public_access = false
  node_instance_types    = ["t3.large"]
  capacity_type          = "ON_DEMAND"
  node_desired_size      = 3
  node_max_size          = 10
  node_min_size          = 2
}

module "rds" {
  source = "../../modules/rds"

  project                 = "vnshop"
  environment             = "prod"
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  allowed_security_groups = []
  instance_class          = "db.t3.xlarge"
  allocated_storage       = 100
  max_allocated_storage   = 500
  db_name                 = "vnshop"
  db_username             = "vnshop_admin"
  db_password             = var.db_password
  multi_az                = true
}

module "elasticache" {
  source = "../../modules/elasticache"

  project                 = "vnshop"
  environment             = "prod"
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  allowed_security_groups = []
  node_type               = "cache.r6g.large"
  num_cache_clusters      = 2
  auth_token              = var.redis_auth_token
}

module "msk" {
  source = "../../modules/msk"

  project                 = "vnshop"
  environment             = "prod"
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  allowed_security_groups = []
  kafka_version           = "3.6.0"
  broker_count            = 3
  broker_instance_type    = "kafka.m5.large"
  broker_ebs_volume_size  = 100
}

module "ecr" {
  source = "../../modules/ecr"

  project     = "vnshop"
  environment = "prod"
  service_names = [
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
