# 前端镜像：Node 构建静态文件，Nginx 提供页面并把 /api 转发到权限中心。
#   docker build -t rbac-web:latest rbac-web
# 拉不到 Docker Hub 时用 --build-arg 换成 SWR 上的镜像（同 deploy/env.example）。
ARG NODE_IMAGE=node:20-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine

FROM ${NODE_IMAGE} AS build
WORKDIR /web
COPY package.json package-lock.json ./
# Normalize only registry host in this image layer; lock integrity remains enforced.
ARG NPM_REGISTRY=https://registry.npmjs.org
RUN sed -i "s#https://registry.npmmirror.com#${NPM_REGISTRY}#g" package-lock.json \
    && npm ci --no-audit --no-fund --registry="${NPM_REGISTRY}"
COPY . .
RUN npm run build

FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV RBAC_PORT=8081
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 443
