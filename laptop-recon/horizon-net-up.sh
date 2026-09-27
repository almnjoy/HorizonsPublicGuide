#!/usr/bin/env bash
# Horizon head-unit adb-over-ethernet bring-up (omarchy-laptop side).
# Direct-connect topology: head unit + USB-eth adapter <-- cable --> laptop USB-eth adapter.
# The laptop becomes the DHCP server + NAT gateway, then adb-connects to the unit.
#
#   sudo ./horizon-net-up.sh            # bring up, wait for lease, adb connect
#   sudo ./horizon-net-up.sh down       # tear back down
set -uo pipefail

IFACE="${HORIZON_IFACE:-enp86s0u1}"
CON="horizon-lab"
ADB_PORT=5555

need_root() { [ "$(id -u)" -eq 0 ] || { echo "run with sudo"; exit 1; }; }

down() {
  echo "==> tearing down"
  nmcli connection down "$CON"     2>/dev/null
  nmcli connection delete "$CON"   2>/dev/null
  ufw delete allow in  on "$IFACE" 2>/dev/null
  ufw delete allow out on "$IFACE" 2>/dev/null
  ufw route delete allow in on "$IFACE" 2>/dev/null
  echo "done"
}

up() {
  if [ ! -e "/sys/class/net/$IFACE" ]; then
    echo "!! $IFACE not present. Plug the laptop's USB-eth adapter in."
    echo "   Interfaces: $(ls /sys/class/net | tr '\n' ' ')"
    exit 1
  fi

  echo "==> ufw: trust the whole $IFACE wire (point-to-point to the head unit)"
  # Opening only port 67 wins the lease and then silently drops everything after
  # it. Confirmed 2026-08-31: unit leased <UNIT_IP> and every follow-up packet
  # hit [UFW BLOCK] -- DNS first, and adb 5555 would have gone the same way.
  # This is a direct cable to one device we own, so trust the interface outright.
  ufw allow in  on "$IFACE" >/dev/null 2>&1
  ufw allow out on "$IFACE" >/dev/null 2>&1
  ufw route allow in on "$IFACE" >/dev/null 2>&1

  echo "==> NetworkManager shared connection on $IFACE (DHCP + NAT out the wifi)"
  nmcli connection delete "$CON" 2>/dev/null
  nmcli connection add type ethernet ifname "$IFACE" con-name "$CON" \
      ipv4.method shared ipv6.method disabled ipv4.never-default yes >/dev/null
  nmcli connection up "$CON" >/dev/null || { echo "!! failed to bring up $CON"; exit 1; }

  GW=$(ip -4 -br addr show "$IFACE" | awk '{print $3}')
  echo "    laptop is $GW  (shared mode hands out 10.42.0.50+)"
  echo
  echo "==> waiting for carrier on $IFACE (cable + unit powered)"
  for i in $(seq 1 40); do
    [ "$(cat /sys/class/net/$IFACE/carrier 2>/dev/null)" = "1" ] && break
    printf '\r    no link... %ss' "$((i*3))"; sleep 3
  done
  echo
  if [ "$(cat /sys/class/net/$IFACE/carrier 2>/dev/null)" = "1" ]; then
    echo "    link up at $(cat /sys/class/net/$IFACE/speed 2>/dev/null)Mb/s"
  else
    echo "    !! still no carrier. Reseat both dongles / check the unit is awake."
  fi

  echo "==> waiting for the head unit to take a lease (2 min)."
  echo "    Known unit MAC <UNIT_MAC>, leased <UNIT_IP> on 2026-08-31."

  LEASES="/var/lib/NetworkManager/dnsmasq-$IFACE.leases"
  UNIT_IP=""
  for i in $(seq 1 60); do
    if [ -s "$LEASES" ]; then
      UNIT_IP=$(awk '{print $3}' "$LEASES" | tail -1)
      [ -n "$UNIT_IP" ] && break
    fi
    # fall back to ARP if the lease file lags
    UNIT_IP=$(ip neigh show dev "$IFACE" 2>/dev/null | awk '/REACHABLE|STALE|DELAY/{print $1; exit}')
    [ -n "$UNIT_IP" ] && break
    printf '\r    ...%ss  link:%s' "$((i*2))" "$(cat /sys/class/net/$IFACE/carrier 2>/dev/null || echo ?)"
    sleep 2
  done
  echo

  if [ -z "$UNIT_IP" ]; then
    echo "!! no lease yet."
    echo "   carrier: $(cat /sys/class/net/$IFACE/carrier 2>/dev/null)  (1 = cable/link good, 0 = no link)"
    echo "   If carrier is 0, the cable or the unit-side adapter is not linking."
    echo "   If carrier is 1 but no lease, the unit's kernel may lack the r8152 driver."
    echo "   Watch raw DHCP with:  tcpdump -ni $IFACE port 67 or port 68"
    exit 1
  fi

  echo "==> head unit at $UNIT_IP"
  ping -c2 -W2 "$UNIT_IP" >/dev/null 2>&1 && echo "    ping OK" || echo "    ping failed (may still adb fine)"

  echo "==> adb connect $UNIT_IP:$ADB_PORT"
  adb disconnect >/dev/null 2>&1
  adb connect "$UNIT_IP:$ADB_PORT"
  sleep 1
  adb devices
  echo
  echo "    id check:  adb shell getprop ro.build.display.id"
  echo "    root?      adb shell id"
  echo "    install:   adb install -r <path>/Horizon.apk"
  echo "$UNIT_IP" > /tmp/horizon-unit-ip
}

need_root
case "${1:-up}" in
  down) down ;;
  *)    up ;;
esac
