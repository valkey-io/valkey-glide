/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Integration tests for Node.js socket reference counting with actual client scenarios
 *
 * These tests validate socket reference behavior in realistic usage patterns
 * including multi-client scenarios, worker threads, and complex lifecycles.
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    beforeEach,
    describe,
    expect,
    it,
} from "@jest/globals";
import { Worker } from "worker_threads";
import { SocketReferenceTestUtils } from "./SocketReferenceTestUtils";
import {
    BaseClient,
    BaseClientConfiguration,
    GlideClient,
    GlideClusterClient,
} from "../build-ts";

// Mock or expected client interfaces with socket reference support
interface SocketReference {
    readonly path: string;
    readonly isActive: boolean;
    readonly referenceCount: number;
}

// Extended client interface that supports socket references
interface ClientWithSocketReference extends BaseClient {
    readonly socketReference?: SocketReference;
}

// Expected factory functions (to be implemented)
declare function createClientWithSocketReference(
    config: BaseClientConfiguration & { socketPath?: string }
): Promise<ClientWithSocketReference>;

declare function createClusterClientWithSocketReference(
    config: BaseClientConfiguration & { socketPath?: string }
): Promise<ClientWithSocketReference>;

// Test utilities
let testUtils: SocketReferenceTestUtils;

describe("Socket Reference Integration: Multi-Client Scenarios", () => {
    beforeAll(async () => {
        testUtils = new SocketReferenceTestUtils();
        await testUtils.setup();
    });

    afterAll(async () => {
        await testUtils.cleanup();
    });

    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should share socket between multiple clients", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();

        // Act: Create multiple clients sharing the same socket
        const client1 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        const client2 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        const client3 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        // Assert: Socket sharing contract
        expect(client1.socketReference).toBeDefined();
        expect(client2.socketReference).toBeDefined();
        expect(client3.socketReference).toBeDefined();

        const ref1 = client1.socketReference!;
        const ref2 = client2.socketReference!;
        const ref3 = client3.socketReference!;

        expect(ref1.path).toBe(socketPath);
        expect(ref2.path).toBe(socketPath);
        expect(ref3.path).toBe(socketPath);

        expect(ref1.referenceCount).toBe(3);
        expect(ref2.referenceCount).toBe(3);
        expect(ref3.referenceCount).toBe(3);

        expect(ref1.isActive).toBe(true);
        expect(ref2.isActive).toBe(true);
        expect(ref3.isActive).toBe(true);

        // Cleanup
        await client1.close();
        await client2.close();
        await client3.close();
    });

    it("should maintain socket while any client is active", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();

        const client1 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        const client2 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        const client3 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        // Act: Close clients one by one
        await client1.close();
        await testUtils.waitForCleanup();

        // Assert: Socket should still be active
        expect(client2.socketReference!.referenceCount).toBe(2);
        expect(client3.socketReference!.referenceCount).toBe(2);
        expect(client2.socketReference!.isActive).toBe(true);
        expect(testUtils.socketFileExists(socketPath)).toBe(true);

        await client2.close();
        await testUtils.waitForCleanup();

        // Assert: Socket should still be active with last client
        expect(client3.socketReference!.referenceCount).toBe(1);
        expect(client3.socketReference!.isActive).toBe(true);
        expect(testUtils.socketFileExists(socketPath)).toBe(true);

        await client3.close();
        await testUtils.waitForCleanup();

        // Assert: Socket should be cleaned up after last client
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });

    it("should handle client crashes gracefully", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();

        let client1 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        let client2 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        expect(client1.socketReference!.referenceCount).toBe(2);

        // Act: Simulate client crash (force garbage collection without proper close)
        client1 = null as any;
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Remaining client should still work
        expect(client2.socketReference!.referenceCount).toBe(1);
        expect(client2.socketReference!.isActive).toBe(true);
        expect(testUtils.socketFileExists(socketPath)).toBe(true);

        // Verify client is still functional
        await expect(client2.ping()).resolves.toBe("PONG");

        // Cleanup
        await client2.close();
    });

    it("should support mixed client types on same socket", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const standaloneConfig = testUtils.getStandaloneServerConfig();
        const clusterConfig = testUtils.getClusterServerConfig();

        // Act: Create different types of clients (if supported)
        const standaloneClient = await createClientWithSocketReference({
            ...standaloneConfig,
            socketPath,
        });

        // For cluster client, use different socket to avoid conflicts
        const clusterSocketPath = testUtils.getTestSocketPath();
        const clusterClient = await createClusterClientWithSocketReference({
            ...clusterConfig,
            socketPath: clusterSocketPath,
        });

        // Assert: Different client types manage their own sockets
        expect(standaloneClient.socketReference!.path).toBe(socketPath);
        expect(clusterClient.socketReference!.path).toBe(clusterSocketPath);
        expect(standaloneClient.socketReference!.referenceCount).toBe(1);
        expect(clusterClient.socketReference!.referenceCount).toBe(1);

        // Cleanup
        await standaloneClient.close();
        await clusterClient.close();
    });

    it("should handle rapid client creation and destruction", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();
        const cycleCount = 50;

        // Act: Rapid creation/destruction cycles
        for (let i = 0; i < cycleCount; i++) {
            const clients = await Promise.all([
                createClientWithSocketReference({ ...serverConfig, socketPath }),
                createClientWithSocketReference({ ...serverConfig, socketPath }),
                createClientWithSocketReference({ ...serverConfig, socketPath }),
            ]);

            // Verify all clients share socket
            expect(clients[0].socketReference!.referenceCount).toBe(3);

            // Perform basic operation
            await Promise.all(clients.map(client => client.ping()));

            // Close all clients
            await Promise.all(clients.map(client => client.close()));

            // Brief pause every 10 cycles
            if (i % 10 === 0) {
                await testUtils.waitForCleanup();
            }
        }

        // Assert: No resource leaks
        await testUtils.waitForCleanup();
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });
});

