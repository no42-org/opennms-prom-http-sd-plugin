#!/bin/sh
# Copyright 2026 Ronny Trommer <ronny@no42.org>
# SPDX-License-Identifier: Apache-2.0
#
# Validate every non-merge commit in a range: Conventional Commit subject,
# Signed-off-by present, Assisted-by trailer well-formed if present.
# Usage: commitlint.sh <range>    e.g. origin/main..HEAD
set -eu
range=${1:?usage: commitlint.sh <range>}
types='feat|fix|docs|style|refactor|perf|test|chore|ci|build|revert'
subject_re="^($types)(\([A-Za-z0-9._/-]+\))?!?: .+"
signoff_re='^Signed-off-by: .+ <.+@.+>$'
assisted_re='^Assisted-by: [A-Za-z0-9._-]+:[A-Za-z0-9._-]+( .*)?$'

commits=$(git rev-list --no-merges "$range")
if [ -z "$commits" ]; then
  echo "commitlint: no commits in $range"
  exit 0
fi

status=0
for sha in $commits; do
  short=$(git rev-parse --short "$sha")
  subject=$(git log -1 --format=%s "$sha")
  body=$(git log -1 --format=%B "$sha")
  if ! printf '%s\n' "$subject" | grep -Eq "$subject_re"; then
    echo "$short: subject is not a Conventional Commit: $subject"; status=1
  fi
  if ! printf '%s\n' "$body" | grep -Eq "$signoff_re"; then
    echo "$short: missing Signed-off-by trailer"; status=1
  fi
  if printf '%s\n' "$body" | grep -q '^Assisted-by:' && ! printf '%s\n' "$body" | grep -Eq "$assisted_re"; then
    echo "$short: malformed Assisted-by trailer, expected 'Assisted-by: <Agent>:<model>'"; status=1
  fi
done
[ $status -eq 0 ] && echo "commitlint: $(echo "$commits" | wc -l | tr -d ' ') commit(s) ok"
exit $status
