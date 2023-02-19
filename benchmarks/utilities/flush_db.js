import redis from "redis";
import commandLineArgs from "command-line-args";

function getAddress(host, tls) {
    const PORT = 6379;
    const protocol = tls ? "rediss" : "redis";
    return `${protocol}://${host}:${PORT}`;
}

async function flush_database(address) {
    const client = redis.createClient({ url: address });
    await client.connect();
    await client.flushAll();
}

const optionDefinitions = [
    { name: "host", type: String },
    { name: "tls", type: Boolean },
];
const receivedOptions = commandLineArgs(optionDefinitions);

Promise.resolve()
    .then(async () => {
        const address = getAddress(receivedOptions.host, receivedOptions.tls);
        console.log("Flushing " + address);
        await flush_database(address);
    })
    .then(() => {
        process.exit(0);
    });
