# Contributing Guidelines

Thank you for your interest in contributing to our project. Whether it's a bug report, new feature, correction, or additional
documentation, we greatly value feedback and contributions from our community.

Please read through this document before submitting any issues or pull requests to ensure we have all the necessary
information to effectively respond to your bug report or contribution.


## Reporting Bugs/Feature Requests

We welcome you to use the GitHub issue tracker to report bugs or suggest features.

When filing an issue, please check existing open, or recently closed, issues to make sure somebody else hasn't already
reported the issue. Please try to include as much information as you can. Details like these are incredibly useful:

* A reproducible test case or series of steps
* The version of our code being used
* Operating system
* Redis version
* Redis cluster information, cluster topology, number of shards, number of replicas, used data types 
* Any modifications you've made relevant to the bug
* Anything unusual about your environment or deployment
* logs

## Contributing via Pull Requests
Contributions via pull requests are much appreciated. Before sending us a pull request, please ensure that:

1. You are working against the latest source on the *main* branch.
2. You check existing open, and recently merged, pull requests to make sure someone else hasn't addressed the problem already.
3. You open an issue to discuss any significant work - we would hate for your time to be wasted.

To send us a pull request, please:

1. Fork the repository.
2. Modify the source; please focus on the specific change you are contributing. If you also reformat all the code, it will be hard for us to focus on your change.
3. Ensure local tests pass.
4. Commit to your fork using clear commit messages.
5. Send us a pull request, answering any default questions in the pull request interface.
6. Pay attention to any automated CI failures reported in the pull request, and stay involved in the conversation.

GitHub provides additional document on [forking a repository](https://help.github.com/articles/fork-a-repo/) and
[creating a pull request](https://help.github.com/articles/creating-a-pull-request/).


## Finding contributions to work on
Looking at the existing issues is a great way to find something to contribute on. As our projects, by default, use the default GitHub issue labels (enhancement/bug/duplicate/help wanted/invalid/question/wontfix), looking at any 'help wanted' issues is a great place to start.


## Code of Conduct
This project has adopted the [Amazon Open Source Code of Conduct](https://aws.github.io/code-of-conduct).
For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq) or contact
opensource-codeofconduct@amazon.com with any additional questions or comments.


## Security issue notifications
If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public github issue.


## Licensing

See the [LICENSE](LICENSE) file for our project's licensing. We will ask you to confirm the licensing of your contribution.


## Folder structure

* babushka-core - [the Rust core](./babushka-core/README.md). This code is compiled into binaries which are used by the various wrappers.
* benchmarks - [benchmarks for the wrappers](./benchmarks/README.md), and for the current leading Redis client in each language.
* csharp - [the C# wrapper](./csharp/README.md). currently not in a usable state.
* examples - [Sample applications](./examples/), in various languages, demonstrating usage of the client.
* logger_core - [the logger we use](./logger_core/).
* node - [the NodeJS wrapper](./node/README.md).
* python - [the Python3 wrapper](./python/README.md)
* submodules - git submodules.
* utils - Utility scripts used in testing, building, and deploying.

## development pre-requirements

* GCC
* pkg-config
* protobuf-compiler (protoc) >= V3
* openssl
* libssl-dev // for amazon-linux install openssl-devel
* python3

### git submodule

run `git submodule update --init --recursive` on first usage, and on any update to the redis-rs fork.

### rustup

https://rustup.rs/

```
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

after the instalation will show-up in the terminal steps to add rustup to the path - do it.

### redis-server

This is required for running tests and local benchmarks.

https://redis.io/docs/getting-started/

```
sudo yum -y install gcc make wget
cd /usr/local/src
sudo wget http://download.redis.io/releases/redis-{0}.tar.gz
sudo tar xzf redis-{0}.tar.gz
sudo rm redis-{0}.tar.gz
cd redis-{0}
sudo make distclean
sudo make BUILD_TLS=yes
sudo mkdir /etc/redis
sudo cp src/redis-server src/redis-cli /usr/local/bin
```

change {0} to the version you want, e.g. 7.0.12. version names are available here: http://download.redis.io/releases/
recommended version - 7.0.12 ATM

### node 16 (or newer)

This is required for the NodeJS wrapper, and for running benchmarks.

```
curl -s https://deb.nodesource.com/setup_16.x | sudo bash
apt-get install nodejs npm
npm i -g npm@8
```

## Recommended VSCode extensions

[GitLens](https://marketplace.visualstudio.com/items?itemName=eamodio.gitlens)