# Batch Implementation Design - Current Progress

## ✅ COMPLETED: Basic Implementation

### Successfully Implemented
- **Command Class**: String-based command representation with factory methods
- **BaseBatch Class**: Base class for batch operations with method chaining
- **Integration**: BaseClient.exec() method working with batch system
- **Testing**: All SharedCommandTests passing (6+ minutes of tests)

### Current Architecture
```
BaseBatch<T> → List<Command> → BaseClient.exec() → GlideClient.executeCommand()
```

### Working Features
- Command accumulation with method chaining
- Basic command factory methods (get, set, mget, hget, etc.)
- Atomic and non-atomic batch execution
- Full integration with existing test suite

## 🔄 IN PROGRESS: Binary Data Support

### Current Goal
Implement comprehensive binary data support across the entire client, not just for batching.

### Binary Data Requirements
Based on user feedback: "The support is for all the client implementation and not special to the batching, so make sure it is available for the usual commands as well"

### Implementation Strategy
1. **Move Command to Client Module**: Access to GlideString class
2. **Enhance Command Class**: Add GlideString[] support while maintaining String[] compatibility
3. **Update BaseBatch**: Add binary data methods
4. **Extend BaseClient**: Binary data support for all operations

## Next Steps

### Phase 1: Binary Data Foundation
1. **Command Class Enhancement**
   ```java
   public class Command {
       private final String command;
       private final GlideString[] arguments;
       
       // Multiple constructors for different use cases
       public Command(String command, GlideString... arguments);
       public Command(String command, String... arguments);
       public Command(CommandType commandType, GlideString... arguments);
       
       // Binary data access methods
       public GlideString[] getArguments();
       public String[] getArgumentsAsStrings();
       public byte[][] getArgumentsAsBytes();
   }
   ```

2. **BaseBatch Binary Methods**
   ```java
   public abstract class BaseBatch<T extends BaseBatch<T>> {
       // Binary data methods
       public T set(GlideString key, GlideString value);
       public T get(GlideString key);
       public T mget(GlideString... keys);
       
       // String convenience methods (existing)
       public T set(String key, String value);
       public T get(String key);
       public T mget(String... keys);
   }
   ```

### Phase 2: Client-Wide Binary Support
1. **BaseClient Enhancement**: Add binary data methods to all operations
2. **GlideClient Integration**: Ensure binary data flows through to native layer
3. **Testing**: Comprehensive binary data tests

### Phase 3: Advanced Features
1. **Performance Optimization**: Memory and execution efficiency
2. **Error Handling**: Comprehensive error management
3. **Documentation**: Complete API documentation

## Technical Considerations

### Binary Data Handling
- **GlideString Integration**: Full support for binary data through existing GlideString class
- **Backward Compatibility**: Maintain all existing String-based APIs
- **Performance**: Minimal overhead for binary operations

### Module Architecture
- **Command Location**: Must be in client module for GlideString access
- **Dependency Management**: Clean separation between core and client modules
- **Import Structure**: Proper import hierarchy for binary data support

## Success Metrics

- ✅ All existing tests pass
- ✅ Basic batch operations functional
- 🔄 Binary data support implemented
- 🔄 Backward compatibility maintained
- 🔄 Performance acceptable
- 🔄 Clean API design

## Current Implementation Status

### Completed ✅
- [x] Command class with String[] arguments
- [x] BaseBatch base class with method chaining
- [x] Common command factory methods
- [x] Integration with BaseClient.exec()
- [x] All SharedCommandTests passing

### In Progress 🔄
- [ ] Command class with GlideString[] arguments
- [ ] BaseBatch binary data methods
- [ ] BaseClient binary data support
- [ ] Comprehensive binary data tests

### Future 📋
- [ ] Performance optimization
- [ ] Advanced error handling
- [ ] Complete documentation
- [ ] Migration guide

## Implementation Notes

The current implementation provides a solid foundation for binary data support. The next critical step is implementing GlideString support in the Command class, which requires moving it to the client module where GlideString is available.

Key insight: Binary data support should be implemented across the entire client, not just for batching operations, as requested by the user.

## Design Challenges

### 1. Command Storage Structure
**Original:** Protobuf batch with `Command` objects containing `RequestType` and `ArgsArray`

**New:** Need to store `Command` objects in a way that preserves:
- Command type information
- Arguments as strings OR binary data
- Execution order
- Binary output preferences

### 2. Argument Type Handling
**Original:** `ArgsBuilder` with `GlideString` support and type checking

**New:** Need to handle:
- String and GlideString arguments
- Type validation
- Conversion to String[] for Command objects
- **CRITICAL:** Binary data preservation through GlideString

