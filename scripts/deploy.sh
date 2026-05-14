#!/bin/bash
# TrailOfOrbis Deployment Script
#
# Deploys exactly what players download: a single JAR dropped into mods/.
# The JAR has IncludesAssetPack:true — Hytale reads Server/ and Common/
# assets directly from inside the JAR. No separate asset pack folder needed.
#
# Additionally creates a companion asset pack directory for the Asset Editor.
# The JAR's assets are immutable and invisible to the editor — a separate
# directory pack with a distinct identity makes them editable.

set -e

# Fix for Git Bash on Windows: grep -P needs UTF-8 locale
export LC_ALL=en_US.UTF-8

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Resolve plugin dir from script location (works in Git Bash on Windows)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# Server directory — override with HYTALE_SERVER_DIR environment variable
SERVER_DIR="${HYTALE_SERVER_DIR:-}"
if [ -z "$SERVER_DIR" ]; then
    echo -e "${RED}Error: HYTALE_SERVER_DIR environment variable is not set.${NC}"
    echo "Set it to your Hytale server directory, e.g.:"
    echo "  export HYTALE_SERVER_DIR=\"/c/path/to/your/HytaleServer\""
    exit 1
fi
MODS_DIR="$SERVER_DIR/mods"

echo -e "${YELLOW}=== TrailOfOrbis Deployment (Player-Style) ===${NC}"
echo ""

