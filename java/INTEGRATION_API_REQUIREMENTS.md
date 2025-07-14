# Integration Test API Requirements

## Expected Client Classes

### glide.api.BaseClient
**Purpose:** Abstract base class for all client types
**Key Features:**
- Static constant: `public static final String OK = "OK"`
- Abstract methods implemented by GlideClient and GlideClusterClient
- Common operations: get, set, del, exists, ping, close
- Type conversions and CompletableFuture handling

**Required Methods:**
```java
// Basic operations
CompletableFuture<String> get(String key)
CompletableFuture<String> set(String key, String value)
CompletableFuture<Long> del(String... keys)
CompletableFuture<Long> exists(String... keys)
CompletableFuture<String> ping()

// Hash operations
CompletableFuture<Long> hset(String key, String field, String value)
CompletableFuture<String> hget(String key, String field)
CompletableFuture<Map<String, String>> hgetall(String key)

// Advanced operations
CompletableFuture<Object> customCommand(String[] args)
Map<String, Object> getStatistics()

// Lifecycle
void close()
```

### glide.api.GlideClient
**Purpose:** Standalone Redis/Valkey client implementation
**Key Features:**
- Extends BaseClient
- Static factory method: `createClient(GlideClientConfiguration)`
- Standalone-specific operations

**Required Methods:**
```java
// Factory
static CompletableFuture<GlideClient> createClient(GlideClientConfiguration config)

// Standalone operations
CompletableFuture<String> select(int database)
CompletableFuture<Long> dbsize()
CompletableFuture<String> flushdb()
CompletableFuture<String> flushall()

// Server management
CompletableFuture<String> info(InfoOptions.Section[] sections)
```

## Configuration Classes (Existing)

### GlideClientConfiguration
**Location:** `glide.api.models.configuration.GlideClientConfiguration`
**Purpose:** Configuration for standalone client
**Key Properties:**
```java
List<NodeAddress> addresses;      // Connection endpoints
boolean useTLS;                   // TLS configuration
ReadFrom readFrom;                // Read preference
ServerCredentials credentials;    // Authentication
Integer databaseId;               // Target database
int requestTimeout;               // Request timeout in ms
```

### NodeAddress
**Purpose:** Server endpoint specification
```java
String host;
int port;
```

### InfoOptions.Section
**Purpose:** Server info section enumeration
```java
enum Section {
    SERVER, CLIENTS, MEMORY, PERSISTENCE, STATS,
    REPLICATION, CPU, COMMANDSTATS, CLUSTER,
    KEYSPACE, ALL, DEFAULT, EVERYTHING
}
```

## Integration Test Usage Patterns

### Client Creation
```java
// Common pattern in tests
GlideClientConfiguration config = commonClientConfig().build();
GlideClient client = GlideClient.createClient(config).get();
```

### Basic Operations
```java
// Set/Get pattern
assertEquals(OK, client.set(key, value).get());
assertEquals(value, client.get(key).get());

// Delete operations
assertEquals(1L, client.del(key).get());
assertEquals(0L, client.exists(key).get());
```

### Server Information
```java
// Info command usage
String info = client.info(new Section[]{Section.SERVER}).get();
// Parse version from info response
```

### Custom Commands
```java
// ACL operations
assertEquals(1L, client.customCommand(new String[]{"ACL", "DELUSER", username}).get());
assertEquals(OK, client.customCommand(new String[]{"ACL", "SETUSER", username, "on", ">password"}).get());
```

### Statistics and Monitoring
```java
// Client statistics
Map<String, Object> stats = client.getStatistics();
assertFalse(stats.isEmpty());
assertEquals(2, stats.size()); // Expected stats count
```

## Integration Test Files (80+ files)

### Key Test Classes
- `SharedClientTests.java` - Common client behavior tests
- `StandaloneClientTests.java` - Standalone-specific tests
- `SharedCommandTests.java` - Command operation tests
- `TestUtilities.java` - Helper functions and common configurations

### Common Test Utilities
```java
// Configuration builders
GlideClientConfiguration.GlideClientConfigurationBuilder commonClientConfig()

// Server version detection
String getServerVersion(BaseClient client)

// ACL user management
void deleteAclUser(GlideClient client, String username)
void setNewAclUserPassword(GlideClient client, String username, String password)
```

## Compatibility Layer Implementation Strategy

### 1. BaseClient Implementation
```java
public abstract class BaseClient {
    public static final String OK = "OK";
    protected final io.valkey.glide.core.client.GlideClient coreClient;

    // Delegate all operations to coreClient with type conversion
    public CompletableFuture<String> get(String key) {
        return coreClient.get(key);
    }

    // Handle array operations with proper conversion
    public CompletableFuture<Long> del(String... keys) {
        return coreClient.executeCommand(new Command(CommandType.DEL, keys))
            .thenApply(result -> Long.parseLong(result.toString()));
    }
}
```

### 2. GlideClient Implementation
```java
public class GlideClient extends BaseClient {
    private GlideClient(io.valkey.glide.core.client.GlideClient coreClient) {
        super(coreClient);
    }

    public static CompletableFuture<GlideClient> createClient(GlideClientConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            // Convert config to core client config
            var coreConfig = convertConfiguration(config);
            var coreClient = new io.valkey.glide.core.client.GlideClient(coreConfig);
            return new GlideClient(coreClient);
        });
    }
}
```

### 3. Configuration Conversion
```java
private static io.valkey.glide.core.client.GlideClient.Config convertConfiguration(
        GlideClientConfiguration config) {
    List<String> addresses = config.getAddresses().stream()
        .map(addr -> addr.getHost() + ":" + addr.getPort())
        .collect(Collectors.toList());

    return new io.valkey.glide.core.client.GlideClient.Config(addresses);
}
```

## Critical Success Factors

### Must Work for Integration Tests
1. **Factory Method:** `GlideClient.createClient()` must return working client
2. **All Basic Operations:** get, set, del, exists, ping must work
3. **Custom Commands:** Support for ACL and other administrative commands
4. **Type Safety:** Proper conversion between String/Long/Object return types
5. **Exception Handling:** Proper error propagation and formatting

### Performance Considerations
1. **Minimal Overhead:** Compatibility layer should add minimal latency
2. **Object Reuse:** Avoid unnecessary object creation in hot paths
3. **Future Chaining:** Efficient CompletableFuture composition
4. **Memory Management:** Proper cleanup and resource management

### Testing Strategy
1. **Compile First:** Ensure zero compilation errors
2. **Unit Tests:** Test individual compatibility layer methods
3. **Integration Tests:** Run existing test suite
4. **Regression Testing:** Ensure no functionality loss
