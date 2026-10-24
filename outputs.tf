output "cluster_name" {
  description = "EKS cluster name."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS cluster endpoint."
  value       = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  description = "Base64-encoded certificate data required to communicate with the cluster."
  value       = module.eks.cluster_certificate_authority_data
}

output "cluster_oidc_issuer_url" {
  description = "OIDC issuer URL for the EKS cluster."
  value       = module.eks.cluster_oidc_issuer_url
}

output "cluster_security_group_id" {
  description = "ID of the EKS cluster security group."
  value       = module.eks.cluster_security_group_id
}

output "node_security_group_id" {
  description = "ID of the node security group."
  value       = module.eks.node_security_group_id
}

output "eks_managed_node_groups" {
  description = "Map of EKS managed node groups."
  value       = module.eks.eks_managed_node_groups
}

output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets."
  value       = [for s in aws_subnet.public : s.id]
}