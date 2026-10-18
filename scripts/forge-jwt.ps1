<#
.SYNOPSIS
  Fabrique un JWT RS256 signe, pour tester la gateway (token expire, autre emetteur...).

.DESCRIPTION
  -RealmKey : signe avec la cle privee du realm (REALM_RSA_PRIVATE_KEY du .env) -> signature valide pour Kong.
  sinon     : signe avec une cle RSA etrangere generee a la volee -> signature invalide pour Kong.
  Utilise openssl (fourni avec Git for Windows).

.EXAMPLE
  # Token expire depuis 1 minute, signe par le realm (GW-03)
  .\scripts\forge-jwt.ps1 -RealmKey -ExpiresIn -60
  # Token d'un autre Keycloak (GW-04)
  .\scripts\forge-jwt.ps1 -Issuer "http://evil.local:8180/realms/annonces"
#>
param(
  [string]$Issuer = "http://localhost:8180/realms/annonces",
  [string]$Subject = "sub-forge",
  [string[]]$Roles = @("USER"),
  [int]$ExpiresIn = 300,
  [switch]$RealmKey
)
$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url([byte[]]$bytes) {
  [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$openssl = (Get-Command openssl -ErrorAction SilentlyContinue).Source
if (-not $openssl) { $openssl = "C:\Program Files\Git\usr\bin\openssl.exe" }
if (-not (Test-Path $openssl)) { throw "openssl introuvable (installer Git for Windows)" }

$tmp = Join-Path ([IO.Path]::GetTempPath()) ("jwt-" + [guid]::NewGuid())
New-Item -ItemType Directory $tmp | Out-Null
try {
  $keyFile = Join-Path $tmp "key.pem"
  if ($RealmKey) {
    $envFile = Join-Path $PSScriptRoot "..\.env"
    $line = Get-Content $envFile | Where-Object { $_ -match '^\s*REALM_RSA_PRIVATE_KEY\s*=' } | Select-Object -First 1
    if (-not $line) { throw "REALM_RSA_PRIVATE_KEY absent du .env" }
    $b64 = ($line -split '=', 2)[1].Trim()
    $pem = "-----BEGIN PRIVATE KEY-----`n" + (($b64 -split '(.{64})' | Where-Object { $_ }) -join "`n") + "`n-----END PRIVATE KEY-----`n"
    [IO.File]::WriteAllText($keyFile, $pem)
  } else {
    & $openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $keyFile
    if ($LASTEXITCODE -ne 0) { throw "echec de la generation de cle openssl" }
  }

  # Horodatage Unix en UTC (Get-Date -UFormat %s renvoie l'heure LOCALE sous PowerShell 5.1)
  $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $header = @{ alg = "RS256"; typ = "JWT" } | ConvertTo-Json -Compress
  $payload = [ordered]@{
    iss = $Issuer; sub = $Subject; azp = "annonces-front"; typ = "Bearer"
    iat = $now; exp = $now + $ExpiresIn
    realm_access = @{ roles = $Roles }
  } | ConvertTo-Json -Compress -Depth 5

  $signingInput = (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))) + "." +
                  (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload)))
  $dataFile = Join-Path $tmp "data"; $sigFile = Join-Path $tmp "sig"
  [IO.File]::WriteAllText($dataFile, $signingInput)
  & $openssl dgst -sha256 -sign $keyFile -out $sigFile $dataFile
  if ($LASTEXITCODE -ne 0) { throw "echec de la signature openssl" }
  $signingInput + "." + (ConvertTo-Base64Url ([IO.File]::ReadAllBytes($sigFile)))
}
finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
