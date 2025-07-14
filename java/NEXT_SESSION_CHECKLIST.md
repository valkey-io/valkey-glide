# Next Session Implementation Checklist

## Priority 1: Critical Foundation ⚡

### 1. Fix BaseClient.java
**File:** `/home/ubuntu/valkey-glide/java/client/src/main/java/glide/api/BaseClient.java`
**Status:** ❌ Has compilation errors
**Issues:**
- Missing proper Command import
- Incorrect CommandType import path
- customCommand() method needs proper implementation

**Actions:**
```java
// Fix imports
import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;

// Fix customCommand implementation
public CompletableFuture<Object> customCommand(String[] args) {
    if (args.length == 0) {
        return CompletableFuture.completedFuture(null);
    }

    String commandName = args[0].toUpperCase();
    String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

    try {
        CommandType commandType = CommandType.valueOf(commandName);
        return executeCommand(commandType, commandArgs);
    } catch (IllegalArgumentException e) {
        // Handle custom commands not in enum
        return client.executeCommandRaw(args);
    }
}
```

### 2. Fix GlideClient.java
**File:** `/home/ubuntu/valkey-glide/java/client/src/main/java/glide/api/GlideClient.java`
**Status:** ❌ Has compilation errors
**Issues:**
- Missing imports
- Configuration conversion needs proper implementation
- Info command handling needs work

**Actions:**
```java
// Add missing imports
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.commands.InfoOptions;

// Fix createClient method
public static CompletableFuture<GlideClient> createClient(GlideClientConfiguration config) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            var coreConfig = convertToConfig(config);
            var coreClient = new io.valkey.glide.core.client.GlideClient(coreConfig);
            return new GlideClient(coreClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create GlideClient", e);
        }
    });
}

// Implement proper configuration conversion
private static io.valkey.glide.core.client.GlideClient.Config convertToConfig(
        GlideClientConfiguration config) {
    List<String> addresses = new ArrayList<>();

    if (config.getAddresses() != null && !config.getAddresses().isEmpty()) {
        for (var address : config.getAddresses()) {
            addresses.add(address.getHost() + ":" + address.getPort());
        }
    } else {
        addresses.add("localhost:6379"); // Default
    }

    return new io.valkey.glide.core.client.GlideClient.Config(addresses);
}
```

### 3. Add Missing Method to Core GlideClient
**File:** `/home/ubuntu/valkey-glide/java/src/main/java/io/valkey/glide/core/client/GlideClient.java`
**Status:** ❌ Missing executeCommandRaw method
**Action:** Add support for arbitrary command execution

```java
// Add to core GlideClient
public CompletableFuture<Object> executeCommandRaw(String[] args) {
    // Handle commands not in CommandType enum
    return CompletableFuture.supplyAsync(() -> {
        try {
            return executeCommandNative(clientPtr, args[0],
                Arrays.copyOfRange(args, 1, args.length));
        } catch (Exception e) {
            throw new RuntimeException("Raw command execution failed", e);
        }
    });
}
```

## Priority 2: Compilation Fixes 🔧

### 4. Resolve Protobuf Dependencies
**Problem:** Existing classes still import protobuf
**Files to check:**
- `glide.connectors.handlers.*`
- `glide.managers.*`
- `glide.api.models.BaseBatch.java`
- `glide.api.models.Batch.java`

**Strategy:**
- Option A: Remove/stub protobuf-dependent classes temporarily
- Option B: Create minimal protobuf stubs
- Option C: Conditionally compile based on feature flags

### 5. Missing GlideClusterClient Reference
**File:** `glide.api.models.configuration.ClusterSubscriptionConfiguration.java`
**Issue:** References non-existent GlideClusterClient
**Action:** Either create stub or remove reference

## Priority 3: Integration Support 🔗

### 6. Add Statistics Support
**Issue:** Integration tests expect `getStatistics()` to return meaningful data
**Implementation:**
```java
// In BaseClient
public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("connections", 1);
    stats.put("requests", 0L); // TODO: Track in core client
    return stats;
}
```

