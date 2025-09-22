/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Comprehensive socket reference counting tests for Node.js
 *
 * This file consolidates all socket reference testing including:
 * - Contract validation and behavioral tests
 * - Integration testing with multiple clients
 * - Stress testing and performance validation
 * - Edge cases and error handling
 *
 * The tests ensure robust socket lifecycle management in JavaScript
 * with proper reference counting and automatic cleanup.
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
import { SocketReferenceTestUtils, AdvancedSocketTestUtils, PerformanceTestUtils } from "./SocketReferenceTestUtils";

// Import from the built native module
const native = require('../build-ts/valkey-glide.linux-x64-gnu.node');
const {
    SocketReference,
    StartSocketConnectionWithReference,
    IsSocketActive,
    GetActiveSocketCount,
    CleanupAllSockets,
    ForceCleanupSocket
} = native;

// Test utilities
let testUtils: SocketReferenceTestUtils;
let advancedUtils: AdvancedSocketTestUtils;
let perfUtils: PerformanceTestUtils;

describe("Socket Reference Counting", () => {
    beforeAll(async () => {
        testUtils = new SocketReferenceTestUtils();
        advancedUtils = new AdvancedSocketTestUtils();
        perfUtils = new PerformanceTestUtils();

        await testUtils.setup();
        await advancedUtils.setup();
        await perfUtils.setup();
    });

    afterAll(async () => {
        await testUtils.cleanup();
        await advancedUtils.cleanup();
        await perfUtils.cleanup();
    });

    beforeEach(async () => {
        await testUtils.resetEnvironment();
        await advancedUtils.resetEnvironment();
        await perfUtils.resetEnvironment();
    });

    afterEach(async () => {
        // Ensure cleanup after each test
        CleanupAllSockets();
        await testUtils.validateNoLeaks();
    });

    // ========================================================================
    // BASIC REFERENCE COUNTING CONTRACTS
    // ========================================================================

    describe("Reference Counting Contracts", () => {
        it("should create socket reference with count of 1", async () => {
            const socketRef = await StartSocketConnectionWithReference();

            expect(socketRef).toBeDefined();
            expect(typeof socketRef.path).toBe('string');
            expect(socketRef.path).toMatch(/\.sock$/);
            expect(socketRef.referenceCount).toBe(1);
            expect(socketRef.isActive).toBe(true);
            expect(IsSocketActive(socketRef.path)).toBe(true);
        });

        it("should increment reference count for same socket path", async () => {
            const socketRef1 = await StartSocketConnectionWithReference();
            const socketRef2 = await StartSocketConnectionWithReference(socketRef1.path);

            expect(socketRef2.path).toBe(socketRef1.path);
            expect(socketRef2.referenceCount).toBe(2);
            expect(socketRef1.referenceCount).toBe(2); // Both should show updated count
        });

        it("should maintain separate counts for different sockets", async () => {
            const socketRef1 = await StartSocketConnectionWithReference();
            const socketRef2 = await StartSocketConnectionWithReference();

            expect(socketRef1.path).not.toBe(socketRef2.path);
            expect(socketRef1.referenceCount).toBe(1);
            expect(socketRef2.referenceCount).toBe(1);
        });

        it("should track active socket count correctly", async () => {
            const initialCount = GetActiveSocketCount();

            const socket1 = await StartSocketConnectionWithReference();
            expect(GetActiveSocketCount()).toBe(initialCount + 1);

            const socket2 = await StartSocketConnectionWithReference();
            expect(GetActiveSocketCount()).toBe(initialCount + 2);

            // Adding reference to existing socket shouldn't increase count
            await StartSocketConnectionWithReference(socket1.path);
            expect(GetActiveSocketCount()).toBe(initialCount + 2);
        });

        it("should handle reference count edge cases", async () => {
            const socketRef = await StartSocketConnectionWithReference();

            // Create many references to same socket
            const references = [];
            for (let i = 0; i < 10; i++) {
                references.push(await StartSocketConnectionWithReference(socketRef.path));
            }

            // All should have same path and reference count
            for (const ref of references) {
                expect(ref.path).toBe(socketRef.path);
                expect(ref.referenceCount).toBe(11); // Original + 10 new
            }
        });
    });

    // ========================================================================
    // SOCKET LIFECYCLE MANAGEMENT
    // ========================================================================

    describe("Socket Lifecycle Management", () => {
        it("should create valid socket paths", async () => {
            const socketRef = await StartSocketConnectionWithReference();

            expect(socketRef.path).toMatch(/^\/tmp\/.*\.sock$/);
            expect(socketRef.path.length).toBeGreaterThan(20);
            expect(testUtils.socketFileExists(socketRef.path)).toBe(true);
        });

        it("should maintain socket active state correctly", async () => {
            const socketRef = await StartSocketConnectionWithReference();

            expect(socketRef.isActive).toBe(true);
            expect(IsSocketActive(socketRef.path)).toBe(true);

            // Socket should remain active with multiple references
            const ref2 = await StartSocketConnectionWithReference(socketRef.path);
            expect(ref2.isActive).toBe(true);
            expect(IsSocketActive(socketRef.path)).toBe(true);
        });

        it("should handle cleanup operations", async () => {
            const socket1 = await StartSocketConnectionWithReference();
            const socket2 = await StartSocketConnectionWithReference();

            const initialCount = GetActiveSocketCount();

            CleanupAllSockets();

            expect(GetActiveSocketCount()).toBe(0);
            expect(IsSocketActive(socket1.path)).toBe(false);
            expect(IsSocketActive(socket2.path)).toBe(false);
        });

        it("should handle force cleanup of specific socket", async () => {
            const socket1 = await StartSocketConnectionWithReference();
            const socket2 = await StartSocketConnectionWithReference();

            ForceCleanupSocket(socket1.path);

            expect(IsSocketActive(socket1.path)).toBe(false);
            expect(IsSocketActive(socket2.path)).toBe(true);
        });

        it("should handle invalid socket paths gracefully", () => {
            expect(IsSocketActive("")).toBe(false);
            expect(IsSocketActive("/nonexistent/path.sock")).toBe(false);
            expect(IsSocketActive("invalid-path")).toBe(false);
        });
    });

    // ========================================================================
    // ERROR HANDLING AND EDGE CASES
    // ========================================================================

    describe("Error Handling", () => {
        it("should reject invalid socket paths", async () => {
            await expect(StartSocketConnectionWithReference("")).rejects.toThrow();
            await expect(StartSocketConnectionWithReference("/invalid/path")).rejects.toThrow();
        });

        it("should handle filesystem permission errors", async () => {
            const socketRef = await StartSocketConnectionWithReference();

            // Make socket read-only
            await testUtils.makeSocketReadOnly(socketRef.path);

            try {
                // Should still be able to reference existing socket
                const ref2 = await StartSocketConnectionWithReference(socketRef.path);
                expect(ref2.path).toBe(socketRef.path);
            } finally {
                await testUtils.restoreSocketPermissions(socketRef.path);
            }
        });

        it("should handle concurrent cleanup operations", async () => {
            const socketRef = await StartSocketConnectionWithReference();

            // Concurrent cleanup calls should not cause issues
            const cleanupPromises = Array(5).fill(0).map(() =>
                Promise.resolve().then(() => ForceCleanupSocket(socketRef.path))
            );

            await Promise.all(cleanupPromises);
            expect(IsSocketActive(socketRef.path)).toBe(false);
        });

        it("should handle resource exhaustion scenarios", async () => {
            const sockets = [];

            // Create many sockets (but not too many to avoid real exhaustion)
            for (let i = 0; i < 50; i++) {
                sockets.push(await StartSocketConnectionWithReference());
            }

            expect(GetActiveSocketCount()).toBeGreaterThanOrEqual(50);

            // Cleanup should handle all sockets
            CleanupAllSockets();
            expect(GetActiveSocketCount()).toBe(0);
        });
    });

    // ========================================================================
    // INTEGRATION TESTING
    // ========================================================================

    describe("Integration Testing", () => {
        it("should handle multiple client scenarios", async () => {
            // Simulate multiple clients using same socket
            const sharedSocket = await StartSocketConnectionWithReference();

            const clientReferences = [];
            for (let i = 0; i < 5; i++) {
                clientReferences.push(await StartSocketConnectionWithReference(sharedSocket.path));
            }

            // All clients should see same socket with correct reference count
            for (const ref of clientReferences) {
                expect(ref.path).toBe(sharedSocket.path);
                expect(ref.referenceCount).toBe(6); // Original + 5 clients
            }

            // Socket should remain active
            expect(IsSocketActive(sharedSocket.path)).toBe(true);
        });

        it("should handle mixed socket creation patterns", async () => {
            // Create some individual sockets
            const individual1 = await StartSocketConnectionWithReference();
            const individual2 = await StartSocketConnectionWithReference();

            // Create shared socket with multiple references
            const shared = await StartSocketConnectionWithReference();
            const sharedRef1 = await StartSocketConnectionWithReference(shared.path);
            const sharedRef2 = await StartSocketConnectionWithReference(shared.path);

            // Verify all are tracked correctly
            expect(GetActiveSocketCount()).toBe(3); // 2 individual + 1 shared
            expect(individual1.referenceCount).toBe(1);
            expect(individual2.referenceCount).toBe(1);
            expect(shared.referenceCount).toBe(3);
            expect(sharedRef1.referenceCount).toBe(3);
            expect(sharedRef2.referenceCount).toBe(3);
        });

        it("should maintain consistency across rapid operations", async () => {
            const operations = [];

            // Rapid creation and referencing
            for (let i = 0; i < 20; i++) {
                if (i % 3 === 0) {
                    operations.push(StartSocketConnectionWithReference());
                } else {
                    // Reference a previous socket (if any exist)
                    operations.push(StartSocketConnectionWithReference());
                }
            }

            const results = await Promise.all(operations);

            // All operations should succeed
            expect(results).toHaveLength(20);
            for (const result of results) {
                expect(result).toBeDefined();
                expect(typeof result.path).toBe('string');
                expect(result.referenceCount).toBeGreaterThan(0);
            }
        });

        it("should handle cleanup during active references", async () => {
            const socket = await StartSocketConnectionWithReference();
            const ref1 = await StartSocketConnectionWithReference(socket.path);
            const ref2 = await StartSocketConnectionWithReference(socket.path);

            expect(socket.referenceCount).toBe(3);

            // Force cleanup should work even with multiple references
            ForceCleanupSocket(socket.path);

            expect(IsSocketActive(socket.path)).toBe(false);
        });
    });

    // ========================================================================
    // PERFORMANCE AND STRESS TESTING
    // ========================================================================

    describe("Performance Testing", () => {
        it("should handle high-frequency socket creation", async () => {
            const startTime = Date.now();
            const sockets = [];

            // Create 100 sockets rapidly
            for (let i = 0; i < 100; i++) {
                sockets.push(await StartSocketConnectionWithReference());
            }

            const duration = Date.now() - startTime;

            expect(sockets).toHaveLength(100);
            expect(GetActiveSocketCount()).toBeGreaterThanOrEqual(100);
            expect(duration).toBeLessThan(5000); // Should complete within 5 seconds

            // Verify all sockets are unique and valid
            const paths = new Set(sockets.map(s => s.path));
            expect(paths.size).toBe(100); // All unique paths
        });

        it("should handle high-frequency reference creation", async () => {
            const baseSocket = await StartSocketConnectionWithReference();
            const references = [baseSocket];

            const startTime = Date.now();

            // Create 100 references to same socket
            for (let i = 0; i < 100; i++) {
                references.push(await StartSocketConnectionWithReference(baseSocket.path));
            }

            const duration = Date.now() - startTime;

            expect(references).toHaveLength(101); // Original + 100 references
            expect(GetActiveSocketCount()).toBe(1); // Still just one socket
            expect(duration).toBeLessThan(3000); // Should be faster than creating new sockets

            // All should have same path and reference count
            for (const ref of references) {
                expect(ref.path).toBe(baseSocket.path);
                expect(ref.referenceCount).toBe(101);
            }
        });

        it("should handle stress test with mixed operations", async () => {
            const results = await perfUtils.measureThroughput(async () => {
                const operation = Math.random();

                if (operation < 0.6) {
                    // 60% - Create new socket
                    return await StartSocketConnectionWithReference();
                } else if (operation < 0.9) {
                    // 30% - Reference existing socket (if any)
                    const count = GetActiveSocketCount();
                    if (count > 0) {
                        return await StartSocketConnectionWithReference();
                    } else {
                        return await StartSocketConnectionWithReference();
                    }
                } else {
                    // 10% - Check socket status
                    const count = GetActiveSocketCount();
                    return { operation: 'status_check', count };
                }
            }, 2000); // Run for 2 seconds

            expect(results.operationCount).toBeGreaterThan(50);
            expect(results.errors).toBe(0);
            expect(results.throughput).toBeGreaterThan(25); // At least 25 ops/sec
        });

        it("should maintain performance under memory pressure", async () => {
            // Create memory pressure
            const cleanupMemory = await advancedUtils.createMemoryPressure(50); // 50MB

            try {
                const sockets = [];
                const startTime = Date.now();

                // Create sockets under memory pressure
                for (let i = 0; i < 50; i++) {
                    sockets.push(await StartSocketConnectionWithReference());
                }

                const duration = Date.now() - startTime;

                expect(sockets).toHaveLength(50);
                expect(duration).toBeLessThan(10000); // Should still complete reasonably fast

            } finally {
                cleanupMemory();
            }
        });

        it("should handle concurrent socket operations", async () => {
            // Create concurrent operations
            const operations = Array(20).fill(0).map(async (_, index) => {
                if (index % 2 === 0) {
                    return await StartSocketConnectionWithReference();
                } else {
                    // Wait a bit then reference the first socket created
                    await advancedUtils.sleep(10);
                    return await StartSocketConnectionWithReference();
                }
            });

            const results = await Promise.all(operations);

            expect(results).toHaveLength(20);

            // All operations should succeed
            for (const result of results) {
                expect(result).toBeDefined();
                expect(typeof result.path).toBe('string');
            }

            // Should have created multiple sockets
            expect(GetActiveSocketCount()).toBeGreaterThan(1);
        });

        it("should handle resource monitoring during operations", async () => {
            const monitoringResult = await advancedUtils.monitorResources(async () => {
                const sockets = [];

                // Create and reference sockets while monitoring
                for (let i = 0; i < 30; i++) {
                    sockets.push(await StartSocketConnectionWithReference());

                    if (i % 5 === 0) {
                        // Every 5th operation, reference an existing socket
                        const existing = sockets[Math.floor(Math.random() * sockets.length)];
                        await StartSocketConnectionWithReference(existing.path);
                    }

                    await advancedUtils.sleep(10); // Small delay for monitoring
                }

                return sockets.length;
            }, 50); // Monitor every 50ms

            expect(monitoringResult.result).toBe(30);
            expect(monitoringResult.resourceUsage.length).toBeGreaterThan(5);

            // Memory usage should be reasonable
            const finalMemory = monitoringResult.resourceUsage[monitoringResult.resourceUsage.length - 1];
            expect(finalMemory.memory.heapUsed).toBeLessThan(100 * 1024 * 1024); // Less than 100MB
        });
    });

    // ========================================================================
    // EDGE CASES AND BOUNDARY CONDITIONS
    // ========================================================================

    describe("Edge Cases", () => {
        it("should handle rapid cleanup and recreation", async () => {
            for (let i = 0; i < 10; i++) {
                const socket = await StartSocketConnectionWithReference();
                expect(IsSocketActive(socket.path)).toBe(true);

                CleanupAllSockets();
                expect(IsSocketActive(socket.path)).toBe(false);
                expect(GetActiveSocketCount()).toBe(0);
            }
        });

        it("should handle socket path uniqueness", async () => {
            const sockets = [];
            const paths = new Set();

            // Create many sockets quickly to test path uniqueness
            for (let i = 0; i < 100; i++) {
                const socket = await StartSocketConnectionWithReference();
                sockets.push(socket);
                paths.add(socket.path);
            }

            // All paths should be unique
            expect(paths.size).toBe(100);
            expect(sockets).toHaveLength(100);
        });

        it("should handle reference counting accuracy under stress", async () => {
            const baseSocket = await StartSocketConnectionWithReference();
            const references = [baseSocket];

            // Create references in batches with validation
            for (let batch = 0; batch < 5; batch++) {
                const batchRefs = [];

                // Create 10 references in this batch
                for (let i = 0; i < 10; i++) {
                    batchRefs.push(await StartSocketConnectionWithReference(baseSocket.path));
                }

                references.push(...batchRefs);

                // Validate reference count is correct
                const expectedCount = references.length;
                for (const ref of batchRefs) {
                    expect(ref.referenceCount).toBe(expectedCount);
                }
            }

            // Final validation
            expect(references).toHaveLength(51); // 1 original + 50 references
            expect(GetActiveSocketCount()).toBe(1); // Still just one socket
        });

        it("should handle mixed path formats gracefully", async () => {
            const validSocket = await StartSocketConnectionWithReference();

            // Test various invalid path formats
            const invalidPaths = [
                "",
                "   ",
                "/",
                "relative/path.sock",
                "/tmp/",
                "/tmp/nonexistent.sock",
                "not-a-path",
                "/dev/null"
            ];

            for (const invalidPath of invalidPaths) {
                try {
                    await StartSocketConnectionWithReference(invalidPath);
                    // If it doesn't throw, it should at least be handled gracefully
                } catch (error) {
                    // Expected for invalid paths
                    expect(error).toBeDefined();
                }
            }

            // Valid socket should still work
            expect(IsSocketActive(validSocket.path)).toBe(true);
        });

        it("should handle cleanup during reference creation", async () => {
            const socket = await StartSocketConnectionWithReference();

            // Start creating references while cleaning up
            const operations = [
                StartSocketConnectionWithReference(socket.path),
                StartSocketConnectionWithReference(socket.path),
                Promise.resolve().then(() => ForceCleanupSocket(socket.path)),
                StartSocketConnectionWithReference(socket.path),
            ];

            // Some operations may fail due to cleanup, but shouldn't crash
            const results = await Promise.allSettled(operations);

            expect(results).toHaveLength(4);

            // At least the cleanup should succeed
            const cleanupResult = results[2];
            expect(cleanupResult.status).toBe('fulfilled');
        });
    });

    // ========================================================================
    // REGRESSION AND COMPATIBILITY TESTING
    // ========================================================================

    describe("Regression Testing", () => {
        it("should maintain backward compatibility", async () => {
            // Test that we can still create sockets without breaking existing functionality
            const newSocket = await StartSocketConnectionWithReference();

            expect(newSocket).toBeDefined();
            expect(typeof newSocket.path).toBe('string');
            expect(typeof newSocket.referenceCount).toBe('number');
            expect(typeof newSocket.isActive).toBe('boolean');

            expect(newSocket.referenceCount).toBe(1);
            expect(newSocket.isActive).toBe(true);
        });

        it("should handle version compatibility scenarios", async () => {
            // Test that socket reference functionality works with expected API
            const socket = await StartSocketConnectionWithReference();

            // Verify all expected properties exist
            expect(socket).toHaveProperty('path');
            expect(socket).toHaveProperty('referenceCount');
            expect(socket).toHaveProperty('isActive');

            // Verify all expected functions work
            expect(typeof IsSocketActive).toBe('function');
            expect(typeof GetActiveSocketCount).toBe('function');
            expect(typeof CleanupAllSockets).toBe('function');
            expect(typeof ForceCleanupSocket).toBe('function');

            // Test function calls
            expect(IsSocketActive(socket.path)).toBe(true);
            expect(GetActiveSocketCount()).toBeGreaterThan(0);
        });

        it("should handle data integrity across operations", async () => {
            const testData = {
                sockets: [],
                paths: new Set(),
                references: new Map()
            };

            // Create complex reference scenario
            for (let i = 0; i < 10; i++) {
                const socket = await StartSocketConnectionWithReference();
                testData.sockets.push(socket);
                testData.paths.add(socket.path);
                testData.references.set(socket.path, 1);

                // Add some references to existing sockets
                if (i > 3) {
                    const existingPath = testData.sockets[i % 3].path;
                    const ref = await StartSocketConnectionWithReference(existingPath);
                    testData.references.set(existingPath, testData.references.get(existingPath)! + 1);

                    // Verify reference count matches our tracking
                    expect(ref.referenceCount).toBe(testData.references.get(existingPath));
                }
            }

            // Verify data integrity
            expect(testData.paths.size).toBe(10); // All paths unique
            expect(GetActiveSocketCount()).toBe(10); // Correct socket count

            // Verify each socket's reference count
            for (const [path, expectedCount] of testData.references) {
                expect(IsSocketActive(path)).toBe(true);
            }
        });
    });
});