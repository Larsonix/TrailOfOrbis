#!/bin/bash
# Trail of Orbis Plugin Index Generator
# Regenerates all .index/ files from Java source.
# Replaces the lost ~/tools/index-plugin.sh from the WSL2 era.
#
# Usage: ./scripts/index-plugin.sh
# Called automatically by .git/hooks/pre-commit when .java files are staged.

# Fix for Git Bash on Windows: grep -P needs UTF-8 locale
export LC_ALL=en_US.UTF-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
INDEX_DIR="$PROJECT_DIR/.index"
BASE_PKG="io/github/larsonix/trailoforbis"
BASE_PKG_DOT="io.github.larsonix.trailoforbis"

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
FILE_COUNT=$(find "$SRC_DIR" -name "*.java" | wc -l | tr -d ' ')
HEADER="Generated: $TIMESTAMP ($FILE_COUNT source files)"

mkdir -p "$INDEX_DIR"

echo "Indexing $FILE_COUNT source files..."

# Helper: extract type names from a Java file, excluding comments/Javadoc
extract_types() {
    grep -v '^\s*[*/]' "$1" 2>/dev/null | grep -v '^\s*//' | \
        grep -oP '(?:class|interface|enum|record)\s+\K\w+' 2>/dev/null || true
}

# ─────────────────────────────────────────────
# 1. CLASS_INDEX.txt
#    Maps every type name (top-level + inner) → file path
# ─────────────────────────────────────────────
echo "  [1/8] CLASS_INDEX.txt"
{
    echo "# $HEADER"
    find "$SRC_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$SRC_DIR/}"
        extract_types "$file" | while read -r name; do
            printf '%s\t%s\n' "$name" "$relpath"
        done
    done | sort -t$'\t' -k1,1
} > "$INDEX_DIR/CLASS_INDEX.txt"

# ─────────────────────────────────────────────
# 2. METHOD_INDEX.txt
#    Maps every type name → enclosing file class → file path
# ─────────────────────────────────────────────
echo "  [2/8] METHOD_INDEX.txt"
{
    echo "# $HEADER"
    find "$SRC_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$SRC_DIR/}"
        enclosing=$(basename "$file" .java)
        extract_types "$file" | while read -r name; do
            printf '%s\t%s\t%s\n' "$name" "$enclosing" "$relpath"
        done
    done | sort -t$'\t' -k1,1
} > "$INDEX_DIR/METHOD_INDEX.txt"

# ─────────────────────────────────────────────
# 3. IMPORT_MAP.txt
#    Maps top-level class name → fully qualified import path
# ─────────────────────────────────────────────
echo "  [3/8] IMPORT_MAP.txt"
{
    echo "# $HEADER"
    find "$SRC_DIR" -name "*.java" | sort | while read -r file; do
        pkg=$(grep -oP '^package\s+\K[^;]+' "$file" 2>/dev/null | head -1 || true)
        [ -z "$pkg" ] && continue
        classname=$(basename "$file" .java)
        printf '%s\t%s.%s\n' "$classname" "$pkg" "$classname"
    done | sort -t$'\t' -k1,1
} > "$INDEX_DIR/IMPORT_MAP.txt"

# ─────────────────────────────────────────────
# 4. PACKAGE_MAP.md
#    Class counts per package, sorted by count (descending)
# ─────────────────────────────────────────────
echo "  [4/8] PACKAGE_MAP.md"
{
    echo "# Trail of Orbis Package Map"
    echo "# Auto-generated - do not edit manually"
    echo "# $HEADER"
    echo ""
    find "$SRC_DIR/$BASE_PKG" -name "*.java" | while read -r file; do
        dir=$(dirname "$file")
        reldir="${dir#$SRC_DIR/}"
        echo "$reldir" | tr '/' '.'
    done | sort | uniq -c | sort -rn | while read -r count pkg; do
        printf '| `%s` | %s classes |\n' "$pkg" "$count"
    done
} > "$INDEX_DIR/PACKAGE_MAP.md"

