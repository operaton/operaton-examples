#!/usr/bin/env bash
# Sets the version of the repository: the root aggregator (pom.xml,
# build.gradle.kts) and every self-contained example under examples/*/*.
#
# Usage: .github/scripts/set-project-version.sh <new-version>
set -euo pipefail

if [ ! -f mvnw ] || [ ! -d examples ]; then
  echo "⚠️ Run this script from the repository root." >&2
  exit 1
fi
if [ $# -ne 1 ] || [ -z "$1" ]; then
  echo "⚠️ Usage: $0 <new-version>" >&2
  exit 1
fi
NEW_VERSION=$1

CURRENT_VERSION=$(./mvnw -B -q -N help:evaluate 2>/dev/null -Dexpression=project.version -DforceStdout | tail -n 1)
if [ -z "$CURRENT_VERSION" ]; then
  echo "::error::Could not determine the current project version." >&2
  exit 1
fi
if [ "$CURRENT_VERSION" = "$NEW_VERSION" ]; then
  echo "✅ Version is already $NEW_VERSION"
  exit 0
fi
echo "🔍 Updating version $CURRENT_VERSION → $NEW_VERSION"

poms=(pom.xml examples/*/*/pom.xml)
gradles=(build.gradle.kts examples/*/*/build.gradle.kts)

# The project version is the only <version> element that carries exactly the
# repository version; dependencies and plugins have their own versions.
CURRENT_VERSION="$CURRENT_VERSION" NEW_VERSION="$NEW_VERSION" python3 - "${poms[@]}" "${gradles[@]}" <<'PY'
import os, sys
old, new = os.environ["CURRENT_VERSION"], os.environ["NEW_VERSION"]
patterns = {
    "pom.xml": (f"<version>{old}</version>", f"<version>{new}</version>"),
    "build.gradle.kts": (f'version = "{old}"', f'version = "{new}"'),
}
failed = []
for path in sys.argv[1:]:
    before, after = patterns[os.path.basename(path)]
    text = open(path, encoding="utf-8").read()
    if text.count(before) != 1:
        failed.append(f"{path}: expected exactly one '{before}', found {text.count(before)}")
        continue
    open(path, "w", encoding="utf-8").write(text.replace(before, after))
if failed:
    sys.exit("::error::" + "\n".join(failed))
print(f"✅ Updated {len(sys.argv) - 1} build files")
PY

if git grep -n -F -e "<version>$CURRENT_VERSION</version>" -e "version = \"$CURRENT_VERSION\"" \
    -- 'pom.xml' '*/pom.xml' 'build.gradle.kts' '*/build.gradle.kts'; then
  echo "::error::The files above still contain the old version $CURRENT_VERSION." >&2
  exit 1
fi
echo "✅ Version set to $NEW_VERSION"
