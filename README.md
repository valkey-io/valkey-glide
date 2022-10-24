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
sudo make distclean
sudo mkdir /etc/redis
sudo cp src/redis-server src/redis-cli /usr/local/bin
```

change {0} to the version you want. version names are available here: http://download.redis.io/releases/
recommended version - 6.2.5 ATM
