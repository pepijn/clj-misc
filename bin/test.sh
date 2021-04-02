#!/usr/bin/env bash

set -euxo pipefail

# Shellcheck itself
shellcheck "$0"

function main() {
  clj-kondo --lint src test
  clojure -M:test
}

main "$@"
