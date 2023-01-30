import { SocketConnection, BabushkaInternal, setLoggerConfig } from "..";
import net from "net";
import fs from "fs";
import os from "os";
import path from "path";

const { HEADER_LENGTH_IN_BYTES, ResponseType, RequestType } = BabushkaInternal;

beforeAll(() => {
    setLoggerConfig("info");
});

function sendResponse(socket, responseType, callbackIndex, message = "") {
    const length = HEADER_LENGTH_IN_BYTES + message.length;
    let bufferLength = length;
    if (bufferLength % 4 !== 0) {
        bufferLength += 4 - (bufferLength % 4);
    }
    const buffer = new ArrayBuffer(bufferLength);
    const response = new Uint32Array(buffer);
    response[0] = length;
    response[1] = callbackIndex;
    response[2] = responseType;
    const encoder = new TextEncoder();
    encoder.encodeInto(message, new Uint8Array(buffer, HEADER_LENGTH_IN_BYTES));
    socket.write(new Uint8Array(buffer));
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
                    const uint32Array = new Uint32Array(data.buffer, 0, 3);

                    expect(data.byteLength).toEqual(HEADER_LENGTH_IN_BYTES);
                    expect(uint32Array[0]).toEqual(HEADER_LENGTH_IN_BYTES); // length of message when empty string is the address
                    expect(uint32Array[2]).toEqual(RequestType.ServerAddress);

                    sendResponse(socket, ResponseType.Null, uint32Array[1]);
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

xdescribe("SocketConnectionInternals", () => {
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
                const uint32Array = new Uint32Array(data.buffer, 0, 3);
                expect(uint32Array[0]).toEqual(15);
                expect(uint32Array[2]).toEqual(RequestType.GetString);

                sendResponse(
                    socket,
                    ResponseType.String,
                    uint32Array[1],
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
                const uint32Array = new Uint32Array(data.buffer, 0, 3);
                expect(uint32Array[0]).toEqual(15);
                expect(uint32Array[2]).toEqual(RequestType.GetString);

                sendResponse(socket, ResponseType.Null, uint32Array[1]);
            });
            const result = await connection.get("foo");
            expect(result).toBeNull();
        });
    });

    it("should reject requests that received a response error", async () => {
        await testWithResources(async (connection, socket) => {
            const error = "check";
            socket.once("data", (data) => {
                const uint32Array = new Uint32Array(data.buffer, 0, 3);
                sendResponse(
                    socket,
                    ResponseType.RequestError,
                    uint32Array[1],
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
                const uint32Array = new Uint32Array(data.buffer, 0, 3);
                sendResponse(
                    socket,
                    ResponseType.ClosingError,
                    uint32Array[1],
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
