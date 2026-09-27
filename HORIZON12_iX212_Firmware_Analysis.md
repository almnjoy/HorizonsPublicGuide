# Stinger HORIZON12 / iX212 Firmware Analysis

**Report date:** August 21, 2026  
**Source directory:** `Z:\CustomApps\BUSINESS\ChatGPT-Codex\horizons`  
**Original OTA:** `Z:\CustomApps\BUSINESS\ChatGPT-Codex\update.zip`  
**Device family:** Stinger HORIZON12 / iX212, shared iX2xx firmware platform

## Executive summary

The Stinger HORIZON12 is a customized Android 9 automotive head unit built on a Telechips TCC8031/TCC803x platform. It uses a 32-bit Android userspace on four Cortex-A53 cores, 2 GB of RAM, a Mali GPU, dedicated video-processing hardware, and an external automotive audio DSP. The vendor board is identified as a **Skypine A11T Board for AVN**.

This is not a conventional locked consumer Android build. The firmware is an `eng/test-keys` build with:

- `ro.secure=0`
- `ro.debuggable=1`
- ADB configured in the system defaults
- SELinux forced to permissive at boot
- A setuid-root `su` binary
- BusyBox
- AOSP Settings, Package Installer, and DocumentsUI
- Root Explorer preinstalled
- Recovery, fastboot, and USB-boot code in the bootloader

The device therefore appears to be **already rooted by design**. No rooting exploit or replacement ROM should be necessary.

The bulky Stinger interface is primarily the HOME application `com.skypine.mainapp`. Vehicle integration is handled separately by system services and by `com.aampglobal.radio`, the PAC Integration V15 application. The safest customization is consequently to install a lightweight replacement HOME/launcher while leaving the Skypine, PAC, Android Automotive, camera, audio, Bluetooth, CarPlay, and Android Auto packages enabled.

The firmware also contains a platform-signed `com.skypine.split` application with a `SplitActivity`, `SplitService`, task-resizing code, widget interfaces, and resources for HDMI, CarPlay, Android Auto, media, radio, cameras, phone, and vehicle functions. Controlled split-screen operation is therefore not merely theoretical; the manufacturer already implemented a split/dashboard subsystem that should be investigated before building a replacement.

## Scope and methodology

The extracted `horizons` directory was inventoried recursively without modifying its contents. The Android `system.new.dat.br` and `vendor.new.dat.br` block streams were decompressed and reconstructed only in anonymous memory, then inspected as ext4 filesystems. No partition image was written to disk or mounted read-write.

The analysis included:

- File headers and container signatures
- OTA metadata and updater script
- Transfer-list and ext4 filesystem structure
- Android build properties
- Kernel and boot-image metadata
- Device-tree properties
- Bootloader strings and partition-map clues
- Android binary manifests for 82 APKs
- APK v1/v2 signing certificates
- Installed activities, services, receivers, permissions, and HOME handlers
- ADB, USB, developer, root, recovery, fastboot, and verity indicators
- Intel HEX validation for the two microcontroller images
- Original `update.zip` structure, checksum, signature footer, and signer certificate

The only file created by this work is this report.

## Firmware and container formats

The firmware is a legacy, non-A/B Android block OTA rather than a modern `payload.bin` update.

| Item | Format and purpose |
|---|---|
| `update.zip` | Android recovery OTA ZIP with a whole-file PKCS#7 signature |
| `system.new.dat.br` | Brotli-compressed transfer-list v4 block stream; reconstructs a 1.5 GiB ext4 system partition |
| `vendor.new.dat.br` | Brotli-compressed transfer-list v4 block stream; reconstructs a 100 MiB ext4 vendor partition |
| `system.transfer.list` | Android block OTA transfer list, version 4 |
| `vendor.transfer.list` | Android block OTA transfer list, version 4 |
| `system.patch.dat` / `vendor.patch.dat` | Empty; this is a full replacement rather than a differential patch |
| `boot.img` | Android boot image header v1 with a compressed ARM kernel and no ramdisk |
| `dtb.img` | Flattened Device Tree, version 17 |
| `bootloader.img` | Telechips bootloader/secure-firmware container with U-Boot-style commands and OP-TEE-related components |
| `snor.img` | Serial NOR firmware image installed through a Telechips-specific updater device |
| `splash.img` | Proprietary multi-image splash container |
| `compatibility.zip` | Android VINTF system/vendor compatibility matrices and manifests |
| `update-binary` | Statically linked 32-bit ARM recovery updater with Telechips extensions |
| `b_update.hex` | Valid Intel HEX MCU firmware mapped at `0x08004000`; 95,180 populated bytes |
| `l_update.hex` | Valid Intel HEX MCU firmware mapped at `0x08004000`; 37,264 populated bytes |

