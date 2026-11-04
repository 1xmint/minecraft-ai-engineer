#!/usr/bin/env bash
set -euo pipefail
[[ "${CI:-}" == true ]] || { echo "Run the full-adder proof on GitHub CI, not Josh's PC." >&2; exit 64; }
repo="$(git rev-parse --show-toplevel)"
temporary="$(mktemp -d "${RUNNER_TEMP:-/tmp}/mce-full-adder.XXXXXX")"
coord="$temporary/coordination"
evidence="$repo/mod/build/full-adder-multiplayer-evidence"
mkdir -p "$coord" "$evidence"
pids=()
companion_pid=""
cleanup() {
  for pid in "${pids[@]}"; do kill -- "-$pid" 2>/dev/null || true; done
  if [[ -n "$companion_pid" ]]; then kill -- "-$companion_pid" 2>/dev/null || true; fi
  cp -a "$coord" "$evidence/" 2>/dev/null || true
  for role in full-adder-alice full-adder-bob; do
    checkout="$repo"; [[ "$role" == full-adder-bob ]] && checkout="$temporary/bob"
    run="$checkout/mod/build/run/mp-$role"
    if [[ -d "$run" ]]; then find "$run" -type f \( -name '*.log' -o -name '*.png' \) -exec cp --parents '{}' "$evidence/" \;; fi
  done
  git -C "$repo" worktree remove --force "$temporary/bob" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
git -C "$repo" worktree add --detach "$temporary/bob" HEAD
for checkout in "$repo" "$temporary/bob"; do
  (cd "$checkout/mod"; chmod +x gradlew; ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m compileClientJava)
done
# Synthetic offline fixture identity only. This entry point never calls an SDK/model.
export MCE_CODEX_OPERATOR_UUID=10920508-d5d8-3eed-93d2-92f193afe7d7
setsid node "$repo/scripts/ci/companion.mjs" "$temporary/config-full-adder-alice" >"$evidence/companion.log" 2>&1 &
companion_pid=$!
for attempt in {1..100}; do
  kill -0 "$companion_pid" 2>/dev/null || { cat "$evidence/companion.log" >&2; exit 1; }
  if grep -q 'Companion ready' "$evidence/companion.log"; then break; fi
  sleep 0.2
done
grep -q 'Companion ready' "$evidence/companion.log" || { echo 'Fixture companion did not become ready' >&2; exit 1; }
launch() {
  local checkout="$1" role="$2" username="$3" overlay="$4"
  setsid bash -c '
    set -euo pipefail
    cd "$1/mod"
    export MCE_MP_ROLE="$2" MCE_MP_COORD="$3" MCE_CONFIG_DIR="$3/../config-$2" MCE_DISABLE_OVERLAY="$5"
    timeout 25m xvfb-run -a -s "-screen 0 854x480x24" ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m -PmceClientName="$4" -PmceTestRunDir="build/run/mp-$2" runClientGameTest
  ' _ "$checkout" "$role" "$coord" "$username" "$overlay" >"$evidence/$role.log" 2>&1 &
  pids+=("$!")
}
launch "$repo" full-adder-alice Alice false
launch "$temporary/bob" full-adder-bob Bob true
for pid in "${pids[@]}"; do wait "$pid"; done
for marker in full-adder-preview.done full-adder-private.done full-adder-viewer.done full-adder-built.done full-adder-revocation.done full-adder-interruption.done full-adder-functional.done full-adder-interference.done full-adder-restarted.done full-adder-undo.done full-adder-alice.done full-adder-bob.done; do
  [[ -f "$coord/$marker" && "$(cat "$coord/$marker")" == passed ]] || { echo "Missing full-adder receipt: $marker" >&2; exit 1; }
done
kill -- "-$companion_pid"
wait "$companion_pid"
companion_pid=""
cp "$temporary/config-full-adder-alice/projects.sqlite" "$evidence/projects.sqlite"
node scripts/ci/verify-full-adder-session.mjs "$evidence/projects.sqlite" "$coord" | tee "$evidence/full-adder-session-proof.json"
