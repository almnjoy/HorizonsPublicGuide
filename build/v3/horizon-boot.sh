#!/system/bin/sh
# Horizon boot v3 (2026-09-22). Runs as root after boot_completed via horizon-adbd.rc.
# HARD RULES (DEPLOY-STATE.md, 2026-09-22 incident):
#   - NEVER set adb_enabled=1 and NEVER write sys.usb.config. USB host mode carries the
#     ethernet dongle, which is the unit's ONLY network path. Enabling adb removes adb.
#   - No am stack manipulation (crashed system_server twice, 2026-09-11).
# Deliberately does NOT disable com.skypine.split or set immersive: the three-surface
# chrome plan needs the stock 170 / 1321 / 429 split left alone.

# Make our launcher HOME (no-op if already set).
cmd package set-home-activity com.example.horizon/.MainActivity >/dev/null 2>&1

# If something ever flipped adb_enabled to 1, put it back so host mode survives.
if [ "$(settings get global adb_enabled 2>/dev/null)" = "1" ]; then
  settings put global adb_enabled 0
fi

# adbd is 'disabled' in stock init and AdbService stops it shortly after boot because
# adb_enabled=0. The rc wins the first round; this loop wins the LAST one (~70s window).
i=0
while [ $i -lt 14 ]; do
  setprop service.adb.tcp.port 5555
  start adbd
  sleep 5
  i=$((i+1))
done
