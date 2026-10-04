# =============================================================================
# AWS Bedrock Access for Tier 1 Inference
# Replaces self-hosted vLLM on GPU nodes.
# The EKS worker node IAM role is granted bedrock:InvokeModel so that
# Tier1Connector can call Llama 3 (meta.llama3-8b-instruct-v1:0) via the
# Bedrock Runtime API using the node's instance profile — no API keys needed.
# =============================================================================

data "aws_iam_role" "eks_node_role" {
  name = module.eks.eks_managed_node_groups["cpu_nodes"].iam_role_name
}

resource "aws_iam_role_policy" "bedrock_invoke" {
  name = "nexus-gateway-bedrock-invoke"
  role = data.aws_iam_role.eks_node_role.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowBedrockInvoke"
        Effect = "Allow"
        Action = [
          "bedrock:InvokeModel",
          "bedrock:InvokeModelWithResponseStream"
        ]
        Resource = [
          # Llama 3 8B Instruct — primary Tier 1 model
          "arn:aws:bedrock:${var.aws_region}::foundation-model/meta.llama3-8b-instruct-v1:0",
          # Llama 3 70B — fallback if 8B is insufficient
          "arn:aws:bedrock:${var.aws_region}::foundation-model/meta.llama3-70b-instruct-v1:0"
        ]
      }
    ]
  })
}

output "bedrock_model_id" {
  description = "Bedrock model ID to set in Tier1Connector application config"
  value       = "meta.llama3-8b-instruct-v1:0"
}
