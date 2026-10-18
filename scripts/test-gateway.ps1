<#
.SYNOPSIS
  Tests de bout en bout a travers la gateway Kong : criteres GW, RL et MT de l'enonce.

.DESCRIPTION
  Pre-requis : stack demarree (docker compose up -d --wait), .env present.
  Les tokens sont obtenus par le vrai flux Authorization Code + PKCE (scripts/get-token.ps1),
  les tokens invalides sont fabriques par scripts/forge-jwt.ps1.

  Rate limiting (3 appels / minute / utilisateur) : hors section RL, un 429 est attendu
  (Retry-After) puis l'appel est rejoue. La suite complete dure donc ~5 minutes.

.EXAMPLE
  .\scripts\test-gateway.ps1                    # GW + RL + MT
  .\scripts\test-gateway.ps1 -Sections GW,RL    # sans les regles metier
#>
param(
  [ValidateSet("GW", "RL", "MT")][string[]]$Sections = @("GW", "RL", "MT"),
  [string]$Gateway = "http://localhost:8080"
)
$ErrorActionPreference = "Continue"
# Corps envoyes a curl en UTF-8 SANS BOM ([Text.Encoding]::UTF8 en ajoute un sous PowerShell 5.1 -> JSON illisible)
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
Push-Location (Join-Path $PSScriptRoot "..")

$script:resultats = New-Object System.Collections.ArrayList
$debut = Get-Date

# ---------------------------------------------------------------- outils

function Verifier([string]$id, [string]$libelle, [bool]$ok, [string]$detail = "") {
  [void]$script:resultats.Add([pscustomobject]@{ Id = $id; Ok = $ok; Libelle = $libelle })
  if ($ok) { Write-Host ("[ OK  ] {0,-6} {1}" -f $id, $libelle) -ForegroundColor Green }
  else {
    Write-Host ("[ECHEC] {0,-6} {1}" -f $id, $libelle) -ForegroundColor Red
    if ($detail) { Write-Host "                $detail" -ForegroundColor DarkGray }
  }
}

function Titre([string]$texte) { Write-Host "`n=== $texte ===" -ForegroundColor Cyan }

# Tokens renouveles toutes les 4 minutes (duree de vie : 5 minutes)
$script:tokens = @{}
function Token([string]$utilisateur) {
  $t = $script:tokens[$utilisateur]
  if (-not $t -or ((Get-Date) - $t.Date).TotalSeconds -gt 240) {
    $t = @{ Valeur = (& "$PSScriptRoot\get-token.ps1" -User $utilisateur); Date = Get-Date }
    $script:tokens[$utilisateur] = $t
  }
  $t.Valeur
}

