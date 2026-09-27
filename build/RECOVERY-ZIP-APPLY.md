# Horizon recovery-zip: apply + rollback

## Files (newest md5s)

| File | md5 | What it does | Risk |
|---|---|---|---|
| `swupdate_6230_horizon.zip` | `5a43461620d5d632f35d2f182219fa54` | **Try first.** Adds Horizon.apk only. No firmware change. | None (app-add only) |
| `swupdate_6230_horizon_full.zip` | `2dceebf6e11afe6a92b88081d6f5669d` | Full 0.6.9 firmware + Horizon + root adb door, one flash. Injection now mounts `/system_root` (the real system-as-root path). | Full flash (see below) |
| `swupdate_6230_horizon_remove.zip` | `42fe51ac6c06998a3d0963d370927729` | Deletes Horizon.apk back off /system. | None |

## Try order
1. Flash **`swupdate_6230_horizon.zip`** (minimal). If it installs, done, zero firmware risk.
2. If it fails at unzip, flash **`swupdate_6230_horizon_full.zip`** (read the two sections below first).

## What the full-inject is
It's Stinger's real `swupdate` update, unmodified where it matters, with our stuff appended:
- The inner `update.zip` is the genuine 0.6.9 firmware (system, vendor, boot, dtb, splash, bootloader, SNOR) with **their flashing script left byte-for-byte intact**. I only appended two post-flash steps: copy `Horizon.apk` into `/system/app`, and add `service.adb.tcp.port=5555` to `build.prop`. Both run AFTER all flashing finishes, so they cannot brick, worst case you get the firmware without the app.
- It's re-signed with the AOSP testkey (`A40DA80A…5DC`, matches your otacert) and wrapped with the real `b_update.hex` / `l_update.hex`, so the structure is identical to the real package.
- The inject writes Horizon to both `/system/app` and `/system/system/app`, and patches `build.prop` in both possible locations, because this is a system-as-root image and I can't be 100% sure which path the mount uses. The wrong one no-ops.

## The root door (what you asked for)
`service.adb.tcp.port=5555` in build.prop makes **adbd listen on TCP 5555 every boot, as root** (the unit is `ro.secure=0`). How to actually reach it, given the USB ports are host-only and there's no general Wi-Fi:
- Plug a cheap **USB-to-Ethernet adapter** (or a USB Wi-Fi dongle the unit supports) into a front USB port. The unit is a USB host there, so it takes the adapter and gets an IP.
- From a laptop on the same network: `adb connect <unit-ip>:5555` then `adb shell`. Root shell.
- That turns the host-only ports (which blocked adb-over-USB) into the way in. Find the unit's IP from your router/DHCP or the adapter.

Security note: this leaves a root adb port open on any network the unit joins. It's a deliberate backdoor for you; the remove zip or a later build.prop edit closes it.

## The risk of the full file (said plainly)
It is the full firmware flash. It rewrites the **bootloader and SNOR**, so if power drops mid-flash it can brick. It **upgrades you 0.6.1 → 0.6.9, PAC V11 → V15, reflashes the MCU, and factory-resets all settings.** That is the same risk any official firmware update carries (these are Stinger's exact signed images), plus our two safe post-flash steps. Do it with the vehicle running or on solid power, and do not interrupt it.

## After a successful install (either file)
Reboot, press home, pick Horizon (Just once to test, then Always, or Settings > Default apps > Home).

## Rollback
Horizon lives in `/system`, so a factory reset does NOT remove it.
1. Easiest: Settings > Apps > Horizon > Disable, or just don't set it as Home.
2. Full remove: flash `swupdate_6230_horizon_remove.zip`.
3. The stock launcher is never touched, so you always have a home to fall back to. (Note: the remove zip deletes the app but leaves the adb-tcp line in build.prop; say the word and I'll add a build.prop-cleanup step if you want that gone too.)
