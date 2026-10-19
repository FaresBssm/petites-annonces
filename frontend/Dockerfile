# ---------- Étape 1 : build Angular (Node), jetée après la compilation ----------
FROM node:22-alpine AS build
WORKDIR /build

# Dépendances d'abord : couche en cache tant que package*.json ne change pas
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---------- Étape 2 : exécution (Nginx "slim" : sans modules optionnels, fichiers statiques) ----------
FROM nginx:1.29-alpine-slim
LABEL org.opencontainers.image.title="petites-annonces-frontend" \
      org.opencontainers.image.description="SPA Angular PetitesAnnonces servie par Nginx"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/petites-annonces-front/browser /usr/share/nginx/html
EXPOSE 80
