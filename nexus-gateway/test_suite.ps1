$BaseUrl = "http://localhost:8080/v1/chat/completions"
$Headers = @{
    "Content-Type" = "application/json"
    "X-API-Key" = "sk-nexus-test-key-12345"
}

Write-Host "--- TEST 1: PII Redaction (Multiple Entities) ---"
$payload1 = @{
    model = "gpt-3.5-turbo"
    messages = @(
        @{ role = "user"; content = "My name is John Doe, my email is john.doe@example.com, and my phone number is 555-0199." }
    )
} | ConvertTo-Json -Depth 10

try {
    $response1 = Invoke-RestMethod -Uri $BaseUrl -Method Post -Headers $Headers -Body $payload1
    Write-Host "Response received. Content:"
    Write-Host $response1.choices[0].message.content
} catch {
    Write-Host "Failed: $_"
}

Write-Host "`n--- TEST 2: Semantic Caching (Context Compression) ---"
$payload2 = @{
    model = "gpt-3.5-turbo"
    messages = @(
        @{ role = "user"; content = "What is the capital of France?" }
    )
} | ConvertTo-Json -Depth 10

try {
    Write-Host "First Request (Cache Miss):"
    $t1 = Measure-Command {
        $r1 = Invoke-RestMethod -Uri $BaseUrl -Method Post -Headers $Headers -Body $payload2
    }
    Write-Host "Time: $($t1.TotalMilliseconds)ms"

    Write-Host "Second Request (Cache Hit):"
    $t2 = Measure-Command {
        $r2 = Invoke-RestMethod -Uri $BaseUrl -Method Post -Headers $Headers -Body $payload2
    }
    Write-Host "Time: $($t2.TotalMilliseconds)ms"
} catch {
    Write-Host "Failed: $_"
}

Write-Host "`n--- TEST 3: Market Quota / Different Tenant Routing ---"
$HeadersAdmin = @{
    "Content-Type" = "application/json"
    "X-API-Key" = "sk-nexus-test-key-admin"
}
$payload3 = @{
    model = "gpt-4o"
    messages = @(
        @{ role = "user"; content = "Generate a complex algorithm." }
    )
} | ConvertTo-Json -Depth 10

try {
    $response3 = Invoke-RestMethod -Uri $BaseUrl -Method Post -Headers $HeadersAdmin -Body $payload3
    Write-Host "Admin Tenant Response received. Content:"
    Write-Host $response3.choices[0].message.content
} catch {
    Write-Host "Failed: $_"
}
