#!/usr/bin/env bash
# Let the unit's broadcast DHCPDISCOVER reach dnsmasq: accept all inbound on the
# point-to-point cable and disable reverse-path filtering (drops src 0.0.0.0).
set -uo pipefail
IFACE="${HORIZON_IFACE:-enp86s0u1}"
[ "$(id -u)" -eq 0 ] || { echo "run with: sudo $0"; exit 1; }

echo "==> rp_filter OFF for $IFACE + all"
sysctl -w net.ipv4.conf.all.rp_filter=0 >/dev/null
sysctl -w net.ipv4.conf.default.rp_filter=0 >/dev/null
sysctl -w net.ipv4.conf.$IFACE.rp_filter=0 >/dev/null

echo "==> iptables: ACCEPT everything inbound on $IFACE (top of INPUT)"
iptables -D INPUT -i "$IFACE" -j ACCEPT 2>/dev/null || true
iptables -I INPUT 1 -i "$IFACE" -j ACCEPT
# also nftables-native, in case a table other than iptables-nft is filtering
if nft list tables 2>/dev/null | grep -q .; then
  nft insert rule inet filter input iifname "$IFACE" accept 2>/dev/null || true
fi

echo
echo "=== rp_filter now: all=$(cat /proc/sys/net/ipv4/conf/all/rp_filter) $IFACE=$(cat /proc/sys/net/ipv4/conf/$IFACE/rp_filter) ==="
echo "=== INPUT top rules ==="
iptables -L INPUT -n -v --line-numbers | head -6
echo
echo "############################################################"
echo "#  Path opened. dnsmasq (still running) should now HEAR the unit."
echo "#  Watch the hz-dhcp.sh window for DHCPDISCOVER -> OFFER -> ACK."
echo "#  If the unit already backed off, reboot the radio once more."
echo "############################################################"
