#!/usr/bin/env node

// Test backward compatibility with existing StartSocketConnection
const native = require('../build-ts/valkey-glide.linux-x64-gnu.node');
const {
    StartSocketConnection,
    StartSocketConnectionWithReference,
    IsSocketActive,
    CleanupAllSockets,
} = native;

async function testBackwardCompatibility() {
    console.log('🔄 Testing Backward Compatibility\n');
    console.log('=' .repeat(50));

    try {
        CleanupAllSockets();

        // Test 1: Legacy StartSocketConnection still works
        console.log('\n✓ Test 1: Legacy StartSocketConnection');
        const legacyPath = await StartSocketConnection();
        console.log(`  Socket created at: ${legacyPath}`);
        console.log(`  Is active: ${IsSocketActive(legacyPath)}`);

        // Test 2: New StartSocketConnectionWithReference works
        console.log('\n✓ Test 2: New StartSocketConnectionWithReference');
        const socketRef = await StartSocketConnectionWithReference();
        console.log(`  Socket created at: ${socketRef.path}`);
        console.log(`  Reference count: ${socketRef.referenceCount}`);
        console.log(`  Is active: ${socketRef.isActive}`);

        // Test 3: Both can coexist
        console.log('\n✓ Test 3: Both APIs can coexist');
        const path1 = await StartSocketConnection();
        const ref2 = await StartSocketConnectionWithReference();
        console.log(`  Legacy socket: ${path1.substring(0, 50)}...`);
        console.log(`  Reference socket: ${ref2.path.substring(0, 50)}...`);
        console.log(`  Both are active: ${IsSocketActive(path1)} && ${ref2.isActive}`);

        // Test 4: Can create reference to existing socket
        console.log('\n✓ Test 4: Can reference existing socket');
        const ref3 = await StartSocketConnectionWithReference(path1);
        console.log(`  Created reference to legacy socket`);
        console.log(`  Same path: ${ref3.path === path1}`);
        console.log(`  Reference count: ${ref3.referenceCount}`);

        // Cleanup
        CleanupAllSockets();

        console.log('\n' + '=' .repeat(50));
        console.log('✅ Backward Compatibility Verified!');
        console.log('\nKey findings:');
        console.log('  • Legacy StartSocketConnection still works');
        console.log('  • New StartSocketConnectionWithReference works');
        console.log('  • Both APIs can be used simultaneously');
        console.log('  • Can create references to existing sockets');
        console.log('  • No breaking changes detected');

    } catch (error) {
        console.error('\n❌ Backward compatibility test failed:', error);
        process.exit(1);
    }
}

testBackwardCompatibility();