#!/bin/sh
# Rebuild LWJGL 3.4.1 component for ZalithLauncher 1 (GLFW emulation layer from jre_lwjgl3glfw + stock LWJGL 3.4.1 jars)
# Usage: scripts/build-lwjgl341.sh <dir-with-stock-lwjgl-3.4.1-jars>   (needs JDK 17+ : javac + jar)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STOCK="$(cd "${1:?path to stock LWJGL 3.4.1 jars (lwjgl*.jar)}" && pwd)"
OUT="$ROOT/ZalithLauncher/src/main/assets/components/lwjgl3_341"
WORK="$(mktemp -d)"
CP="$(ls "$STOCK"/*.jar | tr '\n' ':')$ROOT/jre_lwjgl3glfw/libs/jsr305.jar"
mkdir -p "$WORK/classes" "$WORK/merge" "$OUT"
find "$ROOT/jre_lwjgl3glfw/src/main/java" -name '*.java' > "$WORK/srcs.txt"
javac -source 8 -target 8 -Xlint:-options -nowarn -cp "$CP" -d "$WORK/classes" @"$WORK/srcs.txt"
cp -r "$WORK/classes/." "$WORK/merge/"
for m in lwjgl-glfw lwjgl-opengl; do (cd "$WORK/merge" && unzip -nq "$STOCK/$m.jar" -x 'META-INF/*' 'module-info.class'); done
rm -rf "$WORK/merge/net/java/openjdk/cacio/ctc"
printf 'Manifest-Version: 1.0\nAutomatic-Module-Name: org.lwjgl\n' > "$WORK/MANIFEST.MF"
jar cfm "$OUT/lwjgl-3.4.1-merged-modules.jar" "$WORK/MANIFEST.MF" -C "$WORK/merge" .
for m in lwjgl lwjgl-stb lwjgl-tinyfd lwjgl-nanovg lwjgl-freetype lwjgl-spng lwjgl-openal; do cp "$STOCK/$m.jar" "$OUT/"; done
date +%s > "$OUT/version"
rm -rf "$WORK"
echo "OK -> $OUT"
