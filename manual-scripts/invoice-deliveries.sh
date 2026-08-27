#!/usr/bin/env bash
# POST /deliveries/invoice using the JSON file given as the first argument.  Pass -v for verbose curl output.
# Edit that file's deliveryIds first — e.g. with an id from create-delivery.sh's output.
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
  echo "Example: $0 invoice-deliveries.json" >&2
  exit 1
fi

source ./token.sh

curl $curlargs -X POST "${BASE_URL}/deliveries/invoice" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"$1"
echo
