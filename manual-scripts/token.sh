# Sourced by the other scripts in this folder for TOKEN and BASE_URL.
# TOKEN is read from manual-scripts/.token (gitignored), written by ../generate-token.sh — which signs
# it with your .env's APP_SECURITY_JWT_SECRET so it verifies against a locally-run app.
# Run ../generate-token.sh once (tokens last 30 days); re-run it if requests start returning 401.
_token_file="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.token"
if [ ! -s "$_token_file" ]; then
  echo "No cached token — run ./generate-token.sh from the repo root first." >&2
  exit 1
fi
TOKEN="$(cat "$_token_file")"
BASE_URL="${BASE_URL:-http://localhost:8080}"
