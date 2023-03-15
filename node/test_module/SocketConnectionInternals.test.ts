import { SocketConnection, BabushkaInternal, setLoggerConfig } from "..";
import net from "net";
import fs from "fs";
import os from "os";
import path from "path";
import {
    redis_request,
    connection_request,
    response,
} from "../src/ProtobufMessage";
import { Reader } from "protobufjs";
import { describe, expect, beforeAll, it } from "@jest/globals";

const { createLeakedValue, MAX_REQUEST_ARGS_LEN } = BabushkaInternal;

const { RequestType, RedisRequest } = redis_request;

beforeAll(() => {
    setLoggerConfig("info");
});

// TODO: use TS enums when tests are in TS.
enum ResponseType {
    /** Type of a response that returns a null. */
    Null = 0,
    /** Type of a response that returns a value which isn't an error. */
    Value = 1,
    /** Type of response containing an error that impacts a single request. */
    RequestError = 2,
    /** Type of response containing an error causes the connection to close. */
    ClosingError = 3,
    /** Type of response containing the string "OK". */
    OK = 4,
}

function sendResponse(
    socket: net.Socket,
    responseType: ResponseType,
    callbackIndex: number,
    message?: string
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
        new_response.requestError = message;
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

function getConnectionAndSocket(): Promise<{
    socket: net.Socket;
    connection: SocketConnection;
    server: net.Server;
}> {
    return new Promise((resolve) => {
        const temporaryFolder = fs.mkdtempSync(
            path.join(os.tmpdir(), `socket_listener`)
        );
        const socketName = path.join(temporaryFolder, "read");
        let connectionPromise: Promise<SocketConnection> | undefined =
            undefined;
        const server = net
            .createServer(async (socket) => {
                socket.once("data", (data) => {
                    const reader = Reader.create(data);
                    const request =
                        connection_request.ConnectionRequest.decodeDelimited(
                            reader
                        );

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
                const connection = await SocketConnection.__CreateConnection(
                    {
                        addresses: [{ host: "foo" }],
                    },
                    socket
                );
                resolve(connection);
            });
        });
    });
}

function closeTestResources(
    connection: SocketConnection,
    server: net.Server,
    socket: net.Socket
) {
    connection.dispose();
    server.close();
    socket.end();
}

async function testWithResources(
    testFunction: (
        connection: SocketConnection,
        socket: net.Socket
    ) => Promise<void>
) {
    const { connection, server, socket } = await getConnectionAndSocket();

    await testFunction(connection, socket);

    closeTestResources(connection, server, socket);
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
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.argsArray!.args!.length).toEqual(1);

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
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.argsArray!.args!.length).toEqual(1);

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
                expect(request.requestType).toEqual(RequestType.SetString);
                expect(request.argsArray!.args!.length).toEqual(2);

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
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.argsArray!.args!.length).toEqual(1);
                sendResponse(
                    socket,
                    ResponseType.RequestError,
                    request.callbackIdx,
                    error
                );
            });
            const request = connection.get("foo");

            await expect(request).rejects.toMatch(error);
        });
    });

    it("should close all requests when receiving a closing error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.argsArray!.args!.length).toEqual(1);
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    request.callbackIdx,
                    error
                );
            });
            const request1 = connection.get("foo");
            const request2 = connection.get("bar");

            await expect(request1).rejects.toMatch(error);
            await expect(request2).rejects.toMatch(error);
        });
    });

    it("should handle receiving a closing error with an unknown callback index", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request =
                    redis_request.RedisRequest.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.GetString);
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    Number.MAX_SAFE_INTEGER,
                    error
                );
            });
            const request1 = connection.get("foo");
            const request2 = connection.get("bar");

            await expect(request1).rejects.toMatch(error);
            await expect(request2).rejects.toMatch(error);
        });
    });

    it("should pass SET arguments", async () => {
        await testWithResources(async (connection, socket) => {
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = RedisRequest.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.SetString);
                const args = request.argsArray!.args!;
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
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.argsVecPointer).not.toBeNull();
                expect(request.argsArray).toBeNull();

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
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.argsArray).not.toBeNull();
                expect(request.argsVecPointer).toBeNull();

                sendResponse(socket, ResponseType.Null, request.callbackIdx);
            });
            const key = "0".repeat(MAX_REQUEST_ARGS_LEN - 1);
            const result = await connection.get(key);

            expect(result).toBeNull();
        });
    });
});
