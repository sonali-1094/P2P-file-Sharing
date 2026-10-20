$ErrorActionPreference = 'Stop'
$baseUrl = 'http://localhost:8080'
$email = "smoke-$([guid]::NewGuid().ToString('N'))@example.test"
$securePassword = Read-Host 'Enter the test account password' -AsSecureString
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$password = $null
$token = $null

function Write-Check([string]$Name, [bool]$Passed, [string]$Detail) {
    $result = if ($Passed) { 'PASS' } else { 'FAIL' }
    Write-Output "$result - $Name ($Detail)"
}

try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
    $registerBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    try {
        $response = Invoke-WebRequest -Method Post -Uri "$baseUrl/api/auth/register" -ContentType 'application/json' -Body $registerBody
        $registerOk = [int]$response.StatusCode -eq 201
        Write-Check 'POST /api/auth/register' $registerOk "HTTP $([int]$response.StatusCode)"
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
        Write-Check 'POST /api/auth/register' $false "HTTP $status"
    }

    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    try {
        $response = Invoke-WebRequest -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body $loginBody
        $loginOk = [int]$response.StatusCode -eq 200
        $loginData = $response.Content | ConvertFrom-Json
        $token = $loginData.token
        $loginOk = $loginOk -and -not [string]::IsNullOrWhiteSpace($token)
        Write-Check 'POST /api/auth/login' $loginOk "HTTP $([int]$response.StatusCode); token received: $(-not [string]::IsNullOrWhiteSpace($token))"
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
        Write-Check 'POST /api/auth/login' $false "HTTP $status"
    }

    if (-not [string]::IsNullOrWhiteSpace($token)) {
        try {
            $response = Invoke-WebRequest -Method Get -Uri "$baseUrl/api/files" -Headers @{ Authorization = "Bearer $token" }
            Write-Check 'GET /api/files with Bearer token' ([int]$response.StatusCode -eq 200) "HTTP $([int]$response.StatusCode)"
        } catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
            Write-Check 'GET /api/files with Bearer token' $false "HTTP $status"
        }
    } else {
        Write-Check 'GET /api/files with Bearer token' $false 'skipped: login did not return a token'
    }

    try {
        $response = Invoke-WebRequest -Method Get -Uri "$baseUrl/api/files"
        Write-Check 'GET /api/files without token' $false "HTTP $([int]$response.StatusCode); expected 401"
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
        Write-Check 'GET /api/files without token' ($status -eq 401) "HTTP $status; expected 401"
    }
} finally {
    if ($passwordPtr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
    }
    $password = $null
    $securePassword = $null
    $token = $null
}
