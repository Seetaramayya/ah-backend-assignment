#!/usr/bin/env bash
# GET /deliveries/business-summary. No request body needed.  Pass -v for verbose curl output.
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

source ./token.sh

curl $curlargs "${BASE_URL}/deliveries/business-summary" \
  -H "Authorization: Bearer ${TOKEN}"
echo
