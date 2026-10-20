#!/usr/bin/env bash
# Publishes the built release APK as a GitHub release:
#   android/scripts/publish_release.sh [version]
# The version defaults to versionName in app/build.gradle.kts. Expects:
#   - dist/orca-android-<version>.apk from scripts/build_release.sh,
#   - the tag v<version> on the checked-out commit (CHANGELOG has a "## <version>" section),
#   - the GitHub CLI, logged in once with `gh auth login`.
# The tag is pushed to origin (Gitea), which mirrors to GitHub; the release is created once
# GitHub has the tag, with the CHANGELOG section as notes. An existing release gets the files
# replaced.
set -euo pipefail

here=$(cd "$(dirname "$0")/.." && pwd)
repo_root=$(cd "$here/.." && pwd)
github_repo=${ORCA_GITHUB_REPO:-cl1x/Orca-Android}
version=${1:-$(grep -oP 'versionName = "\K[^"]+' "$here/app/build.gradle.kts")}
tag="v$version"
apk="$repo_root/dist/orca-android-$version.apk"

die() { echo "error: $*" >&2; exit 1; }

command -v gh >/dev/null || die "GitHub CLI missing – install it (pacman -S github-cli) and run 'gh auth login'"
gh auth status >/dev/null 2>&1 || die "not logged in to GitHub – run 'gh auth login'"
[ -f "$apk" ] && [ -f "$apk.sha256" ] || die "$apk not found – run scripts/build_release.sh first"

cd "$repo_root"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || die "tag $tag missing – commit the version and run: git tag -a $tag -m 'Orca-Android $version'"
[ "$(git rev-parse HEAD)" = "$(git rev-parse "$tag^{commit}")" ] || die "HEAD is not at $tag – the APK must be built from the tagged commit"
git diff --quiet HEAD || die "uncommitted changes – the APK would not match $tag"
[ "$apk" -nt "$(git rev-parse --show-toplevel)/.git/refs/tags/$tag" ] || [ ! -e ".git/refs/tags/$tag" ] \
    || echo "warning: the APK is older than the tag $tag – was it built from the tagged commit?" >&2

notes=$(awk -v v="$version" '$0 == "## " v || index($0, "## " v " ") == 1 {on = 1; next} on && /^## / {exit} on' CHANGELOG.md)
[ -n "${notes//[[:space:]]/}" ] || die "CHANGELOG.md has no section '## $version'"
sha=$(cut -d' ' -f1 "$apk.sha256")
notes_file=$(mktemp)
trap 'rm -f "$notes_file"' EXIT
printf '%s\n\n**Install:** download `%s` and open it (Android 10+, arm64); it updates earlier versions in place.\nSHA-256: `%s`\n' \
    "$(echo "$notes" | sed '/./,$!d')" "$(basename "$apk")" "$sha" > "$notes_file"

echo "Pushing main and $tag to origin …"
git push -q origin main "$tag"

# Gitea pushes to GitHub on its own; wait until GitHub has the tag, so the release does not
# create it on an older commit.
echo -n "Waiting for $tag on GitHub "
for _ in $(seq 1 60); do
    if gh api "repos/$github_repo/git/ref/tags/$tag" >/dev/null 2>&1; then echo " ok"; break; fi
    echo -n "."; sleep 10
done
gh api "repos/$github_repo/git/ref/tags/$tag" >/dev/null 2>&1 || die "$tag did not reach GitHub within 10 minutes – check the Gitea push mirror"

if gh release view "$tag" --repo "$github_repo" >/dev/null 2>&1; then
    echo "Release $tag exists – replacing its files and notes"
    gh release upload "$tag" "$apk" "$apk.sha256" --repo "$github_repo" --clobber
    gh release edit "$tag" --repo "$github_repo" --notes-file "$notes_file"
else
    gh release create "$tag" "$apk" "$apk.sha256" --repo "$github_repo" --verify-tag \
        --title "Orca-Android $version" --notes-file "$notes_file"
fi
echo "Published: https://github.com/$github_repo/releases/tag/$tag"
