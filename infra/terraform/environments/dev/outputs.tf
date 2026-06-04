output "vpc_id" {
  description = "ID of the dev VPC"
  value       = module.vpc.vpc_id
}

output "eks_cluster_endpoint" {
  description = "Endpoint URL of the dev EKS cluster"
  value       = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  description = "Connection endpoint of the dev RDS instance"
  value       = module.rds.endpoint
}

output "redis_endpoint" {
  description = "Primary endpoint of the dev Redis cluster"
  value       = module.elasticache.primary_endpoint
}

output "kafka_brokers" {
  description = "Bootstrap brokers for the dev MSK cluster (SASL/SCRAM)"
  value       = module.msk.bootstrap_brokers_sasl_scram
}

output "ecr_repositories" {
  description = "Map of service name to ECR repository URL for dev"
  value       = module.ecr.repository_urls
}
