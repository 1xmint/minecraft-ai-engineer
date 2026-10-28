#!/usr/bin/env bash
set -euo pipefail

# Three real Minecraft client JVMs share Alice's actual dedicated server. The
# fourth launch reconnects Eve without overlay negotiation. No protocol mocks,
# account credentials or inference calls are involved.
[[ "${CI:-}" == true ]] || { echo "Run this multiplayer proof on GitHub CI, not Josh's PC." >&2; exit 64; }
repo="$(git rev-parse --show-toplevel)"
temporary="$(mktemp -d "${RUNNER_TEMP:-/tmp}/mce-multiplayer.XXXXXX")"
coord="$temporary/coordination"
evidence="$repo/mod/build/multiplayer-evidence"
mkdir -p "$coord" "$evidence"
pids=()
copies=()
companion_pid=""
companion_entry="$repo/scripts/ci/companion.mjs"
client_timeout=14m
harness_seconds=900
# Synthetic provider only; this offline UUID carries no real account credentials.
if [[ "${MCE_LIVE_ACCEPTANCE:-}" != true ]]; then
  export MCE_CODEX_OPERATOR_UUID=10920508-d5d8-3eed-93d2-92f193afe7d7
fi
if [[ "${MCE_LIVE_ACCEPTANCE:-}" == true ]]; then
  [[ "${GITHUB_ACTIONS:-}" == true && "${GITHUB_REPOSITORY:-}" == 1xmint/minecraft-ai-engineer ]] || exit 64
  companion_entry="$repo/scripts/ci/live-companion.mjs"
  client_timeout=29m
  harness_seconds=1800
