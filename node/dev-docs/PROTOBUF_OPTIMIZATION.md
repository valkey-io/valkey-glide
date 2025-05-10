# ProtobufJS Optimization

## Overview

This document describes the optimization approach used for protobufjs in the Valkey Glide Node.js client.

## Optimization Strategy

The Valkey Glide Node.js client uses protobufjs to handle communication with the Rust backend. By default, protobufjs generates a comprehensive set of methods for each message type, including verification, object conversion, and instance creation methods. However, many of these methods aren't used in our codebase and add unnecessary size to the bundle.

### Optimization Flags

We use the following flags in our protobufjs build command:

```bash
pbjs -t static-module -w commonjs --no-verify --no-convert -o build-ts/ProtobufMessage.js ...
```

- `--no-verify`: Removes `.verify()` methods that validate message structure
- `--no-convert`: Removes `.fromObject()` and `.toObject()` conversion methods

### Benefits

This optimization reduces:

1. Bundle size
2. Parse/load time
3. Memory footprint

### Size Comparison

| Metric | Full Build | Optimized Build | Reduction |
|--------|------------|-----------------|-----------|
| File size | 409K | 233K | 43% |
| Lines of code | 9,733 | 5,265 | 46% |

### Important Note

We initially tried adding the `--es6` flag to generate more modern JavaScript syntax, but it caused issues with Jest tests. Specifically, it generated `export const` statements that couldn't be properly processed by Jest's CommonJS loader. We chose to stay with CommonJS module format to maintain compatibility with Jest and other tools.

## Implementation Details

The current build process:

1. Generates protobuf code without verify/convert methods
2. Preserves create methods which our codebase uses extensively
3. Maintains CommonJS compatibility for tools that don't support ES modules

## Future Considerations

If ES6 modules are desired in the future:

1. Jest configuration would need to be updated with custom transformers
2. Dual output could be generated (CommonJS and ES modules)

## File Locations

- Generated protobuf: `build-ts/ProtobufMessage.js`
- Proto files source: `../glide-core/src/protobuf/*.proto`
