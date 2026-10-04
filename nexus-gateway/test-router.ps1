$headers = @{ 
    "Content-Type" = "application/json"
}

$body = '{"prompt": "What is the capital of France?", "tenant_id": "test"}'

try {
    $res = Invoke-RestMethod -Uri "http://localhost:8084/v1/router/dispatch" -Method Post -Headers $headers -Body $body
    $res | ConvertTo-Json -Depth 5
} catch {
    Write-Host "StatusCode: $($_.Exception.Response.StatusCode.value__)"
    Write-Host "StatusDescription: $($_.Exception.Response.StatusDescription)"
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $reader.BaseStream.Position = 0
    $reader.DiscardBufferedData()
    Write-Host $reader.ReadToEnd()
}