Both Intel HEX files have valid checksums and ARM Thumb-style entry addresses. Their `0x08000000` flash mapping is typical of Cortex-M microcontrollers. Their exact roles are not named in the files; they are likely separate peripheral/microcontroller firmwares.

## OTA behavior

The OTA asserts that the target device is `car_tcc803x`. Its update script performs the following operations:

1. Replaces the system partition using `system.new.dat.br`.
2. Replaces the vendor partition using `vendor.new.dat.br`.
3. Writes `boot.img` directly to the boot partition.
4. Writes `splash.img` to the splash partition.
5. Writes `dtb.img` to both `dtb` and `dtb_rec`.
6. Installs `bootloader.img` with a Telechips-specific updater function.
7. Installs `snor.img` with a Telechips-specific updater function.

The package does not explicitly write a recovery image. The system partition contains `install-recovery.sh` plus a `recovery-from-boot.p` patch, which can reconstruct the expected recovery partition from the boot image.

Because the stock OTA updates the bootloader and Serial NOR firmware unconditionally, modifying and reflashing the complete OTA is much riskier than installing or replacing an Android application.

## Android build

| Property | Value |
|---|---|
| Android release | Android 9 |
| SDK/API level | 28 |
| Build ID | `0.6.9_07EC_260624` |
| Incremental build | `68` |
| Build timestamp | June 24, 2026, 04:16:18 UTC |
| Security patch level | October 5, 2018 |
| Build flavor | `car_tcc803x-eng` |
| Build type | `eng` |
| Build tags | `test-keys` |
| Product | `car_tcc803x` |
| Brand | `Dolphin_P` |
| Manufacturer | `telechips` |
| Model | `TCC803X_LCN` |
| ABI | `armeabi-v7a`, `armeabi` |
| Zygote | 32-bit |
| System-as-root | Enabled |
| Treble | Enabled |

The reported 2018 security patch is extremely old even though the vendor build itself was produced in 2026. The head unit should not be treated as a secure general-purpose computer or exposed to untrusted networks.

## Kernel and hardware platform

The boot image contains a gzip-compressed kernel:

`Linux version 4.14.137-tcc (jenkins9@yb-android2) ... #1 SMP PREEMPT Wed Jun 24 12:00:44 CST 2026`

Device-tree findings:

- Compatible SoC: `telechips,tcc8031` / `telechips,tcc803x`
- Board model: `Skypine A11T Board for AVN`
- CPU: four ARM Cortex-A53 cores
- Memory: 2 GiB, starting at physical address `0x20000000`
- GPU: ARM Mali Midgard
- Display framebuffer: Telechips VIOC framebuffer
- Storage controller used by OTA: `16440000.sdhc`
- Audio card: `TCC8031 AK7739 Card`
- Multiple I2S, SPDIF, video, camera, HDMI, USB, mailbox, and automotive interfaces
- Five PL011-style serial controllers are described in the base device tree
- Kernel console: `ttyAMA0` at 115200 baud

The vendor CarPlay configuration describes a 1920×1080, 60 Hz display. It identifies the accessory as `Stinger iX`, but its model identifier is still `iX210`. The iX212 therefore appears to share a substantial firmware and integration base with the iX210/iX2xx family.

## Why 2 GB of RAM is workable

Two gigabytes is limited for a current tablet but reasonable for a fixed-function automotive system:

- Installed APKs are not all resident in memory simultaneously.
- Android starts services as needed and discards cached processes under memory pressure.
- The firmware is a lean 32-bit build without the normal Google Play Services ecosystem running in the background.
- CarPlay and Android Auto execute most application logic on the connected phone. The head unit receives projection video, touch, audio, and control data.
- Video decoding and display composition use dedicated Telechips VPU/GPU hardware rather than consuming only CPU and general RAM.
- The AK7739 and other dedicated audio hardware handle significant DSP work.
- Radio, camera, Bluetooth, SiriusXM, media, setup, and factory applications are modular and need not be active together.

