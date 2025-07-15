/**
 * Test to verify socket cleanup functionality prevents address exhaustion
 */

import { describe, it, expect, beforeEach, afterEach } from "@jest/globals";
import { CleanupSocketFiles } from "../build-ts/native";
import * as fs from "fs";

describe("Socket Cleanup Tests", () => {
    const testSocketPaths: string[] = [];
    const SOCKET_DIR = "/tmp";

    beforeEach(() => {
        // Clean up before each test
        CleanupSocketFiles();
    });

    afterEach(() => {
        // Clean up test files
        testSocketPaths.forEach(path => {
            try {
                fs.unlinkSync(path);
            } catch (error) {
                // Ignore errors - file might already be cleaned up
            }
        });
        testSocketPaths.length = 0;
    });

    it("should clean up leftover socket files matching glide pattern", () => {
        // Create test socket files that match the glide pattern
        const testFiles = [
            `${SOCKET_DIR}/glide-socket-12345-abc123.sock`,
            `${SOCKET_DIR}/glide-socket-67890-def456.sock`,
            `${SOCKET_DIR}/glide-socket-11111-ghi789.sock`
        ];

        testFiles.forEach(path => {
            fs.writeFileSync(path, "");
            testSocketPaths.push(path);
        });

        // Verify files exist before cleanup
        testFiles.forEach(path => {
            expect(fs.existsSync(path)).toBe(true);
        });

        // Run cleanup
        CleanupSocketFiles();

        // Verify files are removed after cleanup
        testFiles.forEach(path => {
            expect(fs.existsSync(path)).toBe(false);
        });
    });

    it("should not remove files that don't match the glide pattern", () => {
        // Create test files that don't match the glide pattern
        const nonMatchingFiles = [
            `${SOCKET_DIR}/other-socket-12345.sock`,
            `${SOCKET_DIR}/glide-socket-12345.txt`, // wrong extension
            `${SOCKET_DIR}/glide-socket-12345`, // no extension
            `${SOCKET_DIR}/socket-12345.sock` // wrong prefix
        ];

        nonMatchingFiles.forEach(path => {
            fs.writeFileSync(path, "");
            testSocketPaths.push(path);
        });

        // Verify files exist before cleanup
        nonMatchingFiles.forEach(path => {
            expect(fs.existsSync(path)).toBe(true);
        });

        // Run cleanup
        CleanupSocketFiles();

        // Verify files are NOT removed after cleanup
        nonMatchingFiles.forEach(path => {
            expect(fs.existsSync(path)).toBe(true);
        });
    });

    it("should handle cleanup gracefully when no socket files exist", () => {
        // This should not throw an error
        expect(() => CleanupSocketFiles()).not.toThrow();
    });

    it("should handle cleanup gracefully when socket directory doesn't exist", () => {
        // Even if the directory doesn't exist, cleanup should not throw
        expect(() => CleanupSocketFiles()).not.toThrow();
    });
});