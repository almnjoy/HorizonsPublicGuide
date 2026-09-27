#!/usr/bin/env bash
# Apply the v3 persistence fix over adb instead of reflashing a USB stick.
# Installs v3's horizon-adbd.rc + horizon-boot.sh with the same modes and SELinux
# labels the v3 updater-script uses, and removes the old v2 horizon-adb.rc hook.
#   hz-v3            (IP=10.42.0.x hz-v3  to override)
set -uo pipefail
IP=${IP:-<UNIT_IP>}; S=$IP:5555
D=/home/user/Projects/LaptopProjects/Horizons/HorizonsStinger
G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; N=$'\e[0m'
say(){ echo "${G}==>${N} $*"; }
die(){ echo "${R}!! $*${N}"; exit 1; }

[ -f "$D/build/v3/horizon-boot.sh" ]  || die "missing build/v3/horizon-boot.sh"
[ -f "$D/build/v3/horizon-adbd.rc" ] || die "missing build/v3/horizon-adbd.rc"

adb connect $S >/dev/null 2>&1; sleep 1
adb devices | grep -q "$S[[:space:]]*device" || die "no adb at $S - is hz-snoop up and the radio booted?"
say "connected $S  (root: $(adb -s $S shell id -u 2>/dev/null | tr -d '\r'))"

BK=$D/laptop-recon/backup-v2-$(date +%Y%m%d-%H%M%S); mkdir -p "$BK"
say "backing up what is there now -> $BK"
for f in /system/bin/horizon-boot.sh /system/etc/init/horizon-adbd.rc /system/etc/init/horizon-adb.rc; do
  adb -s $S pull "$f" "$BK/" >/dev/null 2>&1 && echo "    saved $f"
done

say "remount / rw"
adb -s $S shell 'mount -o rw,remount /' 2>&1 | tr -d '\r'

say "installing v3 boot script"
adb -s $S push "$D/build/v3/horizon-boot.sh" /system/bin/horizon-boot.sh 2>&1 | tr -d '\r' | tail -1
adb -s $S shell 'chmod 0755 /system/bin/horizon-boot.sh; chown 0.0 /system/bin/horizon-boot.sh; chcon u:object_r:system_file:s0 /system/bin/horizon-boot.sh' 2>&1 | tr -d '\r'

say "installing v3 init hook"
adb -s $S push "$D/build/v3/horizon-adbd.rc" /system/etc/init/horizon-adbd.rc 2>&1 | tr -d '\r' | tail -1
adb -s $S shell 'chmod 0644 /system/etc/init/horizon-adbd.rc; chown 0.0 /system/etc/init/horizon-adbd.rc; chcon u:object_r:system_file:s0 /system/etc/init/horizon-adbd.rc' 2>&1 | tr -d '\r'

say "removing the old v2 hook (it fought us and disabled the stock split)"
adb -s $S shell 'rm -f /system/etc/init/horizon-adb.rc' 2>&1 | tr -d '\r'

say "ensuring service.adb.tcp.port=5555 in build.prop"
adb -s $S shell "grep -q '^service.adb.tcp.port' /system/build.prop || echo service.adb.tcp.port=5555 >> /system/build.prop" 2>&1 | tr -d '\r'

say "remount / ro"
adb -s $S shell 'mount -o ro,remount /' 2>&1 | tr -d '\r'

echo; say "VERIFY"
adb -s $S shell 'ls -lZ /system/bin/horizon-boot.sh /system/etc/init/horizon-adbd.rc 2>/dev/null' 2>&1 | tr -d '\r' | sed 's/^/    /'
adb -s $S shell 'ls /system/etc/init/horizon-adb.rc 2>/dev/null && echo "    WARN old v2 hook still present" || echo "    old v2 hook gone"' 2>&1 | tr -d '\r'
adb -s $S shell 'echo "    adb_enabled          = $(settings get global adb_enabled)"; echo "    service.adb.tcp.port = $(getprop service.adb.tcp.port)"' 2>&1 | tr -d '\r'
echo "    v3 marker in boot script: $(adb -s $S shell 'grep -c "Horizon boot v3" /system/bin/horizon-boot.sh' 2>/dev/null | tr -d '\r')"

echo
echo "${Y}The only real test is a reboot: does 5555 still answer ~2 min after a cold boot?${N}"
echo "If it does, the USB-flash tax is gone. If it does not, you are no worse off - flash"
echo "the v3 stick as before. Backup of the v2 files is in $BK"
read -rp "Reboot now and watch for the door? [y/N] " a
[ "${a,,}" = y ] || { echo "stopped. run 'hz-v3-test' later, or reboot by hand."; exit 0; }

say "rebooting - watching for adbd, up to 4 min"
adb -s $S reboot 2>/dev/null; adb disconnect $S >/dev/null 2>&1; sleep 25
for i in $(seq 1 48); do
  if timeout 2 bash -c "echo > /dev/tcp/$IP/5555" 2>/dev/null; then
    adb connect $S >/dev/null 2>&1; sleep 2
    if adb devices | grep -q "$S[[:space:]]*device"; then
      echo; echo "${G}  ================================================"
      echo "   DOOR SURVIVED THE REBOOT. Persistence fixed."
      echo "  ================================================${N}"
      adb -s $S shell 'echo "  uptime: $(uptime)"; echo "  adb_enabled: $(settings get global adb_enabled)"' 2>&1 | tr -d '\r'
      echo; echo "  Now wait 2 more minutes and re-check - AdbService settles late:"
      echo "    timeout 3 bash -c 'echo > /dev/tcp/$IP/5555' && echo STILL OPEN || echo CLOSED"
      exit 0
    fi
  fi
  printf '.'; sleep 5
done
echo; echo "${R}  door did NOT come back. v3 logic did not stick. Flash the v3 stick.${N}"
