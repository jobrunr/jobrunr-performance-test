# Benchmark cluster: one Postgres database (EC2 machine or Aurora) and N application nodes.
# EC2 machines terminate themselves after ttl_hours; Aurora keeps running until terraform destroy.

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.60, < 7.0"
    }
  }
}

data "terraform_remote_state" "shared" {
  backend = "local"
  config  = { path = "${path.module}/../shared/terraform.tfstate" }
}

locals {
  shared = data.terraform_remote_state.shared.outputs
  name   = local.shared.name
}

provider "aws" {
  region = local.shared.region
  default_tags {
    tags = { Project = local.name }
  }
}

variable "node_count" {
  description = "Amount of application nodes; 1 = single-node baseline."
  type        = number
  default     = 1
  validation {
    condition     = var.node_count >= 1
    error_message = "node_count must be at least 1 (node 0 creates the jobs and measures the run)."
  }
}

variable "node_instance_type" {
  description = "JobRunr's default worker count scales with vCPUs; use c7i.4xlarge to compare with the Hetzner EX44."
  type        = string
  default     = "c7i.xlarge"
}

variable "db_type" {
  description = "ec2: Postgres on an EC2 machine with local NVMe; aurora: Aurora PostgreSQL I/O-Optimized."
  type        = string
  default     = "ec2"
  validation {
    condition     = contains(["ec2", "aurora"], var.db_type)
    error_message = "db_type must be ec2 or aurora."
  }
}

variable "db_instance_type" {
  description = "db_type=ec2 only. Must have local NVMe instance storage (m6id, c6id, i4i, ...) for the data directory."
  type        = string
  default     = "m6id.4xlarge"
}

variable "aurora_instance_class" {
  type    = string
  default = "db.r7g.4xlarge"
}

variable "aurora_postgres_version" {
  description = "Latest available minor of this version is used."
  type        = string
  default     = "18"
}

variable "availability_zone" {
  description = "Defaults to the first AZ that offers both node_instance_type and db_instance_type."
  type        = string
  default     = null
}

variable "use_placement_group" {
  description = "Cluster placement group for lower network latency; launches can fail for lack of capacity, especially with mixed instance types."
  type        = bool
  default     = false
}

variable "ttl_hours" {
  description = "Machines terminate themselves after this many hours."
  type        = number
  default     = 6
}

variable "postgres_max_connections" {
  type    = number
  default = 500
}

variable "postgres_extra_args" {
  description = "db_type=ec2 only. Extra postgres settings, e.g. [\"shared_buffers=8GB\"]."
  type        = list(string)
  default     = []
}

data "aws_availability_zones" "available" {
  state = "available"
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

data "aws_ec2_instance_type_offerings" "node" {
  location_type = "availability-zone"
  filter {
    name   = "instance-type"
    values = [var.node_instance_type]
  }
}

data "aws_ec2_instance_type_offerings" "db" {
  location_type = "availability-zone"
  filter {
    name   = "instance-type"
    values = [var.db_instance_type]
  }
}

data "aws_vpc" "default" {
  default = true
}

locals {
  offered_azs = sort(tolist(setintersection(
    data.aws_availability_zones.available.names,
    data.aws_ec2_instance_type_offerings.node.locations,
    var.db_type == "ec2" ? data.aws_ec2_instance_type_offerings.db.locations : data.aws_availability_zones.available.names
  )))
  availability_zone = var.availability_zone != null ? var.availability_zone : try(local.offered_azs[0], "none")
  # Aurora requires a password of at least 8 characters
  db_password = "jobrunr-perf"
}

data "aws_subnet" "default" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = local.availability_zone
  default_for_az    = true

  lifecycle {
    precondition {
      condition     = contains(local.offered_azs, local.availability_zone)
      error_message = "${var.node_instance_type}${var.db_type == "ec2" ? " and ${var.db_instance_type}" : ""} not offered in AZ ${local.availability_zone}. AZs offering them: ${jsonencode(local.offered_azs)}."
    }
  }
}

resource "aws_security_group" "cluster" {
  name   = local.name
  vpc_id = data.aws_vpc.default.id

  ingress {
    description = "All traffic between benchmark machines"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_placement_group" "cluster" {
  count    = var.use_placement_group ? 1 : 0
  name     = local.name
  strategy = "cluster"
}

data "aws_ec2_instance_type" "node" {
  instance_type = var.node_instance_type
}

data "aws_ec2_instance_type" "db" {
  instance_type = var.db_instance_type
}

locals {
  node_arch = contains(data.aws_ec2_instance_type.node.supported_architectures, "arm64") ? "arm64" : "x86_64"
  db_arch   = contains(data.aws_ec2_instance_type.db.supported_architectures, "arm64") ? "arm64" : "x86_64"
}

data "aws_ssm_parameter" "node_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-${local.node_arch}"
}

data "aws_ssm_parameter" "db_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-${local.db_arch}"
}

