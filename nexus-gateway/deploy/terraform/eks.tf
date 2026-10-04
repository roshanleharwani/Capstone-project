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
  }

  tags = {
    Environment = "production"
    Project     = "NexusGateway"
  }
}
