# Phase 3: Complete UDS Replacement Implementation

## Objective

**Complete replacement** of the UDS (Unix Domain Socket) implementation with direct JNI integration while maintaining 100% API compatibility. This is not an optional feature - it's a full migration from UDS to JNI.

## Architecture Transformation

### Current UDS Architecture (TO BE REMOVED):
```
BaseClient Method → CommandManager → Protobuf → UDS Socket → Rust Process → glide-core → Valkey
         ↓
Response Handler ← Protobuf Response ← UDS Socket ← Rust Process ← glide-core ← Valkey
```

### New JNI Architecture (REPLACEMENT):
```
BaseClient Method → JniCommandManager → JNI → glide-core (in-process) → Valkey
         ↓
Response Handler ← Protobuf Response ← JNI ← glide-core (in-process) ← Valkey
```

**Key Change**: Eliminate the UDS socket and standalone Rust process entirely, making glide-core run in-process via JNI.

## BaseClient Integration Analysis

### Critical UDS Components to Replace:

1. **CommandManager (UDS-based)** → **JniCommandManager (JNI-based)**
   - **File**: `/home/ubuntu/valkey-glide/java/client/src/main/java/glide/managers/CommandManager.java`
   - **Current**: Uses ChannelHandler for UDS communication
   - **Replacement**: Use GlideJniClient for direct JNI calls

2. **ConnectionManager (UDS-based)** → **JniConnectionManager (JNI-based)**
   - **Current**: Manages UDS socket connections to Rust process
   - **Replacement**: Manage in-process JNI client lifecycle

3. **Factory Methods (UDS creation)** → **Factory Methods (JNI creation)**
   - **File**: `/home/ubuntu/valkey-glide/java/client/src/main/java/glide/api/BaseClient.java:478`
   - **Method**: `buildCommandManager(ChannelHandler)` and `buildConnectionManager(ChannelHandler)`
   - **Replacement**: Return JNI-based managers instead of UDS-based ones

4. **Client Creation Flow** → **Direct JNI Client Creation**
   - **File**: `/home/ubuntu/valkey-glide/java/client/src/main/java/glide/api/BaseClient.java:360`
   - **Current**: Creates UDS channels, handlers, and connections
   - **Replacement**: Create JNI client directly

## Implementation Strategy

### Phase 3 Tasks (Complete UDS Replacement)

### Task 1: Replace CommandManager with JniCommandManager ✅
**Goal**: Completely replace UDS-based CommandManager with JNI implementation

**Implementation**:
```java
// REPLACE existing CommandManager.java implementation
public class CommandManager {
    // Remove: private final ChannelHandler channel;
    // Add: private final GlideJniClient jniClient;
    private final GlideJniClient jniClient;
    
    // Replace constructor
    public CommandManager(GlideJniClient jniClient) {
        this.jniClient = jniClient;
    }
    
    // Replace ALL UDS-based methods with JNI equivalents
    public <T> CompletableFuture<T> submitNewCommand(
        RequestType requestType,
        String[] arguments,
        GlideExceptionCheckedFunction<Response, T> responseHandler) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Replace UDS call with JNI call
                String command = getCommandString(requestType);
                Object result = jniClient.executeCommand(command, arguments);
                Response response = convertToResponse(result);
                return responseHandler.apply(response);
            } catch (Exception e) {
                throw mapJniException(e);
            }
        });
    }
}
```

### Task 2: Replace ConnectionManager with JniConnectionManager ✅
**Goal**: Replace UDS connection management with JNI client lifecycle

**Implementation**:
```java
// REPLACE existing ConnectionManager.java implementation
public class ConnectionManager {
    // Remove: private final ChannelHandler channelHandler;
    // Add: private GlideJniClient jniClient;
    private GlideJniClient jniClient;
    
    public CompletableFuture<Void> connectToValkey(BaseClientConfiguration config) {
        // Replace UDS connection with JNI client creation
        try {
            GlideJniClient.Config jniConfig = convertConfig(config);
            this.jniClient = new GlideJniClient(jniConfig);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    public GlideJniClient getJniClient() {
        return jniClient;
    }
}
```

### Task 3: Replace Client Factory Methods ✅
**Goal**: Update BaseClient factory methods to create JNI-based managers

**Implementation**:
```java
// REPLACE factory methods in BaseClient.java
protected static CommandManager buildCommandManager(ChannelHandler channelHandler) {
    // Remove UDS-based creation
    // return new CommandManager(channelHandler);
    
    // Add JNI-based creation
    // Extract JniClient from the connection flow
    throw new UnsupportedOperationException("Use buildCommandManager(GlideJniClient) instead");
}

// ADD new factory method
protected static CommandManager buildCommandManager(GlideJniClient jniClient) {
    return new CommandManager(jniClient);
}

protected static ConnectionManager buildConnectionManager(ChannelHandler channelHandler) {
    // Remove UDS-based creation
    // return new ConnectionManager(channelHandler);
    
    // Add JNI-based creation
    return new ConnectionManager();
}
```

