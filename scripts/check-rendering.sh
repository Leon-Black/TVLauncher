#!/bin/sh
set -eu
project_dir=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
java_bin="${JAVA_HOME:-$project_dir/.tools/jdk/Contents/Home}/bin"
output="$project_dir/.tools/rendering-checks"
mkdir -p "$output"
"$java_bin/javac" --release 17 -d "$output" \
    "$project_dir/app/src/main/java/me/leon/tvlauncher/ScrollBounds.java" \
    "$project_dir/checks/me/leon/tvlauncher/RenderingChecks.java"
"$java_bin/java" -cp "$output" me.leon.tvlauncher.RenderingChecks
