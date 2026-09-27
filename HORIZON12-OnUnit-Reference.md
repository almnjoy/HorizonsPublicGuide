# HORIZON12 - On-Unit Reference (from Stinger's own docs)

Two Stinger documents pulled and documented: the install guide (`IG-SRX-JW18128`) and the official iX212 User Guide (`stingersolutions.com PN: IX212`). This is what the factory software actually exposes, and what it means for our project.

## From the install guide (IG-SRX-JW18128)

- **USB mapping (this answers "which USB"):** the **center console port is CarPlay/Android Auto** (`USBC-UN2` into the radio's "USB C" input). The **other retained USB** (`USB-UN1` into "USB A") is "USB thumb drives and charging only." Both run the head unit as host.
- **HDMI:** runs through the **RPA-HD1** module. The streaming device (Roku, Fire Stick) plugs into the RPA-HD1's HD IN and is meant to live in the **glove box**. On-screen it surfaces as `com.skypine.camera/.hdmi.HDMIActivity`. This is the "HDMI when bored" tile.
- **Interface modules:** CH4A-JW18 (vehicle/CAN/SWC/camera integration = the PAC brain), PL1 (PAC-LINK), RPA-HD1 (HDMI/camera). Their firmware updates use a Windows **RadioPRO PC app** over micro-USB to each module (PAC-audio.com/firmware). Separate from the head-unit firmware.

## From the iX212 User Guide - on-screen menu tree
`HOME > Settings`:
- **System Settings** - Clock, **System Firmware Update**
- **Display Settings** - **Face Button Color**, **Backgrounds**
- **Camera Settings**
- **User Settings** - Quick Access Widget
- **Device Settings** - Add a Device
- **Installer Settings** - **Installer Code** (passcode gated)
- **Audio Settings** (also `HOME > Audio Settings`)

Takeaways that matter for us:
- **Display Settings already lets you change the Background and the Face Button Color.** That is real, stock, no-mod cosmetic customization. It is not a custom homepage, but it is the zero-risk baseline, and worth setting so you see how far stock gets you.
- **User Settings > Quick Access Widget** and the Installer widget setup mean the stock UI is widget-driven, consistent with the `com.skypine.split` / PAC widget system we want to reuse.

## System Firmware Update flow (this is the RED ZONE)
- Format a USB to **FAT32 / MS-DOS(FAT)**, 32GB+.
- Put the firmware file in the **root** of the drive. **Do not unzip it.**
- Insert into any USB port. The radio **auto-detects the firmware file on plug-in or at startup**, or `SETTINGS > SYSTEM > System Firmware Update` while inserted, tap Update Firmware. Takes 6-8 minutes.
- **Updating resets ALL settings to factory defaults.** Do not stop it, do not cut power.

Why this matters:
1. This is the same OTA path that flashes bootloader + SNOR. The `swupdate_...zip` you have IS this file. **Do not drop it on a USB stick as an experiment.** It is the one action with a brick path.
2. It auto-detects a firmware file on any inserted USB, so be deliberate about what FAT32 sticks you plug in during recon (use a clean stick with no stray Stinger firmware zip on it).
3. **A firmware update or Master Reset wipes settings and PAC integration config**, which would also revert our HOME choice. That is fine because we keep the stock launcher installed, but it means: back up first (next point), and re-apply after any official update.

## Installer menu + backup
- **Installer Settings is passcode gated ("Installer Code").** It holds Steering Wheel Control setup, camera triggers, PAC integration, and **Master Reset** (restores factory, wipes installer + user settings + PAC Integrations).
- The Installer menu can **export user settings to the USB drive.** Do this before we touch anything, it is a real settings backup.
- **Do not change PAC/secret values in here.** That is the CAN/camera/amp/SWC config. Observe only.

## What the docs do NOT show
There is **no on-screen Developer Options or ADB toggle** in the menu tree. That confirms our access path: ADB comes from the USB gadget or, if the front ports are host-only, the file-manager sideload / network-ADB routes in `SETUP-QUICKSTART.md`. The stock menus give us Backgrounds + Face Button Color and a settings backup, nothing deeper.

## Net effect on the plan
- **Easy win available today, no mod:** set a custom Background + Face Button Color in Display Settings, and export a settings backup from the Installer menu.
- **Our custom launcher stays app-level** and reversible; the only brick path (System Firmware Update / OTA) is one we simply never use.
- **After any official Stinger firmware update:** expect settings reset. Re-select our HOME and re-import the settings backup. Keep the stock launcher installed so this is always a clean fallback.
