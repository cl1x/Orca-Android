#!/usr/bin/env bash
# Builds a signed release APK into dist/ at the repository root:
#   android/scripts/build_release.sh [--publish]
# --publish then creates the GitHub release with scripts/publish_release.sh.
# Needs android/key.properties (storeFile, storePassword, keyAlias, keyPassword) for signing;
# without it the APK is signed with the debug key and must not be published.
set -euo pipefail

publish=0
[ "${1:-}" = "--publish" ] && publish=1

here=$(cd "$(dirname "$0")/.." && pwd)
root=${ORCA_BUILD_ROOT:-$HOME/build/orca-android}
dist="$here/../dist"

if [ ! -f "$here/key.properties" ]; then
    echo "warning: android/key.properties missing – the APK is signed with the debug key" >&2
fi

"$here/scripts/build.sh" release

version=$(grep -oP 'versionName = "\K[^"]+' "$here/app/build.gradle.kts")
mkdir -p "$dist"
cp "$root"/gradle/app/outputs/apk/release/app-release.apk "$dist/orca-android-$version.apk"
(cd "$dist" && sha256sum "orca-android-$version.apk" > "orca-android-$version.apk.sha256")
echo "Release: $dist/orca-android-$version.apk"

[ "$publish" = 1 ] && "$here/scripts/publish_release.sh" "$version"
exit 0
