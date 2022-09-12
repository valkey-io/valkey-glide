import { BabushkaInternal } from ".";
const {
    StartSocketConnection,
    HEADER_LENGTH_IN_BYTES,
    SOCKET_FILE_PATH,
    ResponseType,
    RequestType,
} = BabushkaInternal;
type RequestType = BabushkaInternal.RequestType;
type ResponseType = BabushkaInternal.ResponseType;
import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as net from "net";
import { nextPow2 } from "bit-twiddle";

export class SocketConnection {
    // private readonly readServer: net.Server;
    // private readonly writeServer: net.Server;
    private socket: net.Socket;
    private writeSocket!: net.Socket;
    private readonly promiseResolveFunctions: ((val: any) => void)[] = [];
    private readonly availableCallbackSlots: number[] = [];
    private readonly encoder = new TextEncoder();
    private backingReadBuffer = new ArrayBuffer(1024);
    private backingWriteBuffer: ArrayBuffer | undefined;
    private remainingReadData: Uint8Array | undefined;

    private handleReadData(data: Buffer) {
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
                this.remainingReadData = new Uint8Array(
                    dataArray.buffer,
                    counter,
                    dataArray.byteLength - counter
                );
                break;
            }
            const callbackIndex = header[1];
            const responseType = header[2] as ResponseType;
            const resolveFunction = this.promiseResolveFunctions[callbackIndex];
            if (!resolveFunction) {
                throw new Error("missing callback for index: " + callbackIndex);
            }
            this.availableCallbackSlots.push(callbackIndex);
            if (responseType === ResponseType.Null) {
                resolveFunction(null);
            } else if (responseType === ResponseType.String) {
                const valueLength = length - HEADER_LENGTH_IN_BYTES;
                const keyBytes = Buffer.from(
                    dataArray.buffer,
                    counter + HEADER_LENGTH_IN_BYTES,
                    valueLength
                );
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
        } else {
            this.remainingReadData = new Uint8Array(
                dataArray.buffer,
                counter,
                dataArray.byteLength - counter
            );
        }
    }

    //private constructor(readSocketName: string, writeSocketName: string) {
    private constructor() {

        // this.readServer = net
        //     .createServer((readSocket) => {
        //         readSocket.on("data", (data) => this.handleReadData(data));
        //     })
        //     .listen(readSocketName);

        // this.writeServer = net
        //     .createServer((socket) => {
        //         this.writeSocket = socket;
        //     })
        //     .listen(writeSocketName);
        this.socket = new net.Socket();
    }

    public async connect(socketPath: string) {
        return new Promise((resolve, reject) => {
            this.socket.connect(socketPath)
            .on('connect', ()=>{
                console.log("Connected.");
                resolve("Connected");
            })
            // Messages are buffers. use toString
            .on("data", (data) =>{ this.handleReadData(data);
            })
            .on('error', (data)=> {
                console.error(`Server not active: ${data}`); 
                this.socket.end();
                reject("Failed");
                process.exit(1);
            })
            ;
        });
    }

    private concatBuffers(priorBuffer: Uint8Array, data: Buffer): Uint8Array {
        const requiredLength = priorBuffer.length + data.byteLength;

        if (this.backingReadBuffer.byteLength < requiredLength) {
            this.backingReadBuffer = new ArrayBuffer(nextPow2(requiredLength));
        }
        const array = new Uint8Array(this.backingReadBuffer, 0, requiredLength);
        array.set(priorBuffer);
        array.set(data, priorBuffer.byteLength);
        return array;
    }

    private getCallbackIndex(): number {
        return (
            this.availableCallbackSlots.pop() ??
            this.promiseResolveFunctions.length
        );
    }

    private writeString<T>(
        firstString: string,
        operationType: RequestType,
        secondString?: string
    ): Promise<T> {
        return new Promise((resolve) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseResolveFunctions[callbackIndex] = resolve;
            const headerLength =
                secondString != undefined
                    ? HEADER_LENGTH_IN_BYTES + 4
                    : HEADER_LENGTH_IN_BYTES;
            // length * 3 is the maximum ratio between UTF16 byte count to UTF8 byte count.
            // TODO - in practice we used a small part of our arrays, and this will be very expensive on
            // large inputs. We can use the slightly slower Buffer.byteLength on longer strings.
            const requiredLength =
                headerLength +
                firstString.length * 3 +
                (secondString?.length ?? 0) * 3;
            if (
                !this.backingWriteBuffer ||
                this.backingWriteBuffer.byteLength < requiredLength
            ) {
                this.backingWriteBuffer = new ArrayBuffer(
                    nextPow2(requiredLength)
                );
            }
            let uint8Array = new Uint8Array(
                this.backingWriteBuffer,
                0,
                requiredLength
            );
            const firstStringEncodeResult = this.encoder.encodeInto(
                firstString,
                uint8Array.subarray(headerLength)
            );
            const firstStringLength = firstStringEncodeResult.written ?? 0;
            const secondStringLength =
                secondString == undefined
                    ? 0
                    : this.encoder.encodeInto(
                          secondString,
                          uint8Array.subarray(headerLength + firstStringLength)
                      ).written ?? 0;
            const length =
                headerLength + firstStringLength + secondStringLength;
            const headerUint32Array = new Uint32Array(
                this.backingWriteBuffer,
                0,
                headerLength / 4
            );
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

    get(key: string): Promise<string> {
        return this.writeString(key, RequestType.GetString);
    }

    set(key: string, value: string): Promise<void> {
        return this.writeString(key, RequestType.SetString, value);
    }

    dispose(): void {
        // this.readServer.close();
        // this.writeServer.close();
        //this.writeSocket.end();
        this.socket.end();
    }

    static async CreateConnection(address: string) {
        return new Promise((resolve, reject) => {
            // TODO - create pipes according to Windows convention:
            // https://nodejs.org/api/net.html#identifying-paths-for-ipc-connections
            const temporaryFolder = fs.mkdtempSync(
                path.join(os.tmpdir(), `socket_listener`)
            );
            const readSocketName = path.join(temporaryFolder, "read");
            const writeSocketName = path.join(temporaryFolder, "write");

            // const connection = new SocketConnection(
            //     readSocketName,
            //     writeSocketName
            // );

            let resolved = false;
            const closeCallback = (err: Error | null) => {
                //connection.dispose();
                if (!resolved) {
                    resolved = true;
                    reject(err);
                }
            };
            //this.socket = connection;
            const startCallback = async () => {
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
                    await connection.connect(SOCKET_FILE_PATH);
                    resolve(connection);
                }
            };
            StartSocketConnection(
                address,
                writeSocketName,
                readSocketName,
                startCallback,
                closeCallback
            );
        });
    }
}
