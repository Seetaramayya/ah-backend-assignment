#!/usr/bin/env bash
# Generates a JWT signed with your .env's APP_SECURITY_JWT_SECRET and caches it in
# manual-scripts/.token, so the scripts in that folder don't need a token pasted in.
# Tokens are valid 30 days — re-run this when requests start coming back 401.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo ".env not found — copy .env.example to .env first." >&2
  exit 1
fi

set -a
source .env
set +a

# -Djacoco.skip: the coverage check would otherwise fail this single-test run.
token="$(./mvnw test -Dtest=SampleTokenGeneratorTest -Djacoco.skip=true 2>&1 | sed -n 's/^token=//p' | tail -n 1)"
if [ -z "${token}" ]; then
  echo "Could not extract a token from the test output — run the mvn command above by hand to see why." >&2
  exit 1
fi

printf '%s\n' "${token}" > manual-scripts/.token
echo "Wrote manual-scripts/.token"
echo "${token}"
