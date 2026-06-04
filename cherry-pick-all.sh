#!/bin/bash
# Final remaining commits

set -e

cherry() {
  local hash=$1
  local is_merge=$2
  echo "====> Cherry-picking $hash (merge=$is_merge)"
  local output
  local exit_code=0
  if [ "$is_merge" = "MERGE" ]; then
    output=$(git cherry-pick -s -m 1 "$hash" 2>&1) || exit_code=$?
  else
    output=$(git cherry-pick -s "$hash" 2>&1) || exit_code=$?
  fi
  echo "$output"
  if [ $exit_code -ne 0 ]; then
    if echo "$output" | grep -q "now empty\|nothing added"; then
      echo "  -> Empty, skipping..."
      git cherry-pick --skip
    elif echo "$output" | grep -q "invalid path"; then
      echo "  -> Invalid path, skipping..."
      git cherry-pick --skip 2>/dev/null || true
    elif [ -f .git/CHERRY_PICK_HEAD ]; then
      echo "CONFLICT in $hash - resolve manually, then:"
      echo "  git add <files> && git cherry-pick --continue"
      echo "  Comment out: cherry $hash"
      echo "  Re-run: bash cherry-pick-all.sh"
      exit 1
    fi
  fi
}

cherry cac48feff8bcaabef5832b7b544df154f1aec64b NORMAL
cherry 9cb6ec2b9d9a5821c9be41e92e65409697e60072 MERGE
cherry 7c8c06d5abab28cf523b02f90bd46cc968a0bbee NORMAL
cherry 46fb5787244f084c5b78fd0eb38e3b877f2ce3e1 NORMAL
cherry c8511842a7398523b8e00d9cc4c984974982c166 MERGE
cherry 6a15793e5cb7d586acfacd953e8b3d578f4700c8 MERGE
cherry 7b477594f38af8a3383e2795e9ec3871738a0178 MERGE
cherry 4e6313b52ae7de3e9c9583bec2555610a775f0b6 MERGE
cherry 63e466b1eebe4c6c4b4653105297fa72b4327ee5 NORMAL
# a83827366b - applied
cherry 49ba5c4e154404129209314bf84aa4118080e523 NORMAL
cherry 6714e37e4b04b65e45562762908b69d2c3caa38e NORMAL
cherry fe730908bed98b81e4ca039a6cf3272f8389138b NORMAL
cherry 21f2689a78caf189b80617711669b3be369119b2 NORMAL
cherry b54f22c8e885208d8d3117dfb0e0fc3dca70a31e NORMAL

echo ""
echo "ALL COMMITS CHERRY-PICKED SUCCESSFULLY!"
