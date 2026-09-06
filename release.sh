#!/usr/bin/env bash
# Cuts a release: bumps the app version, commits, pushes, then pushes a
# "vX.Y.Z" tag. Pushing that tag triggers .github/workflows/release.yml,
# which builds the release APK and publishes a GitHub release for it.
#
# Usage: ./release.sh 1.2.0
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <version>   e.g. $0 1.2.0" >&2
  exit 1
fi

VERSION="$1"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must look like X.Y.Z (got: $VERSION)" >&2
  exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
GRADLE_FILE="$REPO_ROOT/app/build.gradle.kts"
TAG="v$VERSION"

cd "$REPO_ROOT"

if [ -n "$(git status --porcelain)" ]; then
  echo "Working tree is not clean. Commit or stash changes first." >&2
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists." >&2
  exit 1
fi

CURRENT_CODE=$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
if [ -z "$CURRENT_CODE" ]; then
  echo "Could not find versionCode in $GRADLE_FILE" >&2
  exit 1
fi
NEW_CODE=$((CURRENT_CODE + 1))

sed -i.bak -E "s/versionCode = [0-9]+/versionCode = $NEW_CODE/" "$GRADLE_FILE"
sed -i.bak -E "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"
rm -f "${GRADLE_FILE}.bak"

echo "Bumped versionName -> $VERSION, versionCode -> $NEW_CODE"

echo "Running unit tests..."
./gradlew testDebugUnitTest

git add "$GRADLE_FILE"
git commit -m "Release $VERSION"
git push

git tag -a "$TAG" -m "Release $VERSION"
git push origin "$TAG"

echo "Pushed tag $TAG. GitHub Actions will build the release APK and publish it."
