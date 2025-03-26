/* eslint @typescript-eslint/no-require-imports: off */
/* eslint no-undef: off */
"use strict";
const util = require("util");
const { exec, spawn } = require("child_process");
const execAsync = util.promisify(exec);

/**
 * Checks which Valkey-compatible server is available
 * @returns {Promise<string>} The server command to use
 */
async function checkWhichCommandAvailable() {
    try {
        const valkeyResponse = await execAsync("which valkey-server");

        if (valkeyResponse.stdout && valkeyResponse.stdout.trim() !== "") {
            return "valkey-server";
        }

        console.log("Valkey response", valkeyResponse);
    } catch (error) {
        console.log("Valkey response", error);
        // Ignore error
    }

    try {
        const redisResponse = await execAsync("which redis-server");

        if (redisResponse.stdout && redisResponse.stdout.trim() !== "") {
            return "redis-server";
        }

        console.log("Redis response", redisResponse);
    } catch (error) {
        // Ignore error
        console.log("Redis response", error);
    }

    throw new Error("Neither valkey-server nor redis-server found in path");
}

/**
 * Starts a Valkey/Redis OSS server
 * @param {string} serverCmd - The server command to run
 * @param {number} port - The port to run on
 * @returns {Promise<Object>} The server process
 */
function startServer(serverCmd, port) {
    return new Promise((resolve, reject) => {
        // nosemgrep
        const serverProcess = spawn(serverCmd, ["--port", port.toString()], {
            stdio: ["ignore", "pipe", "pipe"],
        });
        let output = "";

        serverProcess.stdout.on("data", (data) => {
            output += data.toString();

            if (output.includes("Ready to accept connections")) {
                console.log("Server started successfully");
                resolve(serverProcess);
            }
        });

        serverProcess.stderr.on("data", (data) => {
            console.error(`Server stderr: ${data}`);
        });

        serverProcess.on("error", (error) => {
            reject(new Error(`Failed to start server: ${error.message}`));
        });

        // Set a timeout in case the server doesn't start properly
        setTimeout(() => {
            if (!output.includes("Ready to accept connections")) {
                serverProcess.kill();
                reject(
                    new Error("Server failed to start within timeout period"),
                );
            }
        }, 5000);
    });
}

module.exports = {
    checkWhichCommandAvailable,
    startServer,
};
