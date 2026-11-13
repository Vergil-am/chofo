#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST_DIR="$ROOT_DIR/composeApp/app-resources/windows"

mkdir -p "$DEST_DIR"

if [ -f "$DEST_DIR/mpv-2.dll" ] && [ -f "$DEST_DIR/libmpv-2.dll" ]; then
    echo "Windows libmpv already present in $DEST_DIR"
    exit 0
fi

echo "Fetching latest mpv-dev-x86_64 download URL..."
MPV_URL=$(curl -sL https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest \
    | grep "browser_download_url.*mpv-dev-x86_64-[0-9]" \
    | head -n 1 \
    | cut -d '"' -f 4)

if [ -z "$MPV_URL" ]; then
    MPV_URL="https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261111/mpv-dev-x86_64-20261111-git-bcde6b93cc.7z"
fi

echo "Downloading $MPV_URL..."
TMP_ARCHIVE="/tmp/mpv-dev-$$.7z"
curl -sL "$MPV_URL" -o "$TMP_ARCHIVE"

echo "Extracting libmpv-2.dll to $DEST_DIR..."
7z e "$TMP_ARCHIVE" -o"$DEST_DIR" "libmpv-2.dll" -r -y
cp "$DEST_DIR/libmpv-2.dll" "$DEST_DIR/mpv-2.dll"
rm -f "$TMP_ARCHIVE"

echo "Done! Windows MPV libraries:"
ls -lh "$DEST_DIR"
