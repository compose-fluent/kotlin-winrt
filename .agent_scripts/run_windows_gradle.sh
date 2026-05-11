#!/usr/bin/env bash

set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 <gradle-args...>" >&2
  exit 64
fi

if ! command -v wslpath >/dev/null 2>&1; then
  echo "wslpath is required to convert script paths for PowerShell." >&2
  exit 69
fi

POWERSHELL_EXE="/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"

if [[ ! -x "$POWERSHELL_EXE" ]]; then
  echo "powershell.exe was not found at $POWERSHELL_EXE." >&2
  exit 69
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
windows_script_path="$(wslpath -w "$script_dir/run_windows_gradle.ps1")"

has_console_flag=false
for arg in "$@"; do
  if [[ "$arg" == --console=* ]]; then
    has_console_flag=true
    break
  fi
done

gradle_args=("$@")
if [[ "$has_console_flag" == false ]]; then
  gradle_args+=(--console=plain)
fi

exec "$POWERSHELL_EXE" \
  -NoLogo \
  -NoProfile \
  -NonInteractive \
  -ExecutionPolicy Bypass \
  -File "$windows_script_path" \
  "${gradle_args[@]}"
