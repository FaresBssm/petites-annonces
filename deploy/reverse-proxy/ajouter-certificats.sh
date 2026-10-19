#!/usr/bin/env bash
# À lancer UNE FOIS sur la VM (root) : branche PetitesAnnonces sur le reverse proxy Nginx commun
# et obtient les certificats Let's Encrypt de SES trois sous-domaines uniquement.
#
#   bash /opt/petites-annonces/deploy/reverse-proxy/ajouter-certificats.sh 72.60.188.97
#
# Différences volontaires avec reverse-proxy/init-letsencrypt.sh du serveur, pour ne pas toucher
# aux autres sites hébergés :
#   - seuls nos domaines sont traités (les certificats existants ne sont ni supprimés ni réémis) ;
#   - pas de recréation du conteneur reverse-proxy : rechargement à chaud (nginx -s reload),
#     toujours précédé de "nginx -t" ; en cas d'échec, notre fichier est retiré.
# Test sans consommer les quotas Let's Encrypt : STAGING=1 bash ajouter-certificats.sh <ip>
set -euo pipefail

IP="${1:?Usage : ajouter-certificats.sh <ip-de-la-vm>}"
DOMAINE="${IP//./-}.sslip.io"
DOMAINES=("app.$DOMAINE" "api.$DOMAINE" "auth.$DOMAINE")
PROXY_DIR=/opt/master-affiliation
CONF_CIBLE="$PROXY_DIR/reverse-proxy/conf.d/petites-annonces.conf"
TEMPLATE="$(cd "$(dirname "$0")" && pwd)/petites-annonces.conf.template"
staging_arg=""; [ "${STAGING:-0}" != "0" ] && staging_arg="--staging"

cd "$PROXY_DIR"

recharger_nginx() {
  if docker compose exec -T reverse-proxy nginx -t; then
    docker compose exec -T reverse-proxy nginx -s reload
  else
    echo "!!! nginx -t a échoué : retrait de $CONF_CIBLE, aucun rechargement" >&2
    rm -f "$CONF_CIBLE"
    exit 1
  fi
}

echo "### 1/4 — Certificats factices pour ${DOMAINES[*]} (nginx refuse une conf sans certificat)"
for d in "${DOMAINES[@]}"; do
  docker compose run --rm --entrypoint /bin/sh certbot -c "
    if [ ! -f /etc/letsencrypt/live/$d/fullchain.pem ]; then
      mkdir -p /etc/letsencrypt/live/$d
      openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
        -keyout /etc/letsencrypt/live/$d/privkey.pem \
        -out /etc/letsencrypt/live/$d/fullchain.pem -subj /CN=$d
    fi"
done

echo "### 2/4 — Installation de la conf et rechargement à chaud"
sed "s/__DOMAINE__/$DOMAINE/g" "$TEMPLATE" > "$CONF_CIBLE"
recharger_nginx

echo "### 3/4 — Certificats Let's Encrypt (webroot), uniquement pour nos domaines"
for d in "${DOMAINES[@]}"; do
  # Un certificat déjà réel (renouvelable) est conservé ; seul un certificat factice est remplacé
  if docker compose run --rm --entrypoint /bin/sh certbot -c "test -f /etc/letsencrypt/renewal/$d.conf"; then
    echo "    $d : certificat Let's Encrypt déjà présent, conservé"
    continue
  fi
  docker compose run --rm --entrypoint /bin/sh certbot -c "rm -rf /etc/letsencrypt/live/$d /etc/letsencrypt/archive/$d"
  docker compose run --rm --entrypoint certbot certbot certonly \
    --webroot -w /var/www/certbot $staging_arg \
    --non-interactive --agree-tos --register-unsafely-without-email \
    -d "$d"
done

echo "### 4/4 — Rechargement avec les vrais certificats"
recharger_nginx
echo "Terminé : https://${DOMAINES[0]}  https://${DOMAINES[1]}  https://${DOMAINES[2]}"
echo "Renouvellement : assuré par le service certbot existant (toutes les 12 h)."
