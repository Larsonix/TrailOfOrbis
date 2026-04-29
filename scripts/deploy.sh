#!/bin/bash
# TrailOfOrbis Deployment Script
#
# Deploys exactly what players download: a single JAR dropped into mods/.
# The JAR has IncludesAssetPack:true — Hytale reads Server/ and Common/
# assets directly from inside the JAR. No separate asset pack folder needed.
#
# Config files are synced separately because they live outside the JAR
# (in the plugin data dir) and may have user customizations.

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

# Step 1: Build the JAR
echo -e "${YELLOW}[1/3] Building plugin JAR...${NC}"
cd "$PLUGIN_DIR"
./gradlew shadowJar --quiet
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ JAR built successfully${NC}"
else
    echo -e "${RED}✗ JAR build failed${NC}"
    exit 1
fi

# Step 2: Deploy JAR (player-style: single JAR in mods/)
echo -e "${YELLOW}[2/3] Deploying JAR to server...${NC}"
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

# Step 3: Sync config files
echo -e "${YELLOW}[3/3] Syncing config files...${NC}"
# Use rsync-like approach: replicate full config directory structure
find "$PLUGIN_DIR/src/main/resources/config" -name "*.yml" | while read -r yml; do
    relpath="${yml#$PLUGIN_DIR/src/main/resources/config/}"
    dest="$MODS_DIR/trailoforbis_TrailOfOrbis/config/$relpath"
    mkdir -p "$(dirname "$dest")"
    cp "$yml" "$dest"
done
# Ensure plugin data directory has manifest.json so Hytale's asset scanner
# doesn't warn about "missing or invalid manifest.json". This directory is
# auto-created by Hytale for plugin config + database storage.
PLUGIN_DATA_DIR="$MODS_DIR/trailoforbis_TrailOfOrbis"
if [ ! -f "$PLUGIN_DATA_DIR/manifest.json" ]; then
    cat > "$PLUGIN_DATA_DIR/manifest.json" << 'MANIFEST'
{
  "Group": "trailoforbis",
  "Name": "TrailOfOrbis_Data",
  "Version": "1.0.2",
  "Description": "Trail of Orbis plugin configuration and data",
  "ServerVersion": "2026.03.26-89796e57b"
}
MANIFEST
    echo -e "  ${GREEN}✓ Created manifest.json for plugin data directory${NC}"
fi
CONFIG_COUNT=$(find "$PLUGIN_DIR/src/main/resources/config" -name "*.yml" | wc -l | tr -d ' ')
echo -e "${GREEN}✓ Config files synced ($CONFIG_COUNT YAML files)${NC}"

# Extract info for reporting
HYUI_FILE_ID=$(grep 'val hyuiFileId' "$PLUGIN_DIR/build.gradle.kts" | grep -oP '"\K[0-9]+')
HYUI_VERSION=$(grep -B1 "val hyuiFileId" "$PLUGIN_DIR/build.gradle.kts" | head -1 | grep -oP 'v\K[0-9.]+' || echo "unknown")
HYTALE_VERSION=$(grep -oP '"ServerVersion"\s*:\s*"\K[^"]+' "$PLUGIN_DIR/build/resources/main/manifest.json" 2>/dev/null || echo "unknown")

echo ""
echo -e "${GREEN}=== Deployment Complete (Player-Style) ===${NC}"
echo ""
JAR_NAME=$(basename "$SHADOW_JAR")
JAR_SIZE=$(du -h "$SHADOW_JAR" | cut -f1)
echo "  JAR: $JAR_NAME ($JAR_SIZE)"
echo "  Hytale: $HYTALE_VERSION | HyUI: $HYUI_VERSION"
echo "  Configs: $CONFIG_COUNT YAML files"
echo ""
echo "  This is exactly what players install: one JAR in mods/"
echo ""
echo -e "${YELLOW}Restart the Hytale server to apply changes.${NC}"
