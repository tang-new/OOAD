ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine
FROM ${NODE_IMAGE} AS build
WORKDIR /web
COPY course-integration/frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY course-integration/frontend/ ./
RUN npm run build
FROM ${NGINX_IMAGE}
COPY --from=build /web/dist /usr/share/nginx/html
COPY deploy/full-course-nginx.conf /etc/nginx/templates/default.conf.template
ENV RBAC_PORT=8081 GATEWAY_PORT=8090
EXPOSE 443
