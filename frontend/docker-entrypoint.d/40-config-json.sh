#!/bin/sh
# Exécuté par l'entrypoint de l'image Nginx avant le démarrage : génère /config.json à partir
# des variables d'environnement, pour que la même image serve en local comme en production.
set -eu

auth_url="${PUBLIC_AUTH_URL:-http://localhost:8180}"
api_url="${PUBLIC_API_URL:-http://localhost:8080}"

cat > /usr/share/nginx/html/config.json <<EOF
{
  "issuer": "${auth_url}/realms/annonces",
  "apiUrl": "${api_url}/api"
}
EOF

echo "config.json : issuer=${auth_url}/realms/annonces apiUrl=${api_url}/api"