### 3. Binary Data Support
**Challenge:** Current `Command` class only supports `String[]` arguments, but Valkey can store and return binary data (non-UTF8 byte arrays).

**Requirements:**
- Support `GlideString` arguments containing binary data
- Preserve binary data through batch execution
- Handle mixed String/GlideString arguments
- Support binary output mode for batch results

### 3. Batch Execution
**Original:** Single protobuf batch sent to glide-core

**New:** Need to determine:
- Send individual commands or batch them at core level
- How to handle atomic vs non-atomic batches
- Error handling and partial failures

### 4. API Compatibility
**Original:** Extensive API with 100+ methods in BaseBatch

**New:** Must maintain exact same API surface

## Proposed Solution

### 1. Enhanced Command Class for Binary Support

First, we need to enhance the `Command` class to support binary data:

```java
public class Command {
    private final String command;
    private final GlideString[] arguments;  // Changed from String[] to GlideString[]

    public Command(String command, GlideString... arguments) {
        this.command = command;
        this.arguments = arguments != null ? arguments : new GlideString[0];
    }

    // Convenience constructor for String arguments
    public Command(String command, String... arguments) {
        this.command = command;
        this.arguments = Arrays.stream(arguments)
            .map(GlideString::of)
            .toArray(GlideString[]::new);
    }

    // Mixed argument support
    public Command(String command, Object... arguments) {
        this.command = command;
        this.arguments = Arrays.stream(arguments)
            .map(arg -> {
                if (arg instanceof GlideString) return (GlideString) arg;
                if (arg instanceof String) return GlideString.of((String) arg);
                if (arg instanceof byte[]) return GlideString.of((byte[]) arg);
                return GlideString.of(arg.toString());
            })
            .toArray(GlideString[]::new);
    }

    // Convert to byte[][] for native execution
    public byte[][] toByteArrays() {
        return Arrays.stream(arguments)
            .map(GlideString::getBytes)
            .toArray(byte[][]::new);
    }
}
```

### 2. New BaseBatch Architecture with Binary Support

```java
public abstract class BaseBatch<T extends BaseBatch<T>> {
    // Command storage
    protected final List<Command> commands = new ArrayList<>();

    // Batch configuration
    protected boolean binaryOutput = false;
    protected boolean isAtomic = false;

    // Enhanced command building with binary support
    protected <ArgType> T addCommand(CommandType commandType, ArgType... args) {
        // Convert mixed arguments to proper types
        Object[] processedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            ArgType arg = args[i];
            if (arg instanceof String || arg instanceof GlideString || arg instanceof byte[]) {
                processedArgs[i] = arg;
            } else {
                // Convert other types to string
                processedArgs[i] = arg.toString();
            }
        }

        commands.add(new Command(commandType.toString(), processedArgs));
        return getThis();
    }

    // Type-safe argument validation
    protected <ArgType> void checkTypeOrThrow(ArgType arg) {
        if (!(arg instanceof String) && !(arg instanceof GlideString) && !(arg instanceof byte[])) {
            throw new IllegalArgumentException("Expected String, GlideString, or byte[]");
        }
    }

    // Enhanced binary output support
    public T withBinaryOutput() {
        binaryOutput = true;
        return getThis();
    }
}
```

### 3. Enhanced Command Implementation Pattern

Each command method will follow this pattern with binary support:

```java
// Basic command with type checking
public <ArgType> T get(@NonNull ArgType key) {
    checkTypeOrThrow(key);
    return addCommand(CommandType.GET, key);
}

// Command with multiple arguments
public <ArgType> T set(@NonNull ArgType key, @NonNull ArgType value) {
    checkTypeOrThrow(key);
    checkTypeOrThrow(value);
    return addCommand(CommandType.SET, key, value);
}

// Array argument support
public <ArgType> T mget(@NonNull ArgType[] keys) {
    checkTypeOrThrow(keys);
    return addCommand(CommandType.MGET, (Object[]) keys);
}

// Map argument support (for MSET)
public <ArgType> T mset(@NonNull Map<ArgType, ArgType> keyValuePairs) {
    checkTypeOrThrow(keyValuePairs);
    Object[] args = new Object[keyValuePairs.size() * 2];
    int i = 0;
    for (Map.Entry<ArgType, ArgType> entry : keyValuePairs.entrySet()) {
        args[i++] = entry.getKey();
        args[i++] = entry.getValue();
    }
    return addCommand(CommandType.MSET, args);
}

// Binary-specific commands
public <ArgType> T hset(@NonNull ArgType key, @NonNull ArgType field, @NonNull ArgType value) {
    checkTypeOrThrow(key);
    checkTypeOrThrow(field);
    checkTypeOrThrow(value);
    return addCommand(CommandType.HSET, key, field, value);
}
```

