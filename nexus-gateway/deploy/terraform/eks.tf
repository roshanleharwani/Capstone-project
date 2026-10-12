module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = "1.32"

  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  cluster_endpoint_public_access = true

  enable_cluster_creator_admin_permissions = true

  eks_managed_node_group_defaults = {
    ami_type = "AL2_x86_64"
  }

  eks_managed_node_groups = {
    # CPU Node Group for Spring Boot Proxies, Next.js Dashboard, State Services (Helm)
    cpu_nodes = {
      name = "cpu-node-group"
      instance_types = ["t3.large"]
      min_size     = 2
      max_size     = 5
      desired_size = 2
    }

    # GPU Node Group for self-hosted Tier-3 LLM (vLLM / Llama-3-8B)
    # AWS quota (ap-south-1): G and VT instances — limit 4.
    # desired_size defaults to 0 so terraform apply does NOT start billable GPUs
    # until you deliberately scale up (see deploy/scripts/llm-gpu-scale.ps1).
    gpu_llm_nodes = {
      name           = "gpu-llm-node-group"
      instance_types = [var.gpu_llm_instance_type]
      ami_type       = "AL2_x86_64_GPU"
      capacity_type  = "ON_DEMAND"

      min_size     = 0
      max_size     = var.gpu_llm_max_size
      desired_size = var.gpu_llm_desired_size

      labels = {
        workload             = "llm"
        "nvidia.com/gpu"     = "true"
        "nexus.gateway/role" = "tier3-llm"
      }

      taints = [
        {
          key    = "nvidia.com/gpu"
          value  = "true"
          effect = "NO_SCHEDULE"
        }
      ]

      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = 100
            volume_type           = "gp3"
            delete_on_termination = true
          }
        }
      }
    }
  }

  tags = {
    Environment = "production"
    Project     = "NexusGateway"
  }
}
