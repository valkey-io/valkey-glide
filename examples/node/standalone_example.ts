/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    ClosingError,
    ConnectionError,
    GlideClient,
    Logger,
    RequestError,
    TimeoutError,
} from "@valkey/valkey-glide";

/**
 * Creates and returns a GlideClient instance.
 * This function initializes a GlideClient with the provided list of nodes.
 * The nodes_list may contain either only primary node or a mix of primary
 * and replica nodes. The GlideClient use these nodes to connect to
 * the Standalone setup servers.
 * @param nodesList A list of tuples where each tuple contains a host (str) and port (int). Defaults to [("localhost", 6379)].
 * @returns An instance of GlideClient connected to the discovered nodes.
 */
async function createClient(nodesList = [{ host: "localhost", port: 6379 }]) {
    const addresses = nodesList.map((node) => ({
        host: node.host,
        port: node.port,
    }));

    // Check `GlideClientConfiguration` for additional options.
    return await GlideClient.createClient({
        addresses: addresses,
        // if the server uses TLS, you'll need to enable it. Otherwise the connection attempt will time out silently.
        // useTLS: true,
    });
}

/**
 * Executes the main logic of the application, performing basic operations
 * such as SET, GET, PING, and INFO REPLICATION using the provided GlideClient.
 * @param client An instance of GlideClient.
 */
async function appLogic(client: GlideClient) {
    // Send SET and GET
    const setResponse = await client.set("foo", "bar");
    Logger.log("info", "app", `Set response is: ${setResponse}`);

    const getResponse = await client.get("foo");
    Logger.log("info", "app", `Get response is: ${getResponse?.toString()}`);

    // Send PING to primary
    const pong = await client.ping();
    Logger.log("info", "app", `PING response: ${pong}`);
}

/**
 * Executes the application logic with exception handling.
 */
async function execAppLogic() {
    // Loop through with exception handling
    while (true) {
        let client;

        try {
            client = await createClient();
            return await appLogic(client);
        } catch (error) {
            switch (true) {
                case error instanceof ClosingError:
                    // If the error message contains "NOAUTH", raise the exception
                    // because it indicates a critical authentication issue.
                    if ((error as ClosingError).message.includes("NOAUTH")) {
                        Logger.log(
                            "error",
                            "glide",
                            `Authentication error encountered: ${error}`,
                        );
                    } else {
                        Logger.log(
                            "warn",
                            "glide",
                            `Client has closed and needs to be re-created: ${error}`,
                        );
                    }

                    throw error;
                case error instanceof TimeoutError:
                    // A request timed out. You may choose to retry the execution based on your application's logic
                    Logger.log("error", "glide", `Timeout error: ${error}`);
                    throw error;
                case error instanceof ConnectionError:
                    // The client wasn't able to reestablish the connection within the given retries
                    Logger.log("error", "glide", `Connection error: ${error}`);
                    throw error;
                case error instanceof RequestError:
                    // Other error reported during a request, such as a server response error
                    Logger.log(
                        "error",
                        "glide",
                        `RequestError encountered: ${error}`,
                    );
                    throw error;
                default:
                    Logger.log("error", "glide", `Unexpected error: ${error}`);
                    throw error;
            }
        } finally {
            try {
                if (client) {
                    await client.close();
                }
            } catch (error) {
                Logger.log(
                    "warn",
                    "glide",
                    `Error encountered while closing the client: ${error}`,
                );
            }
        }
    }
}

function main() {
    // In this example, we will utilize the client's logger for all log messages
    Logger.setLoggerConfig("info");
    // Optional - set the logger to write to a file
    // Logger.setLoggerConfig("info", fileName);
    execAppLogic();
}

main();
