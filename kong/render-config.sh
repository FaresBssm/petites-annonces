#!/bin/sh
# Déploiement uniquement : génère la configuration Kong de l'environnement à partir de kong/kong.yml.
#
# kong/kong.yml reste la configuration de référence, valide telle quelle en local (localhost).
# En production, ce script en produit une copie où sont remplacés :
#   - l'émetteur des tokens  http://localhost:8180            -> $PUBLIC_AUTH_URL
#   - l'origine CORS         ^http://localhost:4200$          -> ^<PUBLIC_APP_URL échappée>$
#   - l'adresse du backend   http://backend:8081              -> $BACKEND_URL (nom unique sur une VM partagée)
#   - la clé publique RSA du realm (bloc PEM)                 -> $REALM_PUBLIC_KEY (base64 sur une ligne)
# puis lance Kong normalement. L'image officielle de Kong est utilisée sans build.
set -eu

: "${PUBLIC_AUTH_URL:?PUBLIC_AUTH_URL manquant}"
: "${PUBLIC_APP_URL:?PUBLIC_APP_URL manquant}"
: "${REALM_PUBLIC_KEY:?REALM_PUBLIC_KEY manquant}"
BACKEND_URL="${BACKEND_URL:-http://backend:8081}"

SOURCE=/kong/declarative/kong.yml
CIBLE=/tmp/kong.yml

# Origine CORS en regex ancrée : les points de l'URL sont échappés (sinon "." = n'importe quel caractère)
ORIGINE="^$(printf '%s' "$PUBLIC_APP_URL" | sed 's/\./\\./g')\$"

awk -v auth="$PUBLIC_AUTH_URL" -v origine="$ORIGINE" -v backend="$BACKEND_URL" -v cle="$REALM_PUBLIC_KEY" '
  # Remplacement littéral (sans regex) de toutes les occurrences de "de" par "vers"
  function remplacer(texte, de, vers,    resultat, i) {
    resultat = ""
    while ((i = index(texte, de)) > 0) {
      resultat = resultat substr(texte, 1, i - 1) vers
      texte = substr(texte, i + length(de))
    }
    return resultat texte
  }
  /-----BEGIN PUBLIC KEY-----/ {
    marge = substr($0, 1, index($0, "-") - 1)
    print
    for (i = 1; i <= length(cle); i += 64) print marge substr(cle, i, 64)
    dans_cle = 1
    next
  }
  /-----END PUBLIC KEY-----/ { dans_cle = 0; print; next }
  dans_cle { next }
  {
    ligne = remplacer($0, "^http://localhost:4200$", origine)
    ligne = remplacer(ligne, "http://localhost:8180", auth)
    ligne = remplacer(ligne, "http://backend:8081", backend)
    print ligne
  }
' "$SOURCE" > "$CIBLE"

echo "kong.yml généré : iss=${PUBLIC_AUTH_URL}/realms/annonces, origine=${ORIGINE}, backend=${BACKEND_URL}"
export KONG_DECLARATIVE_CONFIG="$CIBLE"
exec /docker-entrypoint.sh kong docker-start
