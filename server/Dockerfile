# penink VPN 伺服器 — Docker 部署
# 一鍵啟動：docker compose up -d
# 需要 /dev/net/tun 與 root 權限（privileged）

FROM node:20-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential python3 iproute2 iptables curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package.json server.js ./
RUN npm install --no-audit --no-fund

EXPOSE 9543
CMD ["node", "server.js"]