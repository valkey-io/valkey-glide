### Install node 16 (or newer)
```
curl -s https://deb.nodesource.com/setup_16.x | sudo bash
apt-get install nodejs npm
npm i -g npm@8
```

## Install GLIDE package and build the example
```
cd valkey-glide/examples/node
npm install
npx tsc
```

## Run
To run the example (make sure redis/valkey server is available at the address used in the example):
```
cd valkey-glide/examples/node
node index.js
```
