#!/usr/bin/env sh
# Runs the burst load test against a throwaway stack, then removes it. Exits non-zero if a check fails.
set -u
cd "$(dirname "$0")/.."
export LOAD_USER="$(id -u):$(id -g)"
mkdir -p load-tests/results
compose="docker compose -p food4good-load -f infra/docker-compose.yml -f load-tests/compose.yaml"
$compose up --build --abort-on-container-exit --exit-code-from k6 --attach k6
status=$?
$compose down --volumes --remove-orphans
exit $status