### 4. Enhanced BaseClient.exec() Method with Binary Support

```java
public CompletableFuture<Object[]> exec(BaseBatch batch) {
    return exec(batch, false);
}

public CompletableFuture<Object[]> exec(BaseBatch batch, boolean raiseOnError) {
    List<Command> commands = batch.getCommands();
    boolean binaryOutput = batch.isBinaryOutput();

    if (batch.isAtomic()) {
        // Execute as atomic transaction
        return client.executeTransaction(commands, binaryOutput);
    } else {
        // Execute as pipeline
        return client.executePipeline(commands, binaryOutput);
    }
}
```

### 5. Enhanced GlideClient Core Integration

Add new methods to GlideClient for batch execution with binary support:

```java
// In GlideClient.java
public CompletableFuture<Object[]> executePipeline(List<Command> commands, boolean binaryOutput) {
    return executeCommandBatch(commands, false, binaryOutput);
}

public CompletableFuture<Object[]> executeTransaction(List<Command> commands, boolean binaryOutput) {
    return executeCommandBatch(commands, true, binaryOutput);
}

private CompletableFuture<Object[]> executeCommandBatch(List<Command> commands, boolean atomic, boolean binaryOutput) {
    return CompletableFuture.supplyAsync(() -> {
        // Convert commands to native format
        String[] commandNames = commands.stream()
            .map(Command::getCommand)
            .toArray(String[]::new);

        byte[][][] commandArgs = commands.stream()
            .map(Command::toByteArrays)
            .toArray(byte[][][]::new);

        // Execute via native method
        Object[] results = executeCommandBatch(nativeClientPtr, commandNames, commandArgs, atomic);

        // Convert results based on binaryOutput flag
        if (binaryOutput) {
            return convertResultsToBinary(results);
        } else {
            return convertResultsToString(results);
        }
    });
}

private native Object[] executeCommandBatch(long clientPtr, String[] commands, byte[][][] args, boolean atomic);
```

## Implementation Plan

### Phase 1: Core Infrastructure with Binary Support
1. **Enhanced Command class**
   - Add `GlideString[]` argument support
   - Add `toByteArrays()` method for native execution
   - Support mixed String/GlideString/byte[] arguments
   - Maintain backward compatibility with String-only constructors

2. **Update BaseBatch class**
   - Replace protobuf dependencies with enhanced Command list
   - Implement binary-aware command building utilities
   - Add comprehensive type checking helpers
   - Support binaryOutput flag

3. **Enhanced argument handling**
   - Type-safe argument conversion
   - Support for String, GlideString, and byte[] types
   - Conditional argument addition with binary preservation
   - Array and Map argument processing

### Phase 2: Command Methods Implementation with Binary Support
1. **String commands** (GET, SET, MGET, MSET, etc.)
   - Support binary keys and values
   - Preserve binary data through command chain

2. **Hash commands** (HGET, HSET, HGETALL, etc.)
   - Binary field names and values
   - Mixed binary/string field handling

3. **List commands** (LPUSH, BLPOP, etc.)
   - Binary list elements
   - Binary key support

4. **Set commands** (SADD, SREM, etc.)
   - Binary set members
   - Binary key support

5. **Sorted set commands** (ZADD, ZREM, etc.)
   - Binary members with numeric scores
   - Binary key support

6. **Generic commands** (DEL, EXISTS, PING, etc.)
   - Binary key support where applicable
   - Custom command with binary argument support

### Phase 3: Enhanced Batch Execution
1. **Update BaseClient.exec()**
   - Handle Command list with binary data
   - Support atomic vs non-atomic modes
   - Implement proper error handling
   - Binary output mode support

2. **Core integration**
   - Add batch execution to GlideClient with binary support
   - Implement JNI bindings for binary batch operations
   - Handle result parsing and conversion (binary vs string)
   - Memory-efficient binary data handling

### Phase 4: Advanced Binary Features
1. **Binary output support**
   - GlideString result handling
   - Binary-safe argument processing
   - Mixed binary/string result conversion

2. **Error handling**
   - Partial failure modes with binary data
   - Transaction rollback support
   - Binary data corruption detection

3. **Performance optimizations**
   - Batch size optimization with binary data
   - Memory-efficient binary command storage
   - Zero-copy binary data handling where possible

## Key Design Decisions

### 1. Command Storage with Binary Support
- **Decision:** Use `List<Command>` with `GlideString[]` arguments instead of protobuf batch
- **Rationale:** Simpler, cleaner, no protobuf dependencies, full binary data support
- **Trade-off:** May need additional metadata for complex features, but gains binary data integrity

