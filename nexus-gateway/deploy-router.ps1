
$ErrorActionPreference = 'Stop'
$REGION='ap-south-1'
$ACCOUNT='611597330624'
$REPO_URL="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
cmd.exe /c "aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REPO_URL"
docker build -t ${REPO_URL}/nexus-gateway-router:latest -f modules/router-dashboard/Dockerfile .
docker push ${REPO_URL}/nexus-gateway-router:latest
kubectl rollout restart deployment/router-dashboard -n nexus-gateway
Write-Host 'Router redeployed successfully.'

