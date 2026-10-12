# =============================================================================
# Self-hosted Tier-3 LLM on AWS GPU (ap-south-1)
# -----------------------------------------------------------------------------
# Prerequisites (already approved for this account):
#   - EC2 G and VT Instance Limit = 4 in Asia Pacific (Mumbai)
# Node group is defined in eks.tf (gpu_llm_nodes) with desired_size=0 by default.
# This file exposes outputs + optional IAM for Hugging Face model pulls.
# Do NOT run terraform apply / scale-up from automation unless explicitly asked.
# =============================================================================

output "gpu_llm_node_group" {
  description = "EKS managed node group name for the Tier-3 GPU LLM"
  value       = try(module.eks.eks_managed_node_groups["gpu_llm_nodes"].node_group_id, null)
}

output "gpu_llm_instance_type" {
  description = "Instance type used for GPU LLM workers"
  value       = var.gpu_llm_instance_type
}

output "gpu_llm_desired_size" {
  description = "Configured desired GPU node count (0 keeps cost at $0 for GPUs)"
  value       = var.gpu_llm_desired_size
}

output "gpu_llm_service_dns" {
  description = "In-cluster DNS the router uses for Tier-3 inference"
  value       = "http://nexus-vllm.nexus-gateway.svc.cluster.local:8000"
}
