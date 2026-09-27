#!/usr/bin/env bash
# Compile the Java bridge, dex it, swap into the apk shell, sign with testkey.
set -e
cd /home/user/Projects/LaptopProjects/Horizons/HorizonsStinger
VER="${1:-dev}"; T=tools/apktool.jar; K=tools/keys
rm -rf build/bridge-classes build/bridge-dex; mkdir -p build/bridge-classes build/bridge-dex
java -jar tools/ecj.jar -proc:none -bootclasspath tools/android-28.jar -source 1.8 -target 1.8 \
  -d build/bridge-classes $(find build/bridge-src -name '*.java')
java -cp tools/r8.jar com.android.tools.r8.D8 --release --min-api 28 --lib tools/android-28.jar \
  --output build/bridge-dex $(find build/bridge-classes -name '*.class')
java -jar "$T" b build/v03-work -o build/Horizon-$VER-base.apk >/dev/null 2>&1
( cd build/bridge-dex && zip -q "../Horizon-$VER-base.apk" classes.dex )
rm -f "build/Horizon-webview-$VER.apk"
java -cp "/usr/share/java/signapk/*" com.android.signapk.SignApk "$K/testkey.x509.pem" "$K/testkey.pk8" \
  "build/Horizon-$VER-base.apk" "build/Horizon-webview-$VER.apk" 2>/dev/null
echo "built build/Horizon-webview-$VER.apk ($(stat -c%s build/Horizon-webview-$VER.apk) bytes)"
