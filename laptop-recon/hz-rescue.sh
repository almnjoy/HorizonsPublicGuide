#!/usr/bin/env bash
# RACE THE BOOT: catch adbd in the window between it starting and the framework
# reconfiguring USB, then immediately undo adb_enabled so host mode survives.
# Start this BEFORE powering the radio on.  ~/.local/bin/hz-rescue
IP=${IP:-<UNIT_IP>}; S=$IP:5555
G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; N=$'\e[0m'
adb start-server >/dev/null 2>&1
echo "${Y}ARMED. Power the radio on now.${N}  hammering $S with no delay..."
HIT=0
while :; do
  # bare TCP probe, no timeout overhead - we want to see the port the instant it opens
  if (exec 3<>/dev/tcp/$IP/5555) 2>/dev/null; then
    exec 3<&- 2>/dev/null; exec 3>&- 2>/dev/null
    HIT=$((HIT+1))
    [ $HIT -eq 1 ] && echo "${G}>>> 5555 OPEN $(date +%T.%3N) - firing fix${N}"
    adb connect $S >/dev/null 2>&1
    # fire the fix repeatedly; any one landing is enough, it persists in /data
    adb -s $S shell 'settings put global adb_enabled 0' 2>&1 | tr -d '\r' | sed 's/^/    /'
    V=$(adb -s $S shell 'settings get global adb_enabled' 2>/dev/null | tr -d '\r')
    echo "    adb_enabled now = [$V]  (attempt $HIT)"
    if [ "$V" = "0" ]; then
      echo "${G}"
      echo "  ================================================"
      echo "   FIX LANDED. adb_enabled=0 and it persists."
      echo "   USB host mode will survive the NEXT boot."
      echo "  ================================================${N}"
      adb -s $S shell 'setprop persist.sys.usb.config host' 2>/dev/null
      echo "  re-asserted persist.sys.usb.config=host"
      exit 0
    fi
  fi
done
