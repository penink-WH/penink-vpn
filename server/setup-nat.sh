#!/bin/bash
# penink VPN 伺服器快速設定腳本（Linux / root）
# 請先確認 TUN 介面已由 server.js 建立，或手動建立：
#   ip tuntap add dev penink0 mode tun
#   ip addr add 10.27.0.1/24 dev penink0
#   ip link set penink0 up

set -e
echo "=== penink VPN — iptables NAT 設定 ==="

TUN_IF="tun+"         # 匹配所有 tunX 介面
LAN_IF=$(ip route | grep default | awk '{print $5}' | head -1)

if [ -z "$LAN_IF" ]; then
    LAN_IF="eth0"
    echo "[警告] 偵測不到預設網路介面，使用 $LAN_IF 作為外網介面。"
fi

sysctl -w net.ipv4.ip_forward=1

iptables -t nat -C POSTROUTING -s 10.27.0.0/24 -o "$LAN_IF" -j MASQUERADE 2>/dev/null || \
iptables -t nat -A POSTROUTING -s 10.27.0.0/24 -o "$LAN_IF" -j MASQUERADE

iptables -C FORWARD -i "$TUN_IF" -j ACCEPT 2>/dev/null || \
iptables -A FORWARD -i "$TUN_IF" -j ACCEPT

iptables -C FORWARD -o "$TUN_IF" -m state --state RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || \
iptables -A FORWARD -o "$TUN_IF" -m state --state RELATED,ESTABLISHED -j ACCEPT

echo "[完成] NAT 轉發已啟用（外網介面: $LAN_IF）"
echo ""
echo "提示：若要持久化 iptables 規則，請安裝 iptables-persistent："
echo "  sudo apt install iptables-persistent"
echo "  sudo netfilter-persistent save"