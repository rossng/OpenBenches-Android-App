#!/bin/bash
# Downloads the real photos (CC BY-SA, from openbenches.org) for the benches the local
# backend returns near a location, so the sample data looks realistic in demos.
#   ./fetch-photos.sh 51.3811 -2.3590     # Parade Gardens, Bath
# Photos land in photos/, which is gitignored apart from the e2e fixture.
set -euo pipefail
cd "$(dirname "$0")"
LAT="$1" LNG="$2"
LOCAL=http://localhost:8000

ids=$(curl -fsS "$LOCAL/api/nearest/?latitude=$LAT&longitude=$LNG&distance=10" \
  | python3 -c 'import json,sys; print(" ".join(str(f["id"]) for f in json.load(sys.stdin)["features"]))')

for id in $ids; do
  for sha1 in $(curl -fsS "$LOCAL/api/bench/$id?media=true" \
      | python3 -c 'import json,sys; [print(m["sha1"]) for f in json.load(sys.stdin)["features"] for m in f["properties"].get("media", [])]'); do
    dest="photos/${sha1:0:1}/${sha1:1:1}/$sha1.jpg"
    [ -f "$dest" ] && continue
    mkdir -p "$(dirname "$dest")"
    echo "bench $id: $sha1"
    curl -fsS -o "$dest" "https://openbenches.org/image/$sha1" || { echo "  failed"; rm -f "$dest"; }
  done
done
