#!/usr/bin/env bash
# POST /deliveries/v2 (start a delivery) using the JSON file given as the first argument.  Pass -v for verbose curl output.
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

if [ $# -lt 1 ]; then
  echo "Usage: $0 [-v] <path-to-json-file>" >&2
  echo "Example: $0 start-delivery.json" >&2
  exit 1
fi

source ./token.sh

curl $curlargs -X POST "${BASE_URL}/deliveries/v2" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"$1"
echo
