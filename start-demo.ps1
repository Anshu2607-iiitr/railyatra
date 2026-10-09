param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

foreach ($command in @('java','mvn','npm')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command is required. Install Java 21, Maven and Node.js, then rerun this script." }
}
$javaVersion = (& cmd /c 'java -version 2>&1' | Out-String)
if ($javaVersion -notmatch 'version "21[\.]') { throw "Java 21 is required. Current runtime: $javaVersion" }
foreach ($port in @(8080,5173)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is occupied. Stop your existing RailYatra server on that port, then rerun."
    }
}
function New-DemoSecret {
    $bytes = New-Object byte[] 36
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes); return [Convert]::ToBase64String($bytes) } finally { $random.Dispose() }
}
$env:RAILYATRA_JWT_SECRET = New-DemoSecret
$env:RAILYATRA_DEMO_ADMIN_PASSWORD = New-DemoSecret
$env:RAILYATRA_DEMO_USER_PASSWORD = New-DemoSecret
$env:VITE_API_BASE_URL = 'http://localhost:8080'
# Tests run in the normal test profile; the demo profile is enabled only after they pass.
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Push-Location .\backend
try {
    if (-not $SkipTests) {
        & mvn '-Dtest=AdminTrainServiceTest,AdminDashboardRepositoryTest,AdminDashboardSecurityTest,SyntheticRailwayDataGeneratorTest' test
        if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed. Read the Maven output above; demo was not started.' }
    }
    & mvn '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed. Demo was not started.' }
} finally { Pop-Location }
Push-Location .\frontend
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & npm test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

$backendPath = Join-Path $PSScriptRoot 'backend'
$frontendPath = Join-Path $PSScriptRoot 'frontend'
$backendCommand = "Set-Location '" + $backendPath.Replace("'", "''") + "'; java -jar .\target\railyatra-api-0.1.0.jar --spring.profiles.active=demo"
$frontendCommand = "Set-Location '" + $frontendPath.Replace("'", "''") + "'; npm run dev -- --host localhost --port 5173 --strictPort"
# Children inherit generated credentials through their environment; no credentials in command lines or files.
Start-Process powershell.exe -ArgumentList @('-NoExit','-Command',$backendCommand) | Out-Null
Start-Process powershell.exe -ArgumentList @('-NoExit','-Command',$frontendCommand) | Out-Null

$ready = $false
for ($attempt = 0; $attempt -lt 180; $attempt++) {
    try {
        $health = Invoke-RestMethod 'http://localhost:8080/api/health' -TimeoutSec 2
        if ($health.status -eq 'UP') {
            # Health may respond before startup import/account initialization has finished.
            $loginBody = @{identifier='admin@demo.railyatra.invalid';password=$env:RAILYATRA_DEMO_ADMIN_PASSWORD} | ConvertTo-Json
            $admin = Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 3
            $ready = $true; break
        }
    } catch { Start-Sleep -Seconds 2 }
}
if (-not $ready) { throw 'Demo did not become ready. Check the backend window for the startup error.' }
& (Join-Path $PSScriptRoot 'scripts\verify-admin-trains.ps1') -AdminToken $admin.accessToken -UserPassword $env:RAILYATRA_DEMO_USER_PASSWORD
Write-Host "`nRailYatra demo is ready: http://localhost:5173" -ForegroundColor Green
Write-Host "Admin: admin@demo.railyatra.invalid"
Write-Host "Password: $env:RAILYATRA_DEMO_ADMIN_PASSWORD"
Write-Host "Traveller: traveller@demo.railyatra.invalid"
Write-Host "Password: $env:RAILYATRA_DEMO_USER_PASSWORD"
Write-Host 'Use Admin > Manage trains. For booking, search RNC to NDLS, tomorrow, SL or 3A.'
Write-Host 'Data and payments are simulated. Close both server windows to stop the demo.'
Start-Process 'http://localhost:5173/login'
