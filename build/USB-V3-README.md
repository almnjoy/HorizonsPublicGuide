# swupdate_6230_horizon_backdoor_v3-persist.zip

Built 2026-09-22 by the workstation. md5 `fa4a157a15b4cdca177236e668e68570`, 1.2MB.
Same frame as the proven `v2-chcon` (same update-binary, mount `/system_root`, chcon labels, AOSP testkey, whole-file signature verified).

## What it does
1. Installs `Horizon.apk` (the same v0.1 APK v2 shipped) to `/system/app`
2. Deletes the old v2 hook `horizon-adb.rc` and any `HzCar` priv-app + its allowlist
3. Installs `horizon-adbd.rc` (unchanged from repo) + a NEW `horizon-boot.sh`:
   - sets Horizon as HOME
   - if `adb_enabled` is 1, puts it back to 0 (never sets it to 1, never touches `sys.usb.config`)
   - re-asserts `setprop service.adb.tcp.port 5555; start adbd` every 5s for ~70s after boot, so our side wins the last round against AdbService
   - does NOT disable `com.skypine.split` or set immersive (three-surface plan needs stock split)
4. Ensures `service.adb.tcp.port=5555` in build.prop

## Order
1. Master Reset (Installer Settings, passcode). Clears `adb_enabled`.
2. Optional: stock firmware update if you want a clean /system first.
3. **Radio OFF, insert stick with this file at the root, power on.** Let it auto-detect at startup (USB is host mode then). Or run System Firmware Update from the UI if the stick is visible.
4. Reboot. Dongle in, `hz-snoop`, watch for DHCPACK, `adb connect <ip>:5555`.
5. Test the fix: reboot once more and confirm 5555 still answers ~2 min after boot.
6. Then the post-recovery checklist in `DEPLOY-STATE.md` (PAC config, export an Installer settings backup this time, reinstall v1.3/v1.5 over adb).

## Stick gotchas
- FAT32, file at the root, exact filename starting `swupdate_6230_`. One swupdate file on the stick.
- The 687MB full flash corrupted on cheap sticks before. This one is 1.2MB; still eject cleanly.

## Unproven
- That `start adbd` after AdbService settles actually sticks. Logic is sound, not yet run on the unit. If 5555 still closes, the fallback is the same as before: flash this again.
