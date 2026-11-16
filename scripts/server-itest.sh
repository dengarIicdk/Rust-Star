#!/usr/bin/env bash
# Integration test against a REAL Minecraft dedicated server.
#
# Starts the dev server, waits for it to be ready, issues commands, and greps
# the log for the PASS/FAIL verdicts printed by /starflight selftest|verify.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/mods/starflight"

export JAVA_HOME="${JAVA_HOME:-$ROOT/tools/jdk/jdk-21.0.13+9}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/tools/gradle-home}"
GRADLE="$ROOT/tools/gradle-8.14.3/bin/gradle"
LOG="$ROOT/artifacts/logs/server-itest.log"
CMDLOG="$ROOT/artifacts/logs/server-cmds.txt"

mkdir -p "$ROOT/artifacts/logs" run
printf 'eula=true\n' > run/eula.txt
[[ -f run/server.properties ]] || cat > run/server.properties <<'EOF'
online-mode=false
server-port=25599
max-players=2
spawn-protection=0
view-distance=4
simulation-distance=4
level-type=minecraft:flat
EOF

rm -f "$LOG" "$CMDLOG"
: > "$CMDLOG"

echo "itest: starting server (log: $LOG)"
# Feed stdin from a regular file we append to, so commands can be injected at
# specific points. A FIFO does not work here: Gradle's stdin redirect fails on
# Windows pipes, and Minecraft never sees the commands.
( tail -f -n +1 "$CMDLOG" 2>/dev/null | "$GRADLE" --no-daemon --console=plain runServer > "$LOG" 2>&1 ) &
SERVER_PID=$!

cleanup() {
  pkill -f "dev-launch-injector" 2>/dev/null || true
  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
  rm -f "$CMDLOG"
}
trap cleanup EXIT

wait_for() {
  local pattern="$1" timeout="${2:-240}" i=0
  while (( i < timeout )); do
    if grep -q "$pattern" "$LOG" 2>/dev/null; then
      return 0
    fi
    sleep 1
    (( i++ ))
  done
  return 1
}

if ! wait_for 'Done (' 300; then
  echo "itest: FAIL — server never became ready"
  tail -40 "$LOG"
  exit 1
fi
echo "itest: server ready"

# The native core must have loaded. Anything else is a hard failure.
if grep -q 'native core unavailable' "$LOG"; then
  echo "itest: FAIL — native core did not load in the server"
  grep -A6 'Failed to load native core' "$LOG" | head -20
  exit 1
fi
grep -m1 'starflight.*Native core' "$LOG" || true

# A player must exist for the player-relative probe. Use a fake online player
# if possible; otherwise the selftest command falls back to spawning at origin.
echo "--- issuing commands ---"
echo 'starflight status' >> "$CMDLOG"
sleep 3
echo 'starflight selftest' >> "$CMDLOG"
sleep 6
echo 'starflight verify' >> "$CMDLOG"
sleep 4

sleep 2

STATUS=$(grep -c 'Starflight native=' "$LOG" || true)
QUEUED=$(grep -c 'STARFLIGHT-SELFTEST: QUEUED' "$LOG" || true)
VERIFY_LINE=$(grep -m1 'STARFLIGHT-VERIFY' "$LOG" || true)
FAIL_LINE=$(grep -m1 'STARFLIGHT-SELFTEST: FAIL' "$LOG" || true)

echo "itest: status-lines=$STATUS queued-lines=$QUEUED"
echo "itest: verdict: ${VERIFY_LINE:-<none>} ${FAIL_LINE:-}"

if [[ -n "$FAIL_LINE" ]]; then
  echo "RESULT: FAIL"
  exit 1
fi
if [[ -z "$VERIFY_LINE" ]]; then
  echo "RESULT: BLOCKED (no player available to place the probe; see log)"
  grep -E 'starflight (status|selftest|verify)|Unknown or incomplete command|No player' "$LOG" | tail -10
  exit 2
fi
if [[ "$VERIFY_LINE" == *PASS* ]]; then
  echo "RESULT: PASS"
  exit 0
fi
echo "RESULT: FAIL"
exit 1
