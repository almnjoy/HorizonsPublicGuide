# Custom launcher on a Stinger HORIZON12 / iX212 head unit

A working method for replacing the home screen on a **Stinger HORIZON12 / iX212** aftermarket
Android head unit (Telechips `car_tcc803x`, Android 9) with your own launcher — and getting a
root ADB shell to iterate on it.

> **Do this only to a unit you own.** Modifying a head unit can void your warranty and, done
> carelessly, can brick it. Everything here writes to `/system` and `/data`, never the
> bootloader — but you are responsible for your own hardware. No affiliation with Stinger or PAC.

The build story with photos and the video: https://almnjoy.dev/h12-gh

---

## TL;DR — how it works

This unit ships as an Android 9 `eng` build with `ro.secure=0` and SELinux permissive. The
one thing that makes the whole method possible:

**The firmware is signed with the public AOSP test key.** The manufacturer never generated
their own release key, so the private key that signs an over-the-air update is publicly
available from AOSP. That means *you* can build and sign a firmware package the unit will
trust and install.

From there the path is:

1. **Read the stock firmware** and confirm the signing key is the public AOSP testkey.
2. **Build a signed update** that installs a small init script instead of fighting the UI.
3. **The init script opens ADB over TCP** (`service.adb.tcp.port=5555`).
4. **Reach it over the network** — the USB ports are host-only, so a USB-to-Ethernet dongle
   plus a DHCP server on your laptop gives the unit an IP and you a root shell.
5. **Recon in read-only mode** to map what talks to what, then install your own launcher.

---

## The unit (what you're working with)

| | |
|---|---|
| SoC | Telechips TCC803x (4× Cortex-A53), Mali GPU |
| OS | Android 9, build flavor `car_tcc803x-eng`, **test-keys**, `ro.secure=0`, SELinux permissive |
| Display | 1920×1080 |
| System layout | **system-as-root** (recovery mounts the system partition at `/system_root`) |
| Vehicle data | a PAC integration module bridges the car's CAN bus to Android's CarService |

The head unit is essentially an Android tablet. Its apps talk to a **PAC translator module**,
which reads the car's **CAN bus** — the in-car network every controller (steering wheel,
sensors, HVAC) sits on. That's how the stereo knows your speed, gear and oil temp.

---

## Step 1 — Read the firmware and find the key

Download the newest firmware update from the manufacturer's site and unpack it. It's a
standard Android recovery OTA (`update.zip`) with a whole-file PKCS#7 signature.

Pull the certificate out of `META-INF/com/android/otacert` and compare its fingerprint to the
**public AOSP test certificate**. On this unit they match — which means the matching private
key (`testkey.pk8` / `testkey.x509.pem`) is the one published in the AOSP source tree under
`build/target/product/security/`. Fetch that key pair from AOSP; you'll sign with it.

See [`HORIZON12_iX212_Firmware_Analysis.md`](HORIZON12_iX212_Firmware_Analysis.md) for the full
firmware/partition breakdown, and [`HORIZON12-OnUnit-Reference.md`](HORIZON12-OnUnit-Reference.md)
for the on-screen menus and the installer/backup steps to do **before** you touch anything.

## Step 2 — Build a signed "backdoor" update

Rather than flashing a whole custom firmware (which rewrites the bootloader and can brick the
unit), build a **minimal OTA** that only adds two things to `/system`:

- an init script ([`build/horizon-adbd.rc`](build/horizon-adbd.rc)) that starts `adbd` on TCP
  5555 every boot, and
- a boot script ([`build/horizon-boot.sh`](build/horizon-boot.sh)) that re-asserts it.

Because the unit is **system-as-root**, the updater-script mounts the system partition at
`/system_root` and writes to `/system_root/system/...`. Sign the finished zip with the AOSP
testkey (whole-file OTA signature, e.g. `signapk -w`). Full recipe and traps:
[`build/RECOVERY-ZIP-APPLY.md`](build/RECOVERY-ZIP-APPLY.md) and
[`build/USB-V3-README.md`](build/USB-V3-README.md).

Flash it from the on-screen **System Firmware Update** with a USB stick.

> **The `adb_enabled` trap:** do **not** run `settings put global adb_enabled 1` on this unit.
> Enabling ADB the "normal" way makes Android reconfigure USB out of host mode, which kills the
> only network path you have (the Ethernet dongle) and takes the unit off the network. The
> `v3` boot script ([`build/v3/`](build/v3)) leaves that setting alone and just re-asserts
> `adbd` on a short loop after boot. This is the durable fix.

## Step 3 — Get on the network and get root

The USB ports run as **host**, so you can't `adb` over a USB cable (two hosts facing each
other). Instead:

