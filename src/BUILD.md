# Building the Horizon APK (no Android Studio)

The whole thing builds in a Linux sandbox from apt packages, no SDK install. This is the recipe that produced `Horizon-v0.1.apk`.

## Assets the app needs
Put these in an `assets/` folder next to the build (they come from `HORIZON12-Claude-Handoff-Final.zip` > `assets/`):
- `Gagalin-Regular.otf`  (the display font)
- `jeep.webp`  (rename `jeep-surfboard-cutout.webp` to `jeep.webp`)

## Toolchain (apt-download into a prefix, no root needed)
```bash
mkdir -p /tmp/andk /tmp/debs && cd /tmp/debs
PKGS="aapt dalvik-exchange apksigner libandroid-23-java ecj libecj-java p7zip p7zip-full"
DEPS=$(for p in $PKGS; do apt-cache depends --recurse --no-recommends --no-suggests --no-conflicts --no-breaks --no-replaces --no-enhances "$p"; done | grep -E "^[a-z]" | sort -u)
apt-get download $PKGS 2>/dev/null
for d in $DEPS; do case "$d" in android-*|libandroid-*|libprotobuf-lite23) apt-get download "$d" 2>/dev/null;; esac; done
for f in *.deb; do dpkg-deb -x "$f" /tmp/andk; done
```
Key paths after that:
- aapt (v1) ELF: `/tmp/andk/usr/bin/aapt`  -- run with `LD_LIBRARY_PATH=/tmp/andk/usr/lib/x86_64-linux-gnu/android` (the `lib7z.so` it needs is a symlink into p7zip, hence p7zip-full)
- dexer: `/tmp/andk/usr/bin/dalvik-exchange` (this is `dx`)
- android.jar (API 23): `/tmp/andk/usr/lib/android-sdk/platforms/android-23/android.jar`
- ecj compiler jar: `/tmp/andk/usr/share/java/ecj.jar`  -- invoke via `java -cp ecj.jar org.eclipse.jdt.internal.compiler.batch.Main` (NOT `-jar`)
- apksigner jar: `/tmp/andk/usr/share/java/apksigner.jar`
- `keytool` is on the system (openjdk).

## Build
```bash
export LD_LIBRARY_PATH=/tmp/andk/usr/lib/x86_64-linux-gnu/android
AAPT=/tmp/andk/usr/bin/aapt
DX=/tmp/andk/usr/bin/dalvik-exchange
ECJ=/tmp/andk/usr/share/java/ecj.jar
APKSIGNER=/tmp/andk/usr/share/java/apksigner.jar
ANDROID=/tmp/andk/usr/lib/android-sdk/platforms/android-23/android.jar

# layout: AndroidManifest.xml, res/values/strings.xml, src/com/example/horizon/MainActivity.java, assets/{Gagalin-Regular.otf,jeep.webp}
$AAPT package -f -m -J gen -S res -M AndroidManifest.xml -I $ANDROID
java -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main -1.8 -nowarn -cp "$ANDROID" -d classes $(find gen src -name "*.java")
$DX --dex --output=classes.dex classes
$AAPT package -f -M AndroidManifest.xml -S res -A assets -I $ANDROID -F app.unaligned.apk
$AAPT add app.unaligned.apk classes.dex
keytool -genkeypair -keystore debug.ks -storepass android -keypass android -alias horizon -dname "CN=ALMNJOY Horizon" -keyalg RSA -keysize 2048 -validity 10000
java -jar $APKSIGNER sign --ks debug.ks --ks-pass pass:android --key-pass pass:android --min-sdk-version 23 --out Horizon.apk app.unaligned.apk
```
`res/values/strings.xml` just needs: `<resources><string name="app_name">Horizon</string></resources>`

## v0.2 note: platform-signing for vehicle data
v0.1 is debug-signed (fine for a launcher). To read CarService/PAC vehicle data (gauges, climate), sign instead with the public AOSP **platform** test key (the stock apps use it; fetch `platform.pk8` + `platform.x509.pem` from AOSP `build/target/product/security/`, googlesource `?format=TEXT` gives base64), and add the car permissions + `<uses-library android:name="android.car"/>`. Compiling car APIs needs a newer `android.jar` than API 23 (car API isn't in a stock android.jar; pull `android.car.jar` off the device once adb is up).

## Install (once adb-over-TCP is up, see README)
```
adb install -r Horizon.apk        # updates in seconds, no re-flash
```
