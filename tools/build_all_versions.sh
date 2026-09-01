#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

build_target() {
    local minecraft_version="$1"
    local build_suffix="$2"
    ./gradlew --no-daemon clean test build prepareRelease \
        -Pminecraft_version="$minecraft_version" \
        -Ptarget_build_dir="build/$build_suffix"
}

release_channel="$({ sed -n 's/^release_channel=//p' gradle.properties | head -n 1; } \
    | tr -d '[:space:]' \
    | tr '[:upper:]' '[:lower:]')"

build_target "26.1.2" "26.1.2"
if [[ "$release_channel" != "alpha" ]]; then
    build_target "26.2" "26.2"
    echo "Built Minecraft 26.1.2 and 26.2 in release/."
else
    echo "Built Alpha for Minecraft 26.1.2 only in release/."
fi
