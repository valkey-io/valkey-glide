import {
    SocketReference,
    StartSocketConnectionWithReference,
    IsSocketActive,
    GetActiveSocketCount,
    CleanupAllSockets,
    ForceCleanupSocket,
} from "./build-ts/native";

// Simple test to verify types and imports work
async function testSocketReference() {
    console.log("Testing SocketReference implementation...");

    try {
        // Test utility functions first
        console.log("Active socket count:", GetActiveSocketCount());
        console.log("Is socket active (test path):", IsSocketActive("/tmp/test.sock"));

        // Test socket creation
        console.log("Creating socket reference...");
        const socketRef = await StartSocketConnectionWithReference();

        console.log("Socket path:", socketRef.path);
        console.log("Is active:", socketRef.isActive);
        console.log("Reference count:", socketRef.referenceCount);

        // Test cleanup
        console.log("Cleaning up...");
        CleanupAllSockets();

        console.log("Test completed successfully!");
    } catch (error) {
        console.error("Test failed:", error);
    }
}

// Run the test if this file is executed directly
if (require.main === module) {
    testSocketReference().catch(console.error);
}

export { testSocketReference };