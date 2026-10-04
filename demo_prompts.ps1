$gatewayUrl = "http://localhost:8080/v1/chat/completions"

$headers = @{
    "Content-Type" = "application/json"
    "X-API-Key"    = "sk-nexus-test-key-12345"
    "X-Tenant-ID"  = "cs-dept"
}

$testCases = @(
    @{
        name = "1. Fast Path (Simple Greeting)"
        desc = "A very short prompt that skips complex routing and directly hits the cheap Tier 1 model."
        content = "Hi, how are you today?"
    },
    @{
        name = "2. Complex Reasoning (OpenAI / Tier 2 Routing)"
        desc = "A complex coding and math question. The CompressionPredictor will see this has low redundancy, and the ModelClassifier will route it to Tier 2 (OpenAI)."
        content = "Please write a complex Python script that calculates the eigenvalues of a 4x4 matrix, including all the mathematical derivations and a proof of the Cayley-Hamilton theorem."
    },
    @{
        name = "3. PII Detection (Forced Tier 1)"
        desc = "A prompt containing sensitive PII (credit card). The Ingress Privacy Shield will mask the PII and force the request to Tier 1 (AWS Bedrock) to ensure data never leaves the VPC."
        content = "My name is John Doe and my credit card number is 4532-1234-5678-9012. Please do not share this with anyone."
    },
    @{
        name = "4. API Secret Leak Prevention"
        desc = "A prompt containing an AWS access key. The Entropy Scanner detects it and masks it."
        content = "Here is my config file: AKIAIOSFODNN7EXAMPLE. Please help me debug why I am getting an auth error."
    },
    @{
        name = "5. Cascade Guard Escalate (Tier 1 -> Tier 2)"
        desc = "A prompt asking for something Tier 1 might fail at. If Tier 1 hallucinates or gives a low-confidence answer, the CascadeGuard catches it and escalates it to Tier 2 automatically."
        content = "As an AI language model, I cannot answer this, but what is the exact mechanism of horizontal gene transfer in extremophiles?"
    }
)

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host " Nexus Gateway Interactive Demo" -ForegroundColor Cyan
Write-Host " Make sure the Dashboard is open on http://localhost:3000" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host ""

foreach ($test in $testCases) {
    Write-Host "-> Executing Test: $($test.name)" -ForegroundColor Yellow
    Write-Host "   Description: $($test.desc)" -ForegroundColor DarkGray
    
    $body = @{
        messages = @(
            @{
                role = "user"
                content = $test.content
            }
        )
        model = "auto"
    } | ConvertTo-Json -Depth 5
    
    try {
        $response = Invoke-RestMethod -Uri $gatewayUrl -Method Post -Headers $headers -Body $body
        Write-Host "   [SUCCESS] Received response!" -ForegroundColor Green
        
        $tier = "Unknown"
        if ($response.model -like "*gpt*") {
            $tier = "Tier 2 (OpenAI)"
        } else {
            $tier = "Tier 1 (Bedrock/Local)"
        }
        
        Write-Host "   Model Used: $tier" -ForegroundColor Magenta
        Write-Host "   Response snippet: $($response.choices[0].message.content.Substring(0, [math]::Min(60, $response.choices[0].message.content.Length)))..." -ForegroundColor White
    } catch {
        Write-Host "   [ERROR] Request failed: $_" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "Waiting 5 seconds before next request so you can see the dashboard update..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 5
}

Write-Host "Demo complete! Check the Dashboard for the updated Analytics." -ForegroundColor Cyan
