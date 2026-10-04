# ============================================================
# NexusGateway Cluster Destroy Script
# Completely deletes the EKS cluster and Node Group to eliminate all AWS billing.
# WARNING: This is destructive. You will need to recreate the cluster from scratch next time!
# ============================================================

$CLUSTER   = "nexus-gateway-cluster"
$REGION    = "ap-south-1"
$NODEGROUP = aws eks list-nodegroups --cluster-name $CLUSTER --region $REGION --query 'nodegroups[0]' --output text

$REGION    = "ap-south-1"

Write-Host "==> Initiating permanent deletion of EKS Node Group..." -ForegroundColor Red
aws eks delete-nodegroup `
    --cluster-name $CLUSTER `
    --nodegroup-name $NODEGROUP `
    --region $REGION

Write-Host "==> Waiting for Node Group deletion to complete (can take 5-10 minutes)..." -ForegroundColor Yellow
aws eks wait nodegroup-deleted `
    --cluster-name $CLUSTER `
    --nodegroup-name $NODEGROUP `
    --region $REGION

Write-Host "==> Initiating permanent deletion of EKS Cluster..." -ForegroundColor Red
aws eks delete-cluster `
    --name $CLUSTER `
    --region $REGION

Write-Host "==> Waiting for Cluster deletion to complete (can take 5-10 minutes)..." -ForegroundColor Yellow
aws eks wait cluster-deleted `
    --name $CLUSTER `
    --region $REGION

Write-Host "======================================" -ForegroundColor Green
Write-Host "  AWS EKS Cluster successfully destroyed!" -ForegroundColor Green
Write-Host "  You will no longer incur the $0.10/hr control plane charge." -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
