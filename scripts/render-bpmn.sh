#!/usr/bin/env bash
# Render all BPMN files to PNG using bpmn-to-image (https://github.com/bpmn-io/bpmn-to-image)
#
# Prerequisites: npm install -g bpmn-to-image
#
# Usage:
#   ./scripts/render-bpmn.sh            # render all BPMN files
#   ./scripts/render-bpmn.sh <pattern>  # render only files matching grep pattern
set -euo pipefail

if ! command -v bpmn-to-image &>/dev/null; then
  echo "ERROR: bpmn-to-image not found. Install with:"
  echo "  npm install -g bpmn-to-image"
  exit 1
fi

pattern="${1:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
count=0

while IFS= read -r bpmn; do
  [[ -n "$pattern" && "$bpmn" != *"$pattern"* ]] && continue
  png="${bpmn%.bpmn}.png"
  echo "  → ${bpmn#"$root/"}"
  bpmn-to-image "$bpmn:$png"
  count=$((count + 1))
done < <(find "$root" \
  -name "*.bpmn" \
  -not -path "*/.git/*" \
  -not -path "*/target/*" \
  -not -path "*/build/*" \
  -not -path "*/.gradle/*" \
  -not -path "*/.worktrees/*" \
  -not -path "*/.claude/*" \
  | sort)

echo "Rendered $count diagram(s)."
