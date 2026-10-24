module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "21.29.0"

  cluster_name    = var.eks_cluster_name
  cluster_version = var.kubernetes_version

  # Networking
  vpc_id     = aws_vpc.this.id
  subnet_ids = [for s in aws_subnet.public : s.id]

  # Control-plane endpoint access
  endpoint_private_access = true
  endpoint_public_access  = true

  # Restrict public API access if desired
  endpoint_public_access_cidrs = var.public_api_cidrs

  # Service CIDR (optional override)
  service_ipv4_cidr = var.cluster_service_ipv4_cidr

  # Control plane logging
  enabled_log_types = [
    "api",
    "audit",
    "authenticator",
  ]

  # CloudWatch log group configuration
  cloudwatch_log_group_retention_in_days = 90
  cloudwatch_log_group_class             = null
  create_cloudwatch_log_group            = true

  # Encryption for secrets at rest
  create_kms_key = true
  encryption_config = {
    resources        = ["secrets"]
    provider_key_arn = null
  }
  enable_kms_key_rotation = true

  # IAM and IRSA
  enable_irsa = true

  # EKS Managed node group for TMS workloads
  eks_managed_node_groups = {
    tms_core_ng = {
      desired_size = var.node_desired_size
      min_size     = var.node_min_size
      max_size     = var.node_max_size

      instance_types = var.node_instance_types
      capacity_type  = "ON_DEMAND"

      subnet_ids = [for s in aws_subnet.public : s.id]

      disk_size = 50

      labels = {
        "workload"    = "tms-core-backend"
        "environment" = var.environment
      }

      tags = {
        Name        = "${var.eks_cluster_name}-tms-core-ng"
        ManagedBy   = "ace"
        Environment = var.environment
        Project     = var.project_id
      }

      # Enable detailed monitoring and IMDSv2
      enable_monitoring = true
      metadata_options = {
        http_tokens                 = "required"
        http_put_response_hop_limit = 2
        http_endpoint               = "enabled"
      }
    }
  }

  # Cluster-level tags
  cluster_tags = {
    Name        = var.eks_cluster_name
    ManagedBy   = "ace"
    Environment = var.environment
    Project     = var.project_id
  }

  tags = merge(
    {
      ManagedBy   = "ace"
      Environment = var.environment
      Project     = var.project_id
    },
    var.tags
  )
}