# Step 0: Auto-increment dev build version
# Each deploy gets a unique version (e.g., 1.0.8-dev1, 1.0.8-dev2) so the
# config sync system detects a version change and overwrites configs from JAR.
# Public releases use clean versions (1.0.8) without the -dev suffix.
MANIFEST="$PLUGIN_DIR/src/main/resources/manifest.json"
DEV_BUILD_FILE="$PLUGIN_DIR/.dev-build"
BASE_VERSION=$(sed -n 's/.*"Version" *: *"\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)

# Read and increment dev build counter
if [ -f "$DEV_BUILD_FILE" ]; then
    DEV_BUILD=$(cat "$DEV_BUILD_FILE")
    DEV_BUILD=$((DEV_BUILD + 1))
else
    DEV_BUILD=1
fi
echo "$DEV_BUILD" > "$DEV_BUILD_FILE"

DEV_VERSION="${BASE_VERSION}-dev${DEV_BUILD}"
echo -e "${YELLOW}[0/4] Dev version: ${DEV_VERSION}${NC}"

# Patch manifest.json with dev version (reverted after build)
sed -i "s/\"Version\": \"${BASE_VERSION}\"/\"Version\": \"${DEV_VERSION}\"/" "$MANIFEST"

# Step 1: Build the JAR
echo -e "${YELLOW}[1/4] Building plugin JAR...${NC}"
cd "$PLUGIN_DIR"
./gradlew clean build --quiet
BUILD_RESULT=$?

# Always revert manifest.json to base version (even if build failed)
sed -i "s/\"Version\": \"${DEV_VERSION}\"/\"Version\": \"${BASE_VERSION}\"/" "$MANIFEST"

if [ $BUILD_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ JAR built successfully (${DEV_VERSION})${NC}"
else
    echo -e "${RED}✗ JAR build failed${NC}"
    exit 1
fi

# Step 2: Deploy JAR (player-style: single JAR in mods/)
echo -e "${YELLOW}[2/4] Deploying JAR to server...${NC}"
# Find the built shadow JAR dynamically (supports any version)
SHADOW_JAR=$(find "$PLUGIN_DIR/build/libs" -name "TrailOfOrbis-*.jar" -type f | head -1)
if [ -z "$SHADOW_JAR" ]; then
    echo -e "${RED}Error: No shadow JAR found in build/libs/${NC}"
    exit 1
fi

# Remove ALL old TrailOfOrbis JARs first (prevents duplicate mod loading)
OLD_JARS=$(find "$MODS_DIR" -maxdepth 1 -name "TrailOfOrbis-*.jar" -type f 2>/dev/null)
if [ -n "$OLD_JARS" ]; then
    echo "$OLD_JARS" | while read -r old; do
        rm -f "$old" 2>/dev/null && echo -e "  Removed old: $(basename "$old")" || \
            echo -e "  ${YELLOW}⚠ Could not remove $(basename "$old") — server may be running${NC}"
    done
fi

# Remove legacy asset pack folder if present (no longer needed — assets are in the JAR)
if [ -d "$MODS_DIR/TrailOfOrbis_Realms" ]; then
    rm -rf "$MODS_DIR/TrailOfOrbis_Realms" 2>/dev/null && \
        echo -e "  Removed legacy TrailOfOrbis_Realms/ folder (assets now bundled in JAR)" || \
        echo -e "  ${YELLOW}⚠ Could not remove TrailOfOrbis_Realms/ — server may be running${NC}"
fi

cp "$SHADOW_JAR" "$MODS_DIR/"
echo -e "${GREEN}✓ JAR deployed (assets bundled inside via IncludesAssetPack)${NC}"

# Step 3: Create companion asset pack for Asset Editor (dev-only)
# The JAR's embedded asset pack is immutable and the Asset Editor can't discover
# JAR packs during its directory scan (AssetModule skips .jar files).
# This creates a SEPARATE directory pack with a DIFFERENT identity so it
# coexists alongside the JAR pack (not replacing it). The JAR pack still
# handles runtime assets — this directory just makes them visible in the editor.
# CRITICAL: Must NOT use the same Group:Name as the JAR, otherwise the JAR's
# registerPack() is skipped and runtime assets (HUDs, UI) break.
echo -e "${YELLOW}[3/4] Setting up Asset Editor pack...${NC}"
ASSET_EDITOR_DIR="$MODS_DIR/TrailOfOrbis_AssetPack"
rm -rf "$ASSET_EDITOR_DIR" 2>/dev/null
mkdir -p "$ASSET_EDITOR_DIR"

# Use the processed asset-pack manifest (no Main class, different Name,
# has @HYTALE_VERSION@ resolved by Gradle processResources)
BUILD_ASSETS="$PLUGIN_DIR/build/resources/main/hytale-assets"
cp "$BUILD_ASSETS/manifest.json" "$ASSET_EDITOR_DIR/manifest.json"

# Copy asset directories from source (writable for editor)
if [ -d "$BUILD_ASSETS/Server" ]; then
    cp -r "$BUILD_ASSETS/Server" "$ASSET_EDITOR_DIR/"
fi
if [ -d "$BUILD_ASSETS/Common" ]; then
    cp -r "$BUILD_ASSETS/Common" "$ASSET_EDITOR_DIR/"
fi

echo -e "${GREEN}✓ Asset Editor pack created (trailoforbis:TrailOfOrbis_Realms at TrailOfOrbis_AssetPack/)${NC}"

# Step 4: Clean up stale files from old deployments
echo -e "${YELLOW}[4/4] Cleaning up stale files...${NC}"
# Clean up stale files from old deployments in the plugin data directory.
# Players never have these — they just drop the JAR. Our old deploy script
# created a manifest.json with IncludesAssetPack=true and copied Server/Common
# dirs, which caused Hytale to see a phantom second mod and show a red
# "older server version" warning on every world change.
PLUGIN_DATA_DIR="$MODS_DIR/trailoforbis_TrailOfOrbis"
for stale in "$PLUGIN_DATA_DIR/manifest.json" "$PLUGIN_DATA_DIR/Server" "$PLUGIN_DATA_DIR/Common"; do
    if [ -e "$stale" ]; then
        rm -rf "$stale" 2>/dev/null && \
            echo -e "  Removed stale $(basename "$stale") from plugin data dir" || \
            echo -e "  ${YELLOW}⚠ Could not remove $(basename "$stale") — server may be running${NC}"
    fi
done
echo -e "${GREEN}✓ Cleanup complete${NC}"

# Extract info for reporting
HYUI_FILE_ID=$(grep 'val hyuiFileId' "$PLUGIN_DIR/build.gradle.kts" | sed -n 's/.*"\([0-9]*\)".*/\1/p')
HYUI_VERSION=$(grep -B1 "val hyuiFileId" "$PLUGIN_DIR/build.gradle.kts" | head -1 | sed -n 's/.*v\([0-9.]*\).*/\1/p')
HYUI_VERSION="${HYUI_VERSION:-unknown}"
HYTALE_VERSION=$(sed -n 's/.*"ServerVersion" *: *"\([^"]*\)".*/\1/p' "$PLUGIN_DIR/build/resources/main/manifest.json" 2>/dev/null | head -1)
HYTALE_VERSION="${HYTALE_VERSION:-unknown}"

echo ""
echo -e "${GREEN}=== Deployment Complete (Player-Style) ===${NC}"
echo ""
JAR_NAME=$(basename "$SHADOW_JAR")
JAR_SIZE=$(du -h "$SHADOW_JAR" | cut -f1)
echo "  JAR: $JAR_NAME ($JAR_SIZE)"
echo "  Version: $DEV_VERSION (base: $BASE_VERSION, dev build: $DEV_BUILD)"
echo "  Hytale: $HYTALE_VERSION | HyUI: $HYUI_VERSION"
echo ""
echo "  This is exactly what players install: one JAR in mods/"
echo ""
echo -e "${YELLOW}Restart the Hytale server to apply changes.${NC}"
