# Deploy Horizon WebView shell (v0.2) — your cockpit, live on the unit

Built 2026-09-02 on omarchy-laptop. `Horizon-webview-v0.2.apk` = your existing
Horizon package (com.example.horizon, HOME launcher, fullscreen) with MainActivity
swapped to a WebView that loads assets/index.html = HORIZON12-Cockpit-Prototype.html
(all 8 screens, embedded Gagalin + webp). Signed with the AOSP testkey via signapk.

## When the radio is back on
1. Reconnect:  sudo ~/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/hz-dhcp.sh ; sudo ~/Projects/LaptopProjects/Horizons/HorizonsStinger/laptop-recon/hz-fix-input.sh ; adb connect <UNIT_IP>:5555
2. Deploy (root-push to /system, because the installed Horizon is signed with a
   different key so `adb install -r` would be refused):
     adb -s <UNIT_IP>:5555 root            # already root, no-op
     adb -s <UNIT_IP>:5555 remount 2>/dev/null || \
       adb -s <UNIT_IP>:5555 shell 'mount -o rw,remount /system 2>/dev/null || mount -o rw,remount /system_root'
     adb -s <UNIT_IP>:5555 push Horizon-webview-v0.2.apk /system/app/Horizon.apk
     adb -s <UNIT_IP>:5555 shell 'chmod 0644 /system/app/Horizon.apk; chcon u:object_r:system_file:s0 /system/app/Horizon.apk'
     adb -s <UNIT_IP>:5555 reboot
3. After boot: press HOME, pick Horizon. You should see your cockpit render in the WebView.

## If it doesn't render / crashes
- `adb logcat -d | grep -iE "horizon|webview|AndroidRuntime"` right after launch.
- Fallback that never touches /system: `adb install -r` won't work (sig mismatch); instead
  temporarily rename the old one:  adb shell 'mv /system/app/Horizon.apk /sdcard/Horizon.apk.bak'
  then reboot to lose the launcher, then push the new one. (Keep the .bak to roll back.)
- Roll back: push the original build/Horizon-webview... no — restore from recon/2026-09-02/apks/com.example.horizon.apk (the ORIGINAL native app), same push+reboot.

## NEXT after it renders (Milestone 2 — live data)
Add a data bridge: root daemon on the unit scrapes `dumpsys car_service` (HVAC_TEMPERATURE_SET,
HVAC_FAN_SPEED, HVAC_AUTO_ON, ENV_OUTSIDE_TEMPERATURE) + `dumpsys media_session` (now-playing),
serves JSON on 127.0.0.1; the cockpit JS fetches it and binds to the screens. Two-way (set
fan/temp) later via a proper platform-signed native CarPropertyManager bridge.
