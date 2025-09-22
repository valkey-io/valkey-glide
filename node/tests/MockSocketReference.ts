/**
 * Mock implementation of SocketReference for testing
 * This demonstrates what the NAPI implementation should provide
 */

export interface SocketReference {
    readonly path: string;
    readonly isActive: boolean;
    readonly referenceCount: number;
}

// Mock storage for testing
const mockSockets = new Map<string, { refCount: number; active: boolean }>();

export async function StartSocketConnectionWithReference(
    path?: string,
): Promise<SocketReference> {
    const socketPath = path || `/tmp/test-socket-${Date.now()}.sock`;

    // Mock implementation - in real NAPI this would call Rust
    if (!mockSockets.has(socketPath)) {
        mockSockets.set(socketPath, { refCount: 1, active: true });
    } else {
        const socket = mockSockets.get(socketPath)!;
        socket.refCount++;
    }

    const socketData = mockSockets.get(socketPath)!;

    return {
        get path() {
            return socketPath;
        },
        get isActive() {
            return socketData.active;
        },
        get referenceCount() {
            return socketData.refCount;
        },
    };
}

export function IsSocketActive(path: string): boolean {
    const socket = mockSockets.get(path);
    return socket ? socket.active : false;
}

export function GetActiveSocketCount(): number {
    return Array.from(mockSockets.values()).filter((s) => s.active).length;
}

export function CleanupAllSockets(): void {
    mockSockets.clear();
}

export function ForceCleanupSocket(path: string): void {
    mockSockets.delete(path);
}

// Mock client for testing
export class MockClient {
    private socketRef?: SocketReference;

    async connect(socketPath?: string): Promise<void> {
        this.socketRef = await StartSocketConnectionWithReference(socketPath);
    }

    getSocketReference(): SocketReference | undefined {
        return this.socketRef;
    }

    close(): void {
        if (this.socketRef) {
            const socket = mockSockets.get(this.socketRef.path);

            if (socket) {
                socket.refCount--;

                if (socket.refCount <= 0) {
                    socket.active = false;
                    mockSockets.delete(this.socketRef.path);
                }
            }

            this.socketRef = undefined;
        }
    }
}
