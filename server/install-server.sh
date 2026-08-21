#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Run as root: sudo bash install-server.sh YOUR_PUBLIC_IP_OR_DNS"
  exit 1
fi

ENDPOINT="${1:-}"
PORT="${WG_PORT:-51820}"
WG_NET4="10.77.0"
WG_NET6="fd00:77::"

if [[ -z "$ENDPOINT" ]]; then
  echo "Usage: sudo bash install-server.sh YOUR_PUBLIC_IP_OR_DNS"
  echo "Example: sudo bash install-server.sh vpn.example.com"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y wireguard wireguard-tools iptables qrencode

WAN_IF="$(ip route show default | awk '{print $5; exit}')"
if [[ -z "$WAN_IF" ]]; then
  echo "Could not detect the Internet-facing interface."
  exit 1
fi

install -d -m 700 /etc/wireguard
umask 077
SERVER_PRIVATE="$(wg genkey)"
SERVER_PUBLIC="$(printf '%s' "$SERVER_PRIVATE" | wg pubkey)"
CLIENT_PRIVATE="$(wg genkey)"
CLIENT_PUBLIC="$(printf '%s' "$CLIENT_PRIVATE" | wg pubkey)"

cat > /etc/wireguard/wg0.conf <<CFG
[Interface]
Address = ${WG_NET4}.1/24, ${WG_NET6}1/64
ListenPort = ${PORT}
PrivateKey = ${SERVER_PRIVATE}
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -A FORWARD -o wg0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT; iptables -t nat -A POSTROUTING -o ${WAN_IF} -j MASQUERADE; ip6tables -A FORWARD -i wg0 -j ACCEPT; ip6tables -A FORWARD -o wg0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT; ip6tables -t nat -A POSTROUTING -o ${WAN_IF} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -D FORWARD -o wg0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT; iptables -t nat -D POSTROUTING -o ${WAN_IF} -j MASQUERADE; ip6tables -D FORWARD -i wg0 -j ACCEPT; ip6tables -D FORWARD -o wg0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT; ip6tables -t nat -D POSTROUTING -o ${WAN_IF} -j MASQUERADE

[Peer]
PublicKey = ${CLIENT_PUBLIC}
AllowedIPs = ${WG_NET4}.2/32, ${WG_NET6}2/128
CFG

cat > /etc/sysctl.d/99-shieldai-wireguard.conf <<'SYSCTL'
net.ipv4.ip_forward=1
net.ipv6.conf.all.forwarding=1
SYSCTL
sysctl --system >/dev/null

if command -v ufw >/dev/null 2>&1; then
  ufw allow "${PORT}/udp" || true
fi

systemctl enable --now wg-quick@wg0

CLIENT_FILE="/root/shieldai-client.conf"
cat > "$CLIENT_FILE" <<CFG
[Interface]
PrivateKey = ${CLIENT_PRIVATE}
Address = ${WG_NET4}.2/32, ${WG_NET6}2/128
DNS = 1.1.1.1, 1.0.0.1

[Peer]
PublicKey = ${SERVER_PUBLIC}
Endpoint = ${ENDPOINT}:${PORT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
CFG
chmod 600 "$CLIENT_FILE"

echo
echo "ShieldAI WireGuard server is running."
echo "UDP port: ${PORT}"
echo "Client config: ${CLIENT_FILE}"
echo
echo "Scan this QR code from a trusted screen or copy the config into ShieldAI VPN Settings:"
qrencode -t ansiutf8 < "$CLIENT_FILE"
echo
echo "IMPORTANT: If this server is behind a home router, forward UDP ${PORT} to this machine."
echo "Never upload /etc/wireguard/wg0.conf or ${CLIENT_FILE} to GitHub."
