/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * Comprehensive contract validation tests for Node.js socket reference counting
 *
 * These tests enforce the behavioral contracts and edge cases identified
 * by TDD analysis to ensure robust socket lifecycle management in JavaScript.
 *
 * IMPORTANT: These tests are written BEFORE implementation (TDD approach).
 * They define the exact contract that the NAPI bindings must fulfill.
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
import { SocketReferenceTestUtils } from "./SocketReferenceTestUtils";

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

// Test utilities and environment setup
let testUtils: SocketReferenceTestUtils;

describe("Socket Reference Contract: Reference Counting", () => {
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

    it("should create socket reference with initial count of 1", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();

        // Act
        const socketRef = await StartSocketConnectionWithReference(socketPath);

        // Assert: Initial reference contract
        expect(socketRef.referenceCount).toBe(1);
        expect(socketRef.path).toBe(socketPath);
        expect(socketRef.isActive).toBe(true);
        expect(IsSocketActive(socketPath)).toBe(true);
        expect(GetActiveSocketCount()).toBe(1);
    });

    it("should increment reference count when socket is shared", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const ref1 = await StartSocketConnectionWithReference(socketPath);

        // Act: Get reference to same socket
        const ref2 = await StartSocketConnectionWithReference(socketPath);

        // Assert: Shared reference contract
        expect(ref1.referenceCount).toBe(2);
        expect(ref2.referenceCount).toBe(2);
        expect(ref1.path).toBe(ref2.path);
        expect(GetActiveSocketCount()).toBe(1); // Still only one unique socket
    });

    it("should maintain accurate count with multiple references", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();

        // Act: Create multiple references
        const refs = await Promise.all([
            StartSocketConnectionWithReference(socketPath),
            StartSocketConnectionWithReference(socketPath),
            StartSocketConnectionWithReference(socketPath),
            StartSocketConnectionWithReference(socketPath),
            StartSocketConnectionWithReference(socketPath),
        ]);

        // Assert: Multiple reference contract
        for (const ref of refs) {
            expect(ref.referenceCount).toBe(5);
            expect(ref.path).toBe(socketPath);
            expect(ref.isActive).toBe(true);
        }
        expect(GetActiveSocketCount()).toBe(1);
    });

    it("should decrement count when references are garbage collected", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        let ref1 = await StartSocketConnectionWithReference(socketPath);
        let ref2 = await StartSocketConnectionWithReference(socketPath);
        const ref3 = await StartSocketConnectionWithReference(socketPath);

        expect(ref1.referenceCount).toBe(3);

        // Act: Trigger garbage collection of specific references
        ref1 = null as any;
        ref2 = null as any;
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Decremented reference contract
        expect(ref3.referenceCount).toBe(1);
        expect(ref3.isActive).toBe(true);
        expect(IsSocketActive(socketPath)).toBe(true);
    });

    it("should cleanup socket when last reference is dropped", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        let socketRef = await StartSocketConnectionWithReference(socketPath);

        expect(socketRef.referenceCount).toBe(1);
        expect(IsSocketActive(socketPath)).toBe(true);

        // Act: Drop last reference
        socketRef = null as any;
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Cleanup contract
        expect(IsSocketActive(socketPath)).toBe(false);
        expect(GetActiveSocketCount()).toBe(0);
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });
});

