const redis = require("redis");
const commandLineArgs = require("command-line-args");

function getAddress(host) {
    const PORT = 6379;
    return `redis://${host}:${PORT}`;
}

async function flush_database(address) {
    const client = redis.createClient({ url: address });
    await client.connect();
    await client.flushAll();
}

const optionDefinitions = [{ name: "host", type: String }];
const receivedOptions = commandLineArgs(optionDefinitions);

Promise.resolve()
    .then(async () => {
        const address = getAddress(receivedOptions.host);
        console.log("Flushing " + address);
        await flush_database(address);
    })
    .then(() => {
        process.exit(0);
    });
