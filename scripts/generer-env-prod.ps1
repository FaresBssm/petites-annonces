<#
.SYNOPSIS
  Genere deploy/.env.prod : configuration et secrets de PRODUCTION (fichier ignore par Git).

.DESCRIPTION
  - URL publiques sslip.io deduites de l'IP de la VM (https://app|api|auth.<ip-avec-tirets>.sslip.io)
  - mots de passe aleatoires (bases, admin Keycloak, utilisateurs de test du realm)
  - NOUVELLE paire RSA du realm (la paire de demo de .env.example est publique : jamais en production)
  - nouveau secret partage gateway -> backend
  Le contenu du fichier est ensuite colle dans le secret GitHub "PROD_ENV_FILE".
  Les valeurs ne sont jamais affichees dans la console.

.EXAMPLE
  .\scripts\generer-env-prod.ps1 -Ip 72.60.188.97 -DockerHub faresbssm
#>
param(
  [Parameter(Mandatory = $true)][string]$Ip,
  [Parameter(Mandatory = $true)][string]$DockerHub,
  [string]$Sortie = (Join-Path $PSScriptRoot "..\deploy\.env.prod"),
  [switch]$Ecraser
)
$ErrorActionPreference = "Stop"

if ((Test-Path $Sortie) -and -not $Ecraser) {
  throw "$Sortie existe deja (secrets en service ?). Relancer avec -Ecraser pour le regenerer."
}

function Aleatoire([int]$octets = 24) {
  $buf = New-Object byte[] $octets
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buf)
  [Convert]::ToBase64String($buf).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$openssl = (Get-Command openssl -ErrorAction SilentlyContinue).Source
if (-not $openssl) { $openssl = "C:\Program Files\Git\usr\bin\openssl.exe" }
if (-not (Test-Path $openssl)) { throw "openssl introuvable (installer Git for Windows)" }

# Nouvelle paire RSA 2048 du realm
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("rsa-" + [guid]::NewGuid())
New-Item -ItemType Directory $tmp | Out-Null
try {
  & $openssl genpkey -quiet -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp\private.pem"
  & $openssl pkey -in "$tmp\private.pem" -pubout -out "$tmp\public.pem"
  if ($LASTEXITCODE -ne 0) { throw "echec openssl" }
  $privee = ((Get-Content "$tmp\private.pem") | Where-Object { $_ -notmatch '-----' }) -join ''
  $publique = ((Get-Content "$tmp\public.pem") | Where-Object { $_ -notmatch '-----' }) -join ''
} finally {
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

$domaine = ($Ip -replace '\.', '-') + ".sslip.io"
$lignes = @(
  "# PetitesAnnonces - PRODUCTION ($domaine) - genere le $(Get-Date -Format 'yyyy-MM-dd HH:mm')",
  "# NE PAS COMMITER. Contenu a coller dans le secret GitHub PROD_ENV_FILE.",
  "",
  "PUBLIC_APP_URL=https://app.$domaine",
  "PUBLIC_API_URL=https://api.$domaine",
  "PUBLIC_AUTH_URL=https://auth.$domaine",
  "",
  "DOCKERHUB_NAMESPACE=$DockerHub",
  "IMAGE_TAG=latest",
  "",
  "POSTGRES_USER=postgres",
  "POSTGRES_PASSWORD=$(Aleatoire)",
  "KEYCLOAK_DB_USER=keycloak",
  "KEYCLOAK_DB_PASSWORD=$(Aleatoire)",
  "ANNONCES_DB_USER=annonces",
  "ANNONCES_DB_PASSWORD=$(Aleatoire)",
  "",
  "KC_BOOTSTRAP_ADMIN_USERNAME=kcadmin",
  "KC_BOOTSTRAP_ADMIN_PASSWORD=$(Aleatoire)",
  "",
  "REALM_ALICE_PASSWORD=$(Aleatoire 12)",
  "REALM_BOB_PASSWORD=$(Aleatoire 12)",
  "REALM_MODO_PASSWORD=$(Aleatoire 12)",
  "REALM_ADMIN_PASSWORD=$(Aleatoire 12)",
  "",
  "REALM_RSA_PRIVATE_KEY=$privee",
  "REALM_PUBLIC_KEY=$publique",
  "",
  "GATEWAY_SECRET=$(Aleatoire 32)"
)

# UTF-8 sans BOM, fins de ligne LF (fichier lu par docker compose sous Linux)
[IO.File]::WriteAllText([IO.Path]::GetFullPath($Sortie), (($lignes -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))

Write-Host "Fichier genere : $([IO.Path]::GetFullPath($Sortie))" -ForegroundColor Green
Write-Host "Variables : $((($lignes | Where-Object { $_ -match '^[A-Z_]+=' }) | ForEach-Object { ($_ -split '=')[0] }) -join ', ')"
Write-Host "Etape suivante : copier TOUT son contenu dans le secret GitHub PROD_ENV_FILE." -ForegroundColor Yellow
