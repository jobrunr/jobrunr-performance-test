# Long-lived resources: image repository, results bucket and the IAM role of the benchmark machines.

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.60, < 7.0"
    }
  }
}

variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "name" {
  type    = string
  default = "jobrunr-perf"
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = var.name }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_ecr_repository" "app" {
  name                 = var.name
  image_tag_mutability = "MUTABLE"
  force_delete         = true
}

resource "aws_s3_bucket" "results" {
  bucket_prefix = "${var.name}-results-"
}

resource "aws_iam_role" "node" {
  name = "${var.name}-node"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy" "node" {
  name = "${var.name}-node"
  role = aws_iam_role.node.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject"]
        Resource = "${aws_s3_bucket.results.arn}/runs/*"
      },
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter"]
        Resource = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.name}/*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "node" {
  name = "${var.name}-node"
  role = aws_iam_role.node.name
}

output "region" {
  value = var.region
}

output "name" {
  value = var.name
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "results_bucket" {
  value = aws_s3_bucket.results.bucket
}

output "instance_profile_name" {
  value = aws_iam_instance_profile.node.name
}

output "license_parameter_name" {
  value = "/${var.name}/license"
}
