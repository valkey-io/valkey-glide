#!/bin/bash

#remove comment to print the lines running.
#set -x

if command -v python
then
    pythonCommand=python
else
    if command -v python3
    then
        pythonCommand=python3
    else
        echo "python / python3 could not be found"
        exit
    fi
fi

resultFiles=
writeResultsCSV=1
runAllBenchmarks=1
runPython=0
runNode=0
runCsharp=0
concurrentTasks="1 10 100 1000"
dataSize="100 4000"

function runPythonBenchmark(){
  cd ${PYTHON_FOLDER}
  $pythonCommand -m venv .env
  source .env/bin/activate
  pip install --upgrade --quiet pip
  pip install --quiet -r requirements.txt
  maturin develop
  echo "Starting Python benchmarks"
  cd ${BENCH_FOLDER}/python 
  pip install --quiet -r requirements.txt
  python python_benchmark.py --resultsFile=../$1 --dataSize $dataSize --concurrentTasks $concurrentTasks
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
  npm run bench -- --resultsFile=../$1 --dataSize $dataSize --concurrentTasks $concurrentTasks
}

function runCSharpBenchmark(){
  cd ${BENCH_FOLDER}/csharp
  dotnet clean
  dotnet build
  dotnet run --property:Configuration=Release --resultsFile=../$1 --dataSize $dataSize --concurrentTasks $concurrentTasks
}

script=`pwd`/${BASH_SOURCE[0]}
RELATIVE_BENCH_PATH=`dirname ${script}`
export BENCH_FOLDER=`realpath ${RELATIVE_BENCH_PATH}`
export PYTHON_FOLDER="${BENCH_FOLDER}/../python"
export BENCH_RESULTS_FOLDER="${BENCH_FOLDER}/results"
identifier=$(date +"%F")-$(date +"%H")-$(date +"%M")-$(date +"%S")
# Create results folder 
mkdir -p $BENCH_RESULTS_FOLDER

function Help() {
    echo Running the script without any arguments runs all benchmarks.
    echo Pass -node, -csharp, -python as arguments in order to run the node, csharp, or python benchmarks accordingly.
    echo Multiple such flags can be passed.
    echo Pass -no-csv to skip analysis of the results.
    echo Pass -d and then a space-delimited list of sizes for data.
    echo Pass -f and then a space-delimited list of number of concurrent operations.
    echo Example: passing as options \"-node -tasks 10 100 -data 500 20 -python\" will cause the node and python benchmarks to run, with the following configurations:
    echo "         10 concurrent tasks and 500 bytes of data per value,"
    echo "         10 concurrent tasks and 20 bytes of data per value, "
    echo "         100 concurrent tasks and 500 bytes of data per value, "
    echo "         100 concurrent tasks and 20 bytes of data per value, "
}

while test $# -gt 0
do
    case "$1" in
        -h) #print help message.
            Help
            exit
            ;;
        -data) # set size of data
            dataSize=$2" "
            shift
            until [[ $2 =~ ^- ]] || [ -z $2 ]; do
                dataSize+=$2
                shift
            done
            ;;
        -tasks) # set number of concurrent tasks
            concurrentTasks=$2" "
            shift
            until [[ $2 =~ ^- ]] || [ -z $2 ]; do
                concurrentTasks+=$2" "
                shift
            done
            ;;
        -python)  
            runAllBenchmarks=0
            runPython=1 
            ;;
        -node) 
            runAllBenchmarks=0
            runNode=1 
            ;;
        -csharp)
            runAllBenchmarks=0
            runCsharp=1
            ;;
        -no-csv) writeResultsCSV=0 ;;        
    esac
    shift
done

if [ $runAllBenchmarks == 1 ] || [ $runPython == 1 ]; 
then
    pythonResults=results/python-$identifier.json
    resultFiles+=$pythonResults" "
    runPythonBenchmark $pythonResults
fi

if [ $runAllBenchmarks == 1 ] || [ $runNode == 1 ]; 
then
    nodeResults=results/node-$identifier.json
    resultFiles+=$nodeResults" "
    runNodeBenchmark $nodeResults
fi

if [ $runAllBenchmarks == 1 ] || [ $runCsharp == 1 ]; 
then
    csharpResults=results/csharp-$identifier.json
    resultFiles+=$csharpResults" "
    runCSharpBenchmark $csharpResults   
fi

if [ $writeResultsCSV == 1 ]; 
then
    cd ${BENCH_FOLDER}
    finalCSV=results/final-$identifier.csv
    $pythonCommand csv_exporter.py $resultFiles$finalCSV
fi
