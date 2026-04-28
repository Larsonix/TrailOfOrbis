#!/bin/bash
# Setup script for Java LSP MCP server
# This enables Claude Code to have IDE-like Java code completion

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TOOLS_DIR="$HOME/.local/share/claude-tools"

echo "=== Java LSP MCP Setup ==="
echo "Project: $PROJECT_DIR"
echo "Tools will be installed to: $TOOLS_DIR"
echo ""

# Create tools directory
mkdir -p "$TOOLS_DIR"

# Step 1: Install Maven if not present
if ! command -v mvn &> /dev/null; then
    echo "[1/4] Installing Maven..."
    sudo apt update && sudo apt install -y maven
else
    echo "[1/4] Maven already installed: $(mvn --version | head -1)"
fi

# Step 2: Download JDTLS
JDTLS_DIR="$TOOLS_DIR/jdtls"
if [ ! -d "$JDTLS_DIR" ]; then
    echo "[2/4] Downloading Eclipse JDTLS..."
    mkdir -p "$JDTLS_DIR"

    # Get latest milestone version
    JDTLS_URL="https://download.eclipse.org/jdtls/milestones/1.40.0/jdt-language-server-1.40.0-202410311350.tar.gz"

    curl -L "$JDTLS_URL" | tar xz -C "$JDTLS_DIR"
    echo "JDTLS installed to: $JDTLS_DIR"
else
    echo "[2/4] JDTLS already installed at: $JDTLS_DIR"
fi

# Step 3: Clone and build LSP4J-MCP
LSP4J_DIR="$TOOLS_DIR/LSP4J-MCP"
LSP4J_JAR="$LSP4J_DIR/target/lsp4j-mcp-1.0.0-SNAPSHOT.jar"

if [ ! -f "$LSP4J_JAR" ]; then
    echo "[3/4] Building LSP4J-MCP..."

    if [ -d "$LSP4J_DIR" ]; then
        rm -rf "$LSP4J_DIR"
    fi

    git clone https://github.com/stephanj/LSP4J-MCP.git "$LSP4J_DIR"
    cd "$LSP4J_DIR"
    mvn clean package -DskipTests
    cd "$PROJECT_DIR"

    echo "LSP4J-MCP built: $LSP4J_JAR"
else
    echo "[3/4] LSP4J-MCP already built at: $LSP4J_JAR"
fi

# Step 4: Configure Claude MCP
echo "[4/4] Configuring Claude Code MCP server..."

# Create wrapper script that JDTLS can use
JDTLS_LAUNCHER="$JDTLS_DIR/bin/jdtls"
if [ ! -f "$JDTLS_LAUNCHER" ]; then
    # Find the launcher JAR
    LAUNCHER_JAR=$(find "$JDTLS_DIR" -name "org.eclipse.equinox.launcher_*.jar" | head -1)
    CONFIG_DIR="$JDTLS_DIR/config_linux"

    cat > "$JDTLS_DIR/bin/jdtls" << EOF
#!/bin/bash
java \\
    -Declipse.application=org.eclipse.jdt.ls.core.id1 \\
    -Dosgi.bundles.defaultStartLevel=4 \\
    -Declipse.product=org.eclipse.jdt.ls.core.product \\
    -Dlog.level=ALL \\
    -noverify \\
    -Xmx1G \\
    --add-modules=ALL-SYSTEM \\
    --add-opens java.base/java.util=ALL-UNNAMED \\
    --add-opens java.base/java.lang=ALL-UNNAMED \\
    -jar "$LAUNCHER_JAR" \\
    -configuration "$CONFIG_DIR" \\
    -data "\${1:-\$HOME/.cache/jdtls-workspace}" \\
    "\$@"
EOF
    chmod +x "$JDTLS_DIR/bin/jdtls"
fi

# Remove old config if exists, then add new one
cd "$PROJECT_DIR"
claude mcp remove java-lsp --scope project 2>/dev/null || true

claude mcp add java-lsp --scope project -- \
    java -jar "$LSP4J_JAR" "$PROJECT_DIR" "$JDTLS_DIR/bin/jdtls"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Java LSP MCP server configured!"
echo "JDTLS will automatically use your Gradle dependencies including:"
echo "  - com.hypixel.hytale:Server (from Maven repository)"
echo ""
echo "Restart Claude Code to activate the new MCP server."
echo ""
echo "To verify: claude mcp list"
