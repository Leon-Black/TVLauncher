#!/bin/sh
# Requires an installed JDK and SDK. Does not download Java or accept SDK licenses.
set -eu
project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_dir"

if [ -z "${JAVA_HOME:-}" ] && [ -x "$project_dir/.tools/jdk/Contents/Home/bin/java" ]; then
    export JAVA_HOME="$project_dir/.tools/jdk/Contents/Home"
fi
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! "$java_cmd" -version >/dev/null 2>&1; then
    echo '缺少 Java。请准备 JDK 17 并设置 JAVA_HOME，然后重新运行。' >&2
    exit 1
fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$project_dir/.tools/android-sdk}}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$project_dir/.tools/gradle-home}"
if [ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ] ||
   [ ! -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
    echo '缺少 Android SDK Platform 35 / Build Tools 35.0.0。' >&2
    echo '请先通过 sdkmanager 安装，并自行阅读、接受相关许可。' >&2
    exit 1
fi
if [ ! -f "$project_dir/.tools/debug.keystore" ]; then
    mkdir -p "$project_dir/.tools"
    keytool_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
    "$keytool_cmd" -genkeypair -keystore "$project_dir/.tools/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=Android Debug,O=Android,C=US'
fi
if [ -x "$project_dir/.tools/gradle-8.13/bin/gradle" ]; then
    gradle_cmd="$project_dir/.tools/gradle-8.13/bin/gradle"
else
    gradle_cmd="$project_dir/gradlew"
fi
if [ "$#" -eq 0 ]; then
    set -- :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
fi
exec "$gradle_cmd" "$@"
