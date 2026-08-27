#!/usr/bin/env bash
# GET /deliveries/{deliveryId}/invoice — latest invoicing outcome for a delivery
# (poll this for a PENDING id returned by invoice-deliveries.sh).  Pass -v for verbose curl output.
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
  echo "Usage: $0 [-v] <delivery-id>" >&2
  echo "Example: $0 69201507-0ae4-4c56-ac2d-75fbe27efad8" >&2
  exit 1
fi

source ./token.sh

curl $curlargs "${BASE_URL}/deliveries/$1/invoice" \
  -H "Authorization: Bearer ${TOKEN}"
echo
