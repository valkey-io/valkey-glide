#!/usr/bin/env node

// Test what's exported from the native module
const native = require('../build-ts/valkey-glide.linux-x64-gnu.node');

console.log('Native module exports:');
console.log('======================');

const moduleExports = Object.keys(native).sort();
moduleExports.forEach(exp => {
    const type = typeof native[exp];
    console.log(`- ${exp}: ${type}`);
});

// Check for socket reference functions
console.log('\nSocket Reference Functions:');
console.log('---------------------------');
const socketFuncs = [
    'SocketReference',
    'StartSocketConnectionWithReference',
    'IsSocketActive',
    'GetActiveSocketCount',
    'CleanupAllSockets',
    'ForceCleanupSocket'
];

socketFuncs.forEach(func => {
    if (native[func]) {
        console.log(`✓ ${func}: ${typeof native[func]}`);
    } else {
        console.log(`✗ ${func}: not found`);
    }
});

// Check existing StartSocketConnection
if (native.StartSocketConnection) {
    console.log('\n✓ StartSocketConnection (legacy) exists');
}