1. Put a **USB-to-Ethernet dongle** in a front port. The unit takes it as a host device.
2. Run a **DHCP server on your laptop** over that link and hand the unit an IP. The scripts in
   [`laptop-recon/`](laptop-recon) do exactly this on Linux
   ([`hz-dhcp.sh`](laptop-recon/hz-dhcp.sh), [`hz-net.sh`](laptop-recon/hz-net.sh),
   [`hz-snoop.sh`](laptop-recon/hz-snoop.sh)). They also punch the firewall hole a broadcast
   DHCP Discover needs (a stray `nft` drop rule will silently eat it — see the comments).
3. `adb connect <unit-ip>:5555` → root shell (`ro.secure=0`).

Now you can `adb install -r your.apk`, push files to `/sdcard`, and read the whole system live.

## Step 4 — Recon before you build

Put your AI/assistant or yourself in **read-only mode** first. Map what's installed, what each
menu is made of, and what survives a reboot before changing anything:

```
adb -s <unit-ip>:5555 shell pm list packages -f
adb -s <unit-ip>:5555 shell dumpsys activity activities | grep mResumedActivity
adb -s <unit-ip>:5555 shell dumpsys car_service          # the vehicle property table
```

The stock UI is not one app — it's several (a launcher, the PAC integration, a split/dashboard
service, projection apps, media, radio). Knowing which package draws which screen is what lets
you sit *beside* them instead of fighting them.

## Step 5 — Install your launcher

Two approaches, from simplest to most robust:

- **Set your own HOME app.** Build a launcher, `adb install -r`, then
  `cmd package set-home-activity <your.pkg>/.MainActivity`. The stock launcher stays installed
  as a fallback.
- **Overlay chrome that survives projection.** The stock frame reserves a left rail and a right
  panel around *every* app (CarPlay included). You can reclaim those exact reserved strips with
  `TYPE_APPLICATION_OVERLAY` WebViews and paint your own UI there. The Java sources under
  [`build/bridge-src/`](build/bridge-src) show the overlay service; the HTML templates under
  [`build/preview/`](build/preview) are a starting point you can restyle.

A key window-manager gotcha: a system navigation-bar window outranks an application overlay, so
your overlay gets inset unless you add `FLAG_LAYOUT_NO_LIMITS`. See the source comments.

## Live vehicle data (optional)

The gauge/CAN properties are `signature|privileged`, but on this test-keyed build a
**testkey/platform-signed** helper gets them with no priv-app allowlist. The helper under
[`build/car-helper-src/`](build/car-helper-src) reads `CarPropertyManager` and writes a small
JSON your UI polls. Which properties actually exist on this unit (speed, odometer, fuel, gear,
oil temp are present; RPM, coolant, oil pressure, 12V and 4x4 are **not** standard here) is
documented in the firmware analysis.

---

## Recovery

- **Remove your changes:** the `swupdate_..._remove` recipe deletes your added files back off
  `/system`. Build it the same way (updater-script + testkey signature).
- **Lost the network after an `adb_enabled` mistake:** power off, insert the firmware USB
  stick, then power on — the unit reads the stick at startup while USB is still in host mode.
  Or Master Reset from the passcode-gated Installer menu (this also clears the PAC integration
  config, so re-do that after).
- **Signer mismatch on `adb install -r`** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): a `/system`
  app can't be updated by a differently-signed build. Remove it from `/system`, reboot so
  PackageManager forgets it, then install to `/data`.

---

## Repo layout

```
HORIZON12_iX212_Firmware_Analysis.md   full firmware / partition / signing breakdown
HORIZON12-OnUnit-Reference.md          on-screen menus, installer menu, backup-first steps
build/                                 init scripts, boot scripts, updater recipes, app source
  bridge-src/                          the launcher + overlay service (Java)
  car-helper-src/                      the vehicle-data helper (CarPropertyManager)
  preview/                             HTML UI templates (rail / center / switcher)
  RECOVERY-ZIP-APPLY.md, USB-V3-README.md, DEPLOY-WEBVIEW.md   how to build/flash the zips
laptop-recon/                          Linux scripts: DHCP + adb-over-ethernet + recovery
src/                                   minimal launcher source + BUILD.md
tools/                                 build helper + the public AOSP certs (fetch the keys from AOSP)
```

## Notes / credits

- The signing keys referenced here are the **public AOSP test keys** — fetch them from the
  Android Open Source Project; they are not shipped in this repo.
- Package names in the source use `com.example.*` — rename to your own before you build.
- This documents one person's method on their own unit. Your firmware version, PAC module and
  exact property table may differ. Verify on your own hardware in read-only mode first.
