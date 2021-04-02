#!/usr/bin/env bash

set -euxo pipefail

# Shellcheck itself
shellcheck "$0"

function main() {
  clj-kondo --lint src
  clojure -M:test
}

main "$@"
