$headers = @{ 
    "Content-Type" = "application/json"
    "Authorization" = "Bearer sk-nexus-test-key-12345"
}

$tenants = @(
    @{ Id="CS Dept"; Prompts=@("Short", "A slightly longer prompt about computer science.", "A very long prompt that goes on and on to consume more tokens and show up higher on the chart. What is the architecture of a transformer model and how does self-attention work? Explain in detail.") },
    @{ Id="Bio Dept"; Prompts=@("DNA structure?", "How does CRISPR work? Please provide a detailed overview of the process and ethical implications.") },
    @{ Id="Admin"; Prompts=@("Summarize meeting notes.") },
    @{ Id="Engineering"; Prompts=@("Calculate structural load.", "What is the best way to design a scalable microservices architecture using Spring Boot and Kubernetes? Please provide examples.") }
)

foreach ($tenant in $tenants) {
    foreach ($prompt in $tenant.Prompts) {
        $headers["X-Tenant-ID"] = $tenant.Id
        $body = '{"model": "tier2", "messages": [{"role": "user", "content": "' + $prompt + '"}]}'
        try {
            $res = Invoke-RestMethod -Uri "http://127.0.0.1:8081/v1/chat/completions" -Method Post -Headers $headers -Body $body
            Write-Host "Sent request for $($tenant.Id)"
        } catch {
            Write-Host "Error for $($tenant.Id) : $($_.Exception.Message)"
        }
        Start-Sleep -Milliseconds 200
    }
}
