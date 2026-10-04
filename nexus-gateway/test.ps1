$headers = @{ 
    "Content-Type" = "application/json"
    "Authorization" = "Bearer sk-nexus-test-key-12345"
    "X-Tenant-ID" = "CS Dept" 
}

Write-Host "--- TESTING TIER 1 (AWS Bedrock Llama 3) ---"
$body1 = '{"model": "tier1", "messages": [{"role": "user", "content": "What is the capital of France? My SSN is 123-45-6789."}]}'
$res1 = Invoke-RestMethod -Uri "http://127.0.0.1:8081/v1/chat/completions" -Method Post -Headers $headers -Body $body1
$res1 | ConvertTo-Json -Depth 5

Write-Host "`n--- TESTING TIER 2 (OpenAI GPT-4o) ---"
$body2 = '{"model": "tier2", "messages": [{"role": "user", "content": "What is the difference between AWS Bedrock and OpenAI? My SSN is 123-45-6789."}]}'
$res2 = Invoke-RestMethod -Uri "http://127.0.0.1:8081/v1/chat/completions" -Method Post -Headers $headers -Body $body2
$res2 | ConvertTo-Json -Depth 5
