# babushka

## pre-requirements:

### git submodule -

run `git submodule update --init --recursive` on first usage, and on any update to the redis-rs fork.

### rustup -

https://rustup.rs/

```
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

after the instalation will show-up in the terminal steps to add rustup to the path - do it.

### redis-server

https://redis.io/docs/getting-started/

```
sudo yum -y install gcc make wget
cd /usr/local/src
sudo wget http://download.redis.io/releases/{0}.tar.gz
sudo tar xzf {0}.tar.gz
sudo rm {0}.tar.gz
cd {0}
sudo make distclean
sudo make
sudo mkdir /etc/redis
sudo cp src/redis-server src/redis-cli /usr/local/bin
```

change {0} to the version you want. version names are available here: http://download.redis.io/releases/
recommended version - 6.2.5 ATM

### node 16 (or newer)

```
curl -s https://deb.nodesource.com/setup_16.x | sudo bash
apt-get install nodejs npm
npm i -g npm@8
```

## benchmarks:

`benchmarks/install_and_test.sh` is the benchmark script we use to check performance. run `install_and_test.sh -h` to get the full list of available flags.

If while running benchmarks your redis-server is killed every time the program runs the 4000 data-size benchmark, it might be because you don't have enough available storage on your machine.
To solve this issue, you have two options -

1. Allocate more storage to your'e machine. for me the case was allocating from 500 gb to 1000 gb.
2. Go to benchmarks/install_and_test.sh and change the "dataSize="100 4000"" to a data-size that your machine can handle. try for example dataSize="100 1000".

## Additional packages

For python, run `sudo apt install python3.10-venv` (with the relevant python version), in order to be able to create a virtual environment.
In order to run rust-analyzer through remote VScode, you need to run `sudo apt install pkg-config libssl-dev`.

## Recommended VSCode extensions

### General

[GitLens](https://marketplace.visualstudio.com/items?itemName=eamodio.gitlens)

### Rust development

[rust-analyzer](https://marketplace.visualstudio.com/items?itemName=rust-lang.rust-analyzer) - Rust language server.
[CodeLLDB](https://marketplace.visualstudio.com/items?itemName=vadimcn.vscode-lldb) - Debugger.
[Even Better TOML](https://marketplace.visualstudio.com/items?itemName=tamasfe.even-better-toml) - TOML language support.

### C# development

[C#](https://marketplace.visualstudio.com/items?itemName=ms-dotnettools.csharp) - lightweight language server and in-editor test runner.

### TypeScript development

[Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode) -JavaScript / TypeScript formatter.
[Jest Runner](https://marketplace.visualstudio.com/items?itemName=firsttris.vscode-jest-runner) - in-editor test runner.
[Jest Test Explorer](https://marketplace.visualstudio.com/items?itemName=kavod-io.vscode-jest-test-adapter) - adapter to the VSCode testing UI.
[ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint) - linter.

### Python development

[Python](https://marketplace.visualstudio.com/items?itemName=ms-python.python) - Language server
[Black Formetter](https://marketplace.visualstudio.com/items?itemName=ms-python.black-formatter) - Formatter