# ─────────────────────────────────────────────
# 5. DEPENDENCY_MAP.md
#    Per-module import dependencies (first-level subpackages)
# ─────────────────────────────────────────────
echo "  [5/8] DEPENDENCY_MAP.md"
{
    echo "# Trail of Orbis Module Dependency Map"
    echo "# Auto-generated - do not edit manually"
    echo "# Shows which packages import from which other packages"
    echo "# $HEADER"
    echo ""
    echo "| Package | Depends On |"
    echo "|---------|-----------|"
    find "$SRC_DIR/$BASE_PKG" -mindepth 1 -maxdepth 1 -type d | sort | while read -r moddir; do
        mod=$(basename "$moddir")
        escaped_pkg="${BASE_PKG_DOT//./\\.}"
        deps=$(grep -rhoP "import\s+${escaped_pkg}\\.(\w+)" "$moddir/" 2>/dev/null | \
            grep -oP '\.\w+$' | sed 's/^\.//' | sort -u | grep -v "^${mod}$" | tr '\n' ', ' | sed 's/, $//' || true)
        if [ -z "$deps" ]; then
            printf '| `%s` | *(none)* |\n' "$mod"
        else
            printf '| `%s` | %s |\n' "$mod" "$deps"
        fi
    done
} > "$INDEX_DIR/DEPENDENCY_MAP.md"

