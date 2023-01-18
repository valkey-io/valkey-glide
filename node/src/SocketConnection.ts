import { BabushkaInternal } from "../";
import * as net from "net";
import { Logger } from "./Logger";
const {
    StartSocketConnection,
    HEADER_LENGTH_IN_BYTES,
    ResponseType,
    RequestType,
} = BabushkaInternal;

type RequestType = BabushkaInternal.RequestType;
type ResponseType = BabushkaInternal.ResponseType;
type PromiseFunction = (value?: any) => void;

type WriteRequest = {
    callbackIndex: number;
    args: string[];
    type: RequestType;
};

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
    private bufferedWriteRequests: WriteRequest[] = [];
    private writeInProgress = false;
    private remainingReadData: Uint8Array | undefined;

    private handleReadData(data: Buffer) {
        const dataArray = this.remainingReadData
            ? this.concatBuffers(this.remainingReadData, data)
            : new Uint8Array(data.buffer, data.byteOffset, data.length);

        let counter = 0;
        while (counter <= dataArray.byteLength - HEADER_LENGTH_IN_BYTES) {
            const header = new DataView(dataArray.buffer, counter, 12);
            const length = header.getUint32(0, true);
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
            const callbackIndex = header.getUint32(4, true);
            const responseType = header.getUint32(8, true) as ResponseType;
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
        // Demo - if logger has been initialized by the external-user on info level this log will be shown
        Logger.instance.log("info", "connection", `construct socket`);

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
        argLengths: number[],
        offset: number
    ) {
        const headerView = new DataView(
            this.backingWriteBuffer,
            offset,
            headerLength
        );
        headerView.setUint32(0, length, true);
        headerView.setUint32(4, callbackIndex, true);
        headerView.setUint32(8, operationType, true);

        for (let i = 0; i < argLengths.length - 1; i++) {
            const argLength = argLengths[i];
            headerView.setUint32(
                HEADER_LENGTH_IN_BYTES + 4 * i,
                argLength,
                true
            );
        }
    }

    private getHeaderLength(writeRequest: WriteRequest) {
        return HEADER_LENGTH_IN_BYTES + 4 * (writeRequest.args.length - 1);
    }

    private lengthOfStrings(request: WriteRequest) {
        return request.args.reduce((sum, arg) => sum + arg.length, 0);
    }

    private encodeStringToWriteBuffer(str: string, byteOffset: number): number {
        const encodeResult = this.encoder.encodeInto(
            str,
            new Uint8Array(this.backingWriteBuffer, byteOffset)
        );
        return encodeResult.written ?? 0;
    }

    private getRequiredBufferLength(writeRequests: WriteRequest[]): number {
        return writeRequests.reduce((sum, request) => {
            return (
                sum +
                this.getHeaderLength(request) +
                // length * 3 is the maximum ratio between UTF16 byte count to UTF8 byte count.
                // TODO - in practice we used a small part of our arrays, and this will be very expensive on
                // large inputs. We can use the slightly slower Buffer.byteLength on longer strings.
                this.lengthOfStrings(request) * 3
            );
        }, 0);
    }

    private writeBufferedRequestsToSocket() {
        this.writeInProgress = true;
        const writeRequests = this.bufferedWriteRequests.splice(
            0,
            this.bufferedWriteRequests.length
        );
        const requiredBufferLength =
            this.getRequiredBufferLength(writeRequests);

        if (
            !this.backingWriteBuffer ||
            this.backingWriteBuffer.byteLength < requiredBufferLength
        ) {
            this.backingWriteBuffer = new ArrayBuffer(requiredBufferLength);
        }
        let cursor = 0;
        for (const writeRequest of writeRequests) {
            const headerLength = this.getHeaderLength(writeRequest);
            let argOffset = 0;
            const writtenLengths = [];
            for (const arg of writeRequest.args) {
                const argLength = this.encodeStringToWriteBuffer(
                    arg,
                    cursor + headerLength + argOffset
                );
                argOffset += argLength;
                writtenLengths.push(argLength);
            }

            const length = headerLength + argOffset;
            this.writeHeaderToWriteBuffer(
                length,
                writeRequest.callbackIndex,
                writeRequest.type,
                headerLength,
                writtenLengths,
                cursor
            );
            cursor += length;
        }

        const uint8Array = new Uint8Array(this.backingWriteBuffer, 0, cursor);
        this.socket.write(uint8Array, undefined, () => {
            if (this.bufferedWriteRequests.length > 0) {
                this.writeBufferedRequestsToSocket();
            } else {
                this.writeInProgress = false;
            }
        });
    }

    private writeOrBufferRequest(writeRequest: WriteRequest) {
        this.bufferedWriteRequests.push(writeRequest);
        if (this.writeInProgress) {
            return;
        }
        this.writeBufferedRequestsToSocket();
    }

    public get(key: string): Promise<string> {
        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRequest({
                args: [key],
                type: RequestType.GetString,
                callbackIndex,
            });
        });
    }

    public set(key: string, value: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRequest({
                args: [key, value],
                type: RequestType.SetString,
                callbackIndex,
            });
        });
    }

    private setServerAddress(address: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const callbackIndex = this.getCallbackIndex();
            this.promiseCallbackFunctions[callbackIndex] = [resolve, reject];
            this.writeOrBufferRequest({
                args: [address],
                type: RequestType.ServerAddress,
                callbackIndex,
            });
        });
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
        const path = await StartSocketConnection();
        const socket = await this.GetSocket(path);
        return await this.__CreateConnection(address, socket);
    }
}
