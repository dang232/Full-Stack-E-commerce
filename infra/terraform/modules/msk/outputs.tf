output "bootstrap_brokers_sasl_scram" {
  description = "Bootstrap brokers string for SASL/SCRAM authentication"
  value       = aws_msk_cluster.main.bootstrap_brokers_sasl_scram
}

output "bootstrap_brokers_tls" {
  description = "Bootstrap brokers string for TLS authentication"
  value       = aws_msk_cluster.main.bootstrap_brokers_tls
}

output "cluster_arn" {
  description = "ARN of the MSK cluster"
  value       = aws_msk_cluster.main.arn
}

output "zookeeper_connect_string" {
  description = "Zookeeper connection string for the MSK cluster"
  value       = aws_msk_cluster.main.zookeeper_connect_string
}
