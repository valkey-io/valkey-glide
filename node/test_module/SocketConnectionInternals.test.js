import { SocketConnection, BabushkaInternal, setLoggerConfig } from "..";
import net from "net";
import fs from "fs";
import os from "os";
import path from "path";
import { pb_message } from "../src/ProtobufMessage";
import { Reader } from "protobufjs";

const { RequestType, createLeakedValue } = BabushkaInternal;

// TODO: use TS enums when tests are in TS.
const ResponseType = {
    /** Type of a response that returns a null. */
    Null: 0,
    /** Type of a response that returns a value which isn't an error. */
    Value: 1,
    /** Type of response containing an error that impacts a single request. */
    RequestError: 2,
    /** Type of response containing an error causes the connection to close. */
    ClosingError: 3,
    /** Type of response containing the string "OK". */
    OK: 4,
};

beforeAll(() => {
    setLoggerConfig("info");
});

function sendResponse(
    socket,
    responseType,
    callbackIndex,
    message = undefined
) {
    var response = pb_message.Response.create();
    response.callbackIdx = callbackIndex;
    if (responseType == ResponseType.Value) {
        const pointer = createLeakedValue(message);
        const pointer_number = Number(pointer.toString())
        response.respPointer = pointer_number;
    } else if (responseType == ResponseType.ClosingError) {
        response.closingError = message;
    } else if (responseType == ResponseType.RequestError) {
        response.requestError = message;
    } else if (responseType == ResponseType.Null) {
        // do nothing
    } else if (responseType == ResponseType.OK) {
        response.constantResponse = pb_message.ConstantResponse.OK;
    } else {
        throw new Error("Got unknown response type: ", responseType);
    }
    let response_bytes = pb_message.Response.encodeDelimited(response).finish();
    socket.write(response_bytes);
}

function getConnectionAndSocket() {
    return new Promise((resolve) => {
        const temporaryFolder = fs.mkdtempSync(
            path.join(os.tmpdir(), `socket_listener`)
        );
        const socketName = path.join(temporaryFolder, "read");
        let connectionPromise = undefined;
        const server = net
            .createServer(async (socket) => {
                socket.once("data", (data) => {
                    const reader = Reader.create(data);
                    const request = pb_message.Request.decodeDelimited(reader);
                    expect(request.requestType).toEqual(RequestType.ServerAddress);

                    sendResponse(socket, ResponseType.Null, request.callbackIdx);
                });

                const connection = await connectionPromise;
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
                    "",
                    socket
                );
                resolve(connection);
            });
        });
    });
}

function closeTestResources(connection, server, socket) {
    connection.dispose();
    server.close();
    socket.end();
}

async function testWithResources(testFunction) {
    const { connection, server, socket } = await getConnectionAndSocket();

    await testFunction(connection, socket);

    closeTestResources(connection, server, socket);
}

describe("SocketConnectionInternals", () => {
    it("Test setup returns values", async () => {
        await testWithResources((connection, socket) => {
            expect(connection).toEqual(expect.anything());
            expect(socket).toEqual(expect.anything());
        });
    });

    it("should close socket on dispose", async () => {
        await testWithResources(async (connection, socket) => {
            const endReceived = new Promise((resolve) => {
                socket.once("end", resolve(true));
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
                const request = pb_message.Request.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.args.length).toEqual(1);

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
                const request = pb_message.Request.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.args.length).toEqual(1);

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
                const request = pb_message.Request.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.SetString);
                expect(request.args.length).toEqual(2);

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
                const request = pb_message.Request.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.args.length).toEqual(1);
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

    it("should all requests when receiving a closing error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const reader = Reader.create(data);
                const request = pb_message.Request.decodeDelimited(reader);
                expect(request.requestType).toEqual(RequestType.GetString);
                expect(request.args.length).toEqual(1);
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
});
