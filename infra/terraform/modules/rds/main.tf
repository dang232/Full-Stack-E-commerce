resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-${var.environment}-rds-subnet-group"
  subnet_ids = var.subnet_ids

  tags = {
    Name = "${var.project}-${var.environment}-rds-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project}-${var.environment}-rds-sg"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = var.allowed_security_groups
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project}-${var.environment}-rds-sg"
  }
}

resource "aws_db_parameter_group" "main" {
  name   = "${var.project}-${var.environment}-postgres16"
  family = "postgres16"

  tags = {
    Name = "${var.project}-${var.environment}-postgres16"
  }
}

resource "aws_db_instance" "main" {
  identifier             = "${var.project}-${var.environment}-postgres"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = var.instance_class
  allocated_storage      = var.allocated_storage
  max_allocated_storage  = var.max_allocated_storage
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  parameter_group_name   = aws_db_parameter_group.main.name
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  multi_az               = var.multi_az
  storage_encrypted      = true
  deletion_protection    = true
  skip_final_snapshot    = false
  final_snapshot_identifier = "${var.project}-${var.environment}-postgres-final"

  tags = {
    Name = "${var.project}-${var.environment}-postgres"
  }
}