### Task 4: Replace Client Creation Flow ✅
**Goal**: Completely rewrite client creation to use JNI instead of UDS

**Implementation**:
```java
// REPLACE createClient method in BaseClient.java
protected static <T extends BaseClient> CompletableFuture<T> createClient(
        @NonNull BaseClientConfiguration config, Function<ClientBuilder, T> constructor) {
    try {
        // Remove UDS-based creation:
        // ThreadPoolResource threadPoolResource = ...
        // MessageHandler messageHandler = ...
        // ChannelHandler channelHandler = ...
        // ConnectionManager connectionManager = buildConnectionManager(channelHandler);
        // CommandManager commandManager = buildCommandManager(channelHandler);
        
        // Replace with JNI-based creation:
        ConnectionManager connectionManager = buildConnectionManager(null);
        
        return connectionManager
                .connectToValkey(config)
                .thenApply(ignored -> {
                    GlideJniClient jniClient = connectionManager.getJniClient();
                    CommandManager commandManager = buildCommandManager(jniClient);
                    
                    return constructor.apply(
                        new ClientBuilder(
                            connectionManager,
                            commandManager,
                            null, // No MessageHandler needed for JNI
                            Optional.empty() // No subscription config for now
                        ));
                });
    } catch (Exception e) {
        var future = new CompletableFuture<T>();
        future.completeExceptionally(e);
        return future;
    }
}
```

### Task 5: Build Response Conversion System ✅
**Goal**: Convert JNI responses to protobuf Response format (maintain API compatibility)

**Implementation**:
```java
// ADD to CommandManager.java
private Response convertToResponse(Object jniResult) {
    Response.Builder responseBuilder = Response.newBuilder();
    
    if (jniResult == null) {
        responseBuilder.setRespPointer(0);
    } else if (jniResult instanceof String) {
        responseBuilder.setRespPointer(createStringPointer((String) jniResult));
    } else if (jniResult instanceof Object[]) {
        responseBuilder.setRespPointer(createArrayPointer((Object[]) jniResult));
    } else if (jniResult instanceof Number) {
        responseBuilder.setRespPointer(createNumberPointer((Number) jniResult));
    }
    
    return responseBuilder.build();
}
```

### Task 6: Remove UDS Components ✅
**Goal**: Remove all UDS-specific code and dependencies

**Components to Remove**:
- ChannelHandler usage from managers
- UDS socket creation and management
- Netty dependencies (if only used for UDS)
- ThreadPoolResource for UDS communication
- MessageHandler for UDS responses

### Task 7: Integration Testing ✅
**Goal**: Ensure all existing tests pass with JNI replacement

**Strategy**:
- Run complete existing test suite
- Verify no API changes are visible to client code
- Validate performance improvements are maintained

## RequestType to Command Mapping

Need to implement complete mapping for all 200+ RequestType enums:

```java
private static final Map<RequestType, String> REQUEST_TYPE_MAPPING;
static {
    Map<RequestType, String> mapping = new HashMap<>();
    
    // String commands
    mapping.put(RequestType.Get, "GET");
    mapping.put(RequestType.Set, "SET");
    mapping.put(RequestType.Append, "APPEND");
    mapping.put(RequestType.Strlen, "STRLEN");
    
    // Hash commands  
    mapping.put(RequestType.HGet, "HGET");
    mapping.put(RequestType.HSet, "HSET");
    mapping.put(RequestType.HDel, "HDEL");
    
    // ... all 200+ commands
    
    REQUEST_TYPE_MAPPING = Collections.unmodifiableMap(mapping);
}
```

## Success Criteria

### Complete UDS Elimination ✅
- **Zero UDS code remaining** in the codebase
- **All UDS dependencies removed** 
- **JNI is the only communication method**

### API Compatibility ✅
- **100% backward compatibility** - existing code works unchanged
- **Same method signatures** - no API changes
- **Same exception behaviors** - error handling unchanged

### Performance ✅
- **Maintain 1.68x+ improvement** over original UDS
- **No memory leaks** in long-running operations
- **Thread safety** for concurrent usage

### Feature Completeness ✅
- **All 200+ commands supported**
- **Batch operations** work correctly
- **Cluster routing** functions properly
- **Resource management** handles cleanup

## Implementation Timeline

1. **Days 1-2**: Tasks 1-2 (Replace CommandManager and ConnectionManager)
2. **Day 3**: Task 3 (Replace factory methods)
3. **Day 4**: Task 4 (Replace client creation flow)
4. **Day 5**: Task 5 (Response conversion system)
5. **Day 6**: Tasks 6-7 (Remove UDS components and testing)

**Total Estimated Time**: 6 days

This is a **complete migration**, not an addition. The end result will be a JNI-only implementation with no UDS code remaining.