fi
cleanup() {
  for pid in "${pids[@]}"; do kill -- "-$pid" 2>/dev/null || true; done
  if [[ -n "$companion_pid" ]]; then kill -- "-$companion_pid" 2>/dev/null || true; fi
  for role in alice bob eve eve-text; do
    checkout="$repo"
    [[ "$role" == bob ]] && checkout="$temporary/bob"
    [[ "$role" == eve || "$role" == eve-text ]] && checkout="$temporary/eve"
    run="$checkout/mod/build/run/mp-$role"
    if [[ -d "$run" ]]; then
      mkdir -p "$evidence/$role-runtime"
      find "$run" -type f \( -name '*.log' -o -name '*.png' \) -exec cp --parents '{}' "$evidence/$role-runtime" \;
    fi
  done
  for copy in "${copies[@]}"; do git -C "$repo" worktree remove --force "$copy" 2>/dev/null || true; done
  cp -a "$coord" "$evidence/" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

git -C "$repo" worktree add --detach "$temporary/bob" HEAD
git -C "$repo" worktree add --detach "$temporary/eve" HEAD
copies+=("$temporary/bob" "$temporary/eve")

# Loom shares its Minecraft extraction cache across checkouts. Finish its first
# configuration serially before three Gradle processes consume that cache.
for checkout in "$repo" "$temporary/bob" "$temporary/eve"; do
  (
    cd "$checkout/mod"
    chmod +x gradlew
    ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m compileClientJava
  )
done

: >"$evidence/companion.log"
start_companion() {
  local previous_ready ready
  previous_ready=$(grep -c 'Companion ready' "$evidence/companion.log" || true)
  setsid node "$companion_entry" "$temporary/config-alice" >>"$evidence/companion.log" 2>&1 &
  companion_pid=$!
  for attempt in {1..50}; do
    if ! kill -0 "$companion_pid" 2>/dev/null; then cat "$evidence/companion.log" >&2; return 1; fi
    ready=$(grep -c 'Companion ready' "$evidence/companion.log" || true)
    if (( ready > previous_ready )); then return 0; fi
    sleep 0.2
  done
  echo "CI companion did not become ready" >&2
  return 1
}
restart_companion() {
  local old_pid="$companion_pid" stop_status receipt_prefix="${1:-companion}"
  kill -0 "$old_pid" 2>/dev/null || { echo "Original companion was not live at restart request" >&2; return 1; }
  kill -TERM "$old_pid" || return 1
  for attempt in {1..50}; do
    if ! kill -0 "$old_pid" 2>/dev/null; then break; fi
    sleep 0.2
  done
  if kill -0 "$old_pid" 2>/dev/null; then echo "Original companion did not stop" >&2; return 1; fi
  if wait "$old_pid"; then stop_status=0; else stop_status=$?; fi
  if [[ "$stop_status" != 0 && "$stop_status" != 143 ]]; then echo "Companion exited unexpectedly: $stop_status" >&2; return 1; fi
  start_companion || return 1
  [[ "$companion_pid" != "$old_pid" ]] || { echo "Restart did not create a distinct companion process" >&2; return 1; }
  printf '{"oldPid":%s,"newPid":%s}\n' "$old_pid" "$companion_pid" >"$coord/$receipt_prefix-restart.done.tmp"
  mv "$coord/$receipt_prefix-restart.done.tmp" "$coord/$receipt_prefix-restart.done"
  echo "Actual companion restarted: $old_pid -> $companion_pid, same private SQLite/config directory"
}
start_companion

launch() {
  local role="$1" username="$2" checkout="$3" overlay_disabled="$4"
  setsid bash -c '
    set -euo pipefail
    cd "$1/mod"
    export MCE_MP_ROLE="$2" MCE_MP_COORD="$3" MCE_CONFIG_DIR="$3/../config-$2" MCE_DISABLE_OVERLAY="$4"
    chmod +x gradlew
    timeout "$6" xvfb-run -a -s "-screen 0 854x480x24" ./gradlew --no-daemon --max-workers=1 \
      -Dorg.gradle.jvmargs=-Xmx512m -PmceClientName="$5" -PmceTestRunDir="build/run/mp-$2" runClientGameTest
  ' _ "$checkout" "$role" "$coord" "$overlay_disabled" "$username" "$client_timeout" >"$evidence/$role.log" 2>&1 &
  pids+=("$!")
}

launch alice Alice "$repo" false
launch bob Bob "$temporary/bob" false
launch eve Eve "$temporary/eve" false
text_started=false
companion_restarted=false
generated_companion_restarted=false
deadline=$((SECONDS + harness_seconds))
while true; do
  if compgen -G "$coord/*.failed" >/dev/null; then
    cat "$coord/"*.failed >&2
    exit 1
  fi
  if [[ "$companion_restarted" == false && -f "$coord/companion-restart.request" ]]; then
    restart_companion
    companion_restarted=true
  fi
  if [[ "$generated_companion_restarted" == false && -f "$coord/generated-companion-restart.request" ]]; then
    restart_companion generated-companion
    generated_companion_restarted=true
  fi
  if [[ "$text_started" == false && -f "$coord/eve.done" ]]; then
    # Eve must fully exit before her second native process reuses the profile.
    if ! kill -0 "${pids[2]}" 2>/dev/null; then
      wait "${pids[2]}"
      launch eve-text Eve "$temporary/eve" true
      text_started=true
    fi
  fi
  if [[ -f "$coord/alice.done" && -f "$coord/bob.done" && -f "$coord/eve-text.done" ]]; then break; fi
  if (( SECONDS > deadline )); then echo "Dedicated multiplayer CI timed out" >&2; exit 1; fi
  for index in 0 1 2; do
    if ! kill -0 "${pids[$index]}" 2>/dev/null; then
      role=(alice bob eve)
      if [[ ! -f "$coord/${role[$index]}.done" ]]; then
        echo "${role[$index]} exited before its native test completed" >&2
        tail -n 80 "$evidence/${role[$index]}.log" >&2
        exit 1
      fi
    fi
  done
  sleep 2
done
[[ "$companion_restarted" == true ]] || { echo "Native flow did not request an actual companion restart" >&2; exit 1; }
for pid in "${pids[@]}"; do wait "$pid"; done
[[ ! -f "$temporary/config-alice/inference-attempted" ]] || { echo "An unauthorized CI request reached inference" >&2; exit 1; }
if [[ "${MCE_LIVE_ACCEPTANCE:-}" == true ]]; then
  if [[ "${MCE_GENERATED_ACCEPTANCE:-}" == true ]]; then
    [[ -f "$MCE_ACCEPTANCE_EXCHANGE_DIR/behavior/accepted.json" && -f "$MCE_ACCEPTANCE_EXCHANGE_DIR/geometry/accepted.json" && ! -f "$MCE_ACCEPTANCE_EXCHANGE_DIR/unexpected-inference" ]] || { echo "Generated stages were not accepted exactly once" >&2; exit 1; }
  else
    [[ -f "$MCE_ACCEPTANCE_EXCHANGE_DIR/accepted.json" && ! -f "$MCE_ACCEPTANCE_EXCHANGE_DIR/unexpected-inference" ]] || { echo "Live exchange was not accepted exactly once" >&2; exit 1; }
  fi
  cp -a "$MCE_ACCEPTANCE_EXCHANGE_DIR" "$evidence/live-exchange"
fi
node "$repo/scripts/ci/verify-memory.mjs" "$temporary/config-alice/projects.sqlite" "$coord" >"$evidence/memory-proof.json"
find "$repo/mod/build/run/mp-alice" -type f \( -name '*.png' -o -name '*.log' \) -exec cp --parents '{}' "$evidence" \;
echo "Dedicated multiplayer proof passed: Alice, Bob, denied Eve, text-only Eve and saved-world restart."