describe("Socket Reference Integration: Worker Thread Scenarios", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle socket sharing across worker threads", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const workerScript = testUtils.createWorkerScript(`
            const { parentPort } = require('worker_threads');
            const { createClientWithSocketReference } = require('../build-ts');

            async function runWorker() {
                try {
                    const client = await createClientWithSocketReference({
                        addresses: [{ host: 'localhost', port: 6379 }],
                        socketPath: '${socketPath}'
                    });

                    const result = await client.ping();
                    const socketInfo = {
                        path: client.socketReference.path,
                        referenceCount: client.socketReference.referenceCount,
                        isActive: client.socketReference.isActive
                    };

                    await client.close();
                    parentPort.postMessage({ result, socketInfo });
                } catch (error) {
                    parentPort.postMessage({ error: error.message });
                }
            }

            runWorker();
        `);

        // Act: Create workers that share the same socket
        const workers = [
            new Worker(workerScript),
            new Worker(workerScript),
            new Worker(workerScript),
        ];

        const results = await Promise.all(
            workers.map(worker =>
                new Promise((resolve, reject) => {
                    worker.on('message', resolve);
                    worker.on('error', reject);
                })
            )
        );

        // Assert: Workers successfully shared socket
        for (const result of results) {
            const typedResult = result as any;
            expect(typedResult.error).toBeUndefined();
            expect(typedResult.result).toBe("PONG");
            expect(typedResult.socketInfo.path).toBe(socketPath);
            expect(typedResult.socketInfo.isActive).toBe(true);
        }

        // Cleanup workers
        await Promise.all(workers.map(worker => worker.terminate()));
    });

    it("should cleanup socket when all worker threads exit", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const longRunningWorkerScript = testUtils.createWorkerScript(`
            const { parentPort } = require('worker_threads');
            const { createClientWithSocketReference } = require('../build-ts');

            async function runWorker() {
                const client = await createClientWithSocketReference({
                    addresses: [{ host: 'localhost', port: 6379 }],
                    socketPath: '${socketPath}'
                });

                // Signal ready
                parentPort.postMessage('ready');

                // Wait for exit signal
                parentPort.on('message', async (message) => {
                    if (message === 'exit') {
                        await client.close();
                        parentPort.postMessage('closed');
                    }
                });
            }

            runWorker();
        `);

        // Act: Start multiple long-running workers
        const workers = [
            new Worker(longRunningWorkerScript),
            new Worker(longRunningWorkerScript),
        ];

        // Wait for workers to be ready
        await Promise.all(workers.map(worker =>
            new Promise(resolve => {
                worker.on('message', (message) => {
                    if (message === 'ready') resolve(message);
                });
            })
        ));

        // Verify socket exists
        expect(testUtils.socketFileExists(socketPath)).toBe(true);

        // Signal workers to exit
        workers.forEach(worker => worker.postMessage('exit'));

        // Wait for workers to close clients
        await Promise.all(workers.map(worker =>
            new Promise(resolve => {
                worker.on('message', (message) => {
                    if (message === 'closed') resolve(message);
                });
            })
        ));

        // Terminate workers
        await Promise.all(workers.map(worker => worker.terminate()));
        await testUtils.waitForCleanup();

        // Assert: Socket should be cleaned up
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });
});

describe("Socket Reference Integration: Complex Lifecycle Scenarios", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle client reconnection scenarios", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();

        // Create initial client
        let client = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        const initialRefCount = client.socketReference!.referenceCount;
        expect(initialRefCount).toBe(1);

        // Act: Simulate connection issues and reconnection
        await client.close();
        await testUtils.waitForCleanup();

        // Recreate client with same socket path
        client = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        // Assert: New client should have fresh socket reference
        expect(client.socketReference!.referenceCount).toBe(1);
        expect(client.socketReference!.path).toBe(socketPath);
        expect(client.socketReference!.isActive).toBe(true);

        // Cleanup
        await client.close();
    });

    it("should handle graceful shutdown with multiple client types", async () => {
        // Arrange
        const socketPath1 = testUtils.getTestSocketPath();
        const socketPath2 = testUtils.getTestSocketPath();
        const standaloneConfig = testUtils.getStandaloneServerConfig();
        const clusterConfig = testUtils.getClusterServerConfig();

        // Create mix of client types
        const clients = await Promise.all([
            createClientWithSocketReference({
                ...standaloneConfig,
                socketPath: socketPath1,
            }),
            createClientWithSocketReference({
                ...standaloneConfig,
                socketPath: socketPath1,
            }),
            createClusterClientWithSocketReference({
                ...clusterConfig,
                socketPath: socketPath2,
            }),
        ]);

        // Verify initial state
        expect(clients[0].socketReference!.referenceCount).toBe(2); // Shared socket
        expect(clients[1].socketReference!.referenceCount).toBe(2); // Shared socket
        expect(clients[2].socketReference!.referenceCount).toBe(1); // Unique socket

        // Act: Graceful shutdown
        await Promise.all(clients.map(client => client.close()));
        await testUtils.waitForCleanup();

        // Assert: All sockets cleaned up
        expect(testUtils.socketFileExists(socketPath1)).toBe(false);
        expect(testUtils.socketFileExists(socketPath2)).toBe(false);
    });

    it("should handle memory pressure during client operations", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();
        const initialMemory = await testUtils.getMemoryUsage();

        // Act: Create many clients under memory pressure
        const clientBatches: ClientWithSocketReference[][] = [];

        for (let batch = 0; batch < 10; batch++) {
            const batchClients = await Promise.all(
                Array(20).fill(0).map(() =>
                    createClientWithSocketReference({
                        ...serverConfig,
                        socketPath,
                    })
                )
            );

            clientBatches.push(batchClients);

            // Perform operations
            await Promise.all(batchClients.map(client => client.ping()));

            // Close half the clients to create churn
            await Promise.all(
                batchClients.slice(0, 10).map(client => client.close())
            );

            // Force GC every few batches
            if (batch % 3 === 0) {
                await testUtils.forceGarbageCollection();
            }
        }

        // Cleanup remaining clients
        for (const batch of clientBatches) {
            await Promise.all(
                batch.slice(10).map(client => client.close())
            );
        }

        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Memory usage should be reasonable
        const finalMemory = await testUtils.getMemoryUsage();
        const memoryGrowth = finalMemory.heapUsed - initialMemory.heapUsed;

        expect(memoryGrowth).toBeLessThan(50 * 1024 * 1024); // Less than 50MB growth
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });

    it("should handle client timeout scenarios with socket cleanup", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = {
            ...testUtils.getTestServerConfig(),
            requestTimeout: 100, // Very short timeout
        };

        // Act: Create client and trigger timeout
        const client = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        // Perform operation that might timeout
        try {
            await client.customCommand(['SLEEP', '1']); // Sleep longer than timeout
        } catch (error) {
            // Expected timeout error
        }

        // Assert: Socket should still be managed properly
        expect(client.socketReference!.isActive).toBe(true);
        expect(testUtils.socketFileExists(socketPath)).toBe(true);

        // Cleanup
        await client.close();
        await testUtils.waitForCleanup();

        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });

    it("should handle mixed auto and manual cleanup scenarios", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const serverConfig = testUtils.getTestServerConfig();

        // Create multiple clients
        const client1 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        let client2 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });
        const client3 = await createClientWithSocketReference({
            ...serverConfig,
            socketPath,
        });

        expect(client1.socketReference!.referenceCount).toBe(3);

        // Act: Mix of manual close and garbage collection
        await client1.close(); // Manual close
        client2 = null as any; // GC cleanup
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Last client should still work
        expect(client3.socketReference!.referenceCount).toBe(1);
        expect(client3.socketReference!.isActive).toBe(true);
        await expect(client3.ping()).resolves.toBe("PONG");

        // Final cleanup
        await client3.close();
        await testUtils.waitForCleanup();

        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });
});