#!/bin/zsh
set -euo pipefail

project_dir="${0:A:h:h}"
cd "$project_dir"

mkdir -p out
find src/main/java -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -d out

if (( $# == 0 )); then
  echo "未提供参数，执行一次快速演示实验。"
  set -- benchmark gcut01 results/demo 1 20261012 QUICK
fi

java -cp out com.assessment.packing.experiment.ExperimentMain "$@"
