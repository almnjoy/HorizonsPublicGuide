#!/usr/bin/env bash
# HORIZON: hand out DHCP *and* watch the wire, in one window.
#   sudo /home/user/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/hz-snoop.sh
# Ctrl-C stops both.
set -uo pipefail
[ "$(id -u)" -eq 0 ] || { echo "run: sudo $0"; exit 1; }

BASE=/home/user/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/logs
DHCPLOG=$BASE/horizon-dnsmasq.log
CAPLOG=$BASE/horizon-cap.log
PCAP=$BASE/horizon-$(date +%Y%m%d-%H%M%S).pcap
mkdir -p "$BASE"

C_D=$'\e[36m'; C_C=$'\e[33m'; C_E=$'\e[31m'; C_G=$'\e[32m'; C_0=$'\e[0m'

command -v tcpdump >/dev/null || { echo "${C_E}tcpdump missing -> sudo pacman -S tcpdump${C_0}"; exit 1; }

# --- find the USB ethernet dongle (skips wifi, so the ALFA can't hijack it) ---
IFACE=""
for n in /sys/class/net/*; do
  i=$(basename "$n")
  case "$i" in lo|wl*|docker*|veth*|tailscale*|virbr*|br-*|tun*|tap*) continue;; esac
  case "$(readlink -f "$n/device" 2>/dev/null)" in *usb*) IFACE="$i"; break;; esac
done
[ -z "$IFACE" ] && { echo "${C_E}!! no USB ethernet dongle. present: $(ls /sys/class/net | tr '\n' ' ')${C_0}"; exit 1; }
echo "${C_G}==> dongle: $IFACE  carrier=$(cat /sys/class/net/$IFACE/carrier 2>/dev/null)${C_0}"

# --- keep USB from suspending it out from under us ---
U=$(readlink -f "/sys/class/net/$IFACE/device" 2>/dev/null || true)
while [ -n "$U" ] && [ "$U" != "/" ]; do
  [ -f "$U/idVendor" ] && { echo on > "$U/power/control" 2>/dev/null; echo -1 > "$U/power/autosuspend_delay_ms" 2>/dev/null; break; }
  U=$(dirname "$U")
done

nmcli device set "$IFACE" managed no 2>/dev/null || true
pkill -f "dnsmasq -d --port=0" 2>/dev/null || true
sleep 1

ip addr flush dev "$IFACE" 2>/dev/null || true
ip addr add <LAPTOP_IP>/24 dev "$IFACE"
ip link set "$IFACE" up
for k in all default "$IFACE"; do sysctl -w "net.ipv4.conf.$k.rp_filter=0" >/dev/null 2>&1 || true; done

# --- open the interface in every firewall layer (nft inet filter is the packet-eater) ---
iptables -D INPUT -i "$IFACE" -j ACCEPT 2>/dev/null || true
iptables -I INPUT 1 -i "$IFACE" -j ACCEPT 2>/dev/null || true
if nft list table inet filter >/dev/null 2>&1; then
  nft insert rule inet filter input iifname "$IFACE" accept 2>/dev/null \
    && echo "${C_G}   nft inet filter: opened for $IFACE${C_0}"
fi
command -v ufw >/dev/null && ufw status 2>/dev/null | grep -q "Status: active" && ufw allow in on "$IFACE" >/dev/null 2>&1

cleanup() { echo; echo "${C_E}==> stopping${C_0}"; kill $CAP_PID $DHCP_PID 2>/dev/null; wait 2>/dev/null; echo "pcap saved: $PCAP"; }
trap cleanup INT TERM EXIT

: > "$DHCPLOG"; : > "$CAPLOG"

# --- CAPTURE: DHCP handshake + ARP + ping + adb/5555 ---
stdbuf -oL tcpdump -i "$IFACE" -n -l -tttt -s 0 -w - \
    'port 67 or port 68 or arp or icmp or tcp port 5555' 2>/dev/null \
  | tee "$PCAP" \
  | stdbuf -oL tcpdump -r - -n -l -tttt 2>/dev/null \
  | while IFS= read -r l; do printf '%s[CAP ]%s %s\n' "$C_C" "$C_0" "$l"; done | tee -a "$CAPLOG" &
CAP_PID=$!
sleep 1

echo
echo "############################################################"
echo "#  DHCP + CAPTURE LIVE on $IFACE   (server <LAPTOP_IP>, pool .50-.200)"
echo "#  >>> NOW POWER-CYCLE THE HEAD UNIT"
echo "#  want: DHCPDISCOVER -> DHCPOFFER -> DHCPACK 10.42.0.x"
echo "#  pcap: $PCAP"
echo "############################################################"
echo

# --- DHCP in the foreground ---
stdbuf -oL dnsmasq -d --port=0 --interface="$IFACE" --bind-interfaces --except-interface=lo \
    --dhcp-range=10.42.0.50,10.42.0.200,1h --dhcp-authoritative --log-dhcp 2>&1 \
  | while IFS= read -r l; do printf '%s[DHCP]%s %s\n' "$C_D" "$C_0" "$l"; done | tee -a "$DHCPLOG" &
DHCP_PID=$!

wait $DHCP_PID
