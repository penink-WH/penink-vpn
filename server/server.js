#!/usr/bin/env node
// penink VPN 伺服器端
// 啟動：sudo node server.js
// 需要 root 權限才能建立 TUN 介面並進行 NAT 轉發。

const { WebSocketServer } = require('ws');
const { Tun } = require('tuntap2');
const http = require('http');
const net = require('net');

// 任何未捕捉例外都不能讓伺服器進程死亡（TUN 讀寫競態等）
process.on('uncaughtException', (e) => {
    console.error('[!] 未捕捉例外，伺服器持續運作:', e && e.message);
});
process.on('unhandledRejection', (e) => {
    console.error('[!] 未處理的 Promise 拒絕:', e && e.message);
});

// ── 設定 ──────────────────────────────────────────
const PASSWORD   = process.env.PASSWORD || 'CHANGE_ME';         // 與 App 一致的密鑰
const WS_PORT    = Number(process.env.WS_PORT || 9543);        // WebSocket 監聽連接埠
const TUN_ADDR   = process.env.TUN_ADDR || '10.27.0.1/24';    // TUN 介面 IP（App 側為 10.27.0.2）
const MTU        = 1380;

// ── HTTP 門面（供 health check / 防止外網看到 WS 提示） ──
const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('penink vpn — OK');
});

// ── WebSocket ──────────────────────────────────────
const wss = new WebSocketServer({ server });

let activeWs = null;          // 目前連線的 WS client

wss.on('connection', (ws, req) => {
    // 驗證
    const auth = (req.headers['authorization'] || '').replace(/^Bearer /i, '');
    if (auth !== PASSWORD) {
        ws.close(4001, 'Unauthorized');
        return;
    }

    console.log(`[+] 客戶端已連線 ${req.socket.remoteAddress}`);

    // 舊連線斷開（標記後其 TUN 寫入會被忽略，避免寫到已釋放的 fd）
    if (activeWs && activeWs !== ws) {
        try { activeWs.replaced = true; } catch (_) {}
        try { activeWs.close(1000, 'Replaced'); } catch (_) {}
    }

    // 建立 TUN 介面（本連線專屬）
    let tun = null;
    try {
        tun = new Tun();
        tun.mtu = MTU;
        tun.ipv4 = TUN_ADDR;
        tun.isUp = true;
        console.log(`[+] TUN 已建立: ${tun.name}  ${TUN_ADDR}`);
    } catch (e) {
        console.error('[-] TUN 建立失敗:', e.message);
        ws.close(1011, 'TUN failed');
        return;
    }

    activeWs = ws;
    ws.tun = tun;

    // TUN → WS：收到 IP 封包，透過 WS 送給客戶端
    tun.on('data', (buf) => {
        if (ws.replaced) return;
        try {
            if (ws.readyState === ws.OPEN) {
                ws.send(buf, { binary: true });
            }
        } catch (e) {
            console.error('[-] TUN 讀取/送出錯誤:', e.message);
        }
    });

    // WS → TUN：客戶端送來的 IP 封包，寫入 TUN（先驗證為合法 IP 封包）
    ws.on('message', (msg, isBinary) => {
        if (isBinary !== true || !ws.tun || ws.replaced) return;
        const pkt = Buffer.isBuffer(msg) ? msg : Buffer.from(msg);
        if (pkt.length < 20) return;
        const version = pkt[0] >> 4;
        if (version !== 4 && version !== 6) return;
        try {
            ws.tun.write(pkt);
        } catch (e) {
            console.error('[-] TUN 寫入錯誤:', e.message);
        }
    });

    ws.on('close', () => {
        console.log('[-] 客戶端已斷線');
        if (activeWs === ws) activeWs = null;
        if (ws.tun) {
            try { ws.tun.release(); } catch (_) {}
            ws.tun = null;
        }
    });

    ws.on('error', (err) => {
        console.error('[-] WebSocket 錯誤:', err.message);
    });
});

server.listen(WS_PORT, () => {
    console.log(`[penink VPN server] WebSocket 監聽中 ws://0.0.0.0:${WS_PORT}/`);
    console.log(`[penink VPN server] TUN 位址: ${TUN_ADDR}`);
    console.log('[penink VPN server] 請確保已設定 iptables NAT：');
    console.log('  sudo sysctl -w net.ipv4.ip_forward=1');
    console.log(`  sudo iptables -t nat -A POSTROUTING -s ${TUN_ADDR.replace('/24', '/24')} -o eth0 -j MASQUERADE`);
    console.log('  sudo iptables -A FORWARD -i tun+ -j ACCEPT');
    console.log('  sudo iptables -A FORWARD -o tun+ -m state --state RELATED,ESTABLISHED -j ACCEPT');
    console.log('');
    console.log('  若使用 Cloudflare Tunnel（推薦），執行：');
    console.log(`  cloudflared tunnel --url http://localhost:${WS_PORT}`);
    console.log('  然後在 App 的地址欄填入 Cloudflare 分配的網域（例如 your-domain.trycloudflare.com）');
});