resource "aws_security_group" "msk" {
  name        = "${var.project}-${var.environment}-msk-sg"
  description = "Security group for MSK Kafka brokers"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 9092
    to_port         = 9096
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
    Name = "${var.project}-${var.environment}-msk-sg"
  }
}

resource "aws_msk_configuration" "main" {
  name              = "${var.project}-${var.environment}-msk-config"
  kafka_versions    = [var.kafka_version]
  description       = "MSK configuration for ${var.project} ${var.environment}"

  server_properties = <<-EOT
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=6
    log.retention.hours=168
  EOT
}

resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.project}-${var.environment}-kafka"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.broker_ebs_volume_size
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  client_authentication {
    sasl {
      scram = true
    }
  }

  broker_logs {
    cloudwatch_logs {
      enabled   = true
      log_group = "/aws/msk/${var.project}-${var.environment}"
    }
  }

  tags = {
    Name = "${var.project}-${var.environment}-kafka"
  }
}
