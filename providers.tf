terraform {
  required_version = ">= 1.5.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.70.0"
    }
  }
}

provider "aws" {
  region = var.region
}

# Optional: separate provider alias for us-east-1 if you later need ACM/global resources
provider "aws" {
  alias  = "use1"
  region = "us-east-1"
}