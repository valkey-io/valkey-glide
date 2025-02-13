/* eslint no-undef: off */
import { spawn } from "child_process";
import findFreePorts from "find-free-ports";
import { GlideClient } from "@valkey/valkey-glide";
import { checkWhichCommandAvailable } from "../../tests/TestUtilities";

const PORT_NUMBER = 4001;

async function main() {
    console.log("Starting main");
    const port = await findFreePorts(PORT_NUMBER).then(
        ([free_port]) => free_port,
    );
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
        throw new Error("Ecma Test failed");
    } else {
        console.log("Ecma Test passed");
    }

    await client.flushall();
    client.close();
    serverProcess.kill();
    console.log("Done");
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