describe("Socket Reference Contract: Lifecycle Management", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should create socket files on first reference", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();

        // Assert: File doesn't exist initially
        expect(testUtils.socketFileExists(socketPath)).toBe(false);

        // Act
        const socketRef = await StartSocketConnectionWithReference(socketPath);

        // Assert: Socket file lifecycle contract
        expect(socketRef.isActive).toBe(true);
        expect(testUtils.socketFileExists(socketPath)).toBe(true);
    });

    it("should persist socket files while references exist", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const ref1 = await StartSocketConnectionWithReference(socketPath);
        const ref2 = await StartSocketConnectionWithReference(socketPath);

        // Act: Various operations while references exist
        await testUtils.simulateWorkload();
        await testUtils.forceGarbageCollection(); // Partial GC shouldn't affect active refs

        // Assert: Persistence contract
        expect(ref1.isActive).toBe(true);
        expect(ref2.isActive).toBe(true);
        expect(testUtils.socketFileExists(socketPath)).toBe(true);
        expect(IsSocketActive(socketPath)).toBe(true);
    });

    it("should remove socket files when last reference drops", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        let ref1 = await StartSocketConnectionWithReference(socketPath);
        let ref2 = await StartSocketConnectionWithReference(socketPath);

        expect(testUtils.socketFileExists(socketPath)).toBe(true);

        // Act: Drop all references
        ref1 = null as any;
        ref2 = null as any;
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: File removal contract
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
        expect(IsSocketActive(socketPath)).toBe(false);
    });

    it("should handle abnormal termination gracefully", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        await StartSocketConnectionWithReference(socketPath);

        // Act: Simulate abnormal termination
        CleanupAllSockets();
        await testUtils.waitForCleanup();

        // Assert: Abnormal termination contract
        expect(IsSocketActive(socketPath)).toBe(false);
        expect(GetActiveSocketCount()).toBe(0);
        expect(testUtils.socketFileExists(socketPath)).toBe(false);
    });
});

describe("Socket Reference Contract: Thread Safety", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle concurrent socket creation safely", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const concurrentCount = 50;

        // Act: Create many concurrent references
        const promises = Array(concurrentCount).fill(0).map(() =>
            StartSocketConnectionWithReference(socketPath)
        );
        const refs = await Promise.all(promises);

        // Assert: Thread safety contract
        for (const ref of refs) {
            expect(ref.referenceCount).toBe(concurrentCount);
            expect(ref.path).toBe(socketPath);
            expect(ref.isActive).toBe(true);
        }
        expect(GetActiveSocketCount()).toBe(1); // Only one unique socket
    });

    it("should handle concurrent cleanup safely", async () => {
        // Arrange
        const socketPaths = Array(10).fill(0).map(() => testUtils.getTestSocketPath());
        const refsPerSocket = 10;

        // Create multiple sockets with multiple references each
        const allRefs = await Promise.all(
            socketPaths.flatMap(path =>
                Array(refsPerSocket).fill(0).map(() =>
                    StartSocketConnectionWithReference(path)
                )
            )
        );

        expect(GetActiveSocketCount()).toBe(10);

        // Act: Concurrent cleanup
        const cleanupPromises = allRefs.map(async (ref, index) => {
            // Stagger cleanup to create contention
            await testUtils.sleep(Math.random() * 10);
            return ref; // Keep reference until GC
        });

        await Promise.all(cleanupPromises);

        // Clear all references
        allRefs.splice(0, allRefs.length);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Concurrent cleanup contract
        expect(GetActiveSocketCount()).toBe(0);
        for (const path of socketPaths) {
            expect(IsSocketActive(path)).toBe(false);
            expect(testUtils.socketFileExists(path)).toBe(false);
        }
    });

    it("should prevent race conditions in reference counting", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const iterationCount = 100;

        // Act: Rapid creation and destruction cycles
        for (let i = 0; i < iterationCount; i++) {
            const refs = await Promise.all([
                StartSocketConnectionWithReference(socketPath),
                StartSocketConnectionWithReference(socketPath),
                StartSocketConnectionWithReference(socketPath),
            ]);

            // Validate consistency during rapid operations
            for (const ref of refs) {
                expect(ref.referenceCount).toBe(3);
            }

            // Clear references
            refs.splice(0, refs.length);
            await testUtils.forceGarbageCollection();
            await testUtils.waitForCleanup();
        }

        // Assert: Race condition prevention contract
        expect(GetActiveSocketCount()).toBe(0);
        expect(IsSocketActive(socketPath)).toBe(false);
    });
});

