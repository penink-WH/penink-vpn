# penink VPN 伺服器 — 可在 macOS(Colima Linux VM) / Linux / Docker 執行
# 一鍵啟動：docker-compose up -d --build

FROM node:20-slim

# 使用 aliyun 鏡像加速（tw 地區連 deb.debian.org 極慢）
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends build-essential python3 iproute2 iptables curl \
    && rm -rf /var/lib/apt/lists/*

# 小小工具（獨立層，避免每次重編譯工具鏈）
RUN apt-get update && apt-get install -y --no-install-recommends procps && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package.json server.js setup-nat.sh ./
RUN npm install --no-audit --no-fund

CMD ["sh", "-c", "bash setup-nat.sh && node server.js"]