# ─────────────────────────────────────────────
# 6. API_SURFACE.md
#    Public method signatures per class
# ─────────────────────────────────────────────
echo "  [6/8] API_SURFACE.md"
{
    echo "# Trail of Orbis Plugin API Surface"
    echo "# Auto-generated - do not edit manually"
    echo "# Regenerate with: ./scripts/index-plugin.sh"
    echo "# $HEADER"

    find "$SRC_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$SRC_DIR/}"
        classname=$(basename "$file" .java)

        # Only include files with public members
        has_public=$(grep -cP '^\s*public\s' "$file" 2>/dev/null || echo 0)
        [ "$has_public" -eq 0 ] && continue

        echo ""
        echo "## $classname"
        echo "File: \`$relpath\`"
        echo '```java'

        # Class/interface/enum/record declaration
        grep -P '^\s*public\s+(abstract\s+|final\s+|sealed\s+)?(class|interface|enum|record)\s+' "$file" 2>/dev/null | head -1 | sed 's/^\s*//' | sed 's/\s*{.*//' | sed 's/\s*$//' || true
        echo ""

        # Public method/constructor/record signatures (handles multi-line with awk)
        awk '
        /^\s*public\s/ && !/^\s*public\s+(abstract |final |sealed )?(class|interface|enum|record) / {
            line = $0
            sub(/^\s+/, "", line)
            if (line ~ /[{;]/) {
                sub(/\s*\{.*/, "", line)
                sub(/\s*;.*/, "", line)
                print line
            } else {
                while ((getline nextline) > 0) {
                    gsub(/^\s+/, " ", nextline)
                    line = line nextline
                    if (line ~ /[{;]/) {
                        sub(/\s*\{.*/, "", line)
                        sub(/\s*;.*/, "", line)
                        print line
                        break
                    }
                }
            }
        }
        ' "$file" 2>/dev/null || true

        echo '```'
    done
} > "$INDEX_DIR/API_SURFACE.md"

# ─────────────────────────────────────────────
# 7. ECS_SYSTEMS.md
#    ECS system registrations, types, and dependencies
# ─────────────────────────────────────────────
echo "  [7/8] ECS_SYSTEMS.md"
{
    echo "# ECS System Registration & Dependencies"
    echo "# Auto-generated - do not edit manually"
    echo "# Regenerate with: ./scripts/index-plugin.sh"
    echo "# $HEADER"
    echo ""
    echo "## Systems by Type"

    for sys_type in "DamageEventSystem" "DeathSystems.OnDeathSystem" "EntityEventSystem" "TickSystem"; do
        pattern=$(echo "$sys_type" | sed 's/\./\\./g')
        matches=$(grep -rlP "extends\s+${pattern}" "$SRC_DIR" 2>/dev/null || true)
        [ -z "$matches" ] && continue

        echo ""
        echo "### $sys_type"
        echo "| System | Dependencies | File |"
        echo "|--------|-------------|------|"

        for file in $matches; do
            relpath="${file#$SRC_DIR/}"
            classname=$(basename "$file" .java)
            lineno=$(grep -nP "extends\s+${pattern}" "$file" 2>/dev/null | head -1 | cut -d: -f1 || true)

            # Extract system ordering dependencies using awk for cleaner parsing
            deps=$(awk '
            /^\s*[*\/]/ { next }  # skip comments
            /Order\.(BEFORE|AFTER)/ {
                line = $0
                # SystemDependency: Order.BEFORE, ClassName.class
                while (match(line, /Order\.(BEFORE|AFTER)[^,]*,\s*([A-Za-z]+(\.[A-Za-z]+)*)\.class/, m)) {
                    printf "%s %s, ", m[1], m[2]
                    line = substr(line, RSTART + RLENGTH)
                }
                # SystemGroupDependency: Order.BEFORE, XxxModule.get().getYyyGroup()
                line = $0
                while (match(line, /Order\.(BEFORE|AFTER)[^,]*,\s*\w+\.get\(\)\.get(\w+)\(\)/, m)) {
                    printf "%s %s, ", m[1], m[2]
                    line = substr(line, RSTART + RLENGTH)
                }
            }
            ' "$file" 2>/dev/null | sed 's/, $//' || true)
            [ -z "$deps" ] && deps="*(none)*"

            printf '| %s | %s | %s:%s |\n' "$classname" "$deps" "$relpath" "$lineno"
        done
    done

    # Systems registered via registerSystem()
    echo ""
    echo "### Registered via registerSystem()"
    echo "| System | Registration | File |"
    echo "|--------|-------------|------|"
    grep -nP 'registerSystem\(new\s+\w+' "$SRC_DIR/$BASE_PKG/TrailOfOrbis.java" 2>/dev/null | while IFS=: read -r lineno line; do
        sysname=$(echo "$line" | grep -oP 'registerSystem\(new\s+\K\w+' || true)
        [ -z "$sysname" ] && continue
        printf '| %s | line %s | TrailOfOrbis.java:%s |\n' "$sysname" "$lineno" "$lineno"
    done || true

} > "$INDEX_DIR/ECS_SYSTEMS.md"

# ─────────────────────────────────────────────
# 8. EVENT_FLOW.md
#    Event handler registrations with priorities
# ─────────────────────────────────────────────
echo "  [8/8] EVENT_FLOW.md"
tmpfile=$(mktemp)
{
    echo "# Event Flow Map"
    echo "# Auto-generated - do not edit manually"
    echo "# Regenerate with: ./scripts/index-plugin.sh"
    echo "# $HEADER"
    echo ""
    echo "## Events by Type"

    # Pattern 1: registerGlobal(EventPriority.XXX, XxxEvent.class, ...)
    find "$SRC_DIR" -name "*.java" | while read -r file; do
        relpath="${file#$SRC_DIR/}"
        handlerclass=$(basename "$file" .java)
        awk -v handler="$handlerclass" -v fpath="$relpath" '
        /registerGlobal\s*\(/ {
            buf = $0
            lineno = NR
            for (i = 0; i < 5; i++) {
                if (buf ~ /\)/) break
                if ((getline nextline) > 0) buf = buf " " nextline
            }
            if (match(buf, /EventPriority\.([A-Z]+)/, prio) && match(buf, /([A-Za-z]+Event(\.[A-Za-z]+)?)\s*\.class/, evt)) {
                printf "%s|%s|%s|%s:%d|global\n", evt[1], prio[1], handler, fpath, lineno
            }
        }
        ' "$file" 2>/dev/null || true
    done >> "$tmpfile"

    # Pattern 2: getEventRegistry().register(XxxEvent.class, ...)
    grep -rnP 'getEventRegistry\(\)\.register\(\s*\w+Event' "$SRC_DIR" 2>/dev/null | while IFS=: read -r file lineno line; do
        relpath="${file#$SRC_DIR/}"
        handlerclass=$(basename "$file" .java)
        # Extract event class name, strip .class suffix
        evtclass=$(echo "$line" | grep -oP 'register\(\s*\K\w+Event(\.\w+)?' | sed 's/\.class$//' || true)
        [ -z "$evtclass" ] && continue
        printf '%s|NORMAL|%s|%s:%s|scoped\n' "$evtclass" "$handlerclass" "$relpath" "$lineno"
    done >> "$tmpfile" 2>/dev/null || true

    # Sort by event name and output grouped
    if [ -s "$tmpfile" ]; then
        sort -t'|' -k1,1 -k2,2 "$tmpfile" | awk -F'|' '
        BEGIN { prev_evt = "" }
        {
            evt = $1; prio = $2; handler = $3; loc = $4; scope = $5
            if (evt != prev_evt) {
                if (prev_evt != "") print ""
                print ""
                print "### " evt
                print "| Priority | Handler | File | Scope |"
                print "|----------|---------|------|-------|"
                prev_evt = evt
            }
            printf "| %s | %s | %s | %s |\n", prio, handler, loc, scope
        }
        '
    fi

} > "$INDEX_DIR/EVENT_FLOW.md"
rm -f "$tmpfile"

echo ""
echo "Done. Generated indexes for $FILE_COUNT source files in .index/"
echo "  CLASS_INDEX.txt  METHOD_INDEX.txt  IMPORT_MAP.txt"
echo "  API_SURFACE.md   PACKAGE_MAP.md    DEPENDENCY_MAP.md"
echo "  ECS_SYSTEMS.md   EVENT_FLOW.md"
echo ""
echo "Note: HYTALE_API_USED.md was not regenerated (requires decompiled Hytale source)."
