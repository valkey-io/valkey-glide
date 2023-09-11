import { beforeAll, describe, expect, it } from "@jest/globals";
import { MAX_REQUEST_ARGS_LEN, createLeakedValue } from "babushka-rs-internal";
import fs from "fs";
import net from "net";
import os from "os";
import path from "path";
import { Reader } from "protobufjs";
import {
    ClosingError,
    ConnectionOptions,
    RedisClient,
    RedisClusterClient,
    RequestError,
    TimeoutError,
    setLoggerConfig,
} from "../build-ts";
import {
    connection_request,
    redis_request,
    response,
} from "../src/ProtobufMessage";

const { RequestType, RedisRequest } = redis_request;

beforeAll(() => {
    setLoggerConfig("info");
});

enum ResponseType {
    /** Type of a response that returns a null. */
    Null,
    /** Type of a response that returns a value which isn't an error. */
    Value,
    /** Type of response containing an error that impacts a single request. */
    RequestError,
    /** Type of response containing an error causes the connection to close. */
    ClosingError,
    /** Type of response containing the string "OK". */
    OK,
}

function sendResponse(
    socket: net.Socket,
    responseType: ResponseType,
    callbackIndex: number,
    message?: string,
    requestErrorType?: response.RequestErrorType
) {
    const new_response = response.Response.create();
    new_response.callbackIdx = callbackIndex;
    if (responseType == ResponseType.Value) {
        const pointer = createLeakedValue(message!);
        const pointer_number = Number(pointer.toString());
        new_response.respPointer = pointer_number;
    } else if (responseType == ResponseType.ClosingError) {
        new_response.closingError = message;
    } else if (responseType == ResponseType.RequestError) {
        new_response.requestError = new response.RequestError({
            type: requestErrorType,
            message,
        });
    } else if (responseType == ResponseType.Null) {
        // do nothing
    } else if (responseType == ResponseType.OK) {
        new_response.constantResponse = response.ConstantResponse.OK;
    } else {
        throw new Error("Got unknown response type: " + responseType);
    }
    const response_bytes =
        response.Response.encodeDelimited(new_response).finish();
    socket.write(response_bytes);
}

function getConnectionAndSocket(
    checkRequest?: (request: connection_request.ConnectionRequest) => boolean,
    connectionOptions?: ConnectionOptions,
    isCluster?: boolean
): Promise<{
    socket: net.Socket;
    connection: RedisClient | RedisClusterClient;
    server: net.Server;
}> {
    return new Promise((resolve, reject) => {
        const temporaryFolder = fs.mkdtempSync(
            path.join(os.tmpdir(), `socket_listener`)
        );
        const socketName = path.join(temporaryFolder, "read");
        let connectionPromise:
            | Promise<RedisClient | RedisClusterClient>
            | undefined = undefined;
        const server = net
            .createServer(async (socket) => {
                socket.once("data", (data) => {
                    const reader = Reader.create(data);
                    const request =
                        connection_request.ConnectionRequest.decodeDelimited(
                            reader
                        );
                    if (checkRequest && !checkRequest(request)) {
                        reject(
                            `${JSON.stringify(request)}  did not pass condition`
                        );
                    }
                    sendResponse(socket, ResponseType.Null, 0);
                });

                const connection = await connectionPromise!;
                resolve({
                    connection,
                    socket,
                    server,
                });
            })
            .listen(socketName);
        connectionPromise = new Promise((resolve) => {
            const socket = new net.Socket();
            socket.connect(socketName).once("connect", async () => {
                const options = connectionOptions ?? {
                    addresses: [{ host: "foo" }],
                };
                const connection = isCluster
                    ? await RedisClusterClient.__createClient(options, socket)
                    : await RedisClient.__createClient(options, socket);

                resolve(connection);
            });
        });
    });
}

function closeTestResources(
    connection: RedisClient | RedisClusterClient,
    server: net.Server,
    socket: net.Socket
) {
    connection.dispose();
    server.close();
    socket.end();
}

async function testWithResources(
    testFunction: (
        connection: RedisClient | RedisClusterClient,
        socket: net.Socket
    ) => Promise<void>,
    connectionOptions?: ConnectionOptions
) {
    const { connection, server, socket } = await getConnectionAndSocket(
        undefined,
        connectionOptions
    );

    await testFunction(connection, socket);

    closeTestResources(connection, server, socket);
}

async function testWithClusterResources(
    testFunction: (
        connection: RedisClusterClient,
        socket: net.Socket
    ) => Promise<void>,
    connectionOptions?: ConnectionOptions
) {
    const { connection, server, socket } = await getConnectionAndSocket(
        undefined,
        connectionOptions,
        true
    );

    try {
        if (connection instanceof RedisClusterClient) {
            await testFunction(connection, socket);
        } else {
            throw new Error("Not cluster connection");
        }
    } finally {
        closeTestResources(connection, server, socket);
    }
}

