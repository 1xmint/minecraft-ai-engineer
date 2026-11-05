#!/usr/bin/env bash
set -euo pipefail

# Milestone 5 acceptance. One real dedicated server (inside Alice's client JVM),
# owner Alice with the overlay, editor Bob without it, and never-shared Eve.
# Requests are ordinary addressed chat; commands only set up fixtures. On
# request the launcher freezes and resumes the real companion process so a
# reply arrives after a permission or selection change, and it replaces the
# companion before the saved world reopens. Offline identities only: this does
# not prove Mojang login, a real microphone or any human audio.
[[ "${CI:-}" == true ]] || { echo "Run this acceptance proof on GitHub CI, not Josh's PC." >&2; exit 64; }
repo="$(git rev-parse --show-toplevel)"
temporary="$(mktemp -d "${RUNNER_TEMP:-/tmp}/mce-acceptance.XXXXXX")"
coord="$temporary/coordination"
evidence="$repo/mod/build/acceptance-evidence"
mkdir -p "$coord" "$evidence"
pids=()
copies=()
companion_pid=""
companion_entry="$repo/scripts/ci/companion.mjs"
client_timeout=25m
harness_seconds=1500
# Synthetic provider only; this offline UUID carries no account credentials.
export MCE_CODEX_OPERATOR_UUID=10920508-d5d8-3eed-93d2-92f193afe7d7

cleanup() {
  # A frozen companion would ignore TERM until continued.
  if [[ -n "$companion_pid" ]]; then kill -CONT -- "-$companion_pid" 2>/dev/null || true; fi
  for pid in "${pids[@]}"; do kill -- "-$pid" 2>/dev/null || true; done
  if [[ -n "$companion_pid" ]]; then kill -- "-$companion_pid" 2>/dev/null || true; fi
  for role in alice bob eve; do
    checkout="$repo"
    [[ "$role" != alice ]] && checkout="$temporary/$role"
    run="$checkout/mod/build/run/acc-$role"
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

# Loom shares its extraction cache; configure each checkout serially first.
for checkout in "$repo" "$temporary/bob" "$temporary/eve"; do
  (
    cd "$checkout/mod"
    chmod +x gradlew
    ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m compileClientJava downloadAssets
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
  local old_pid="$companion_pid" stop_status
  kill -0 "$old_pid" 2>/dev/null || { echo "Companion was not live at restart request" >&2; return 1; }
  kill -TERM "$old_pid" || return 1
  for attempt in {1..50}; do
    if ! kill -0 "$old_pid" 2>/dev/null; then break; fi
    sleep 0.2
  done
  if kill -0 "$old_pid" 2>/dev/null; then echo "Companion did not stop" >&2; return 1; fi
  if wait "$old_pid"; then stop_status=0; else stop_status=$?; fi
  if [[ "$stop_status" != 0 && "$stop_status" != 143 ]]; then echo "Companion exited unexpectedly: $stop_status" >&2; return 1; fi
  start_companion || return 1
  [[ "$companion_pid" != "$old_pid" ]] || { echo "Restart did not create a distinct companion process" >&2; return 1; }
  printf '{"oldPid":%s,"newPid":%s}\n' "$old_pid" "$companion_pid" >"$coord/companion-restart.done.tmp"
  mv "$coord/companion-restart.done.tmp" "$coord/companion-restart.done"
  echo "Companion restarted: $old_pid -> $companion_pid, same SQLite/config directory"
}
mark() { printf '%s\n' "$2" >"$coord/$1.tmp"; mv "$coord/$1.tmp" "$coord/$1"; }
start_companion

launch() {
  local role="$1" username="$2" checkout="$3" overlay_disabled="$4"
  setsid bash -c '
    set -euo pipefail
    cd "$1/mod"
    # Blank MCE_MP_ROLE keeps the other client suites idle in these JVMs.
    export MCE_MP_ROLE="" MCE_ACCEPT_ROLE="$2" MCE_ACCEPT_COORD="$3" MCE_CONFIG_DIR="$3/../config-$2" MCE_DISABLE_OVERLAY="$4"
    chmod +x gradlew
    timeout "$6" xvfb-run -a -s "-screen 0 854x480x24" ./gradlew --no-daemon --max-workers=1 \
      -Dorg.gradle.jvmargs=-Xmx512m -PmceClientName="$5" -PmceTestRunDir="build/run/acc-$2" runClientGameTest -x downloadAssets
  ' _ "$checkout" "$role" "$coord" "$overlay_disabled" "$username" "$client_timeout" >"$evidence/$role.log" 2>&1 &
  pids+=("$!")
}

launch alice Alice "$repo" false
launch bob Bob "$temporary/bob" true
launch eve Eve "$temporary/eve" false
roles=(alice bob eve)
handled=()
handled_has() { local item; for item in "${handled[@]}"; do [[ "$item" == "$1" ]] && return 0; done; return 1; }
deadline=$((SECONDS + harness_seconds))
while true; do
  if compgen -G "$coord/*.failed" >/dev/null; then
    cat "$coord/"*.failed >&2
    exit 1
  fi
  for episode in a b; do
    if ! handled_has "pause-$episode" && [[ -f "$coord/pause-$episode.request" ]]; then
      kill -STOP -- "-$companion_pid"
      mark "pause-$episode.done" "{\"stopped\":$companion_pid}"
      handled+=("pause-$episode")
      echo "Companion $companion_pid frozen for late-reply episode $episode"
    fi
    if ! handled_has "resume-$episode" && [[ -f "$coord/resume-$episode.request" ]]; then
      kill -CONT -- "-$companion_pid"
      mark "resume-$episode.done" "{\"continued\":$companion_pid}"
      handled+=("resume-$episode")
      echo "Companion $companion_pid resumed for late-reply episode $episode"
    fi
  done
  if ! handled_has restart && [[ -f "$coord/companion-restart.request" ]]; then
    restart_companion
    handled+=(restart)
  fi
  if [[ -f "$coord/alice.done" && -f "$coord/bob.done" && -f "$coord/eve.done" ]]; then break; fi
  if (( SECONDS > deadline )); then echo "Acceptance CI timed out" >&2; exit 1; fi
  for index in 0 1 2; do
    if ! kill -0 "${pids[$index]}" 2>/dev/null && [[ ! -f "$coord/${roles[$index]}.done" ]]; then
      echo "${roles[$index]} exited before its native test completed" >&2
      tail -n 80 "$evidence/${roles[$index]}.log" >&2
      exit 1
    fi
  done
  sleep 1
done
for step in pause-a resume-a pause-b resume-b restart; do
  handled_has "$step" || { echo "Acceptance flow never requested $step" >&2; exit 1; }
done
for pid in "${pids[@]}"; do wait "$pid"; done
[[ ! -f "$temporary/config-alice/inference-attempted" ]] || { echo "A read-only request reached inference" >&2; exit 1; }
node "$repo/scripts/ci/verify-acceptance.mjs" "$temporary/config-alice/projects.sqlite" "$coord" >"$evidence/acceptance-proof.json"
cat "$evidence/acceptance-proof.json"
echo "Acceptance passed: owner, overlay-free editor, denied player, two late replies and a saved-world restart."
