#!/bin/bash
# Hytale Decompiled Source Index Generator
# Generates searchable indexes from the decompiled HytaleServer.jar source.
# Scopes to com/hypixel/hytale/ only (skips bundled libraries).
#
# Usage: ./scripts/index-hytale.sh
#
# Output: ../APIReference/decompiled-full/.index/
#   CLASS_INDEX.txt    — ClassName → file path
#   METHOD_INDEX.txt   — methodName → ClassName → file path
#   IMPORT_MAP.txt     — ClassName → fully.qualified.package.ClassName
#   API_SURFACE.md     — Public method signatures per class
#   PACKAGE_MANIFEST.md — Package overview with class counts
#   CLASS_HIERARCHY.md  — Inheritance trees (extends/implements)

# Fix for Git Bash on Windows: grep -P needs UTF-8 locale
export LC_ALL=en_US.UTF-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DECOMPILED_DIR="$(cd "$PROJECT_DIR/../APIReference/decompiled-full" && pwd)"
HYTALE_DIR="$DECOMPILED_DIR/com/hypixel/hytale"
INDEX_DIR="$DECOMPILED_DIR/.index"

if [ ! -d "$HYTALE_DIR" ]; then
    echo "ERROR: Decompiled source not found at $HYTALE_DIR"
    exit 1
fi

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
FILE_COUNT=$(find "$HYTALE_DIR" -name "*.java" | wc -l | tr -d ' ')
HEADER="Generated: $TIMESTAMP ($FILE_COUNT Hytale source files)"

mkdir -p "$INDEX_DIR"

echo "Indexing $FILE_COUNT Hytale source files from $HYTALE_DIR ..."
echo "(Scoped to com.hypixel.hytale.* — skipping bundled libraries)"
echo ""

# Helper: extract type names from a Java file, excluding comments
extract_types() {
    grep -v '^\s*[*/]' "$1" 2>/dev/null | grep -v '^\s*//' | \
        grep -oP '(?:class|interface|enum|record)\s+\K\w+' 2>/dev/null || true
}

# ─────────────────────────────────────────────
# 1. CLASS_INDEX.txt
#    Maps every type name → file path (relative to decompiled root)
# ─────────────────────────────────────────────
echo "  [1/6] CLASS_INDEX.txt"
{
    echo "# Hytale Decompiled Class Index"
    echo "# $HEADER"
    echo "# Format: ClassName<tab>relative/path/to/File.java"
    find "$HYTALE_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$DECOMPILED_DIR/}"
        extract_types "$file" | while read -r name; do
            printf '%s\t%s\n' "$name" "$relpath"
        done
    done | sort -t$'\t' -k1,1
} > "$INDEX_DIR/CLASS_INDEX.txt"
class_count=$(wc -l < "$INDEX_DIR/CLASS_INDEX.txt" | tr -d ' ')
echo "       → $class_count entries"

# ─────────────────────────────────────────────
# 2. METHOD_INDEX.txt
#    Maps method name → enclosing class → file path
#    Extracts actual method declarations, not type names
# ─────────────────────────────────────────────
echo "  [2/6] METHOD_INDEX.txt"
{
    echo "# Hytale Decompiled Method Index"
    echo "# $HEADER"
    echo "# Format: methodName<tab>ClassName<tab>relative/path/to/File.java"
    find "$HYTALE_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$DECOMPILED_DIR/}"
        classname=$(basename "$file" .java)
        # Extract method names from declarations (public/protected/private/package-private)
        grep -v '^\s*[*/]' "$file" 2>/dev/null | grep -v '^\s*//' | \
            grep -oP '(?:public|protected|private|static|final|abstract|synchronized|native)\s+(?:(?:static|final|abstract|synchronized|native)\s+)*(?:\w+(?:<[^>]+>)?(?:\[\])*\s+)+\K\w+(?=\s*\()' 2>/dev/null | \
            while read -r method; do
                printf '%s\t%s\t%s\n' "$method" "$classname" "$relpath"
            done
    done | sort -t$'\t' -k1,1
} > "$INDEX_DIR/METHOD_INDEX.txt"
method_count=$(wc -l < "$INDEX_DIR/METHOD_INDEX.txt" | tr -d ' ')
echo "       → $method_count entries"

# ─────────────────────────────────────────────
# 3. IMPORT_MAP.txt
#    Maps class name → fully qualified path
# ─────────────────────────────────────────────
echo "  [3/6] IMPORT_MAP.txt"
{
    echo "# Hytale Decompiled Import Map"
    echo "# $HEADER"
    echo "# Format: ClassName<tab>com.hypixel.hytale.package.ClassName"
    find "$HYTALE_DIR" -name "*.java" | sort | while read -r file; do
        pkg=$(grep -oP '^package\s+\K[^;]+' "$file" 2>/dev/null | head -1 || true)
        [ -z "$pkg" ] && continue
        classname=$(basename "$file" .java)
        printf '%s\t%s.%s\n' "$classname" "$pkg" "$classname"
    done | sort -t$'\t' -k1,1
} > "$INDEX_DIR/IMPORT_MAP.txt"
import_count=$(wc -l < "$INDEX_DIR/IMPORT_MAP.txt" | tr -d ' ')
echo "       → $import_count entries"