This makes 2 GB adequate for the intended application set and a lightweight replacement launcher. It is less suitable for treating the radio as a general Android tablet with many modern applications, WebViews, games, and background services.

## Application architecture

The system contains 82 APKs, including the Android framework and standard platform components.

### Main Stinger/Skypine launcher

**Package:** `com.skypine.mainapp`  
**APK:** `/system/priv-app/mainapp/mainapp.apk`  
**HOME activity:** `com.skypine.mainapp.ui.MainActivity`  
**Service:** `com.skypine.mainapp.service.MainService`

`MainActivity` declares the Android `HOME`, `LAUNCHER`, and `DEFAULT` categories. This is the primary bulky interface the user sees. `MainService` is exported and uses the `android.intent.action.MAIN_SERVICE` action, which suggests the package also coordinates application state or hardware-related UI functions. The package should remain installed and enabled even if another HOME application is selected.

### PAC vehicle integration

**Package:** `com.aampglobal.radio`  
**APK:** `/system/app/PAC/IX2xx-PAC-Integration_V15.apk`  
**Version:** 15  
**Shared UID:** `android.uid.system`  
**Signature:** AOSP platform test certificate

The PAC app contains functionality for:

- Vehicle information
- HVAC displays and widgets
- Steering-wheel controls
- Camera triggers, presets, and overlays
- Audio integration
- Gauges and off-road widgets
- Vehicle settings
- PAC advanced settings

Important activities include:

- `com.aampglobal.radio.MainActivity`
- `com.aampglobal.radio.SecretMenuActivity`
- `com.aampglobal.radio.PACAdvancedActivity`
- `com.aampglobal.radio.VehicleSettingsActivity`
- `com.aampglobal.radio.VehicleInfoActivity`
- `com.aampglobal.radio.HVACActivity`
- `com.aampglobal.radio.camera.CameraActivity`
- `com.aampglobal.radio.SWCActivity`

Several of these activities, including the secret menu, are exported. They can potentially be opened directly once ADB access is established. Changes inside factory or secret menus may affect vehicle integration and should be approached cautiously.

### Existing split/dashboard system

**Package:** `com.skypine.split`  
**Activity:** `com.skypine.split.SplitActivity`  
**Service:** `com.skypine.split.service.SplitService`  
**Shared UID:** `android.uid.system`

The split application is platform-signed and requests `BIND_APPWIDGET`, vehicle-extension, audio-control, and settings permissions. Its code and resources contain:

- Task-resizing logic, including a `resizeTask` string
- App-widget manager and custom widget interfaces
- HDMI resources and activity identifiers
- CarPlay and Android Auto resources
- Media, radio, phone, camera, setup, DSP, and vehicle widgets
- AIDL widget callback interfaces
- Separate left/right widget layouts

This is strong evidence that the original software already supports a controlled dashboard/split arrangement. A practical customization effort should first launch and inspect this package, then determine whether its layouts can be replaced or whether a new launcher can call its existing services.

### Projection and input applications

- **Android Auto:** `com.google.android.projection.sink`
  - Main activity: `com.google.android.projection.sink.ui.MainActivity`
  - Separate settings and simulation-settings activities
- **Skypine CarPlay UI:** `com.skypine.carplay`
  - Main activity: `com.skypine.carplay.CarPlayActivity`
  - Service: `com.skypine.carplay.CarPlayService`
- **CarPlay framework service:** `com.android.car.carplay`
  - Boot receiver and IAP/AirPlay service interfaces
- **HDMI/camera package:** `com.skypine.camera`
  - HDMI activity: `com.skypine.camera.hdmi.HDMIActivity`
  - Reverse-camera and auxiliary-input activities and services

The HDMI activity is already present. Roku or phone-mirroring input should therefore be possible without adding a new HDMI driver. The remaining question is whether HDMI and a navigation activity can share the display simultaneously through the existing split service. The split package's resources make that plausible.

### Other notable vendor applications

