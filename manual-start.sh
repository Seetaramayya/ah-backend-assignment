#!/usr/bin/env bash
# TODO: no Windows equivalent (manual-start.cmd / manual-start.ps1) exists yet.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo ".env not found — copy .env.example to .env first." >&2
  exit 1
fi

set -a
source .env
set +a

docker compose up -d mock-api database

./mvnw spring-boot:run