# ─────────────────────────────────────────────
# 4. PACKAGE_MANIFEST.md
#    Package overview with class counts and descriptions
# ─────────────────────────────────────────────
echo "  [4/6] PACKAGE_MANIFEST.md"
{
    echo "# Hytale Server Package Manifest"
    echo "# $HEADER"
    echo ""
    echo "| Package | Classes | Path |"
    echo "|---------|---------|------|"
    find "$HYTALE_DIR" -name "*.java" | while read -r file; do
        dir=$(dirname "$file")
        reldir="${dir#$DECOMPILED_DIR/}"
        echo "$reldir"
    done | sort | uniq -c | sort -rn | while read -r count pkg; do
        pkgdot=$(echo "$pkg" | tr '/' '.')
        printf '| `%s` | %s | `%s` |\n' "$pkgdot" "$count" "$pkg"
    done
} > "$INDEX_DIR/PACKAGE_MANIFEST.md"

# ─────────────────────────────────────────────
# 5. API_SURFACE.md
#    Public method signatures for key classes (Managers, Services, Registries)
#    Only generates for classes with 3+ public methods to keep it focused
# ─────────────────────────────────────────────
echo "  [5/6] API_SURFACE.md"
{
    echo "# Hytale Server API Surface"
    echo "# Public method signatures for classes with 3+ public methods"
    echo "# $HEADER"

    find "$HYTALE_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$DECOMPILED_DIR/}"
        classname=$(basename "$file" .java)

        # Count public members (tr -d handles potential multi-line output from grep -c)
        pub_count=$(grep -c 'public ' "$file" 2>/dev/null | tr -d '[:space:]' || echo 0)
        [ "$pub_count" -lt 3 ] && continue

        echo ""
        echo "## $classname"
        echo "File: \`$relpath\`"
        echo '```java'

        # Class declaration
        grep -P '^\s*public\s+(abstract\s+|final\s+|sealed\s+)?(class|interface|enum|record)\s+' "$file" 2>/dev/null | head -1 | sed 's/^\s*//' | sed 's/\s*{.*//' | sed 's/\s*$//' || true
        echo ""

        # Public method signatures
        awk '
        /^\s*public\s/ && !/^\s*public\s+(abstract |final |sealed )?(class|interface|enum|record) / {
            line = $0
            sub(/^\s+/, "", line)
            if (line ~ /[{;]/) {
                sub(/\s*\{.*/, "", line)
                sub(/\s*;.*/, "", line)
                print line
            } else {
                for (i = 0; i < 5; i++) {
                    if ((getline nextline) > 0) {
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
        }
        ' "$file" 2>/dev/null || true

        echo '```'
    done
} > "$INDEX_DIR/API_SURFACE.md"

# ─────────────────────────────────────────────
# 6. CLASS_HIERARCHY.md
#    Inheritance trees — what extends/implements what
# ─────────────────────────────────────────────
echo "  [6/6] CLASS_HIERARCHY.md"
{
    echo "# Hytale Class Hierarchy"
    echo "# $HEADER"
    echo ""
    echo "## extends relationships"
    echo ""
    echo "| Class | Extends | File |"
    echo "|-------|---------|------|"
    # Decompiled code puts extends/implements on separate lines, so we join lines with awk
    find "$HYTALE_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$DECOMPILED_DIR/}"
        classname=$(basename "$file" .java)
        # Join class declaration with next lines to capture multi-line extends
        parent=$(awk '/^public\s+(abstract |final |sealed )?(class|interface|enum)\s/ {
            line = $0
            for (i = 0; i < 3; i++) {
                if (line ~ /\{/) break
                if ((getline next) > 0) line = line " " next
            }
            if (match(line, /extends\s+([A-Za-z_][A-Za-z0-9_]*)/, m)) print m[1]
        }' "$file" 2>/dev/null | head -1)
        [ -z "$parent" ] && continue
        printf '| `%s` | `%s` | `%s` |\n' "$classname" "$parent" "$relpath"
    done

    echo ""
    echo "## implements relationships"
    echo ""
    echo "| Class | Implements | File |"
    echo "|-------|-----------|------|"
    find "$HYTALE_DIR" -name "*.java" | sort | while read -r file; do
        relpath="${file#$DECOMPILED_DIR/}"
        classname=$(basename "$file" .java)
        impls=$(awk '/^public\s+(abstract |final |sealed )?(class|interface|enum)\s/ {
            line = $0
            for (i = 0; i < 5; i++) {
                if (line ~ /\{/) break
                if ((getline next) > 0) line = line " " next
            }
            if (match(line, /implements\s+([^{]+)/, m)) {
                gsub(/\s*\{.*/, "", m[1])
                print m[1]
            }
        }' "$file" 2>/dev/null | head -1)
        [ -z "$impls" ] && continue
        printf '| `%s` | `%s` | `%s` |\n' "$classname" "$impls" "$relpath"
    done
} > "$INDEX_DIR/CLASS_HIERARCHY.md"

echo ""
echo "Done. Generated 6 indexes for $FILE_COUNT Hytale source files in:"
echo "  $INDEX_DIR/"
echo ""
echo "  CLASS_INDEX.txt     — Type name → file path"
echo "  METHOD_INDEX.txt    — Method name → class → file path"
echo "  IMPORT_MAP.txt      — Class name → fully qualified import"
echo "  PACKAGE_MANIFEST.md — Package overview with class counts"
echo "  API_SURFACE.md      — Public method signatures"
echo "  CLASS_HIERARCHY.md  — Inheritance trees"
