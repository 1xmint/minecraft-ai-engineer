#!/usr/bin/env bash
set -euo pipefail
[[ "${CI:-}" == true && -f "${MCE_PHYSICAL_CARPET_JAR:-}" ]] || { echo 'Physical multiplayer proof is CI-only and requires the pinned jar.' >&2; exit 64; }
repo="$(git rev-parse --show-toplevel)"
temporary="$(mktemp -d "${RUNNER_TEMP:-/tmp}/mce-physical.XXXXXX")"
coord="$temporary/coordination"
evidence="$repo/mod/build/physical-multiplayer-evidence"
mkdir -p "$coord" "$evidence"
pids=()
cleanup() {
  for pid in "${pids[@]}"; do kill -- "-$pid" 2>/dev/null || true; done
  cp -a "$coord" "$evidence/" 2>/dev/null || true
  for role in physical-alice physical-bob; do
    checkout="$repo"; [[ "$role" == physical-bob ]] && checkout="$temporary/bob"
    run="$checkout/mod/build/run/mp-$role"
    if [[ -d "$run" ]]; then find "$run" -type f \( -name '*.log' -o -name '*.png' \) -exec cp --parents '{}' "$evidence/" \;; fi
  done
  git -C "$repo" worktree remove --force "$temporary/bob" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
git -C "$repo" worktree add --detach "$temporary/bob" HEAD
cat >"$temporary/carpet.gradle" <<'GRADLE'
allprojects {
    afterEvaluate {
        dependencies.add('runtimeOnly', files(System.getenv('MCE_PHYSICAL_CARPET_JAR')))
    }
}
GRADLE
for checkout in "$repo" "$temporary/bob"; do
  (cd "$checkout/mod"; chmod +x gradlew; ./gradlew --init-script "$temporary/carpet.gradle" --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m compileClientJava)
done
launch() {
  local checkout="$1" role="$2" username="$3"
  setsid bash -c '
    set -euo pipefail
    cd "$1/mod"
    export MCE_MP_ROLE="$2" MCE_MP_COORD="$3" MCE_CONFIG_DIR="$3/../config-$2"
    timeout 10m xvfb-run -a -s "-screen 0 854x480x24" ./gradlew --init-script "$4" --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m -PmceClientName="$5" -PmceTestRunDir="build/run/mp-$2" runClientGameTest
  ' _ "$checkout" "$role" "$coord" "$temporary/carpet.gradle" "$username" >"$evidence/$role.log" 2>&1 &
  pids+=("$!")
}
launch "$repo" physical-alice Alice
launch "$temporary/bob" physical-bob Bob
for pid in "${pids[@]}"; do wait "$pid"; done
for marker in physical-placement.done physical-gather.done physical-chat.done physical-peer-denied.done physical-disconnect.done physical-restart.done physical-inventory-saved.done physical-inventory-restarted.done physical-alice.done physical-bob.done; do
  [[ -f "$coord/$marker" && "$(cat "$coord/$marker")" == passed ]] || { echo "Missing native receipt: $marker" >&2; exit 1; }
done
node scripts/ci/verify-physical-gather.mjs "$coord/gather-records"
echo 'Physical worker recovery proof passed: native mining/pickup, saved inventory reload under denial, plus companion chat and same-world reboot.'