### 2. Argument Handling with Binary Preservation
- **Decision:** Convert all arguments to `GlideString[]` at command creation time, preserve binary data
- **Rationale:** Matches GlideString structure, supports binary data, maintains type safety
- **Trade-off:** Slight memory overhead for GlideString wrappers, but preserves binary data integrity

### 3. Type Safety with Binary Support
- **Decision:** Maintain original generic type checking with `<ArgType>` supporting String, GlideString, and byte[]
- **Rationale:** Preserves API compatibility, type safety, and adds binary support
- **Trade-off:** Requires careful type validation and conversion, but provides comprehensive data type support

### 4. Enhanced Batch Execution
- **Decision:** Add binary-aware batch execution methods to GlideClient core
- **Rationale:** Better performance than individual command execution, preserves binary data
- **Trade-off:** Requires JNI implementation for batch operations, but provides native-level performance

### 5. Binary Output Mode
- **Decision:** Support both string and binary output modes at batch level
- **Rationale:** Matches original API behavior, allows applications to choose appropriate data handling
- **Trade-off:** Requires result conversion logic, but provides flexible data handling

## Testing Strategy

### 1. Unit Tests
- Each command method with various argument types (String, GlideString, byte[])
- Type checking and validation for binary data
- Command building and storage with binary preservation
- Binary data round-trip testing

### 2. Integration Tests
- Batch execution with mixed command types and binary data
- Atomic vs non-atomic batch behavior with binary values
- Error handling and partial failures with binary data
- Binary output mode validation

### 3. Performance Tests
- Batch size optimization with binary data
- Memory usage profiling for binary data handling
- Comparison with original implementation performance
- Binary data throughput testing

### 4. Binary Data Specific Tests
- Non-UTF8 binary data handling
- Large binary data (BLOBs) support
- Mixed text/binary data in same batch
- Binary data corruption detection
- Memory leak testing for binary data

### 5. Compatibility Tests
- API compatibility with existing code using binary data
- Binary output mode validation
- All existing integration tests must pass
- Backward compatibility with String-only usage

## Migration Path

### 1. Backwards Compatibility
- Maintain exact same API surface with binary support
- Preserve all method signatures
- Support String, GlideString, and byte[] arguments seamlessly
- Ensure existing String-only code continues to work

### 2. Enhanced Binary Support
- Add comprehensive binary data handling
- Support mixed binary/text operations
- Provide binary output mode for applications needing it
- Ensure binary data integrity throughout the pipeline

### 3. Gradual Migration
- Implement core binary-aware infrastructure first
- Add commands incrementally with binary support
- Test each command group thoroughly with binary data
- Validate binary data handling at each step

### 4. Performance Validation
- Benchmark against original implementation with binary data
- Optimize based on binary data performance metrics
- Ensure no regression in functionality or performance
- Validate binary data throughput and memory usage

## Future Enhancements

### 1. Advanced Binary Features
- Compression support for large binary data
- Streaming binary data handling
- Binary data type detection and optimization
- Custom binary serialization support

### 2. Performance Optimizations
- Command pooling with binary data awareness
- Batch size auto-tuning based on data types
- Memory-mapped binary data handling
- Zero-copy binary data operations

### 3. Enhanced Binary Data Management
- Binary data validation and checksums
- Binary data compression for network efficiency
- Binary data indexing for faster retrieval
- Binary data encryption support

### 4. Monitoring and Observability
- Binary data metrics and monitoring
- Binary data performance tracking
- Binary data error analysis
- Binary data usage analytics

## Conclusion

This enhanced design provides a comprehensive solution for implementing batch operations in the new Valkey GLIDE Java client with full binary data support. The approach maintains API compatibility while adding robust binary data handling capabilities that were crucial in the original implementation.

### Key Benefits:
1. **Full Binary Data Support**: Native handling of binary data through GlideString integration
2. **API Compatibility**: Maintains exact same API surface with enhanced binary capabilities
3. **Performance**: Leverages native batch execution with binary data preservation
4. **Type Safety**: Comprehensive type checking for String, GlideString, and byte[] arguments
5. **Flexibility**: Supports mixed text/binary operations and binary output modes

### Critical Binary Data Features:
- **Data Integrity**: Preserves binary data throughout the entire pipeline
- **Memory Efficiency**: Minimal copying of binary data during batch operations
- **Type Safety**: Robust type checking prevents binary data corruption
- **Performance**: Native-level binary data handling through JNI integration

The implementation will be done incrementally, with special attention to binary data handling at each phase. This approach ensures that both existing String-based code and new binary-aware applications can leverage the enhanced batch system effectively.

The enhanced design addresses the critical gap in binary data support while maintaining the rich functionality and performance characteristics expected from a high-performance Valkey client.
