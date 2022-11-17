import { BabushkaInternal } from "../";
import * as net from "net";
const {
    StartSocketConnection,
    HEADER_LENGTH_IN_BYTES,
    ResponseType,
    RequestType,
} = BabushkaInternal;

type RequestType = BabushkaInternal.RequestType;
type ResponseType = BabushkaInternal.ResponseType;
type PromiseFunction = (value?: any) => void;

export class SocketConnection {
    private socket: net.Socket;
    private readonly promiseCallbackFunctions: [
        PromiseFunction,
        PromiseFunction
    ][] = [];
    private readonly availableCallbackSlots: number[] = [];
    private readonly encoder = new TextEncoder();
    private backingReadBuffer = new ArrayBuffer(1024);
    private backingWriteBuffer = new ArrayBuffer(1024);
    private remainingReadData: Uint8Array | undefined;
    private previousOperation = Promise.resolve();

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
            const [resolve, reject] =
                this.promiseCallbackFunctions[callbackIndex];
            this.availableCallbackSlots.push(callbackIndex);
            if (responseType === ResponseType.Null) {
                resolve(null);
            } else {
                const valueLength = length - HEADER_LENGTH_IN_BYTES;
                const keyBytes = Buffer.from(
                    dataArray.buffer,
                    counter + HEADER_LENGTH_IN_BYTES,
                    valueLength
                );
                const message = keyBytes.toString("utf8");
                if (responseType === ResponseType.String) {
                    resolve(message);
                } else if (responseType === ResponseType.RequestError) {
                    reject(message);
                } else if (responseType === ResponseType.ClosingError) {
                    this.dispose(message);
                }
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

    private constructor(socket: net.Socket) {
        this.socket = socket;
        this.socket
            .on("data", (data) => this.handleReadData(data))
            .on("error", (err) => {
                console.error(`Server closed: ${err}`);
                this.dispose();
            });
    }

    private concatBuffers(priorBuffer: Uint8Array, data: Buffer): Uint8Array {
        const requiredLength = priorBuffer.length + data.byteLength;

        if (this.backingReadBuffer.byteLength < requiredLength) {
            this.backingReadBuffer = new ArrayBuffer(requiredLength);
        }
        const array = new Uint8Array(this.backingReadBuffer, 0, requiredLength);
        array.set(priorBuffer);
        array.set(data, priorBuffer.byteLength);
        return array;
    }

    private getCallbackIndex(): number {
        return (
            this.availableCallbackSlots.pop() ??
            this.promiseCallbackFunctions.length
        );
    }

    private writeHeaderToWriteBuffer(
        length: number,
        callbackIndex: number,
        operationType: RequestType,
        headerLength: number,
        firstStringLength: number | undefined
    ) {
        const headerUint32Array = new Uint32Array(
            this.backingWriteBuffer,
            0,
            headerLength / 4
        );
        headerUint32Array[0] = length;
        headerUint32Array[1] = callbackIndex;
        headerUint32Array[2] = operationType;
        if (firstStringLength) {
            headerUint32Array[3] = firstStringLength;
        }
    }

    private getHeaderLength(hasAdditionalString: boolean) {
        return hasAdditionalString
            ? HEADER_LENGTH_IN_BYTES + 4
            : HEADER_LENGTH_IN_BYTES;
    }

    private encodeStringToWriteBuffer(str: string, byteOffset: number): number {
        const encodeResult = this.encoder.encodeInto(
            str,
            new Uint8Array(this.backingWriteBuffer, byteOffset)
        );
        return encodeResult.written ?? 0;
    }

    private chainNewWriteOperation(
        firstString: string,
        operationType: RequestType,
        secondString: string | undefined,
        callbackIndex: number
    ) {
        this.previousOperation = this.previousOperation.then(
            () =>
                new Promise((resolve) => {
                    const headerLength = this.getHeaderLength(
                        secondString !== undefined
                    );
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
                            requiredLength
                        );
                    }

                    const firstStringLength = this.encodeStringToWriteBuffer(
                        firstString,
                        headerLength
                    );
                    const secondStringLength =
                        secondString == undefined
                            ? 0
                            : this.encodeStringToWriteBuffer(
                                  secondString,
                                  headerLength + firstStringLength
                              );

                    const length =
                        headerLength + firstStringLength + secondStringLength;
                    this.writeHeaderToWriteBuffer(
                        length,
                        callbackIndex,
                        operationType,
                        headerLength,
                        secondString !== undefined
                            ? firstStringLength
                            : undefined
                    );

                    const uint8Array = new Uint8Array(
                        this.backingWriteBuffer,
                        0,
                        length
                    );
                    this.socket.write(uint8Array, undefined, () => resolve());
                })
        );
    }

    private writeString<T>(
        firstString: string,
        operationType: RequestType,
        secondString?: string
    ): Promise<T> {
        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.chainNewWriteOperation(
                firstString,
                operationType,
                secondString,
                callbackIndex
            );
        });
    }

    public get(key: string): Promise<string> {
        return this.writeString(key, RequestType.GetString);
    }

    public set(key: string, value: string): Promise<void> {
        return this.writeString(key, RequestType.SetString, value);
    }

    private setServerAddress(address: string): Promise<void> {
        return this.writeString(address, RequestType.ServerAddress);
    }

    public dispose(errorMessage?: string): void {
        this.promiseCallbackFunctions.forEach(([_resolve, reject], _index) => {
            reject(errorMessage);
        });
        this.socket.end();
    }

    static async __CreateConnection(
        address: string,
        connectedSocket: net.Socket
    ): Promise<SocketConnection> {
        const connection = new SocketConnection(connectedSocket);
        await connection.setServerAddress(address);
        return connection;
    }

    private static GetSocket(path: string): Promise<net.Socket> {
        return new Promise((resolve, reject) => {
            const socket = new net.Socket();
            socket
                .connect(path)
                .once("connect", () => resolve(socket))
                .once("error", reject);
        });
    }

    public static async CreateConnection(
        address: string
    ): Promise<SocketConnection> {
        return new Promise((resolve, reject) => {
            const startCallback = async (
                err: null | Error,
                path: string | null
            ) => {
                if (path !== null) {
                    const socket = await this.GetSocket(path);
                    const connection = await this.__CreateConnection(
                        address,
                        socket
                    );
                    resolve(connection);
                } else if (err !== null) {
                    reject(err);
                }
            };

            StartSocketConnection(startCallback);
        });
    }
}
