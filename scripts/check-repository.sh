#!/bin/zsh
set -euo pipefail

project_dir="${0:A:h:h}"
cd "$project_dir"

forbidden_files=(${(f)"$(find . -type f \
  -not -path './.git/*' \
  -not -path './target/*' \
  -not -path './out/*' \
  -not -path './results/*' \
  \( -iname '*.pdf' -o -iname '*.docx' -o -iname '*.ppt' \
     -o -iname '*.pptx' -o -iname '*.zip' \
     -o -iname '*教程*' -o -iname '*答辩*' -o -iname '*explainer*' \) -print)"})

if (( ${#forbidden_files[@]} > 0 )); then
  echo "Repository check failed: reference or tutorial files must stay outside the source repository."
  printf '  %s\n' "${forbidden_files[@]}"
  exit 1
fi

if find . -name '.DS_Store' -not -path './.git/*' -print -quit | grep -q .; then
  echo "Repository check failed: .DS_Store files were found."
  exit 1
fi

if [[ -d .github/modernize ]]; then
  echo "Repository check failed: local modernization tool state must stay outside the repository."
  exit 1
fi

echo "Repository structure check passed."