describe("SocketConnectionInternals", () => {
    it("Test setup returns values", async () => {
        await testWithResources((connection, socket) => {
            expect(connection).toEqual(expect.anything());
            expect(socket).toEqual(expect.anything());
            return Promise.resolve();
        });
    });

    it("should close socket on dispose", async () => {
        await testWithResources(async (connection, socket) => {
            const endReceived = new Promise((resolve) => {
                socket.once("end", () => resolve(true));
            });
            connection.dispose();

            expect(await endReceived).toBeTruthy();
        });
    });

    it("should pass values received from socket", async () => {
        await testWithResources(async (connection, socket) => {
            const expected = "bar";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1
                );

                sendResponse(
                    socket,
                    ResponseType.Value,
                    request.callbackIdx,
                    expected
                );
            });
            const result = await connection.get("foo");
            expect(result).toEqual(expected);
        });
    });

    it("should pass null returned from socket", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1
                );

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const result = await connection.get("foo");
            expect(result).toBeNull();
        });
    });

    it("should pass OK returned from socket", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.SetString
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    2
                );

                sendResponse(socket, ResponseType.OK, request.callbackIdx);
            });
            const result = await connection.set("foo", "bar");
            expect(result).toEqual("OK");
        });
    });

    it("should reject requests that received a response error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1
                );
                sendResponse(
                    socket,
                    ResponseType.RequestError,
                    request.callbackIdx,
                    error
                );
            });
            const request = connection.get("foo");

            await expect(request).rejects.toEqual(new RequestError(error));
        });
    });

    it("should close all requests when receiving a closing error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                expect(request.singleCommand?.argsArray?.args?.length).toEqual(
                    1
                );
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    request.callbackIdx,
                    error
                );
            });
            const request1 = connection.get("foo");
            const request2 = connection.get("bar");

            await expect(request1).rejects.toEqual(new ClosingError(error));
            await expect(request2).rejects.toEqual(new ClosingError(error));
        });
    });

    it("should handle receiving a closing error with an unknown callback index", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    Number.MAX_SAFE_INTEGER,
                    error
                );
            });
            const request1 = connection.get("foo");
            const request2 = connection.get("bar");

            await expect(request1).rejects.toEqual(new ClosingError(error));
            await expect(request2).rejects.toEqual(new ClosingError(error));
        });
    });

    it("should pass SET arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.SetString
                );
                const args = request.singleCommand?.argsArray?.args;
                if (!args) {
                    throw new Error("no args");
                }
                expect(args.length).toEqual(5);
                expect(args[0]).toEqual("foo");
                expect(args[1]).toEqual("bar");
                expect(args[2]).toEqual("XX");
                expect(args[3]).toEqual("GET");
                expect(args[4]).toEqual("EX 10");
                sendResponse(socket, ResponseType.OK, request.callbackIdx);
            });
            const request1 = connection.set("foo", "bar", {
                conditionalSet: "onlyIfExists",
                returnOldValue: true,
                expiry: { type: "seconds", count: 10 },
            });

            await expect(await request1).toMatch("OK");
        });
    });

    it("should send pointer for request with large size arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                expect(request.singleCommand?.argsVecPointer).not.toBeNull();
                expect(request.singleCommand?.argsArray).toBeNull();

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const key = "0".repeat(MAX_REQUEST_ARGS_LEN);
            const result = await connection.get(key);

            expect(result).toBeNull();
        });
    });

    it("should send vector of strings for request with small size arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.GetString
                );
                expect(request.singleCommand?.argsArray).not.toBeNull();
                expect(request.singleCommand?.argsVecPointer).toBeNull();

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const key = "0".repeat(MAX_REQUEST_ARGS_LEN - 1);
            const result = await connection.get(key);

            expect(result).toBeNull();
        });
    });

    it("should pass credentials on connection", async () => {
        const username = "this is a username";
        const password = "more like losername, amiright?";
        const { connection, server, socket } = await getConnectionAndSocket(
            (request: connection_request.ConnectionRequest) =>
                request.authenticationInfo?.password === password &&
                request.authenticationInfo?.username === username,
            {
                addresses: [{ host: "foo" }],
                credentials: { username, password },
            }
        );
        closeTestResources(connection, server, socket);
    });

    it("should timeout before receiving response from core", async () => {
        await testWithResources(
            async (connection, socket) => {
                socket.once("data", (data) =>
                    setTimeout(() => {
                        const reader = Reader.create(data);
                        const request = RedisRequest.decodeDelimited(reader);
                        expect(request.singleCommand?.requestType).toEqual(
                            RequestType.GetString
                        );
                        expect(
                            request.singleCommand?.argsArray?.args?.length
                        ).toEqual(1);
                    }, 20)
                );
                await expect(connection.get("foo")).rejects.toThrow(
                    TimeoutError
                );
            },
            {
                addresses: [{ host: "foo" }],
                responseTimeout: 1,
            }
        );
    });

    it("should pass routing information from user", async () => {
        const route1 = "allPrimaries";
        const route2 = {
            type: "replicaSlotKey" as const,
            key: "foo",
        };
        await testWithClusterResources(async (connection, socket) => {
            socket.on("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.singleCommand?.requestType).toEqual(
                    RequestType.CustomCommand
                );
                if (request.singleCommand?.argsArray?.args?.at(0) === "SET") {
                    expect(request.route?.simpleRoutes).toEqual(
                        redis_request.SimpleRoutes.AllPrimaries
                    );
                } else if (
                    request.singleCommand?.argsArray?.args?.at(0) === "GET"
                ) {
                    expect(request.route?.slotKeyRoute).toEqual({
                        slotType: redis_request.SlotTypes.Replica,
                        slotKey: "foo",
                    });
                } else {
                    throw new Error("unexpected command");
                }

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const result1 = await connection.customCommand(
                "SET",
                ["foo", "bar"],
                route1
            );
            expect(result1).toBeNull();

            const result2 = await connection.customCommand(
                "GET",
                ["foo"],
                route2
            );
            expect(result2).toBeNull();
        });
    });
});