### 7. Info Command Enhancement
**Issue:** Integration tests use InfoOptions.Section[] parameter
**Implementation:**
```java
// In GlideClient
public CompletableFuture<String> info(InfoOptions.Section[] sections) {
    if (sections == null || sections.length == 0) {
        return executeCommand(CommandType.INFO).thenApply(Object::toString);
    }

    String sectionArg = Arrays.stream(sections)
        .map(section -> section.name().toLowerCase())
        .collect(Collectors.joining(" "));

    return executeCommand(CommandType.INFO, sectionArg).thenApply(Object::toString);
}
```

## Priority 4: Testing and Validation ✅

### 8. Create Compatibility Test
**File:** `/home/ubuntu/valkey-glide/java/client/src/test/java/glide/api/CompatibilityTest.java`
**Purpose:** Verify compatibility layer works before running integration tests

```java
@Test
public void testBasicCompatibility() {
    var config = GlideClientConfiguration.builder()
        .address(NodeAddress.builder().host("localhost").port(6379).build())
        .build();

    GlideClient client = GlideClient.createClient(config).get();

    // Test basic operations
    assertEquals(BaseClient.OK, client.set("test", "value").get());
    assertEquals("value", client.get("test").get());
    assertEquals("PONG", client.ping().get());

    client.close();
}
```

### 9. Incremental Compilation Testing
**Strategy:** Fix errors in batches and test compilation frequently

```bash
# Test compilation after each fix
cd /home/ubuntu/valkey-glide/java && ./gradlew :client:compileJava

# Count remaining errors
./gradlew :client:compileJava 2>&1 | grep "error:" | wc -l
```

### 10. Integration Test Smoke Test
**Goal:** Run one simple integration test to verify end-to-end functionality

```bash
# Run single test class first
./gradlew :integTest:test --tests "glide.SharedClientTests.validate_statistics"
```

## Success Metrics for Session

### ✅ Must Achieve
1. **Zero compilation errors** in client module
2. **GlideClient.createClient() works** - can create client instance
3. **Basic operations work** - get, set, ping return expected results
4. **Statistics method works** - returns non-empty map

### 🎯 Should Achieve
1. **CustomCommand works** - can execute ACL commands
2. **Info command works** - returns server information
3. **One integration test passes** - validates compatibility

### 🌟 Nice to Have
1. **Multiple integration tests pass** - broader compatibility
2. **Performance baseline** - measure vs existing client
3. **Error handling** - proper exception propagation

## Command Execution Priority

### Session Start Commands
```bash
# 1. Check current status
cd /home/ubuntu/valkey-glide/java && ./gradlew :client:compileJava 2>&1 | head -20

# 2. Fix BaseClient imports first
# Edit /home/ubuntu/valkey-glide/java/client/src/main/java/glide/api/BaseClient.java

# 3. Test compilation frequently
./gradlew :client:compileJava

# 4. Focus on one error type at a time
./gradlew :client:compileJava 2>&1 | grep -E "(error|warning)" | sort | uniq -c
```

### Debugging Commands
```bash
# Check which protobuf classes are missing
grep -r "ResponseOuterClass" /home/ubuntu/valkey-glide/java/client/src/

# Find integration test imports
grep -r "import glide.api" /home/ubuntu/valkey-glide/java/integTest/src/ | head -10

# Check module dependencies
cat /home/ubuntu/valkey-glide/java/client/src/main/java/module-info.java
```

## Risk Mitigation

### Backup Strategy
- Keep REFACTORING_STATUS.md updated with progress
- Commit working states frequently
- Document any architectural decisions that don't work

### Fallback Plans
- If protobuf elimination is too complex: create minimal protobuf stubs
- If configuration conversion fails: hardcode localhost:6379 for testing
- If too many errors: focus on minimal working subset first

### Communication Strategy
- Document blockers clearly in status files
- Provide specific next steps if session ends incomplete
- Include exact commands and file locations for continuation
