const redis = require("redis");
const commandLineArgs = require("command-line-args");

const HOST = "localhost";
const PORT = 6379;
const ADDRESS = `redis://${HOST}:${PORT}`;
const SIZE_SET_KEYSPACE = 3000000; // 3 million

function generate_value(size) {
    return "0".repeat(size);
}

async function fill_database(data_size) {
    const client = redis.createClient({ url: ADDRESS });
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

const optionDefinitions = [{ name: "dataSize", type: String }];
const receivedOptions = commandLineArgs(optionDefinitions);

Promise.resolve()
    .then(async () => {
        await fill_database(receivedOptions.dataSize);
    })
    .then(() => {
        process.exit(0);
    });
