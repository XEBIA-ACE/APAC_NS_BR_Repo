variable "project_id" {
  description = "Project identifier used for tagging."
  type        = string
  default     = "171f4434-79a6-4b92-b52a-74aef208755f"
}

variable "environment" {
  description = "Deployment environment (e.g., dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "region" {
  description = "AWS region for the EKS cluster."
  type        = string
  default     = "eu-west-1"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to use."
  type        = number
  default     = 1
}

variable "eks_cluster_name" {
  description = "Name of the EKS cluster."
  type        = string
  default     = "tms-core-eks"
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version (major.minor)."
  type        = string
  default     = "1.31"
}

variable "public_api_cidrs" {
  description = "CIDR blocks allowed to access the public EKS API endpoint."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "EC2 instance types for EKS managed node group."
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_desired_size" {
  description = "Desired number of nodes in the managed node group."
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum number of nodes in the managed node group."
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Maximum number of nodes in the managed node group."
  type        = number
  default     = 3
}

variable "cluster_service_ipv4_cidr" {
  description = "CIDR block for Kubernetes services (cluster IPs)."
  type        = string
  default     = null
}

variable "tags" {
  description = "Additional tags to apply to all resources."
  type        = map(string)
  default     = {}
}