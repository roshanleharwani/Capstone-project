# ============================================================
# Scale the GPU LLM node group (Tier-3 vLLM) UP or DOWN.
# DOES NOT run automatically — invoke manually when you are ready to spend.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy\scripts\llm-gpu-scale.ps1 -Action up
#   powershell -ExecutionPolicy Bypass -File deploy\scripts\llm-gpu-scale.ps1 -Action down
#   powershell -ExecutionPolicy Bypass -File deploy\scripts\llm-gpu-scale.ps1 -Action status
#
# Quota (ap-south-1): G + VT instance limit = 4
# Default instance: g4dn.xlarge
# ============================================================

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("up", "down", "status")]
    [string]$Action,

    [string]$Cluster = "nexus-gateway-cluster",
    [string]$Region = "ap-south-1",
    [int]$DesiredUp = 1
)

$ErrorActionPreference = "Stop"

function Get-GpuNodeGroup {
    $groups = aws eks list-nodegroups --cluster-name $Cluster --region $Region --query "nodegroups" --output json | ConvertFrom-Json
    $match = $groups | Where-Object { $_ -like "*gpu*" -or $_ -like "*llm*" } | Select-Object -First 1
    if (-not $match) {
        # Fall back to terraform-style name prefix
        $match = $groups | Where-Object { $_ -like "gpu-llm-node-group*" } | Select-Object -First 1
    }
    if (-not $match) {
        throw "No GPU/LLM node group found on cluster $Cluster. Apply terraform with gpu_llm_nodes first."
    }
    return $match
}

$nodeGroup = Get-GpuNodeGroup
Write-Host "Node group: $nodeGroup" -ForegroundColor Cyan

switch ($Action) {
    "status" {
        aws eks describe-nodegroup `
            --cluster-name $Cluster `
            --nodegroup-name $nodeGroup `
            --region $Region `
            --query "nodegroup.{status:status,desired:scalingConfig.desiredSize,min:scalingConfig.minSize,max:scalingConfig.maxSize,instanceTypes:instanceTypes}" `
            --output table
        kubectl get nodes -l workload=llm -o wide 2>$null
        kubectl get pods -n nexus-gateway -l app=nexus-vllm -o wide 2>$null
    }
    "up" {
        if ($DesiredUp -lt 1 -or $DesiredUp -gt 4) {
            throw "DesiredUp must be between 1 and 4 (AWS G/VT quota)."
        }
        Write-Host "Scaling GPU node group to desired=$DesiredUp (this starts billable g4dn instances)..." -ForegroundColor Yellow
        aws eks update-nodegroup-config `
            --cluster-name $Cluster `
            --nodegroup-name $nodeGroup `
            --region $Region `
            --scaling-config "minSize=0,maxSize=4,desiredSize=$DesiredUp"

        Write-Host "After nodes are Ready, apply:" -ForegroundColor Green
        Write-Host "  kubectl apply -f deploy/kube/nvidia-device-plugin.yaml"
        Write-Host "  kubectl apply -f deploy/kube/nexus-vllm.yaml -n nexus-gateway"
        Write-Host "  kubectl rollout status deployment/nexus-vllm -n nexus-gateway"
    }
    "down" {
        Write-Host "Scaling GPU node group to 0 (stops GPU EC2 billing)..." -ForegroundColor Yellow
        kubectl scale deployment/nexus-vllm --replicas=0 -n nexus-gateway 2>$null
        aws eks update-nodegroup-config `
            --cluster-name $Cluster `
            --nodegroup-name $nodeGroup `
            --region $Region `
            --scaling-config "minSize=0,maxSize=4,desiredSize=0"
        Write-Host "GPU scale-down requested." -ForegroundColor Green
    }
}
