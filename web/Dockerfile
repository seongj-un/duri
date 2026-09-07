# ---------------------------------------------------------------------
# 1) 빌드 — VITE_API_BASE_URL 은 번들에 박힌다. 빌드 시점에 정해야 한다.
# ---------------------------------------------------------------------
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---------------------------------------------------------------------
# 2) 서빙 — 정적 파일뿐이라 nginx 하나면 된다
# ---------------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    # localhost 대신 127.0.0.1 을 쓴다. 컨테이너 안에서 localhost 는 ::1 로 먼저 풀리는데
    # nginx 는 기본적으로 IPv4 만 듣는다.
    CMD wget -qO- http://127.0.0.1/index.html >/dev/null || exit 1
