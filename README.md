# babushka

## pre-requirements:

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
npm install --location=global yarn
```

## benchmarks:

If while running benchmarks you're redis-server killed every time the program run the 4000 data-size benchmark it might be because you don't have enough available storage on your machine.
For solving this issue, you have two option -

1. Allocate more storage to your'e machine. for me the case was allocating from 500 gb to 1000 gb.
2. Go to benchmarks/install_and_test.sh and change the "dataSize="100 4000"" to data-size that your machine can handle. try for example dataSize="100 1000".
