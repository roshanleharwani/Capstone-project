#!/bin/bash
set -e

echo "Installing State & Telemetry services into EKS..."

# Add necessary Helm repositories
./windows-amd64/helm.exe repo add bitnami https://charts.bitnami.com/bitnami
./windows-amd64/helm.exe repo add strimzi https://strimzi.io/charts/
./windows-amd64/helm.exe repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
./windows-amd64/helm.exe repo update

# 1. PostgreSQL with pgvector (using Bitnami Postgres with custom image/init)
# Note: In production, use an image that has pgvector pre-installed (e.g., ankane/pgvector)
echo "Installing PostgreSQL..."
./windows-amd64/helm.exe upgrade --install postgres bitnami/postgresql \
  --namespace state --create-namespace \
  --set auth.postgresPassword=nexus_secret \
  --set auth.database=nexus_gateway \
  --set primary.extendedConfiguration="shared_preload_libraries = 'vector'" \
  --set primary.persistence.enabled=false

# 2. Valkey (using Bitnami Redis as it is a fork and fully compatible)
echo "Installing Valkey (Redis compatible)..."
./windows-amd64/helm.exe upgrade --install valkey bitnami/redis \
  --namespace state \
  --set auth.password=valkey_secret \
  --set architecture=standalone \
  --set master.persistence.enabled=false \
  --set replica.persistence.enabled=false

# 3. Apache Kafka (using Strimzi Operator)
echo "Installing Strimzi Kafka Operator..."
./windows-amd64/helm.exe upgrade --install strimzi-kafka strimzi/strimzi-kafka-operator \
  --namespace state

# Wait for operator to be ready, then apply Kafka cluster CRD
echo "To deploy the Kafka Cluster, run: kubectl apply -f ../configs/kafka-cluster.yaml"

# 4. MinIO
echo "Installing MinIO..."
./windows-amd64/helm.exe upgrade --install minio bitnami/minio \
  --namespace state \
  --set auth.rootUser=admin \
  --set auth.rootPassword=admin_secret \
  --set persistence.enabled=false

# 5. NGINX Ingress Controller
echo "Installing NGINX Ingress Controller..."
./windows-amd64/helm.exe upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.type=LoadBalancer \
  --set controller.service.annotations."service\.beta\.kubernetes\.io/aws-load-balancer-type"="nlb"

echo "Helm installations initiated!"