- `com.skypine.bluetoothui` — Bluetooth phone and music UI
- `com.skypine.hdradio` — HD Radio UI and tuner service
- `com.skypine.media` — USB/local media
- `com.skypine.setup` — head-unit settings and widgets
- `com.skypine.sxm` — SiriusXM UI and service
- `com.skypine.factorymode` — factory tests, serial, GPS, screen, USB, and shell activities
- `com.skypine.autotest` — automated hardware/screen testing
- `com.skypine.logtool` — system log utility
- `com.android.car` — Android Automotive CarService
- `com.android.car.carlauncher` — AOSP Car Launcher, installed but not registered as HOME in this build
- `com.android.car.systemupdater` — USB-attached update activity
- `com.telechips.android.systemupdater` — Telechips updater service and settings activity
- `com.speedsoftware.rootexplorer` — Root Explorer
- AudioControl DM Control and DM Smart DSP Unity applications

Full AOSP Settings, Package Installer, DocumentsUI, Shell, SystemUI, and related services are installed. Google Play Store and the normal Google Mobile Services package set are not present.

## ADB, root, developer mode, and sideloading

Static firmware properties and files show:

- `ro.secure=0`
- `ro.debuggable=1`
- `persist.sys.usb.config=adb` in `/system/etc/prop.default`
- `persist.sys.usb.config=host` in the vendor defaults
- `androidboot.selinux=permissive` in the boot command line
- `/system/bin/adbd` installed
- `/system/xbin/su` installed as mode `04750`, owner root, group shell
- `su` SELinux context: `u:object_r:su_exec:s0`
- `/system/bin/busybox`, BusyBox 1.8.1
- `pm`, `am`, `settings`, `sh`, `toybox`, and `toolbox`

The `su` syntax is the simple AOSP-style implementation:

`su [UID[,GID[,GID2]...]] [COMMAND [ARG...]]`

USB init scripts support ADB-only, MTP+ADB, PTP+ADB, accessory+ADB, audio-source+ADB, MIDI+ADB, and RNDIS+ADB configurations. The old USB gadget configuration uses Google USB VID/PID `18d1:D002` for ADB.

The firmware also includes the `adb root`, `adb unroot`, remount, disable-verity, shell, file-sync, and reboot-to-sideload code paths. With `ro.secure=0`, adbd should normally run as root. Whether the live device requires an ADB authorization prompt depends on its runtime `ro.adb.secure` value and persisted data.

There is a conflict between the system default of `adb` and the vendor default of `host`. A persistent value stored in `/data` can override both. The phone/CarPlay port may remain in USB host mode, while another port or internal/debug connector exposes the gadget/ADB interface.

Package sideloading should be possible through at least one of:

- `adb install`
- AOSP Package Installer
- Root Explorer, which requests `REQUEST_INSTALL_PACKAGES`
- Recovery update ZIP installation

No developer-options UI is required if ADB is already active.

## Recovery, fastboot, USB boot, and partition clues

Bootloader strings contain:

- `RECOVERY MODE`
- `USB BOOT`
- `FASTBOOT`
- `fastboot 0`
- Recovery commands that load the recovery partition and recovery DTB
- USB boot commands that load `/boot/boot.img` and `/boot/dt.dtb` from USB
- `--update_package=/usb/update.zip`

The compiled partition-map string includes separate partitions for:

- `secure`
- `uboot-env`
- `misc`
- `dtb`
- `dtb_rec`
- `dtbo`
- `boot`
- `recovery`
- `splash`
- `cache`
- `system`
- `vendor`
- auxiliary Telechips/A7 partitions
- secure storage
- `userdata`

The firmware is non-A/B. No `vbmeta` partition appears in the compiled map, and the vendor fstab does not apply `avb` or `verify` flags to system/vendor. Android dm-verity enforcement therefore appears absent or disabled. The bootloader contains optional Telechips secure-boot verification code, so its fuse/runtime secure-boot state remains an open question.

The recovery image itself was not provided directly, so its exact menu, ADB-sideload behavior, and embedded trusted-certificate list have not been inspected.

## Firmware and APK signing

### Original OTA

The original `update.zip` contains an Android OTA whole-file signature in its ZIP comment. The embedded PKCS#7 signer certificate is:

- Subject: Android / Android / Mountain View
- SHA-256: `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`

This is the standard public AOSP **testkey** certificate. The included `META-INF/com/android/otacert` has the same fingerprint. The build fingerprint is also marked `eng/test-keys`.

The signature container and signer certificate were confirmed. This report did not independently reproduce recovery's complete cryptographic verification of every signed byte.

### APKs

