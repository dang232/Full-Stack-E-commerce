module "vpc" {
  source = "../../modules/vpc"

  project            = "vnshop"
  environment        = "dev"
  vpc_cidr           = "10.0.0.0/16"
  enable_nat_gateway = true
  aws_region         = var.aws_region
}

module "eks" {
  source = "../../modules/eks"

  project                = "vnshop"
  environment            = "dev"
  subnet_ids             = module.vpc.private_subnet_ids
  cluster_version        = "1.30"
  endpoint_public_access = true
  node_instance_types    = ["t3.medium"]
  capacity_type          = "SPOT"
  node_desired_size      = 2
  node_max_size          = 4
  node_min_size          = 1
}

module "rds" {
  source = "../../modules/rds"

  project                 = "vnshop"
  environment             = "dev"
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  allowed_security_groups = []
  instance_class          = "db.t3.medium"
  allocated_storage       = 20
  max_allocated_storage   = 50
  db_name                 = "vnshop"
  db_username             = "vnshop_admin"
  db_password             = var.db_password
  multi_az                = false
}

module "elasticache" {
  source = "../../modules/elasticache"

  project                 = "vnshop"
  environment             = "dev"
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = module.vpc.private_subnet_ids
  allowed_security_groups = []
  node_type               = "cache.t3.micro"
  num_cache_clusters      = 1
  auth_token              = var.redis_auth_token
}

module "msk" {
  source = "../../modules/msk"

  project                 = "vnshop"
  environment             = "dev"
  vpc_id                  = module.vpc.vpc_id
  subnet_ids              = slice(module.vpc.private_subnet_ids, 0, 1)
  allowed_security_groups = []
  kafka_version           = "3.6.0"
  broker_count            = 1
  broker_instance_type    = "kafka.t3.small"
  broker_ebs_volume_size  = 20
}

module "ecr" {
  source = "../../modules/ecr"

  project     = "vnshop"
  environment = "dev"
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
