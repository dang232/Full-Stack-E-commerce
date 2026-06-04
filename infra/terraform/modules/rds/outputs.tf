output "endpoint" {
  description = "Connection endpoint for the RDS instance"
  value       = aws_db_instance.main.endpoint
}

output "db_name" {
  description = "Name of the database"
  value       = aws_db_instance.main.db_name
}

output "port" {
  description = "Port the RDS instance is listening on"
  value       = aws_db_instance.main.port
}