Core platform/system applications, including PAC Integration V15 and the Skypine launcher, use the standard AOSP **platform test certificate**:

`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`

PAC and other large custom APKs use APK Signature Scheme v2. Because these are public AOSP test keys rather than private production keys, it may be possible later to create platform-signed companion applications that can use signature-level interfaces. A normal replacement launcher should not require this privilege and is safer as the first step.

## Safest customization strategy

### Phase 1: establish read-only live access

1. Park the vehicle and boot the radio in ACC mode.
2. Use a known-good USB data cable.
3. Try each available USB port one at a time.
4. Watch Windows Device Manager for an Android, ADB, Google, or unknown USB device.
5. Record hardware IDs, especially anything beginning with `VID_18D1`.
6. With Google Platform Tools installed, run only:

   ```powershell
   adb devices -l
   adb shell id
   adb shell getprop
   ```

Do not flash, unlock, erase, enter fastboot commands, or place `update.zip` on removable media during discovery.

### Phase 2: test installed alternative interfaces

After ADB works, test activities explicitly without changing HOME:

```text
com.android.car.carlauncher/.HomeActivity
com.android.settings/.Settings
com.skypine.split/.SplitActivity
com.skypine.camera/.hdmi.HDMIActivity
com.aampglobal.radio/.SecretMenuActivity
```

The PAC secret menu should initially be observed only; changing factory values may affect CAN, camera, amplifier, HVAC, or steering-wheel-control behavior.

### Phase 3: install a lightweight launcher

A replacement HOME application should:

- Be visually simple and optimized for 1920×1080 touch use
- Use large controls and minimal animation
- Avoid live wallpaper, embedded browsers, and unnecessary background services
- Launch the existing Skypine, PAC, HDMI, CarPlay, Android Auto, camera, radio, and setup activities
- Leave `com.skypine.mainapp` and its `MainService` installed and enabled
- Leave PAC and every vehicle-integration package untouched
- Provide a quick way to return to the stock HOME activity

The recommended visual direction is a clean, black-and-orange Mojave-themed launcher with:

- A persistent Home/CarPlay/HDMI/Audio/Vehicle/Apps dock
- Now-playing controls
- Vehicle status from PAC/CarService
- Camera shortcuts
- Full-screen CarPlay one tap away
- A controlled HDMI+navigation layout while parked

### Phase 4: validate vehicle functions

After changing HOME, test:

- Reverse and auxiliary cameras
- Steering-wheel controls
- PAC vehicle information
- HVAC display and controls
- Radio and audio focus
- Amplifier/DSP initialization
- Bluetooth calls and music
- Wired and wireless CarPlay
- Android Auto
- HDMI input
- Ignition sleep, wake, and resume
- Split-screen behavior
- Navigation prompts over media audio

Do not disable the stock launcher until all of these work through multiple ignition cycles.

### Phase 5: backup before system-level changes

Before writing any system partition, capture:

- `getprop`
- Complete package list and APK paths
- Current HOME/preferred-activity state
- CarService and activity-manager dumps
- Boot, recovery, DTB, recovery DTB, misc, bootloader, SNOR, system, and vendor partitions
- Partition table and block-device names
- SHA-256 checksums of all backups

## HDMI and split-screen feasibility

HDMI hardware and the `HDMIActivity` already exist. The platform-signed split application includes HDMI, CarPlay, Android Auto, media, radio, camera, and widget components. The most realistic layouts are therefore:

- Roku/HDMI video on one side with head-unit navigation on the other while parked
- HDMI phone mirroring with lightweight head-unit controls beside it
- CarPlay navigation with a small native vehicle/media sidebar
- A split dashboard using the existing Skypine widget interfaces

Phone mirroring and an independent map from the same phone are harder because a single HDMI output normally mirrors one composed phone display. A separate navigation source must run through CarPlay, Android Auto, or a head-unit navigation application.

Video playback while driving raises safety and legal concerns. The preferred behavior is to pause or hide HDMI video and automatically expand navigation once vehicle speed is nonzero. Navigation prompts should duck HDMI/media audio rather than compete for focus.

## Risks and cautions

