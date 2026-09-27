#!/usr/bin/env bash
# Defuse the HzCar boot-loop landmine.
# Removes the testkey priv-app (BootReceiver -> CarService race -> system_server crash)
# and replaces it with the platform-signed /data build that has NO BootReceiver.
#   ~/.local/bin/hz-defuse
set -uo pipefail
IP=${IP:-<UNIT_IP>}; S=$IP:5555
DIR=/home/user/Projects/LaptopProjects/Horizons/HorizonsStinger
BK=$DIR/laptop-recon/backup-$(date +%Y%m%d-%H%M%S)
G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; N=$'\e[0m'
say(){ echo "${G}==>${N} $*"; }
die(){ echo "${R}!! $*${N}"; exit 1; }

adb connect $S >/dev/null 2>&1; sleep 1
adb devices | grep -q "$S[[:space:]]*device" || die "no adb at $S - is hz-snoop running and the unit booted?"
say "connected: $S"

say "backup -> $BK"
mkdir -p "$BK"
adb -s $S pull /system/priv-app/HzCar "$BK/HzCar-privapp" >/dev/null 2>&1
adb -s $S pull /system/etc/permissions/privapp-permissions-horizon.xml "$BK/" >/dev/null 2>&1

say "remount / rw"
adb -s $S shell 'mount -o rw,remount /' 2>&1 | tr -d '\r'

say "removing the priv-app + its permission grant"
adb -s $S shell 'rm -rf /system/priv-app/HzCar' 2>&1 | tr -d '\r'
adb -s $S shell 'rm -f /system/etc/permissions/privapp-permissions-horizon.xml' 2>&1 | tr -d '\r'

say "remount / ro"
adb -s $S shell 'mount -o ro,remount /' 2>&1 | tr -d '\r'

echo
adb -s $S shell 'ls -d /system/priv-app/HzCar' >/dev/null 2>&1 \
  && die "priv-app STILL present - remount probably failed, nothing changed" \
  || say "${G}confirmed: /system/priv-app/HzCar is gone${N}"
adb -s $S shell 'ls /system/etc/permissions/privapp-permissions-horizon.xml' >/dev/null 2>&1 \
  && echo "${Y}   note: permissions xml still present${N}" \
  || say "confirmed: permissions xml is gone"

echo
echo "${Y}Next step REBOOTS the head unit. That is the proof the loop is dead.${N}"
echo "If it loops anyway, recover with build/swupdate_hzcar_remove.zip from a USB stick."
read -rp "Reboot now? [y/N] " a; [ "${a,,}" = y ] || { echo "stopped. run again when ready."; exit 0; }

say "rebooting"
adb -s $S reboot 2>/dev/null; adb disconnect $S >/dev/null 2>&1
sleep 20
say "waiting for it to come back (up to 3 min)..."
for i in $(seq 1 45); do
  if timeout 2 bash -c "echo > /dev/tcp/$IP/5555" 2>/dev/null; then
    adb connect $S >/dev/null 2>&1; sleep 2
    if adb devices | grep -q "$S[[:space:]]*device"; then
      bc=$(adb -s $S shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      [ "$bc" = "1" ] && { say "${G}BOOT COMPLETED - the loop is dead${N}"; break; }
    fi
  fi
  printf '.'; sleep 4
done
echo
adb devices | grep -q "$S[[:space:]]*device" || die "did not come back. USB-stick recovery needed."

say "installing the platform-signed helper to /data (no BootReceiver)"
adb -s $S install -r "$DIR/build/HzCar-platform.apk" 2>&1 | tr -d '\r' | tail -2
adb -s $S shell 'pm grant com.example.hzcar android.permission.WRITE_EXTERNAL_STORAGE' 2>&1 | tr -d '\r'
adb -s $S shell 'am startservice -n com.example.hzcar/.HzCarService' 2>&1 | tr -d '\r'

say "where it lives now (want /data/app, NOT /system):"
adb -s $S shell 'pm path com.example.hzcar' 2>&1 | tr -d '\r' | sed 's/^/    /'
say "BootReceiver registered? (want nothing):"
adb -s $S shell 'dumpsys package com.example.hzcar | grep -c BootReceiver' 2>&1 | tr -d '\r' | sed 's/^/    matches: /'

sleep 8
say "live data after 8s:"
adb -s $S shell 'cat /sdcard/horizon/live.json' 2>&1 | tr -d '\r' | sed 's/^/    /'
echo; say "done. backup at $BK"
