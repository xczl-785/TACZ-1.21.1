#!/bin/zsh
set -e
project_dir="${0:A:h}"
cd "$project_dir"
exec python3 modules/tacz_adapter/tools/workbench/server.py "$@"
