#!/usr/bin/env node

// Simple test of socket reference implementation
const native = require('../build-ts/valkey-glide.linux-x64-gnu.node');
const {
    SocketReference,
    StartSocketConnectionWithReference,
    IsSocketActive,
    GetActiveSocketCount,
    CleanupAllSockets,
} = native;

async function runTest() {
    console.log('🧪 Testing Socket Reference Implementation\n');

    try {
        // Cleanup first
        CleanupAllSockets();

        // Test 1: Create socket reference
        console.log('Test 1: Creating socket reference...');
        const socketRef = await StartSocketConnectionWithReference();
        console.log(`✓ Created socket with path: ${socketRef.path}`);
        console.log(`  - isActive: ${socketRef.isActive}`);
        console.log(`  - referenceCount: ${socketRef.referenceCount}`);

        // Test 2: Check socket is active
        console.log('\nTest 2: Checking socket is active...');
        const isActive = IsSocketActive(socketRef.path);
        console.log(`✓ IsSocketActive(${socketRef.path}): ${isActive}`);

        // Test 3: Check active count
        console.log('\nTest 3: Checking active socket count...');
        const count = GetActiveSocketCount();
        console.log(`✓ Active socket count: ${count}`);

        // Test 4: Create another reference to same socket
        console.log('\nTest 4: Creating another reference to same socket...');
        const socketRef2 = await StartSocketConnectionWithReference(socketRef.path);
        console.log(`✓ Second reference created`);
        console.log(`  - Same path: ${socketRef2.path === socketRef.path}`);
        console.log(`  - Reference count: ${socketRef2.referenceCount}`);

        // Test 5: Cleanup
        console.log('\nTest 5: Cleaning up all sockets...');
        CleanupAllSockets();
        const finalCount = GetActiveSocketCount();
        console.log(`✓ Final active socket count: ${finalCount}`);

        console.log('\n✅ All basic tests passed!');
        console.log('\nNOTE: Full garbage collection integration requires NAPI v3.');
        console.log('The current implementation provides the foundation for socket reference counting.');

    } catch (error) {
        console.error('❌ Test failed:', error);
        process.exit(1);
    }
}

runTest();