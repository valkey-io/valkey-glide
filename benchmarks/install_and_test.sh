#!/bin/bash

set -x

function runPythonBenchmark(){
  cd ${PYTHON_FOLDER}
  python -m venv .env
  source .env/bin/activate
  pip install --upgrade --quiet pip
  pip install --quiet -r requirements.txt
  maturin develop
  echo "Starting Python benchmarks"
  python ${BENCH_FOLDER}/python_benchmark.py
  # exit python virtualenv
  deactivate
}

function runNodeBenchmark(){
  cd ${BENCH_FOLDER}/../node
  npm i
  npm run build-internal
  rm -rf build-ts
  npm run build
  cd ${BENCH_FOLDER}/node
  npm i
  npx tsc
  npm run bench -- --resultsFile=../$1
}

function runCSharpBenchmark(){
  cd ${BENCH_FOLDER}/csharp
  dotnet clean
  dotnet build
  dotnet run --property:Configuration=Release --resultsFile=../$1
}


script=`pwd`/${BASH_SOURCE[0]}
RELATIVE_BENCH_PATH=`dirname ${script}`
export BENCH_FOLDER=`realpath ${RELATIVE_BENCH_PATH}`
export PYTHON_FOLDER="${BENCH_FOLDER}/../python"
export BENCH_RESULTS_FOLDER="${BENCH_FOLDER}/results"
identifier=$(date +"%F")-$(date +"%H")-$(date +"%M")-$(date +"%S")
# Create results folder 
mkdir -p $BENCH_RESULTS_FOLDER
runPythonBenchmark

csharpResults=results/csharp-$identifier.json
runCSharpBenchmark $csharpResults

NODE_FOLDER="${BENCH_FOLDER}/../node"
nodeResults=results/node-$identifier.json
runNodeBenchmark $nodeResults
