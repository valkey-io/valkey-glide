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


script=`pwd`/${BASH_SOURCE[0]}
RELATIVE_BENCH_PATH=`dirname ${script}`
export BENCH_FOLDER=`realpath ${RELATIVE_BENCH_PATH}`
export PYTHON_FOLDER="${BENCH_FOLDER}/../python"
export BENCH_RESULTS_FOLDER="${BENCH_FOLDER}/results"
# Create results folder 
mkdir -p $BENCH_RESULTS_FOLDER
runPythonBenchmark
