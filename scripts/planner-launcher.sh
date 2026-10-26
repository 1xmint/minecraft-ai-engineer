#!/bin/sh
set -eu
# SDK invokes this with `exec ...`; no shell interpolation of model/user text.
[ "${1:-}" = exec ] || exit 64
shift
exec "$MCE_CODEX_BINARY" exec --ignore-user-config --ignore-rules --ephemeral "$@"
