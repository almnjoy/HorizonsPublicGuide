#!/usr/bin/env bash
# HORIZON head-unit network bring-up, all-in-one and self-healing.
# Auto-detects the USB-ethernet dongle (no more guessing enp86s0uX names),
# disables the USB autosuspend that keeps dropping it, opens the path for
# broadcast DHCP, and serves DHCP. One command every session:
#     sudo /home/user/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/hz-net.sh
set -uo pipefail
[ "$(id -u)" -eq 0 ] || { echo "run with: sudo $0"; exit 1; }
LOG=/home/user/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/logs/horizon-dnsmasq.log

# --- Force AX88179 dongles off the broken NCM mode ---
# These come up multi-config; config 2 = cdc_ncm (passes ZERO frames on this laptop).
# Config 1 = ax88179_178a (native, works). Switch it and let the good driver bind.
modprobe ax88179_178a 2>/dev/null || true
for d in /sys/bus/usb/devices/*/idProduct; do
  dev=$(dirname "$d")
  [ "$(cat "$d" 2>/dev/null)" = "1790" ] || continue
  cur=$(cat "$dev/bConfigurationValue" 2>/dev/null || echo "?")
  if [ "$cur" != "1" ]; then
    echo "==> AX88179 on USB config $cur -> forcing config 1 (ax88179_178a)"
    echo 1 > "$dev/bConfigurationValue" 2>/dev/null || echo "   (config switch write failed)"
    sleep 4
  else
    echo "==> AX88179 already on config 1"
  fi
done

detect_iface() {
  # honor an explicit override first
  if [ -n "${HORIZON_IFACE:-}" ] && [ -e "/sys/class/net/$HORIZON_IFACE" ]; then echo "$HORIZON_IFACE"; return 0; fi
  for n in /sys/class/net/*; do
    i=$(basename "$n")
    case "$i" in lo|wl*|docker*|veth*|tailscale*|virbr*|br-*|tun*|tap*) continue;; esac
    dev=$(readlink -f "$n/device" 2>/dev/null || true)
    case "$dev" in *usb*) echo "$i"; return 0;; esac
    drv=$(basename "$(readlink "$n/device/driver" 2>/dev/null || true)" 2>/dev/null || true)
    case "$drv" in cdc_ncm|cdc_ether|r8152|asix|ax88179_178a|rndis_host) echo "$i"; return 0;; esac
  done
  return 1
}

IFACE="$(detect_iface || true)"
if [ -z "$IFACE" ]; then
  echo "!! No USB-ethernet dongle found on the system."
  echo "   Re-seat the adapter in the laptop and re-run. Present ifaces:"
  echo "   $(ls /sys/class/net | tr '\n' ' ')"
  exit 1
fi
echo "==> dongle interface: $IFACE"

# Kill the USB autosuspend that keeps dropping the dongle (walk up to the USB device dir).
usbdev=$(readlink -f "/sys/class/net/$IFACE/device" 2>/dev/null || true)
while [ -n "$usbdev" ] && [ "$usbdev" != "/" ]; do
  if [ -f "$usbdev/idVendor" ] && [ -f "$usbdev/power/control" ]; then
    echo on > "$usbdev/power/control" 2>/dev/null && \
      echo "==> USB autosuspend OFF for $(cat "$usbdev/idVendor"):$(cat "$usbdev/idProduct")"
    echo -1 > "$usbdev/power/autosuspend_delay_ms" 2>/dev/null || true
    break
  fi
  usbdev=$(dirname "$usbdev")
done

echo "==> releasing NetworkManager + stale dnsmasq on $IFACE"
nmcli device set "$IFACE" managed no 2>/dev/null || true
pkill -f "dnsmasq.*--interface=$IFACE" 2>/dev/null || true
sleep 1

echo "==> static <LAPTOP_IP>/24 on $IFACE"
ip addr flush dev "$IFACE" 2>/dev/null || true
ip addr add <LAPTOP_IP>/24 dev "$IFACE"
ip link set "$IFACE" up

echo "==> rp_filter off + iptables ACCEPT inbound on $IFACE"
for k in all default "$IFACE"; do sysctl -w "net.ipv4.conf.$k.rp_filter=0" >/dev/null 2>&1 || true; done
iptables -D INPUT -i "$IFACE" -j ACCEPT 2>/dev/null || true
iptables -I INPUT 1 -i "$IFACE" -j ACCEPT

echo "==> carrier=$(cat /sys/class/net/$IFACE/carrier 2>/dev/null) speed=$(cat /sys/class/net/$IFACE/speed 2>/dev/null)Mb/s  addr=$(ip -4 -br addr show $IFACE)"
: > "$LOG" 2>/dev/null || true
echo
echo "############################################################"
echo "#  DHCP SERVER READY on $IFACE  (server <LAPTOP_IP>, pool .50-.200)"
echo "#  >>> NOW REBOOT / RESET THE RADIO to trigger a fresh Discover"
echo "#  Watch for:  DHCPDISCOVER -> DHCPOFFER -> DHCPACK 10.42.0.x"
echo "############################################################"
echo
exec dnsmasq -d --port=0 --interface="$IFACE" --bind-interfaces --except-interface=lo \
  --dhcp-range=10.42.0.50,10.42.0.200,1h --dhcp-authoritative --log-dhcp 2>&1 | tee -a "$LOG"
