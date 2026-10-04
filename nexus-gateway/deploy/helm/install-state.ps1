# Install stateful services into EKS (PostgreSQL + Valkey)
# Run from nexus-gateway directory: powershell -ExecutionPolicy Bypass -File deploy\helm\install-state.ps1

$helmExe = "$PSScriptRoot\windows-amd64\helm.exe"
$ns      = "nexus-gateway"

Write-Host "==> Adding Bitnami Helm repo..." -ForegroundColor Cyan
& $helmExe repo add bitnami https://charts.bitnami.com/bitnami
& $helmExe repo update

Write-Host ""
Write-Host "==> [1/2] Installing PostgreSQL..." -ForegroundColor Cyan
& $helmExe upgrade --install postgres bitnami/postgresql `
    --namespace $ns `
    --set auth.postgresPassword=nexus_secret `
    --set auth.database=nexus_gateway `
    --set primary.persistence.enabled=false

Write-Host ""
Write-Host "==> [2/2] Installing Valkey (Redis-compatible)..." -ForegroundColor Cyan
& $helmExe upgrade --install valkey bitnami/redis `
    --namespace $ns `
    --set auth.password=valkey_secret `
    --set architecture=standalone `
    --set master.persistence.enabled=false

Write-Host ""
Write-Host "==> Waiting 60s for pods to initialise..." -ForegroundColor Yellow
Start-Sleep -Seconds 60

Write-Host ""
kubectl get pods -n $ns
