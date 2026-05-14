#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 [claude|codex|opencode|all]"
}

tool="${1:-all}"
case "$tool" in
  claude|codex|opencode|all) ;;
  *) usage; exit 1 ;;
esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
agent_dir="$repo_root/.agent"

create_link() {
  local name="$1"
  local target="$repo_root/.$name"

  if [ -L "$target" ]; then
    rm "$target"
  elif [ -e "$target" ]; then
    echo "Skip .$name: path already exists and is not a symlink: $target" >&2
    return 0
  fi

  ln -s ".agent" "$target"
  echo "Linked .$name -> .agent"
}

if [ "$tool" = "all" ]; then
  create_link claude
  create_link codex
  create_link opencode
else
  create_link "$tool"
fi
