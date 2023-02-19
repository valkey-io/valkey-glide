import redis from "redis";
import commandLineArgs from "command-line-args";

const SIZE_SET_KEYSPACE = 3000000; // 3 million

function getAddress(host, tls) {
    const PORT = 6379;
    const protocol = tls ? "rediss" : "redis";
    return `${protocol}://${host}:${PORT}`;
}

function generate_value(size) {
    return "0".repeat(size);
}

async function fill_database(data_size, address) {
    const client = redis.createClient({ url: address });
    const data = generate_value(data_size);
    await client.connect();

    const CONCURRENT_SETS = 1000;

    var sets = Array.from(Array(CONCURRENT_SETS).keys()).map(async (index) => {
        for (let i = 0; i < SIZE_SET_KEYSPACE / CONCURRENT_SETS; ++i) {
            var key = (i * CONCURRENT_SETS + index).toString();
            await client.set(key, data);
        }
    });

    await Promise.all(sets);
}

const optionDefinitions = [
    { name: "dataSize", type: String },
    { name: "host", type: String },
    { name: "tls", type: Boolean },
];
const receivedOptions = commandLineArgs(optionDefinitions);

Promise.resolve()
    .then(async () => {
        const address = getAddress(receivedOptions.host, receivedOptions.tls);
        console.log(
            `Filling ${address} with data size ${receivedOptions.dataSize}`
        );
        await fill_database(receivedOptions.dataSize, address);
    })
    .then(() => {
        process.exit(0);
    });
