#!/usr/bin/env sh
# Starts Ershak on http://localhost:7070 (macOS / Linux).
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "Java is not installed. Install Java 21 from https://adoptium.net and run this again."
  exit 1
fi
exec java -jar release/ershak.jar --open
