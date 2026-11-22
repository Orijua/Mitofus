#!/usr/bin/env bash
# Starts the built mod jar on a real NeoForge dedicated server (no client classes, no dev environment), waits until the
# world is ready, stops it and fails if it crashed, never got ready or loaded a client-only class.
# The game tests run in the dev environment, where client and server share one classpath, so they cannot catch these.
#
# Usage (from the repository root, after ./gradlew build):  tools/ci/dedicated-server-smoke.sh [server dir]
# The server dir (default build/dedicated-server) is wiped first. Needs Java 21 and internet (NeoForge installer).
set -euo pipefail

SERVER_DIR="${1:-build/dedicated-server}"
READY_TIMEOUT="${READY_TIMEOUT:-600}"   # seconds to wait for "Done (": a first start generates the world

prop() { grep "^$1=" gradle.properties | cut -d= -f2; }
NEO_VERSION="$(prop neo_version)"
MOD_ID="$(prop mod_id)"
MOD_VERSION="$(prop mod_version)"
JAR="build/libs/${MOD_ID}-${MOD_VERSION}.jar"

if [ ! -f "$JAR" ]; then
  echo "::error::$JAR not found: run ./gradlew build first"
  exit 1
fi

rm -rf "$SERVER_DIR"
mkdir -p "$SERVER_DIR/mods"
ROOT="$(pwd)"
SERVER_DIR="$(cd "$SERVER_DIR" && pwd)"

echo "Installing NeoForge $NEO_VERSION server in $SERVER_DIR"
INSTALLER="$SERVER_DIR/neoforge-installer.jar"
curl -fsSL -o "$INSTALLER" "https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEO_VERSION}/neoforge-${NEO_VERSION}-installer.jar"
(cd "$SERVER_DIR" && java -jar neoforge-installer.jar --installServer . > installer.log 2>&1) || {
  tail -n 40 "$SERVER_DIR/installer.log"
  echo "::error::The NeoForge installer failed"
  exit 1
}

cp "$JAR" "$SERVER_DIR/mods/"
echo "eula=true" > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<'EOF'
online-mode=false
server-port=25599
spawn-protection=0
view-distance=4
simulation-distance=4
EOF
echo "-Xmx2G" > "$SERVER_DIR/user_jvm_args.txt"

LOG="$SERVER_DIR/server-output.log"
FIFO="$SERVER_DIR/console"
mkfifo "$FIFO"

echo "Starting the server with $(basename "$JAR")"
# The fifo stays open on fd 3 so the server's console only ends when we close it.
(cd "$SERVER_DIR" && exec ./run.sh nogui < "$FIFO" > "$LOG" 2>&1) &
SERVER_PID=$!
exec 3> "$FIFO"

ready=false
for ((i = 0; i < READY_TIMEOUT; i++)); do
  if grep -q 'Done (' "$LOG" 2> /dev/null; then
    ready=true
    break
  fi
  if ! kill -0 "$SERVER_PID" 2> /dev/null; then
    break
  fi
  sleep 1
done

if $ready; then
  echo "Server ready; stopping it"
  echo "stop" >&3
fi
exec 3>&-

# Give it time to save the world and exit; kill it if it hangs.
for ((i = 0; i < 120; i++)); do
  kill -0 "$SERVER_PID" 2> /dev/null || break
  sleep 1
done
if kill -0 "$SERVER_PID" 2> /dev/null; then
  echo "::warning::The server did not stop in 2 minutes; killing it"
  kill -9 "$SERVER_PID" 2> /dev/null || true
fi
wait "$SERVER_PID" 2> /dev/null || true

failed=false
if ! $ready; then
  echo "::error::The server never got ready (no 'Done (' in $READY_TIMEOUT s)"
  failed=true
fi
if ! grep -qE "\\b${MOD_ID}\\b" "$LOG"; then
  echo "::error::The log never mentions $MOD_ID: the mod did not load"
  failed=true
fi
# Client-only code reached on the server: the classic dedicated-server crash.
if grep -qE "Attempted to load class net/minecraft/client|RuntimeDistCleaner|for invalid dist DEDICATED_SERVER|NoClassDefFoundError: net/minecraft/client" "$LOG"; then
  echo "::error::A client-only class was loaded on the dedicated server"
  failed=true
fi
# An error logged by the mod itself (its logger shows as co.or.ov.* in NeoForge's short form).
if grep -aE "/ERROR\] \[(co\.or\.ov\.|com\.orijua\.mitofus)" "$LOG"; then
  echo "::error::The mod logged an error while the server started or stopped"
  failed=true
fi
if grep -q "This crash report has been saved to" "$LOG" || ls "$SERVER_DIR"/crash-reports/*.txt > /dev/null 2>&1; then
  echo "::error::The server wrote a crash report"
  failed=true
fi
if ! grep -qE "Stopping (the )?server" "$LOG" && $ready; then
  echo "::error::The server did not stop cleanly"
  failed=true
fi

if $failed; then
  echo "----- last 80 lines of the server log -----"
  tail -n 80 "$LOG"
  exit 1
fi
echo "Dedicated server OK: the mod loaded, the world generated and the server stopped cleanly"