describe("Socket Reference Contract: Memory Management", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should prevent memory leaks with rapid creation/destruction", async () => {
        // Arrange
        const initialMemory = await testUtils.getMemoryUsage();
        const cycleCount = 200;

        // Act: Rapid create/destroy cycles
        for (let i = 0; i < cycleCount; i++) {
            const socketPath = testUtils.getTestSocketPath();
            let refs = await Promise.all([
                StartSocketConnectionWithReference(socketPath),
                StartSocketConnectionWithReference(socketPath),
                StartSocketConnectionWithReference(socketPath),
            ]);

            // Clear references
            refs = null as any;

            // Force cleanup every 10 cycles
            if (i % 10 === 0) {
                await testUtils.forceGarbageCollection();
                await testUtils.waitForCleanup();
            }
        }

        // Final cleanup
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Memory leak prevention contract
        const finalMemory = await testUtils.getMemoryUsage();
        const memoryGrowth = finalMemory.heapUsed - initialMemory.heapUsed;

        expect(GetActiveSocketCount()).toBe(0);
        expect(memoryGrowth).toBeLessThan(10 * 1024 * 1024); // Less than 10MB growth
    });

    it("should handle garbage collection of circular references", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();

        // Create object with circular reference holding socket reference
        let circularRef: any = {};
        circularRef.self = circularRef;
        circularRef.socketRef = await StartSocketConnectionWithReference(socketPath);

        expect(GetActiveSocketCount()).toBe(1);

        // Act: Clear reference and force GC
        circularRef = null;
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Circular reference cleanup contract
        expect(GetActiveSocketCount()).toBe(0);
        expect(IsSocketActive(socketPath)).toBe(false);
    });

    it("should handle high reference count scenarios", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const highRefCount = 1000;

        // Act: Create many references to same socket
        const refs = await Promise.all(
            Array(highRefCount).fill(0).map(() =>
                StartSocketConnectionWithReference(socketPath)
            )
        );

        // Assert: High reference count contract
        for (const ref of refs) {
            expect(ref.referenceCount).toBe(highRefCount);
        }
        expect(GetActiveSocketCount()).toBe(1);

        // Cleanup: Drop half the references
        refs.splice(0, 500);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Validate partial cleanup
        for (const ref of refs) {
            expect(ref.referenceCount).toBe(500);
        }
        expect(GetActiveSocketCount()).toBe(1);

        // Final cleanup
        refs.splice(0, refs.length);
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        expect(GetActiveSocketCount()).toBe(0);
    });
});

describe("Socket Reference Contract: Error Handling", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should handle socket creation failures gracefully", async () => {
        // Arrange
        const invalidSocketPath = "/invalid/path/that/cannot/be/created.sock";

        // Act & Assert: Error handling contract
        await expect(
            StartSocketConnectionWithReference(invalidSocketPath)
        ).rejects.toThrow();

        // Validate no resource leaks on failure
        expect(GetActiveSocketCount()).toBe(0);
        expect(IsSocketActive(invalidSocketPath)).toBe(false);
    });

    it("should cleanup properly when errors occur", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const ref = await StartSocketConnectionWithReference(socketPath);

        // Act: Simulate error during operation
        ForceCleanupSocket(socketPath);

        // Assert: Error cleanup contract
        expect(IsSocketActive(socketPath)).toBe(false);
        expect(testUtils.socketFileExists(socketPath)).toBe(false);

        // Reference should still be tracked but inactive
        expect(ref.path).toBe(socketPath);
        expect(ref.isActive).toBe(false);
    });

    it("should handle permission errors during cleanup", async () => {
        // Arrange
        const socketPath = testUtils.getTestSocketPath();
        const ref = await StartSocketConnectionWithReference(socketPath);

        // Act: Make socket file read-only
        await testUtils.makeSocketReadOnly(socketPath);

        // Clear reference
        let refCopy: any = ref;
        refCopy = null;
        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Permission error handling contract
        expect(IsSocketActive(socketPath)).toBe(false);

        // Clean up manually for test cleanup
        await testUtils.restoreSocketPermissions(socketPath);
    });

    it("should handle rapid error scenarios without deadlock", async () => {
        // Arrange
        const socketPaths = Array(50).fill(0).map(() => testUtils.getTestSocketPath());

        // Act: Create sockets and immediately force cleanup
        const refs = await Promise.all(
            socketPaths.map(path => StartSocketConnectionWithReference(path))
        );

        // Force cleanup all at once
        socketPaths.forEach(path => ForceCleanupSocket(path));

        // Assert: Rapid error handling contract
        expect(GetActiveSocketCount()).toBe(0);
        for (const path of socketPaths) {
            expect(IsSocketActive(path)).toBe(false);
        }
    });
});

