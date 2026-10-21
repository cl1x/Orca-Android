#!/usr/bin/env bash
# Builds a signed release APK into dist/ at the repository root:
#   android/scripts/build_release.sh [--publish] [--device-tests]
# The unit tests run first; a failing test stops the build.
# --device-tests also runs the engine tests (EngineTest) on the connected emulator/device first.
# --publish then creates the GitHub release with scripts/publish_release.sh.
# Needs android/key.properties (storeFile, storePassword, keyAlias, keyPassword) for signing;
# without it the APK is signed with the debug key and must not be published.
set -euo pipefail

publish=0
device_tests=0
for arg in "$@"; do
    case $arg in
        --publish) publish=1 ;;
        --device-tests) device_tests=1 ;;
        *) echo "unknown option $arg" >&2; exit 2 ;;
    esac
done

here=$(cd "$(dirname "$0")/.." && pwd)
root=${ORCA_BUILD_ROOT:-$HOME/build/orca-android}
dist="$here/../dist"

if [ ! -f "$here/key.properties" ]; then
    echo "warning: android/key.properties missing – the APK is signed with the debug key" >&2
fi

echo "Running unit tests …"
(cd "$here" && ./gradlew --console=plain -q testDebugUnitTest)
if [ "$device_tests" = 1 ]; then
    echo "Running engine tests on the device …"
    (cd "$here" && ./gradlew --console=plain -q connectedDebugAndroidTest)
fi

"$here/scripts/build.sh" release

version=$(grep -oP 'versionName = "\K[^"]+' "$here/app/build.gradle.kts")
mkdir -p "$dist"
cp "$root"/gradle/app/outputs/apk/release/app-release.apk "$dist/orca-android-$version.apk"
(cd "$dist" && sha256sum "orca-android-$version.apk" > "orca-android-$version.apk.sha256")
echo "Release: $dist/orca-android-$version.apk"

[ "$publish" = 1 ] && "$here/scripts/publish_release.sh" "$version"
exit 0
