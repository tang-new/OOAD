#!/usr/bin/env bash
# Reproducible Java formatting with clang-format 22.1.8; integration adapter files are owned by the main agent.
set -euo pipefail
cd "$(dirname "$0")"
clang-format --version
find src -name '*.java' ! -name IntegrationIdentityController.java ! -name IntegrationIdentityService.java ! -name IntegrationViews.java -print0 |
  xargs -0 clang-format -i --style='{BasedOnStyle: Google, Language: Java, IndentWidth: 4, ColumnLimit: 100, AllowShortFunctionsOnASingleLine: None, AllowShortBlocksOnASingleLine: Never}'
