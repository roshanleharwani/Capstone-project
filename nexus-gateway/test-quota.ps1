$tenants = @("CS Dept", "Bio Dept", "Admin", "Engineering")

foreach ($t in $tenants) {
    $headers = @{ 
        "Content-Type" = "application/json"
        "Authorization" = "Bearer sk-nexus-test-key-12345"
        "X-Tenant-ID" = $t
    }

    $body = '{"model": "tier2", "messages": [{"role": "user", "content": "What is the capital of France?"}]}'
    try {
        $res = Invoke-RestMethod -Uri "http://127.0.0.1:8081/v1/chat/completions" -Method Post -Headers $headers -Body $body
        Write-Host "Sent request for $t"
    } catch {
        Write-Host "Error for $t : $($_.Exception.Message)"
    }
}
