#!/usr/bin/env sh
# Runs the dashboard tests against a throwaway stack, then removes it. Exits non-zero if a test fails.
set -u
cd "$(dirname "$0")/.."
export E2E_USER="$(id -u):$(id -g)"
compose="docker compose -p food4good-e2e -f infra/docker-compose.yml -f e2e/compose.yaml"
$compose up --build --abort-on-container-exit --exit-code-from playwright
status=$?
$compose down --volumes --remove-orphans
exit $status