resource "aws_instance" "db" {
  count                                = var.db_type == "ec2" ? 1 : 0
  ami                                  = nonsensitive(data.aws_ssm_parameter.db_ami.value)
  instance_type                        = var.db_instance_type
  subnet_id                            = data.aws_subnet.default.id
  vpc_security_group_ids               = [aws_security_group.cluster.id]
  iam_instance_profile                 = local.shared.instance_profile_name
  placement_group                      = var.use_placement_group ? aws_placement_group.cluster[0].id : null
  instance_initiated_shutdown_behavior = "terminate"
  user_data_replace_on_change          = true

  user_data = templatefile("${path.module}/db-user-data.sh.tftpl", {
    db_password       = local.db_password
    postgres_settings = concat(["max_wal_size=2GB", "random_page_cost=1.1", "shared_preload_libraries=pg_stat_statements", "pg_stat_statements.track=all", "max_connections=${var.postgres_max_connections}"], var.postgres_extra_args)
    ttl_minutes       = var.ttl_hours * 60
  })

  root_block_device {
    volume_type = "gp3"
    volume_size = 100
  }

  lifecycle {
    ignore_changes = [ami]
    precondition {
      condition     = data.aws_ec2_instance_type.db.instance_storage_supported
      error_message = "db_instance_type ${var.db_instance_type} has no local NVMe instance storage; use a type like m6id, c6id or i4i."
    }
  }

  tags = { Name = "${local.name}-db", Role = "db" }
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

data "aws_rds_engine_version" "aurora" {
  count   = var.db_type == "aurora" ? 1 : 0
  engine  = "aurora-postgresql"
  version = var.aurora_postgres_version
  latest  = true
}

resource "aws_db_subnet_group" "aurora" {
  count      = var.db_type == "aurora" ? 1 : 0
  name       = local.name
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_rds_cluster_parameter_group" "aurora" {
  count  = var.db_type == "aurora" ? 1 : 0
  name   = local.name
  family = data.aws_rds_engine_version.aurora[0].parameter_group_family

  parameter {
    name  = "random_page_cost"
    value = "1.1"
  }
  parameter {
    name  = "pg_stat_statements.track"
    value = "all"
  }
  parameter {
    name         = "max_connections"
    value        = var.postgres_max_connections
    apply_method = "pending-reboot"
  }
}

resource "aws_rds_cluster" "aurora" {
  count                           = var.db_type == "aurora" ? 1 : 0
  cluster_identifier              = local.name
  engine                          = "aurora-postgresql"
  engine_version                  = data.aws_rds_engine_version.aurora[0].version_actual
  storage_type                    = "aurora-iopt1"
  database_name                   = "jobrunr_perf"
  master_username                 = "jobrunr"
  master_password                 = local.db_password
  db_subnet_group_name            = aws_db_subnet_group.aurora[0].name
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.aurora[0].name
  vpc_security_group_ids          = [aws_security_group.cluster.id]
  backup_retention_period         = 1
  skip_final_snapshot             = true
  apply_immediately               = true
}

resource "aws_rds_cluster_instance" "aurora" {
  count              = var.db_type == "aurora" ? 1 : 0
  identifier         = "${local.name}-writer"
  cluster_identifier = aws_rds_cluster.aurora[0].id
  engine             = aws_rds_cluster.aurora[0].engine
  engine_version     = aws_rds_cluster.aurora[0].engine_version
  instance_class     = var.aurora_instance_class
  availability_zone  = local.availability_zone
  apply_immediately  = true
}

resource "aws_instance" "node" {
  count                                = var.node_count
  ami                                  = nonsensitive(data.aws_ssm_parameter.node_ami.value)
  instance_type                        = var.node_instance_type
  subnet_id                            = data.aws_subnet.default.id
  vpc_security_group_ids               = [aws_security_group.cluster.id]
  iam_instance_profile                 = local.shared.instance_profile_name
  placement_group                      = var.use_placement_group ? aws_placement_group.cluster[0].id : null
  instance_initiated_shutdown_behavior = "terminate"
  user_data_replace_on_change          = true

  user_data = templatefile("${path.module}/node-user-data.sh.tftpl", {
    ttl_minutes = var.ttl_hours * 60
  })

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
  }

  lifecycle {
    ignore_changes = [ami]
    precondition {
      condition     = var.node_count * 40 + 10 <= var.postgres_max_connections # 40 = Hikari max pool size per node
      error_message = "Not enough postgres_max_connections for ${var.node_count} nodes; raise it (this recreates the DB machine)."
    }
  }

  tags = { Name = "${local.name}-node-${count.index}", Role = count.index == 0 ? "node-0" : "worker" }
}

output "region" {
  value = local.shared.region
}

output "results_bucket" {
  value = local.shared.results_bucket
}

output "ecr_repository_url" {
  value = local.shared.ecr_repository_url
}

output "license_parameter_name" {
  value = local.shared.license_parameter_name
}

output "db_host" {
  value = var.db_type == "ec2" ? aws_instance.db[0].private_ip : aws_rds_cluster_instance.aurora[0].endpoint
}

output "db_password" {
  value = local.db_password
}

output "db_instance_type" {
  value = var.db_type == "ec2" ? var.db_instance_type : "aurora-iopt-${var.aurora_instance_class}"
}

output "node_instance_type" {
  value = var.node_instance_type
}

output "node_count" {
  value = var.node_count
}

output "node0_instance_id" {
  value = aws_instance.node[0].id
}

output "worker_instance_ids" {
  value = join(" ", slice(aws_instance.node[*].id, 1, var.node_count))
}
