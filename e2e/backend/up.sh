#!/bin/bash
# Starts the local OpenBenches backend on http://localhost:8000.
#   ./up.sh            fetch the website source (if needed), build, start, wait until healthy
#   ./up.sh --reset    also wipe the database so it is re-seeded
set -euo pipefail
cd "$(dirname "$0")"

# Pinned commit of the website, from the docker-local-dev branch of a fork that adds its
# Docker setup (dev/). Upstream: https://github.com/openbenches/openbenches.org
OPENBENCHES_REPO="${OPENBENCHES_REPO:-https://github.com/rossng/openbenches.org.git}"
OPENBENCHES_REF="${OPENBENCHES_REF:-ade3f3b3a41834ab87470ed88b219d132a63e03d}"
SRC=.openbenches-src

if [ ! -d "$SRC/.git" ]; then
  git clone --filter=blob:none "$OPENBENCHES_REPO" "$SRC"
fi
if [ "$(git -C "$SRC" rev-parse HEAD)" != "$OPENBENCHES_REF" ]; then
  git -C "$SRC" remote set-url origin "$OPENBENCHES_REPO"
  git -C "$SRC" fetch --quiet origin
  git -C "$SRC" checkout --quiet --detach "$OPENBENCHES_REF"
fi

if [ "${1:-}" = "--reset" ]; then
  docker compose down --volumes
fi

docker compose up --detach --build --wait
echo "OpenBenches backend is up: http://localhost:8000/api/bench/900001"
