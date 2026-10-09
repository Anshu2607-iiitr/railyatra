param([Parameter(Mandatory=$true)][string]$AdminToken,[Parameter(Mandatory=$true)][string]$UserPassword)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$headers = @{Authorization="Bearer $AdminToken"}
$number = 'D' + [guid]::NewGuid().ToString('N').Substring(0,9).ToUpperInvariant()
$payload = @{
    trainNumber=$number; name='Admin smoke test'; type='EXPRESS'; source='RNC'; destination='NDLS'
    runningDays=@('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'); classes=@('SL')
    stops=@(
        @{stationCode='RNC';stopSequence=1;arrival=$null;departure='08:00:00';dayOffset=0;distanceKm=0},
        @{stationCode='BKSC';stopSequence=2;arrival='10:00:00';departure='10:05:00';dayOffset=0;distanceKm=100},
        @{stationCode='NDLS';stopSequence=3;arrival='20:00:00';departure=$null;dayOffset=0;distanceKm=1000}
    )
}
$created = Invoke-RestMethod "$base/api/admin/trains" -Method Post -Headers $headers -ContentType 'application/json' -Body ($payload | ConvertTo-Json -Depth 8)
if ($created.trainNumber -ne $number) { throw 'Create response did not match the saved train.' }
$payload.version=$created.version; $payload.name='Updated admin smoke test'; $payload.type='SUPERFAST'
$edited = Invoke-RestMethod "$base/api/admin/trains/$($created.id)" -Method Put -Headers $headers -ContentType 'application/json' -Body ($payload | ConvertTo-Json -Depth 8)
if ($edited.name -ne $payload.name -or $edited.type -ne 'SUPERFAST') { throw 'Edit did not persist.' }
$date = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,'India Standard Time').AddDays(1).ToString('yyyy-MM-dd')
$search = Invoke-RestMethod "$base/api/trains/search?from=BKSC&to=NDLS&date=$date&class=SL&page=0&size=50"
if (-not ($search.content | Where-Object trainNumber -eq $number)) { throw 'Intermediate route search did not return the created train.' }
$deactivated = Invoke-RestMethod "$base/api/admin/trains/$($created.id)/deactivate" -Method Patch -Headers $headers -ContentType 'application/json' -Body (@{version=$edited.version} | ConvertTo-Json)
if ($deactivated.active) { throw 'Deactivation did not persist.' }
$search = Invoke-RestMethod "$base/api/trains/search?from=BKSC&to=NDLS&date=$date&class=SL&page=0&size=50"
if ($search.content | Where-Object trainNumber -eq $number) { throw 'Deactivated train remained in search.' }
$user = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{identifier='traveller@demo.railyatra.invalid';password=$UserPassword} | ConvertTo-Json)
$denied=$false
try { Invoke-RestMethod "$base/api/admin/trains" -Headers @{Authorization="Bearer $($user.accessToken)"} | Out-Null }
catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 403) { $denied=$true } else { throw } }
if (-not $denied) { throw 'ROLE_USER was not blocked from admin train access.' }
$stats = Invoke-RestMethod "$base/api/admin/dashboard" -Headers $headers
Write-Host "PASS: create, edit, intermediate search, deactivate, ROLE_USER denial. Database trains: $($stats.totalTrains)." -ForegroundColor Green
Write-Host "An inactive smoke-test train ($number) is retained in the isolated demo database."
