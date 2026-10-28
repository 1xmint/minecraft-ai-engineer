#!/usr/bin/env bash
set -euo pipefail

# GitHub Ubuntu runners only. Keep the same real software-rendered game backend.
[[ "${CI:-}" == true ]] || { echo "Prepare graphics on GitHub CI, not Josh's PC." >&2; exit 64; }
packages=(xvfb xauth libgl1-mesa-dri mesa-vulkan-drivers)
ready() {
  command -v xvfb-run >/dev/null || return 1
  command -v xauth >/dev/null || return 1
  for package in "${packages[@]}"; do
    [[ "$(dpkg-query -W -f='${Status}' "$package" 2>/dev/null || true)" == 'install ok installed' ]] || return 1
  done
}
if ready; then exit 0; fi

# The runner's signed APT sources use this mirror file. Each slow mirror has its
# own total budget, so a transfer that never fails cannot consume the fallback.
# Endpoints match actions/runner-images' configure-apt-sources.sh.
for mirror in 'https://archive.ubuntu.com/ubuntu/' 'http://azure.archive.ubuntu.com/ubuntu/'; do
  printf '%s\n' "$mirror" | sudo tee /etc/apt/apt-mirrors.txt >/dev/null
  echo "Preparing required graphics packages through $mirror"
  if sudo timeout --kill-after=10s 240s bash -c '
    set -euo pipefail
    options=(-o Acquire::Retries=1 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20 -o Acquire::ForceIPv4=true)
    apt-get "${options[@]}" update -qq
    apt-get "${options[@]}" install -y --no-upgrade "$@"
  ' _ "${packages[@]}" && ready; then
    exit 0
  fi
  echo "Graphics setup did not complete through $mirror; trying the next bounded source" >&2
done
echo "Required graphics packages are unavailable; refusing to claim a native game check" >&2
exit 1
