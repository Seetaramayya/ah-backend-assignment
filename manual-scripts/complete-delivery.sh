#!/usr/bin/env bash
# PATCH /deliveries/{id} (complete a delivery). First argument is the delivery id
# (e.g. from start-delivery.sh's output), second is the JSON body file.  Pass -v for verbose curl output.
set -euo pipefail
cd "$(dirname "$0")"

curlargs="-sS"
while getopts "v" arg; do
  case $arg in
    v) curlargs="--verbose" ;;
    *) echo "unsupported flag" >&2 && exit 1 ;;
  esac
done
shift $((OPTIND - 1))

if [ $# -lt 2 ]; then
  echo "Usage: $0 [-v] <delivery-id> <path-to-json-file>" >&2
  echo "Example: $0 69201507-0ae4-4c56-ac2d-75fbe27efad8 complete-delivery.json" >&2
  exit 1
fi

source ./token.sh

curl $curlargs -X PATCH "${BASE_URL}/deliveries/$1" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"$2"
echo
