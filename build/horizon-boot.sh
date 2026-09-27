#!/system/bin/sh
# Horizon boot fixups: run as root after boot_completed. Best-effort, SAFE.
# NOTE: aggressive `am stack remove` loops crash system_server (learned the hard
# way 2026-09-11) -> do NOT do stack manipulation here. Just disable the stock
# split app, set immersive, make our launcher HOME, and bring it to front once.
# Fully defeating the CarSystemUI persistent split needs attended work, not this.
sleep 12
pm disable-user --user 0 com.skypine.split 2>/dev/null
settings put global policy_control immersive.full='*' 2>/dev/null
cmd package set-home-activity com.example.horizon/.MainActivity 2>/dev/null
am start --windowingMode 1 -n com.example.horizon/.MainActivity >/dev/null 2>&1
