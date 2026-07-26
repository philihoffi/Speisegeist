# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

# Abhängigkeiten zuerst cachen (nur neu laden wenn package.json sich ändert)
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime stage ----
FROM nginx:1.27-alpine

# Non-root user: nginx-User existiert bereits im Alpine-Image
COPY --from=build --chown=nginx:nginx /app/dist/frontend/browser /usr/share/nginx/html
COPY --chown=nginx:nginx nginx.conf /etc/nginx/conf.d/default.conf

# nginx Master-Prozess benötigt root für Port 80, Worker laufen als nginx
EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO /dev/null http://localhost/
