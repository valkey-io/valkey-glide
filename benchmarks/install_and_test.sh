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
HERE=`dirname ${script}`
BENCH_FOLDER=`realpath ${HERE}`
PYTHON_FOLDER="${BENCH_FOLDER}/../python"
runPythonBenchmark
