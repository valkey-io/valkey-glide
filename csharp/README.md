# Babushka - C#

### Install .NET on ubuntu
Follow this guide: https://learn.microsoft.com/en-us/dotnet/core/install/linux-scripted-manual#scripted-install 
1. Download the install script: ```wget https://dot.net/v1/dotnet-install.sh```
2. Add executable permissions: ```sudo chmod +x ./dotnet-install.sh```
3. For this project we're currently using .NET 6.0, so run the following command: ```./dotnet-install.sh --channel 6.0```
4. Add ```dotnet``` to your PATH: add the following line to your ~/.bashrc file: ```export PATH=/home/ubuntu/.dotnet/:$PATH```, and run ```source ~/.bashrc``` to load it

### Run the benchmark with C# debug symbols
Add COMPlus_PerfMapEnabled=1 to the test run, e.g.:
```COMPlus_PerfMapEnabled=1 dotnet run --property:Configuration=Release --resultsFile=../$1 --dataSize $2 --concurrentTasks $concurrentTasks --clients $chosenClients --host $host```

### Run specific tests
from csharp folder, run: ```dotnet test --filter "FullyQualifiedName=<namespace>.<test class>.<test function>"```
For example:
```dotnet test --filter "FullyQualifiedName=tests.AsyncSocketClientTests.ConcurrentOperationsWork"```
