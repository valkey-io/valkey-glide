const redis = require("redis");

const HOST = "localhost";
const PORT = 6379;
const ADDRESS = `redis://${HOST}:${PORT}`;

async function flush_database() {
    const client = redis.createClient({ url: ADDRESS });
    await client.connect();
    await client.flushAll();
}

Promise.resolve()
    .then(async () => {
        await flush_database();
    })
    .then(() => {
        process.exit(0);
    });
