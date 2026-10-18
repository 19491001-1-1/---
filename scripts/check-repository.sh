#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
# 只检查版本控制范围，不扫描本地的大型实验产物。
# 算法推导和答辩笔记可以保存在 docs/。
failed=0
while IFS= read -r -d '' path; do
  case "$path" in
    */.DS_Store|.DS_Store|.github/modernize/*|target/*|out/*|frontend/node_modules/*|frontend/dist/*|results/*|*.class|*.log|*.tmp)
      printf 'Generated or local-only file should not be versioned: %s\n' "$path"
      failed=1
      ;;
  esac
done < <(git ls-files --cached --others --exclude-standard -z)
if [[ "$failed" -ne 0 ]]; then exit 1; fi
echo "Repository structure check passed."
