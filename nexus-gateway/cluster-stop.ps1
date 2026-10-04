# ============================================================
# NexusGateway Cluster Stop Script
# Scales node group to 0 — stops all EC2 billing, keeps arch intact
# Run: powershell -ExecutionPolicy Bypass -File cluster-stop.ps1
# ============================================================

$CLUSTER   = "nexus-gateway-cluster"
$REGION    = "ap-south-1"

Write-Host "==> Fetching node group name dynamically..." -ForegroundColor Cyan
$NODEGROUP = aws eks list-nodegroups --cluster-name $CLUSTER --region $REGION --query "nodegroups[0]" --output text 2>&1
Write-Host "    Node group: $NODEGROUP" -ForegroundColor Gray

if ($NODEGROUP -eq "None" -or [string]::IsNullOrWhiteSpace($NODEGROUP)) {
    Write-Host "ERROR: Node group is missing or could not be found." -ForegroundColor Red
    exit 1
}

Write-Host "==> Scaling node group to 0 (stopping all EC2 nodes)..." -ForegroundColor Yellow
aws eks update-nodegroup-config `
    --cluster-name $CLUSTER `
    --nodegroup-name $NODEGROUP `
    --region $REGION `
    --scaling-config minSize=0,maxSize=5,desiredSize=0

Write-Host "==> Waiting for scale-down to complete..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

$status = aws eks describe-nodegroup `
    --cluster-name $CLUSTER `
    --nodegroup-name $NODEGROUP `
    --region $REGION `
    --query "nodegroup.status" --output text

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Cluster shutdown initiated!" -ForegroundColor Cyan
Write-Host "  Node group status : $status" -ForegroundColor Cyan
Write-Host "  EC2 nodes will terminate in ~2-3 min" -ForegroundColor Cyan
Write-Host "  EKS control plane + all K8s config preserved" -ForegroundColor Cyan
Write-Host ""
Write-Host "  To restart: .\cluster-start.ps1" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Cyan
