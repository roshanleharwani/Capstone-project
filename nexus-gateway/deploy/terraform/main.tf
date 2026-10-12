terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  description = "AWS region"
  default     = "ap-south-1"
}

variable "cluster_name" {
  description = "EKS Cluster Name"
  default     = "nexus-gateway-cluster"
}

# Self-hosted LLM on GPU (ap-south-1 G/VT quota). Keep desired_size=0 until you opt in.
variable "gpu_llm_instance_type" {
  description = "GPU instance type for Tier-3 vLLM (must be covered by G/VT quota)"
  type        = string
  default     = "g4dn.xlarge"
}

variable "gpu_llm_desired_size" {
  description = "Desired GPU nodes. 0 = provision the node group but start no EC2 GPUs."
  type        = number
  default     = 0
}

variable "gpu_llm_max_size" {
  description = "Max GPU nodes (AWS G/VT instance limit in ap-south-1 is 4)"
  type        = number
  default     = 4
}

# ECR Repositories for the monolith and dashboard
resource "aws_ecr_repository" "nexus_proxy" {
  name                 = "nexus-gateway-proxy"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "nexus_dashboard" {
  name                 = "nexus-gateway-dashboard"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}
