const { AsyncClient, SocketConnection } = require("..");
const RedisServer = require("redis-server");
const FreePort = require("find-free-port");
const { v4: uuidv4 } = require("uuid");
const net = require("net")
const SOCKET_FILE_PATH = "babushka-socket";

function onClientConnected(sock) {  
    var remoteAddress = sock.remoteAddress + ':' + sock.remotePort;
    console.log('new client connected: %s', remoteAddress);
   
    sock.on('data', function(data) {
      console.log('%s Says: %s', remoteAddress, data);
      sock.write(data);
      sock.write(' exit');
    });
    sock.on('close',  function () {
      console.log('connection from %s closed', remoteAddress);
    });
    sock.on('error', function (err) {
      console.log('Connection %s error: %s', remoteAddress, err.message);
    });
  };

function OpenServerAndExecute(port, action) {
    return new Promise((resolve, reject) => {
        var mserver = net.createServer(onClientConnected);  
 
        mserver.listen(SOCKET_FILE_PATH, function() {  
            console.log('server listening on %j', server.address());
        });
        const server = new RedisServer(port);
        server.open(async (err) => {
            if (err) {
                reject(err);
            }
            await action();
            server.close();
            resolve(mserver);
        });
    });
}

async function GetAndSetRandomValue(client) {
    const key = uuidv4();
    // Adding random repetition, to prevent the inputs from always having the same alignment.
    const value = uuidv4() + "0".repeat(Math.random() * 7);
    await client.set(key, value);
    const result = await client.get(key);
    expect(result).toEqual(value);
}

describe("NAPI client", () => {
    it("set and get flow works", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            await GetAndSetRandomValue(client);
        });
    });

    it("can handle non-ASCII unicode", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const key = uuidv4();
            const value = "שלום hello 汉字";
            await client.set(key, value);
            const result = await client.get(key);
            expect(result).toEqual(value);
        });
    });

    it("get for missing key returns null", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const result = await client.get(uuidv4());

            expect(result).toEqual(null);
        });
    });

    it("get for empty string", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const key = uuidv4();
            await client.set(key, "");
            const result = await client.get(key);

            expect(result).toEqual("");
        });
    });

    it("send very large values", async () => {
        const startTime = new Date();
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const WANTED_LENGTH = Math.pow(2, 16);
            const getLongUUID = () => {
                let id = uuidv4();
                while (id.length < WANTED_LENGTH) {
                    id += uuidv4();
                }
                return id;
            };
            let key = getLongUUID();
            let value = getLongUUID();
            await client.set(key, value);
            const result = await client.get(key);

            expect(result).toEqual(value);
        });
    });

    it("can handle concurrent operations", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await AsyncClient.CreateConnection(
                "redis://localhost:" + port
            );

            const singleOp = async (index) => {
                if (index % 2 === 0) {
                    await GetAndSetRandomValue(client);
                } else {
                    var result = await client.get(uuidv4());
                    expect(result).toEqual(null);
                }
            };

            const operations = [];

            for (let i = 0; i < 100; ++i) {
                operations.push(singleOp(i));
            }

            await Promise.all(operations);
        });
    });
});

describe("socket client", () => {
    it("set and get flow works", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                "redis://localhost:" + port
            );

            await GetAndSetRandomValue(client);

            client.dispose();
        });
    });

    it("can handle non-ASCII unicode", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                "redis://localhost:" + port
            );

            const key = uuidv4();
            const value = "שלום hello 汉字";
            await client.set(key, value);
            const result = await client.get(key);
            expect(result).toEqual(value);

            client.dispose();
        });
    });

    it("get for missing key returns null", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                "redis://localhost:" + port
            );

            const result = await client.get(uuidv4());

            expect(result).toEqual(null);

            client.dispose();
        });
    });

    it("get for empty string", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                "redis://localhost:" + port
            );

            const key = uuidv4();
            await client.set(key, "");
            const result = await client.get(key);

            expect(result).toEqual("");

            client.dispose();
        });
    });

    it("send very large values", async () => {
        const startTime = new Date();
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                "redis://localhost:" + port
            );

            const WANTED_LENGTH = Math.pow(2, 16);
            const getLongUUID = () => {
                let id = uuidv4();
                while (id.length < WANTED_LENGTH) {
                    id += uuidv4();
                }
                return id;
            };
            let key = getLongUUID();
            let value = getLongUUID();
            await client.set(key, value);
            const result = await client.get(key);

            expect(result).toEqual(value);

            client.dispose();
        });
    });

    it("can handle concurrent operations", async () => {
        const port = await FreePort(3000).then(([free_port]) => free_port);
        await OpenServerAndExecute(port, async () => {
            const client = await SocketConnection.CreateConnection(
                "redis://localhost:" + port
            );

            const singleOp = async (index) => {
                if (index % 2 === 0) {
                    await GetAndSetRandomValue(client);
                } else {
                    var result = await client.get(uuidv4());
                    expect(result).toEqual(null);
                }
            };

            const operations = [];

            for (let i = 0; i < 100; ++i) {
                operations.push(singleOp(i));
            }

            await Promise.all(operations);

            client.dispose();
        });
    });
});
