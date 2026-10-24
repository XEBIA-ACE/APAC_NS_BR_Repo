data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(
    {
      Name        = "${var.eks_cluster_name}-vpc"
      ManagedBy   = "ace"
      Environment = var.environment
      Project     = var.project_id
    },
    var.tags
  )
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(
    {
      Name        = "${var.eks_cluster_name}-igw"
      ManagedBy   = "ace"
      Environment = var.environment
      Project     = var.project_id
    },
    var.tags
  )
}

resource "aws_subnet" "public" {
  for_each = { for idx, az in local.azs : az => idx }

  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, each.value)
  availability_zone       = each.key
  map_public_ip_on_launch = true

  tags = merge(
    {
      Name                        = "${var.eks_cluster_name}-public-${each.key}"
      "kubernetes.io/role/elb"    = "1"
      "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
      ManagedBy                   = "ace"
      Environment                 = var.environment
      Project                     = var.project_id
    },
    var.tags
  )
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(
    {
      Name        = "${var.eks_cluster_name}-public-rt"
      ManagedBy   = "ace"
      Environment = var.environment
      Project     = var.project_id
    },
    var.tags
  )
}

resource "aws_route_table_association" "public" {
  for_each       = aws_subnet.public
  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}