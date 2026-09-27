#!/usr/bin/env bash
# DHCP for the Horizon head unit, with the firewall fix baked in.
# HARD-WON LESSON (2026-09-10): a native nft table `inet filter` with
# `policy drop` on its input hook silently eats broadcast DHCP, and NO accept in
# another table (ufw's ip filter, iptables) can override an nft drop in a
# different table. So we open the dongle interface in EVERY firewall layer.
#   sudo /home/user/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/hz-dhcp.sh
set -uo pipefail
[ "$(id -u)" -eq 0 ] || { echo "run: sudo $0"; exit 1; }
LOG=/home/user/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/logs/horizon-dnsmasq.log

# find the dongle: a usb-backed interface that isn't wifi/lo/virtual
IFACE=""
for n in /sys/class/net/*; do
  i=$(basename "$n")
  case "$i" in lo|wl*|docker*|veth*|tailscale*|virbr*|br-*|tun*|tap*) continue;; esac
  case "$(readlink -f "$n/device" 2>/dev/null)" in *usb*) IFACE="$i"; break;; esac
done
[ -z "$IFACE" ] && { echo "!! no USB ethernet dongle found. present: $(ls /sys/class/net | tr '\n' ' ')"; exit 1; }
echo "==> dongle: $IFACE (carrier=$(cat /sys/class/net/$IFACE/carrier 2>/dev/null))"

nmcli device set "$IFACE" managed no 2>/dev/null || true
pkill -f "dnsmasq -d --port=0" 2>/dev/null || true
sleep 1

ip addr flush dev "$IFACE" 2>/dev/null || true
ip addr add <LAPTOP_IP>/24 dev "$IFACE"
ip link set "$IFACE" up
sysctl -w net.ipv4.conf.all.rp_filter=0 >/dev/null 2>&1 || true
sysctl -w "net.ipv4.conf.$IFACE.rp_filter=0" >/dev/null 2>&1 || true

echo "==> opening $IFACE in every firewall layer"
# 1) iptables / ip filter
iptables -D INPUT -i "$IFACE" -j ACCEPT 2>/dev/null || true
iptables -I INPUT 1 -i "$IFACE" -j ACCEPT 2>/dev/null || true
# 2) native nft `inet filter` (THE culprit: policy drop on input hook)
if nft list table inet filter >/dev/null 2>&1; then
  nft insert rule inet filter input iifname "$IFACE" accept 2>/dev/null \
    && echo "   nft inet filter: opened for $IFACE (this was the packet-eater)" \
    || echo "   nft inet filter present but insert failed - check chain name"
else
  echo "   nft inet filter table not loaded (good, nothing to hole)"
fi
# 3) ufw (interface-scoped, so the rest of ufw stays intact)
if command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow in on "$IFACE" >/dev/null 2>&1 && echo "   ufw: allowed inbound on $IFACE"
fi

: > "$LOG" 2>/dev/null || true
echo "==> DHCP UP on $IFACE (<LAPTOP_IP>, pool .50-.200). BOOT THE RADIO."
exec dnsmasq -d --port=0 --interface="$IFACE" \
  --dhcp-range=10.42.0.50,10.42.0.200,1h --dhcp-authoritative --log-dhcp 2>&1 | tee "$LOG"
