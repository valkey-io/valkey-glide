/* eslint no-undef: off */
/* eslint @typescript-eslint/no-require-imports: off */
const { GlideClient } = require("@valkey/valkey-glide");
const FreePort = require("find-free-port");
const { checkWhichCommandAvailable } = require("../../tests/TestUtilities");

const PORT_NUMBER = 4001;

async function main() {
    console.log("Starting main");
    const port = await FreePort(PORT_NUMBER).then(([free_port]) => free_port);
    const server = await checkWhichCommandAvailable(
        "valkey-server",
        "redis-server",
    );

    const serverProcess = spawn(server, ["--port", port.toString()], {
        stdio: ["ignore", "pipe", "pipe"],
    });

    await new Promise((resolve) => {
        serverProcess.stdout.on("data", (data) => {
            console.log(`${data}`);

            if (data.toString().includes("Ready to accept connections")) {
                resolve();
            }
        });

        serverProcess.stderr.on("data", (data) => {
            console.error(`${data}`);
        });
    });

    const client = await GlideClient.createClient({
        addresses: [{ host: "localhost", port }],
    });
    const setResult = await client.set("test", "test");
    console.log(setResult);
    let getResult = await client.get("test");
    console.log(getResult);

    if (getResult !== "test") {
        throw new Error("Common Test failed");
    } else {
        console.log("Common Test passed");
    }

    await client.flushall();
    client.close();
    serverProcess.kill();
}

main().then(() => {
    console.log("Done");
    process.exit(0);
});