function Claims([string]$jwt) {
  $p = $jwt.Split('.')[1].Replace('-', '+').Replace('_', '/')
  while ($p.Length % 4) { $p += '=' }
  [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
}

# Appel HTTP via curl.exe. Le corps JSON passe par l'entree standard (-d @-) : PowerShell 5.1
# deforme les arguments contenant des guillemets et des espaces.
function Appel {
  param([string]$Methode = "GET", [string]$Chemin, [string]$Jeton, [string]$Corps,
        [hashtable]$Entetes = @{}, [switch]$AttendreSiLimite)
  while ($true) {
    $fHeaders = [IO.Path]::GetTempFileName(); $fCorps = [IO.Path]::GetTempFileName()
    try {
      $opts = @('-s', '-m', '15', '-X', $Methode, '-D', $fHeaders, '-o', $fCorps, '-w', '%{http_code}')
      if ($Jeton) { $opts += @('-H', "Authorization: Bearer $Jeton") }
      foreach ($k in $Entetes.Keys) { $opts += @('-H', "${k}: $($Entetes[$k])") }
      $url = if ($Chemin -match '^https?://') { $Chemin } else { "$Gateway$Chemin" }
      if ($Corps) {
        $opts += @('-H', 'Content-Type: application/json', '-d', '@-')
        $code = $Corps | curl.exe @opts $url
      } else {
        $code = curl.exe @opts $url
      }
      $headers = @{}
      foreach ($ligne in [IO.File]::ReadAllLines($fHeaders)) {
        if ($ligne -match '^([^:]+):\s*(.*)$') { $headers[$matches[1].ToLower()] = $matches[2].Trim() }
      }
      $texte = [IO.File]::ReadAllText($fCorps, [Text.Encoding]::UTF8)
    } finally {
      Remove-Item $fHeaders, $fCorps -ErrorAction SilentlyContinue
    }
    $r = [pscustomobject]@{ Code = [int]("$code".Trim()); Headers = $headers; Texte = $texte; Json = $null }
    if ($texte -and ($texte.TrimStart() -match '^[\[{]')) { try { $r.Json = $texte | ConvertFrom-Json } catch { } }
    if ($AttendreSiLimite -and $r.Code -eq 429) {
      $attente = [int]$r.Headers['retry-after'] + 1
      Write-Host "                (rate limit atteint, attente de $attente s)" -ForegroundColor DarkYellow
      Start-Sleep -Seconds $attente
      continue
    }
    return $r
  }
}

# Requetes /api recues par le backend (log debug de HeaderAuthenticationFilter ; on ignore le healthcheck)
function RequetesBackend { @(docker compose logs backend | Select-String " /api/\S* - X_USER_ID=").Count }
function DerniereRequeteBackend { (docker compose logs backend | Select-String " /api/\S* - X_USER_ID=" | Select-Object -Last 1).ToString() }

$subAlice = (Claims (Token alice)).sub
$subBob = (Claims (Token bob)).sub

# ---------------------------------------------------------------- GW

if ($Sections -contains "GW") {
  Titre "Gateway : authentification centralisee (GW-01 a GW-11)"

  $avant = RequetesBackend
  $r = Appel -Chemin "/api/annonces"
  Start-Sleep -Milliseconds 500
  Verifier "GW-01" "Sans Authorization -> 401, backend non appele" (($r.Code -eq 401) -and ((RequetesBackend) -eq $avant)) "HTTP $($r.Code)"

  $t = Token alice; $p = $t.Split('.')
  $payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String((& { $s = $p[1].Replace('-', '+').Replace('_', '/'); while ($s.Length % 4) { $s += '=' }; $s })))
  $falsifie = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($payload -replace '"sub":"[^"]+"', "`"sub`":`"$subBob`""))).TrimEnd('=').Replace('+', '-').Replace('/', '_')
  $r = Appel -Chemin "/api/me" -Jeton ($p[0] + "." + $falsifie + "." + $p[2])
  Verifier "GW-02" "Signature alteree (sub remplace) -> 401" ($r.Code -eq 401) "HTTP $($r.Code) $($r.Texte)"

  $r = Appel -Chemin "/api/me" -Jeton (& "$PSScriptRoot\forge-jwt.ps1" -RealmKey -ExpiresIn -60)
  Verifier "GW-03" "Token expire -> 401" (($r.Code -eq 401) -and ($r.Texte -match 'expired')) "HTTP $($r.Code) $($r.Texte)"

  $r = Appel -Chemin "/api/me" -Jeton (& "$PSScriptRoot\forge-jwt.ps1" -Issuer "http://evil.local:8180/realms/annonces")
  Verifier "GW-04" "Token d'un autre Keycloak (iss inconnu) -> 401" (($r.Code -eq 401) -and ($r.Texte -match "iss")) "HTTP $($r.Code) $($r.Texte)"

  $r = Appel -Chemin "/api/me" -Jeton (Token alice) -AttendreSiLimite
  Verifier "GW-05" "Token d'alice -> 200, userId = sub, roles = [USER]" (($r.Code -eq 200) -and ($r.Json.userId -eq $subAlice) -and ((@($r.Json.roles) -join ',') -eq 'USER')) "HTTP $($r.Code) $($r.Texte)"

  Start-Sleep -Milliseconds 500
  $log = DerniereRequeteBackend
  Verifier "GW-08" "Backend recoit X_USER_ID et X_ROLE_ID, pas Authorization" (($log -match "X_USER_ID=$subAlice") -and ($log -match "X_ROLE_ID=USER") -and ($log -match "Authorization=absent")) $log

  $r = Appel -Chemin "/api/me" -Jeton (Token alice) -Entetes @{ "X_USER_ID" = $subBob } -AttendreSiLimite
  Verifier "GW-06" "X_USER_ID forge (sub de bob) ignore -> sub d'alice" (($r.Code -eq 200) -and ($r.Json.userId -eq $subAlice)) "HTTP $($r.Code) $($r.Texte)"

  $r = Appel -Methode POST -Chemin "/api/categories" -Jeton (Token alice) -Entetes @{ "X_ROLE_ID" = "ADMIN" } -Corps '{"libelle":"Piratage"}' -AttendreSiLimite
  Verifier "GW-07" "X_ROLE_ID: ADMIN forge -> POST /api/categories 403" ($r.Code -eq 403) "HTTP $($r.Code)"

  $r = Appel -Methode OPTIONS -Chemin "/api/annonces" -Entetes @{ "Origin" = "http://evil.local"; "Access-Control-Request-Method" = "GET"; "Access-Control-Request-Headers" = "authorization" }
  $rOk = Appel -Methode OPTIONS -Chemin "/api/annonces" -Entetes @{ "Origin" = "http://localhost:4200"; "Access-Control-Request-Method" = "GET"; "Access-Control-Request-Headers" = "authorization" }
  Verifier "GW-09" "Preflight depuis evil.local sans Access-Control-Allow-Origin" ((-not $r.Headers.ContainsKey('access-control-allow-origin')) -and ($rOk.Headers['access-control-allow-origin'] -eq 'http://localhost:4200')) "evil: $($r.Headers['access-control-allow-origin']) / 4200: $($rOk.Headers['access-control-allow-origin'])"

  $r = Appel -Chemin "http://localhost:8001/"
  Verifier "GW-10" "Admin API (localhost:8001) injoignable depuis l'hote" ($r.Code -eq 0) "HTTP $($r.Code)"

  $yml = Get-Content kong\kong.yml -Raw
  $noms = [regex]::Matches($yml, '(?m)^\s*-\s*name:\s*([\w-]+)\s*$') | ForEach-Object { $_.Groups[1].Value }
  $plugins = $noms | Where-Object { $_ -notin @('annonces-backend', 'api') }
  $oss = @('cors', 'jwt', 'identity-headers', 'rate-limiting')
  $dbless = ((docker compose exec -T gateway printenv KONG_DATABASE) -join '').Trim()
  $fichiers = (Test-Path kong\plugins\identity-headers\handler.lua) -and (Test-Path kong\plugins\identity-headers\schema.lua)
  Verifier "GW-11" "DB-less, config dans kong/kong.yml, plugin dans kong/plugins/, plugins OSS uniquement" (($dbless -eq 'off') -and $fichiers -and (@($plugins | Where-Object { $_ -notin $oss }).Count -eq 0) -and ($yml -notmatch '(?m)^\s*-\s*name:\s*openid-connect')) "KONG_DATABASE=$dbless plugins=$($plugins -join ',')"
}

# ---------------------------------------------------------------- RL

if ($Sections -contains "RL") {
  Titre "Gateway : rate limiting (RL-01 a RL-05)"
  $tA = Token alice; $tB = Token bob
  $attente = 61 - (Get-Date).Second
  Write-Host "                (attente de $attente s : debut d'une nouvelle fenetre d'une minute)" -ForegroundColor DarkYellow
  Start-Sleep -Seconds $attente
  $tA = Token alice; $tB = Token bob

  $codes = @(1..3 | ForEach-Object { (Appel -Chemin "/api/annonces" -Jeton $tA).Code })
  Verifier "RL-01" "3 appels d'alice en moins d'une minute -> 200" ((($codes | Where-Object { $_ -eq 200 }).Count) -eq 3) "codes : $($codes -join ',')"

  $avant = RequetesBackend
  $r = Appel -Chemin "/api/categories" -Jeton $tA -Entetes @{ "Origin" = "http://localhost:4200" }
  Start-Sleep -Milliseconds 500
  $retry = $r.Headers['retry-after']
  Verifier "RL-02" "4e appel (autre endpoint) -> 429 + Retry-After, backend non appele" (($r.Code -eq 429) -and ($retry -match '^\d+$') -and ((RequetesBackend) -eq $avant)) "HTTP $($r.Code) Retry-After=$retry"
  Verifier "RL-02b" "Le 429 est lisible par la SPA (CORS + Retry-After expose)" (($r.Headers['access-control-allow-origin'] -eq 'http://localhost:4200') -and ($r.Headers['access-control-expose-headers'] -match 'Retry-After'))

  $r = Appel -Chemin "/api/annonces" -Jeton $tB
  Verifier "RL-04" "Bob n'est pas bloque (compteur par utilisateur)" ($r.Code -eq 200) "HTTP $($r.Code)"

  $r = Appel -Methode OPTIONS -Chemin "/api/annonces" -Entetes @{ "Origin" = "http://localhost:4200"; "Access-Control-Request-Method" = "GET"; "Access-Control-Request-Headers" = "authorization" }
  Verifier "RL-05" "Preflight pendant le blocage -> repond, non decompte" (($r.Code -eq 200) -and (-not $r.Headers.ContainsKey('x-ratelimit-remaining-minute'))) "HTTP $($r.Code)"

  Write-Host "                (attente du Retry-After : $retry s)" -ForegroundColor DarkYellow
  Start-Sleep -Seconds ([int]$retry + 1)
  $r = Appel -Chemin "/api/annonces" -Jeton (Token alice)
  Verifier "RL-03" "Apres le delai Retry-After, l'appel repond normalement" ($r.Code -eq 200) "HTTP $($r.Code)"
}

# ---------------------------------------------------------------- MT

if ($Sections -contains "MT") {
  Titre "Regles metier a travers la gateway (MT-01 a MT-10)"
  $suffixe = [guid]::NewGuid().ToString().Substring(0, 6)

  $r = Appel -Methode POST -Chemin "/api/categories" -Jeton (Token modo) -Corps "{`"libelle`":`"Test gateway $suffixe`"}" -AttendreSiLimite
  $r2 = Appel -Methode POST -Chemin "/api/categories" -Jeton (Token admin) -Corps "{`"libelle`":`"Test gateway $suffixe`"}" -AttendreSiLimite
  Verifier "MT-08" "modo POST /api/categories -> 403 ; admin -> 201" (($r.Code -eq 403) -and ($r2.Code -eq 201)) "modo $($r.Code) / admin $($r2.Code)"
  $cat = $r2.Json.id

  $corps = "{`"titre`":`"Velo de course`",`"prix`":350,`"categorieId`":$cat,`"auteurId`":`"$subBob`"}"
  $r = Appel -Methode POST -Chemin "/api/annonces" -Jeton (Token alice) -Corps $corps -AttendreSiLimite
  $id = $r.Json.id
  Verifier "MT-01" "Creation -> 201 EN_ATTENTE, auteurId = sub d'alice malgre le corps, Location publique" (($r.Code -eq 201) -and ($r.Json.statut -eq 'EN_ATTENTE') -and ($r.Json.auteurId -eq $subAlice) -and ($r.Headers['location'] -like "$Gateway/api/annonces/*")) "HTTP $($r.Code) auteurId=$($r.Json.auteurId) Location=$($r.Headers['location'])"

  $r = Appel -Chemin "/api/annonces/$id" -Jeton (Token bob) -AttendreSiLimite
  Verifier "MT-02" "bob lit l'annonce EN_ATTENTE d'alice -> 404" ($r.Code -eq 404) "HTTP $($r.Code)"

  $r = Appel -Methode PUT -Chemin "/api/annonces/$id" -Jeton (Token bob) -Corps "{`"titre`":`"Pirate de bob`",`"prix`":1,`"categorieId`":$cat}" -AttendreSiLimite
  $r2 = Appel -Methode DELETE -Chemin "/api/annonces/$id" -Jeton (Token bob) -AttendreSiLimite
  Verifier "MT-03" "bob PUT / DELETE l'annonce d'alice -> 403 / 403" (($r.Code -eq 403) -and ($r2.Code -eq 403)) "PUT $($r.Code) / DELETE $($r2.Code)"

  $r = Appel -Methode PATCH -Chemin "/api/moderation/annonces/$id/publier" -Jeton (Token alice) -AttendreSiLimite
  Verifier "MT-05" "alice (USER) publie -> 403" ($r.Code -eq 403) "HTTP $($r.Code)"

  $r = Appel -Methode PATCH -Chemin "/api/moderation/annonces/$id/publier" -Jeton (Token modo) -AttendreSiLimite
  $liste = Appel -Chemin "/api/annonces?categorieId=$cat" -Jeton (Token bob) -AttendreSiLimite
  Verifier "MT-06" "modo publie -> 200, l'annonce apparait dans GET /api/annonces" (($r.Code -eq 200) -and (@($liste.Json.contenu.id) -contains $id)) "HTTP $($r.Code)"

  $r = Appel -Methode PUT -Chemin "/api/annonces/$id" -Jeton (Token alice) -Corps "{`"titre`":`"Velo de course, prix baisse`",`"prix`":300,`"categorieId`":$cat}" -AttendreSiLimite
  $liste = Appel -Chemin "/api/annonces?categorieId=$cat" -Jeton (Token bob) -AttendreSiLimite
  Verifier "MT-04" "alice modifie son annonce PUBLIEE -> 200 EN_ATTENTE, retiree de la liste publique" (($r.Code -eq 200) -and ($r.Json.statut -eq 'EN_ATTENTE') -and -not (@($liste.Json.contenu.id) -contains $id)) "HTTP $($r.Code) statut=$($r.Json.statut)"

  $r = Appel -Methode PATCH -Chemin "/api/moderation/annonces/$id/rejeter" -Jeton (Token modo) -Corps '{}' -AttendreSiLimite
  Verifier "MT-07" "modo rejette sans motif -> 400 ProblemDetail" (($r.Code -eq 400) -and ($r.Headers['content-type'] -match 'application/problem\+json')) "HTTP $($r.Code) $($r.Headers['content-type'])"

  $r = Appel -Methode DELETE -Chemin "/api/categories/$cat" -Jeton (Token admin) -AttendreSiLimite
  Verifier "MT-09" "admin supprime une categorie utilisee -> 409" ($r.Code -eq 409) "HTTP $($r.Code)"

  $r = Appel -Methode POST -Chemin "/api/annonces" -Jeton (Token alice) -Corps "{`"titre`":`"Vel`",`"prix`":10,`"categorieId`":$cat}" -AttendreSiLimite
  $r2 = Appel -Methode POST -Chemin "/api/annonces" -Jeton (Token alice) -Corps "{`"titre`":`"Velo de course`",`"prix`":-1,`"categorieId`":$cat}" -AttendreSiLimite
  Verifier "MT-10" "Titre de 3 caracteres / prix negatif -> 400 detaillant le champ" (($r.Code -eq 400) -and $r.Json.errors.titre -and ($r2.Code -eq 400) -and $r2.Json.errors.prix) "titre: $($r.Code) $($r.Json.errors.titre) / prix: $($r2.Code) $($r2.Json.errors.prix)"

  # Nettoyage
  [void](Appel -Methode DELETE -Chemin "/api/annonces/$id" -Jeton (Token admin) -AttendreSiLimite)
  [void](Appel -Methode DELETE -Chemin "/api/categories/$cat" -Jeton (Token admin) -AttendreSiLimite)
}

# ---------------------------------------------------------------- bilan

$ok = @($script:resultats | Where-Object { $_.Ok }).Count
$total = $script:resultats.Count
$duree = [int]((Get-Date) - $debut).TotalSeconds
Write-Host ""
if ($ok -eq $total) { Write-Host "Bilan : $ok / $total criteres OK ($duree s)" -ForegroundColor Green }
else {
  Write-Host "Bilan : $ok / $total criteres OK ($duree s)" -ForegroundColor Red
  $script:resultats | Where-Object { -not $_.Ok } | ForEach-Object { Write-Host "  - $($_.Id) $($_.Libelle)" -ForegroundColor Red }
}
Pop-Location
if ($ok -ne $total) { exit 1 }
