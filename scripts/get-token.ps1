<#
.SYNOPSIS
  Obtient un access token du realm "annonces" en rejouant le flux
  Authorization Code + PKCE (S256), comme le ferait le navigateur.

.DESCRIPTION
  Le client annonces-front n'autorise que le Standard flow (pas de password grant).
  Ce script fait donc ce que fait la SPA :
    1. genere un code_verifier aleatoire et son code_challenge = BASE64URL(SHA256(verifier))
    2. ouvre /auth avec response_type=code + code_challenge (S256) -> formulaire de login
    3. soumet le formulaire (username / password)            -> redirection ?code=...
    4. echange le code + code_verifier sur /token               -> access_token, refresh_token

.EXAMPLE
  .\scripts\get-token.ps1 -User alice                  # affiche le token (mot de passe lu dans .env)
  $t = .\scripts\get-token.ps1 -User modo -Show       # stocke le token, affiche header + payload decodes
  .\scripts\get-token.ps1 -User admin -Full            # reponse /token complete (refresh_token, expires_in...)
#>
param(
  [Parameter(Mandatory = $true)][string]$User,
  [string]$Password,
  [string]$KeycloakUrl = "http://localhost:8180",
  [string]$Realm = "annonces",
  [string]$ClientId = "annonces-front",
  [string]$RedirectUri = "http://localhost:4200/",
  [switch]$Show,
  [switch]$Full
)
$ErrorActionPreference = "Stop"

function ConvertTo-Base64Url([byte[]]$bytes) {
  [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
function ConvertFrom-Base64Url([string]$s) {
  $s = $s.Replace('-', '+').Replace('_', '/')
  switch ($s.Length % 4) { 2 { $s += '==' } 3 { $s += '=' } }
  [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($s))
}

# Mot de passe : parametre, sinon REALM_<USER>_PASSWORD dans le .env a la racine du projet
if (-not $Password) {
  $envFile = Join-Path $PSScriptRoot "..\.env"
  $key = "REALM_$($User.ToUpper())_PASSWORD"
  $line = Get-Content $envFile | Where-Object { $_ -match "^\s*$key\s*=" } | Select-Object -First 1
  if (-not $line) { throw "Pas de mot de passe : passer -Password ou definir $key dans .env" }
  $Password = ($line -split '=', 2)[1].Trim()
}

$oidc = "$KeycloakUrl/realms/$Realm/protocol/openid-connect"

# 1. PKCE : verifier aleatoire (32 octets -> 43 caracteres) et challenge S256
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$buf = New-Object byte[] 32; $rng.GetBytes($buf)
$verifier = ConvertTo-Base64Url $buf
$sha = [Security.Cryptography.SHA256]::Create()
$challenge = ConvertTo-Base64Url ($sha.ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier)))
$state = ConvertTo-Base64Url ([guid]::NewGuid().ToByteArray())

$jar = [IO.Path]::GetTempFileName()
try {
  # 2. Demande d'autorisation -> page de login Keycloak (cookies de session dans $jar)
  $authUrl = "$oidc/auth?client_id=$ClientId&response_type=code&scope=openid" +
             "&redirect_uri=$([uri]::EscapeDataString($RedirectUri))&state=$state" +
             "&code_challenge=$challenge&code_challenge_method=S256"
  $html = (curl.exe -s -c $jar -b $jar $authUrl) -join "`n"
  $m = [regex]::Match($html, 'id="kc-form-login"[^>]*action="([^"]+)"')
  if (-not $m.Success) { $m = [regex]::Match($html, 'action="([^"]+)"') }
  if (-not $m.Success) { throw "Formulaire de login introuvable (Keycloak demarre ? realm '$Realm' importe ?)" }
  $action = [Net.WebUtility]::HtmlDecode($m.Groups[1].Value)

  # 3. Soumission des identifiants -> 302 vers redirect_uri?code=...&state=...
  # (identifiants faux : Keycloak re-affiche le formulaire en 200, sans redirection)
  $location = "$(curl.exe -s -c $jar -b $jar -o NUL -w "%{redirect_url}" `
    --data-urlencode "username=$User" --data-urlencode "password=$Password" $action)"
  $code = [regex]::Match($location, '[?&]code=([^&]+)').Groups[1].Value
  if (-not $code) { throw "Login refuse pour '$User' (mauvais mot de passe ?)" }
  $retState = [regex]::Match($location, '[?&]state=([^&]+)').Groups[1].Value
  if ($retState -ne $state) { throw "state different : reponse non liee a notre demande" }

  # 4. Echange du code contre les tokens, en prouvant la possession du code_verifier
  $json = curl.exe -s -X POST "$oidc/token" `
    --data-urlencode "grant_type=authorization_code" --data-urlencode "client_id=$ClientId" `
    --data-urlencode "code=$code" --data-urlencode "redirect_uri=$RedirectUri" `
    --data-urlencode "code_verifier=$verifier"
  $tokens = ($json -join "") | ConvertFrom-Json
  if (-not $tokens.access_token) { throw "Echec de l'echange du code : $json" }
}
finally {
  Remove-Item $jar -ErrorAction SilentlyContinue
}

if ($Show) {
  $parts = $tokens.access_token.Split('.')
  Write-Host "--- header ---" -ForegroundColor Cyan
  Write-Host (ConvertFrom-Base64Url $parts[0] | ConvertFrom-Json | ConvertTo-Json -Depth 5)
  Write-Host "--- payload ---" -ForegroundColor Cyan
  Write-Host (ConvertFrom-Base64Url $parts[1] | ConvertFrom-Json | ConvertTo-Json -Depth 5)
}

if ($Full) { $tokens } else { $tokens.access_token }