- The Android security patch is from 2018.
- Root ADB and setuid `su` make the device easy to modify but also easy to compromise.
- Do not expose ADB over an untrusted network.
- Do not disable PAC, CarService, camera, audio, Bluetooth, or Skypine services merely because another HOME is selected.
- Do not begin with a custom ROM or modified full OTA.
- The stock OTA writes bootloader and SNOR firmware; a packaging error could permanently disable boot.
- Fastboot presence does not prove the bootloader accepts every flash command or that recovery is immediately available.
- Runtime secure-boot, ADB-authentication, USB-role, and persisted-property states must be checked on the physical radio.
- Factory and secret menus can change vehicle-specific configuration.
- Keep a recovery route and raw partition backups before system-level writes.

## Open questions for live-device testing

1. Which physical USB connector exposes the Android gadget/ADB interface?
2. Does the live radio enumerate as `18d1:D002` or another configfs identity?
3. Is ADB already root, and is host authorization required?
4. What are the runtime values of `persist.sys.usb.config`, `persist.sys.usb2.config`, and `ro.adb.secure`?
5. Can `com.skypine.split.SplitActivity` be launched directly?
6. Which applications does the stock split service allow in each region?
7. Is `HDMIActivity` resizable by the existing task manager?
8. Can CarPlay remain stable when its task bounds change?
9. Is there a second/debug USB or UART connector behind the dash?
10. How is recovery/fastboot selected physically?
11. Does recovery trust the standard AOSP testkey found in the OTA?
12. Is Telechips secure boot active or merely compiled into the bootloader?

## SHA-256 inventory

```text
38819796c7399c25763003ff57f8a87ef68e22d5ec62699a502f0c31ac7ef5e1  horizons\b_update.hex
9fdaddcfbcb915abff8cc34afdd921d2850e4396acbe3c830d5631ac87ceb29c  horizons\l_update.hex
dd7cde9d6c8e58ab8aa1ff3162cf730941edea7bf9042cafa4f1d47526410bd2  horizons\update\boot.img
d60bdeaa9fb56173b6b846aaaea045d88f92884e37b18de677ed1b46cca965a4  horizons\update\bootloader.img
8ec3e230439451779a411f02904af3c80a33afaeb9243c49d802b22576c5a27f  horizons\update\compatibility.zip
4d4222715b3c61fee03df1e7200076e5dc136c02cc10dcca40b52703f893396a  horizons\update\dtb.img
1e4ccb11f5844a6afe15dcacc0ffca3202f82ecb9d0ebf8fa8a60db193df2bc9  horizons\update\META-INF\com\android\metadata
a4384ba815b9499a5ce349b4e33c1755278873fe2eac150a068823f526e6dbde  horizons\update\META-INF\com\android\otacert
f050dcf82447a15898b61f707058ffe6164832bc0d19888e8098f073fd76a069  horizons\update\META-INF\com\google\android\update-binary
61d73dbf7195fb87d52b0ccbad113840e8a7513a3fe261dec88abaa03d8665db  horizons\update\META-INF\com\google\android\updater-script
026dc503db49391d167849d6e5b561191def89b2e3fe5943a1974175be2eea5c  horizons\update\snor.img
047b117ab721109caed8acf9f2bb6217a10447e1b5de8b797b9538274ba2a6b7  horizons\update\splash.img
6435f7c311258dd6b178c4145f4e8df47ed8ea70fbac8802d3b610337d557eae  horizons\update\system.new.dat.br
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  horizons\update\system.patch.dat
ac4ce3c900db4a5883692cbc6b5fc4d40e31bb9e6b0f81c2eced1b1a7db0e727  horizons\update\system.transfer.list
ccb64477769a3db7db5de2fa6b3e31e2f2ec97d77ed6d7894dfd749a47e90d15  horizons\update\vendor.new.dat.br
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  horizons\update\vendor.patch.dat
56376f45d78a668dc63c4a77959f3ddbd56a60a4aff9c1988fb7b4df818bc5e1  horizons\update\vendor.transfer.list
f233c38bfde7d67197d492b8784006534a24adf71f5c6459d0b989903ef07ddd  update.zip
```

## Bottom line

The HORIZON12 already contains nearly everything needed for a safe UI redesign: root-capable ADB, standard Android package management, an alternate AOSP launcher, HDMI support, modular vehicle applications, PAC integration, and a manufacturer split/dashboard service. The most responsible first modification is a small replacement HOME application that reuses those existing components. Full firmware replacement is unnecessary for the desired result.