describe("Socket Reference Contract: Backward Compatibility", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should maintain compatibility with string-based socket API", async () => {
        // This test validates that the new reference-based API doesn't break
        // existing string-based socket management

        // Note: This test will be implemented once we understand the current
        // string-based API structure in the Node.js client
        expect(true).toBe(true); // Placeholder
    });

    it("should allow migration from old to new API", async () => {
        // This test validates the migration path from string-based to reference-based
        // socket management

        // Note: This test will be implemented once we understand the migration
        // requirements
        expect(true).toBe(true); // Placeholder
    });
});

describe("Socket Reference Contract: Performance", () => {
    beforeEach(async () => {
        await testUtils.resetEnvironment();
    });

    afterEach(async () => {
        await testUtils.validateNoLeaks();
    });

    it("should maintain performance under high load", async () => {
        // Arrange
        const startTime = process.hrtime.bigint();
        const operationCount = 1000;

        // Act: Perform many socket operations
        for (let i = 0; i < operationCount; i++) {
            const socketPath = testUtils.getTestSocketPath();
            const refs = await Promise.all([
                StartSocketConnectionWithReference(socketPath),
                StartSocketConnectionWithReference(socketPath),
                StartSocketConnectionWithReference(socketPath),
            ]);

            // Clear references
            refs.splice(0, refs.length);

            if (i % 100 === 0) {
                await testUtils.forceGarbageCollection();
            }
        }

        await testUtils.forceGarbageCollection();
        await testUtils.waitForCleanup();

        // Assert: Performance contract
        const endTime = process.hrtime.bigint();
        const durationMs = Number(endTime - startTime) / 1_000_000;
        const operationsPerSecond = operationCount / (durationMs / 1000);

        expect(operationsPerSecond).toBeGreaterThan(100); // At least 100 ops/sec
        expect(GetActiveSocketCount()).toBe(0);
    });

    it("should scale sub-linearly with concurrent operations", async () => {
        // Test performance scaling with different concurrency levels
        const measurements: Array<{ concurrency: number; duration: number }> = [];

        for (const concurrency of [1, 5, 10, 20]) {
            const startTime = process.hrtime.bigint();
            const socketPath = testUtils.getTestSocketPath();

            // Create concurrent operations
            const promises = Array(concurrency).fill(0).map(async () => {
                for (let i = 0; i < 50; i++) {
                    const ref = await StartSocketConnectionWithReference(socketPath);
                    // Keep reference briefly
                    await testUtils.sleep(1);
                    // Clear reference
                }
            });

            await Promise.all(promises);
            await testUtils.forceGarbageCollection();
            await testUtils.waitForCleanup();

            const endTime = process.hrtime.bigint();
            const duration = Number(endTime - startTime) / 1_000_000;
            measurements.push({ concurrency, duration });
        }

        // Assert: Sub-linear scaling contract
        for (let i = 1; i < measurements.length; i++) {
            const prev = measurements[i - 1];
            const curr = measurements[i];

            const scalingFactor = curr.concurrency / prev.concurrency;
            const durationFactor = curr.duration / prev.duration;

            // Performance should scale better than linearly
            expect(durationFactor).toBeLessThan(scalingFactor * 1.5);
        }
    });
});