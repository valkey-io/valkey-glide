"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.SocketConnection = void 0;
const _1 = require(".");
const { StartSocketConnection, HEADER_LENGTH_IN_BYTES, SOCKET_FILE_PATH, ResponseType, RequestType, } = _1.BabushkaInternal;
const fs = __importStar(require("fs"));
const os = __importStar(require("os"));
const path = __importStar(require("path"));
const net = __importStar(require("net"));
const bit_twiddle_1 = require("bit-twiddle");
class SocketConnection {
    //private constructor(readSocketName: string, writeSocketName: string) {
    constructor() {
        // this.readServer = net
        //     .createServer((readSocket) => {
        //         readSocket.on("data", (data) => this.handleReadData(data));
        //     })
        //     .listen(readSocketName);
        this.promiseResolveFunctions = [];
        this.availableCallbackSlots = [];
        this.encoder = new TextEncoder();
        this.backingReadBuffer = new ArrayBuffer(1024);
        // this.writeServer = net
        //     .createServer((socket) => {
        //         this.writeSocket = socket;
        //     })
        //     .listen(writeSocketName);
        this.socket = new net.Socket();
    }
    handleReadData(data) {
        const dataArray = this.remainingReadData
            ? this.concatBuffers(this.remainingReadData, data)
            : new Uint8Array(data.buffer, data.byteOffset, data.length);
        if (dataArray.byteLength % 4 !== 0) {
            throw new Error("inputs are not aligned to 4.");
        }
        let counter = 0;
        while (counter <= dataArray.byteLength - HEADER_LENGTH_IN_BYTES) {
            const header = new Uint32Array(dataArray.buffer, counter, 3);
            const length = header[0];
            if (length === 0) {
                throw new Error("length 0");
            }
            if (counter + length > dataArray.byteLength) {
                this.remainingReadData = new Uint8Array(dataArray.buffer, counter, dataArray.byteLength - counter);
                break;
            }
            const callbackIndex = header[1];
            const responseType = header[2];
            const resolveFunction = this.promiseResolveFunctions[callbackIndex];
            if (!resolveFunction) {
                throw new Error("missing callback for index: " + callbackIndex);
            }
            this.availableCallbackSlots.push(callbackIndex);
            if (responseType === 0 /* ResponseType.Null */) {
                resolveFunction(null);
            }
            else if (responseType === 1 /* ResponseType.String */) {
                const valueLength = length - HEADER_LENGTH_IN_BYTES;
                const keyBytes = Buffer.from(dataArray.buffer, counter + HEADER_LENGTH_IN_BYTES, valueLength);
                resolveFunction(keyBytes.toString("utf8"));
            }
            counter = counter + length;
            const offset = counter % 4;
            if (offset !== 0) {
                // align counter to 4.
                counter += 4 - offset;
            }
        }
        if (counter == dataArray.byteLength) {
            this.remainingReadData = undefined;
        }
        else {
            this.remainingReadData = new Uint8Array(dataArray.buffer, counter, dataArray.byteLength - counter);
        }
    }
    connect(socketPath) {
        return __awaiter(this, void 0, void 0, function* () {
            return new Promise((resolve, reject) => {
                this.socket.connect(socketPath)
                    .on('connect', () => {
                    console.log("Connected.");
                    resolve("Connected");
                })
                    // Messages are buffers. use toString
                    .on("data", (data) => {
                    this.handleReadData(data);
                })
                    .on('error', (data) => {
                    console.error(`Server not active: ${data}`);
                    this.socket.end();
                    reject("Failed");
                    process.exit(1);
                });
            });
        });
    }
    concatBuffers(priorBuffer, data) {
        const requiredLength = priorBuffer.length + data.byteLength;
        if (this.backingReadBuffer.byteLength < requiredLength) {
            this.backingReadBuffer = new ArrayBuffer((0, bit_twiddle_1.nextPow2)(requiredLength));
        }
        const array = new Uint8Array(this.backingReadBuffer, 0, requiredLength);
        array.set(priorBuffer);
        array.set(data, priorBuffer.byteLength);
        return array;
    }
    getCallbackIndex() {
        var _a;
        return ((_a = this.availableCallbackSlots.pop()) !== null && _a !== void 0 ? _a : this.promiseResolveFunctions.length);
    }
    writeString(firstString, operationType, secondString) {
        return new Promise((resolve) => {
            var _a, _b, _c;
            const callbackIndex = this.getCallbackIndex();
            this.promiseResolveFunctions[callbackIndex] = resolve;
            const headerLength = secondString != undefined
                ? HEADER_LENGTH_IN_BYTES + 4
                : HEADER_LENGTH_IN_BYTES;
            // length * 3 is the maximum ratio between UTF16 byte count to UTF8 byte count.
            // TODO - in practice we used a small part of our arrays, and this will be very expensive on
            // large inputs. We can use the slightly slower Buffer.byteLength on longer strings.
            const requiredLength = headerLength +
                firstString.length * 3 +
                ((_a = secondString === null || secondString === void 0 ? void 0 : secondString.length) !== null && _a !== void 0 ? _a : 0) * 3;
            if (!this.backingWriteBuffer ||
                this.backingWriteBuffer.byteLength < requiredLength) {
                this.backingWriteBuffer = new ArrayBuffer((0, bit_twiddle_1.nextPow2)(requiredLength));
            }
            let uint8Array = new Uint8Array(this.backingWriteBuffer, 0, requiredLength);
            const firstStringEncodeResult = this.encoder.encodeInto(firstString, uint8Array.subarray(headerLength));
            const firstStringLength = (_b = firstStringEncodeResult.written) !== null && _b !== void 0 ? _b : 0;
            const secondStringLength = secondString == undefined
                ? 0
                : (_c = this.encoder.encodeInto(secondString, uint8Array.subarray(headerLength + firstStringLength)).written) !== null && _c !== void 0 ? _c : 0;
            const length = headerLength + firstStringLength + secondStringLength;
            const headerUint32Array = new Uint32Array(this.backingWriteBuffer, 0, headerLength / 4);
            headerUint32Array[0] = length;
            headerUint32Array[1] = callbackIndex;
            headerUint32Array[2] = operationType;
            if (secondStringLength !== undefined) {
                headerUint32Array[3] = firstStringLength;
            }
            uint8Array = new Uint8Array(this.backingWriteBuffer, 0, length);
            //if (!this.writeSocket.write(uint8Array)) {
            if (!this.socket.write(uint8Array)) {
                // If the buffer is still being used, we remove it so that no other write operation will interfere.
                this.backingWriteBuffer = undefined;
            }
        });
    }
    get(key) {
        return this.writeString(key, 1 /* RequestType.GetString */);
    }
    set(key, value) {
        return this.writeString(key, 2 /* RequestType.SetString */, value);
    }
    dispose() {
        // this.readServer.close();
        // this.writeServer.close();
        //this.writeSocket.end();
        this.socket.end();
    }
    static CreateConnection(address) {
        return __awaiter(this, void 0, void 0, function* () {
            return new Promise((resolve, reject) => {
                // TODO - create pipes according to Windows convention:
                // https://nodejs.org/api/net.html#identifying-paths-for-ipc-connections
                const temporaryFolder = fs.mkdtempSync(path.join(os.tmpdir(), `socket_listener`));
                const readSocketName = path.join(temporaryFolder, "read");
                const writeSocketName = path.join(temporaryFolder, "write");
                // const connection = new SocketConnection(
                //     readSocketName,
                //     writeSocketName
                // );
                let resolved = false;
                const closeCallback = (err) => {
                    //connection.dispose();
                    if (!resolved) {
                        resolved = true;
                        reject(err);
                    }
                };
                //this.socket = connection;
                const startCallback = () => __awaiter(this, void 0, void 0, function* () {
                    if (!resolved) {
                        // resolved = true;
                        // let counter = 50;
                        // while (!connection.writeSocket) {
                        //     await new Promise((resolve) => setTimeout(resolve, 1));
                        //     counter--;
                        //     if (counter <= 0) {
                        //         throw new Error("Failed getting a write socket.");
                        //     }
                        // }
                        // resolve(connection);
                        const connection = new SocketConnection();
                        yield connection.connect(SOCKET_FILE_PATH);
                        resolve(connection);
                    }
                });
                StartSocketConnection(address, writeSocketName, readSocketName, startCallback, closeCallback);
            });
        });
    }
}
exports.SocketConnection = SocketConnection;
