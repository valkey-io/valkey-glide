#!/bin/bash

#remove comment to print the lines running.
#set -x

set -e

if command -v python3
then
    pythonCommand=python3
else
    if command -v python
    then
        pythonCommand=python
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
chosenClients="all"
host="localhost"

function runPythonBenchmark(){
  cd ${PYTHON_FOLDER}
  $pythonCommand -m venv .env
  source .env/bin/activate
  pip install --upgrade --quiet pip
  pip install --quiet -r requirements.txt
  maturin develop --release
  echo "Starting Python benchmarks"
  cd ${BENCH_FOLDER}/python 
  pip install --quiet -r requirements.txt
  python python_benchmark.py --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host
  # exit python virtualenv
  deactivate
  echo "done python benchmark"
}

function runNodeBenchmark(){
  cd ${BENCH_FOLDER}/../node
  npm install
  rm -rf build-ts
  npm run build
  cd ${BENCH_FOLDER}/node
  npm install
  npx tsc
  npm run bench -- --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host
}

function runCSharpBenchmark(){
  cd ${BENCH_FOLDER}/csharp
  dotnet clean
  dotnet build
  dotnet run --property:Configuration=Release --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host
}

function flushDB() {
  cd $utilitiesDir
  yarn install
  yarn run flush --host $host
}

function fillDB(){
  flushDB
  cd $utilitiesDir
  yarn run fill --dataSize $1 --host $host
}

utilitiesDir=`pwd`/utilities
script=`pwd`/${BASH_SOURCE[0]}
RELATIVE_BENCH_PATH=`dirname ${script}`
export BENCH_FOLDER=`realpath ${RELATIVE_BENCH_PATH}`
export PYTHON_FOLDER="${BENCH_FOLDER}/../python"
export BENCH_RESULTS_FOLDER="${BENCH_FOLDER}/results"
identifier=$(date +"%F")-$(date +"%H")-$(date +"%M")-$(date +"%S")
# Create results folder 
mkdir -p $BENCH_RESULTS_FOLDER

function resultFileName() {
    echo results/$namePrefix$1-$2-$identifier.json
}

function Help() {
    echo Running the script without any arguments runs all benchmarks.
    echo Pass -node, -csharp, -python as arguments in order to run the node, csharp, or python benchmarks accordingly.
    echo Multiple such flags can be passed.
    echo Pass -no-csv to skip analysis of the results.
    echo
    echo Pass -d and then a space-delimited list of sizes for data.
    echo Pass -f and then a space-delimited list of number of concurrent operations.
    echo Example: passing as options \"-node -tasks 10 100 -data 500 20 -python\" will cause the node and python benchmarks to run, with the following configurations:
    echo "         10 concurrent tasks and 500 bytes of data per value,"
    echo "         10 concurrent tasks and 20 bytes of data per value, "
    echo "         100 concurrent tasks and 500 bytes of data per value, "
    echo "         100 concurrent tasks and 20 bytes of data per value, "
    echo
    echo Pass -only-ffi to only run Babushka FFI based clients.
    echo Pass -only-socket to only run Babushka socket based clients.
    echo Pass -only-babushka to only run Babushk clients.
    echo By default, the benchmark runs against localhost. Pass -host and then the address of the requested Redis server in order to connect to a different server.
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
                dataSize+=$2"  "
                shift
            done
            ;;
        -prefix) # set size of data
            namePrefix=$2-
            shift
            ;;
        -host)
            host=$2
            shift
            ;;
        -tasks) # set number of concurrent tasks
            concurrentTasks=$2" "
            shift
            until [[ $2 =~ ^- ]] || [ -z $2 ]; do
                concurrentTasks+=$2"  "
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
        -only-socket)
            chosenClients="socket"
            ;;
        -only-ffi)
            chosenClients="ffi"
            ;;
        -only-babushka)
            chosenClients="babushka"
            ;;                  
        -no-csv) writeResultsCSV=0 ;;        
    esac
    shift
done

for currentDataSize in $dataSize
do
    fillDB $currentDataSize

    if [ $runAllBenchmarks == 1 ] || [ $runPython == 1 ]; 
    then
        pythonResults=$(resultFileName python $currentDataSize)
        resultFiles+=$pythonResults" "
        runPythonBenchmark $pythonResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runNode == 1 ]; 
    then
        nodeResults=$(resultFileName node $currentDataSize)
        resultFiles+=$nodeResults" "
        runNodeBenchmark $nodeResults $currentDataSize
    fi

    if [ $runAllBenchmarks == 1 ] || [ $runCsharp == 1 ]; 
    then
        csharpResults=$(resultFileName csharp $currentDataSize)
        resultFiles+=$csharpResults" "
        runCSharpBenchmark $csharpResults $currentDataSize
    fi
done



flushDB

if [ $writeResultsCSV == 1 ]; 
then
    cd ${BENCH_FOLDER}
    finalCSV=results/$namePrefix""final-$identifier.csv
    $pythonCommand $utilitiesDir/csv_exporter.py $resultFiles$finalCSV
    echo results are in $finalCSV
fi
