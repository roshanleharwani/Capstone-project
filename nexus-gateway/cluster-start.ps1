# ============================================================
# NexusGateway Cluster Start Script
# Handles two cases:
#   1. Cluster fully destroyed (terraform destroy was run) -> runs terraform apply
#   2. Cluster exists but nodes scaled to 0 (cluster-stop was run) -> scales back up
# Run: powershell -ExecutionPolicy Bypass -File cluster-start.ps1
# ============================================================

$CLUSTER   = "nexus-gateway-cluster"
$REGION    = "ap-south-1"
$NAMESPACE = "nexus-gateway"
$TF_DIR    = "$PSScriptRoot\deploy\terraform"

# ---- Step 1: Detect cluster state ----
Write-Host "==> Checking cluster state..." -ForegroundColor Cyan
$clusterStatus = aws eks describe-cluster --name $CLUSTER --region $REGION --query "cluster.status" --output text 2>$null

if ($clusterStatus -ne "ACTIVE") {
    # Cluster does not exist - need full rebuild via Terraform
    Write-Host "    Cluster not found. Running terraform apply to rebuild..." -ForegroundColor Yellow
    Write-Host ""

    Set-Location $TF_DIR

    Write-Host "==> [1/4] Terraform init..." -ForegroundColor Cyan
    terraform init -upgrade 2>&1

    Write-Host "==> [2/4] Terraform apply (this takes 15-20 min)..." -ForegroundColor Cyan
    terraform apply -auto-approve 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: terraform apply failed. Check output above." -ForegroundColor Red
        exit 1
    }

    Set-Location $PSScriptRoot

    # Update kubeconfig after creation
    Write-Host "==> [3/4] Updating kubeconfig..." -ForegroundColor Cyan
    aws eks update-kubeconfig --name $CLUSTER --region $REGION 2>&1

    # Get node group name dynamically from terraform output
    $NODEGROUP = aws eks list-nodegroups --cluster-name $CLUSTER --region $REGION --query "nodegroups[0]" --output text 2>&1
    Write-Host "    Node group: $NODEGROUP" -ForegroundColor Gray

    # Attach ECR read policy to node IAM role
    Write-Host "==> [4/4] Attaching ECR read policy to node IAM role..." -ForegroundColor Cyan
    $NODE_ROLE = aws eks describe-nodegroup --cluster-name $CLUSTER --nodegroup-name $NODEGROUP --region $REGION --query "nodegroup.nodeRole" --output text 2>&1
    if ($NODE_ROLE -and $NODE_ROLE -ne "None") {
        $NODE_ROLE_NAME = $NODE_ROLE.Split('/')[-1]
        aws iam attach-role-policy --role-name $NODE_ROLE_NAME --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly --region $REGION 2>$null
        Write-Host "    ECR policy attached to: $NODE_ROLE_NAME" -ForegroundColor Green
    }

} else {
    # Cluster exists - just scale the node group back up
    Write-Host "    Cluster ACTIVE. Scaling node group back to 3 nodes..." -ForegroundColor Green
    aws eks update-kubeconfig --name $CLUSTER --region $REGION 2>&1 | Out-Null

    $NODEGROUP = aws eks list-nodegroups --cluster-name $CLUSTER --region $REGION --query "nodegroups[0]" --output text 2>&1
    Write-Host "    Node group: $NODEGROUP"

    if ($NODEGROUP -eq "None" -or [string]::IsNullOrWhiteSpace($NODEGROUP)) {
        Write-Host "    Node group is missing. Running terraform apply to recreate it..." -ForegroundColor Yellow
        Set-Location $TF_DIR
        terraform apply -auto-approve 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: terraform apply failed." -ForegroundColor Red
            exit 1
        }
        Set-Location $PSScriptRoot
        $NODEGROUP = aws eks list-nodegroups --cluster-name $CLUSTER --region $REGION --query "nodegroups[0]" --output text 2>&1
    } else {
        aws eks update-nodegroup-config `
            --cluster-name $CLUSTER `
            --nodegroup-name $NODEGROUP `
            --region $REGION `
            --scaling-config minSize=2,maxSize=5,desiredSize=3 2>&1
    }

    # Ensure ECR policy is attached
    $NODE_ROLE = aws eks describe-nodegroup --cluster-name $CLUSTER --nodegroup-name $NODEGROUP --region $REGION --query "nodegroup.nodeRole" --output text 2>&1
    if ($NODE_ROLE -and $NODE_ROLE -ne "None") {
        $NODE_ROLE_NAME = $NODE_ROLE.Split('/')[-1]
        aws iam attach-role-policy --role-name $NODE_ROLE_NAME --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly --region $REGION 2>$null
        Write-Host "    ECR policy attached to: $NODE_ROLE_NAME" -ForegroundColor Green
    }
}

# ---- Step 2: Wait for nodes to be Ready ----
Write-Host ""
Write-Host "==> Waiting for nodes to become Ready (takes 3-5 minutes after creation)..." -ForegroundColor Yellow
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 30
    $nodeCount = kubectl get nodes --no-headers 2>$null | Select-String " Ready" | Measure-Object -Line | Select-Object -ExpandProperty Lines
    if ($nodeCount -gt 0) {
        $ready = $true
        Write-Host "==> $nodeCount node(s) Ready!" -ForegroundColor Green
        break
    }
    Write-Host "    Still waiting... ($($i * 30)s elapsed)"
}

if (-not $ready) {
    Write-Host "ERROR: Nodes did not become Ready in time. Check AWS console." -ForegroundColor Red
    exit 1
}

# ---- Step 3: Check if workloads need to be deployed ----
$nsPods = kubectl get pods -n $NAMESPACE --no-headers 2>$null | Measure-Object -Line | Select-Object -ExpandProperty Lines
if ($nsPods -eq 0) {
    Write-Host ""
    Write-Host "==> No pods found in $NAMESPACE. You need to deploy the workloads." -ForegroundColor Yellow
    Write-Host "    Run the build_and_push script (from the Gemini AI session) to push images and apply manifests." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "    Quick deploy command:" -ForegroundColor Cyan
    Write-Host "      kubectl apply -f deploy\kube\ -n $NAMESPACE" -ForegroundColor White
} else {
    Write-Host "==> Waiting for pods in $NAMESPACE to be Running..." -ForegroundColor Yellow
    kubectl wait --for=condition=ready pod --all -n $NAMESPACE --timeout=300s
}

# ---- Step 4: Port-forward ----
Write-Host ""
Write-Host "==> Starting port-forward for nexus-proxy on localhost:8081..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "kubectl port-forward svc/nexus-proxy -n $NAMESPACE 8081:8080"

Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "  NexusGateway is UP!" -ForegroundColor Green
Write-Host "  Dashboard : http://localhost:3000"  -ForegroundColor Green
Write-Host "  Proxy     : http://localhost:8081"  -ForegroundColor Green
Write-Host "  (Run 'npm run dev' in dashboard/ to start the UI)" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
