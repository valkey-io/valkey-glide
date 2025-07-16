# Phase 2: Transaction Interface Restoration Design

## Overview

Phase 2 focuses on restoring the complete transaction command interfaces and batch options system, building on the foundation established in Phase 1.

## Success Criteria

- ✅ Transaction command interfaces (`TransactionsCommands`, `TransactionsClusterCommands`) restored
- ✅ Batch options classes (`BatchOptions`, `ClusterBatchOptions`) functional  
- ✅ `exec()` methods with options parameters working
- ✅ 25+ transaction-specific integration tests pass
- ✅ MULTI/EXEC semantics properly implemented

## Architecture Design

### Current State (Post Phase 1)
```
client.exec(batch, boolean) ← [RESTORED]
       ↓
Batch/ClusterBatch (basic) ← [RESTORED]  
       ↓
BaseBatch (basic commands) ← [ENHANCED]
       ↓
JNI CommandManager
```

### Target State (Post Phase 2)
```
client.exec(batch, boolean, options) ← [NEW]
       ↓
Batch/ClusterBatch (with interfaces) ← [ENHANCE]
       ↓
TransactionsCommands interfaces ← [RESTORE]
       ↓
BatchOptions/ClusterBatchOptions ← [RESTORE]
       ↓
Enhanced JNI CommandManager
```

## Component Design

### 1. Transaction Command Interfaces

#### TransactionsCommands Interface
**Source**: `legacy/legacy-batch-system/TransactionsCommands.java`

```java
public interface TransactionsCommands {
    // Transaction lifecycle
    TransactionsCommands multi();
    TransactionsCommands exec();
    TransactionsCommands discard();
    TransactionsCommands watch(GlideString... keys);
    TransactionsCommands unwatch();
    
    // All Redis commands that can be part of transactions
    TransactionsCommands set(GlideString key, GlideString value);
    TransactionsCommands get(GlideString key);
    TransactionsCommands del(GlideString... keys);
    // ... (200+ command methods)
}
```

#### TransactionsClusterCommands Interface  
**Source**: `legacy/legacy-batch-system/TransactionsClusterCommands.java`

```java
public interface TransactionsClusterCommands extends TransactionsCommands {
    // Cluster-specific transaction commands
    // Inherits all standard transaction commands
    // Adds cluster routing and cross-slot considerations
}
```

### 2. Batch Options System

#### BatchOptions Hierarchy
```java
BaseBatchOptions (abstract)
  ├── BatchOptions (standalone)
  └── ClusterBatchOptions (cluster)
```

#### BatchOptions Class
**Source**: `archive/java-old/client/src/main/java/glide/api/models/commands/batch/BatchOptions.java`

```java
public class BatchOptions extends BaseBatchOptions {
    private final boolean atomic;
    private final int timeout;
    private final boolean failFast;
    
    // Builder pattern for configuration
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        public Builder atomic(boolean atomic);
        public Builder timeout(int timeoutMs);  
        public Builder failFast(boolean failFast);
        public BatchOptions build();
    }
}
```

#### ClusterBatchOptions Class
**Source**: `archive/java-old/client/src/main/java/glide/api/models/commands/batch/ClusterBatchOptions.java`

```java
public class ClusterBatchOptions extends BaseBatchOptions {
    private final ClusterBatchRetryStrategy retryStrategy;
    private final RequestRoutingConfiguration routing;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder extends BatchOptions.Builder {
        public Builder retryStrategy(ClusterBatchRetryStrategy strategy);
        public Builder routing(RequestRoutingConfiguration routing);
        public ClusterBatchOptions build();
    }
}
```

### 3. Enhanced Client Methods

#### GlideClient Enhancements
```java
public class GlideClient extends BaseClient implements TransactionsCommands {
    
    // Phase 1 method (already implemented)
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError) { ... }
    
    // NEW: Phase 2 method with options
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError, BatchOptions options) {
        return execWithOptions((BaseBatch) batch, raiseOnError, options);
    }
    
    // NEW: Transaction command interface implementation
    @Override
    public TransactionsCommands multi() {
        // Add MULTI command to current batch context
        return this;
    }
    
    @Override  
    public TransactionsCommands set(GlideString key, GlideString value) {
        // Add SET command to current transaction context
        return this;
    }
    
    // ... implement all TransactionsCommands methods
}
```

#### GlideClusterClient Enhancements
```java
public class GlideClusterClient extends BaseClient implements TransactionsClusterCommands {
    
    // Phase 1 method (already implemented)
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError) { ... }
    
    // NEW: Phase 2 method with options
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError, ClusterBatchOptions options) {
        return execWithOptions((BaseBatch) batch, raiseOnError, options);
    }
    
    // Inherits and implements all TransactionsClusterCommands methods
}
```

### 4. Transaction Context Management

#### Transaction Context Design
```java
public class TransactionContext {
    private final List<Command> commands = new ArrayList<>();
    private boolean inTransaction = false;
    private final Set<GlideString> watchedKeys = new HashSet<>();
    
    public void startTransaction() {
        commands.add(Command.MULTI);
        inTransaction = true;
    }
    
    public void addCommand(Command command) {
        if (!inTransaction) {
            throw new GlideException("Command outside transaction context");
        }
        commands.add(command);
    }
    
    public List<Command> commitTransaction() {
        commands.add(Command.EXEC);
        inTransaction = false;
        return new ArrayList<>(commands);
    }
}
```

## Implementation Strategy

### Step 1: Restore Transaction Command Interfaces

#### 1.1 Copy Transaction Interfaces
**Source**: `legacy/legacy-batch-system/`
- `TransactionsCommands.java` → `client/src/main/java/glide/api/commands/TransactionsCommands.java`  
- `TransactionsClusterCommands.java` → `client/src/main/java/glide/api/commands/TransactionsClusterCommands.java`

#### 1.2 Adaptation Requirements
- Update package imports for current structure
- Ensure return types use current `TransactionsCommands` interface
- Remove commands not yet implemented in Phase 1/2 scope

#### 1.3 Client Interface Implementation
Update client classes to implement transaction interfaces:
```java
// GlideClient.java
public class GlideClient extends BaseClient implements TransactionsCommands {
    private TransactionContext transactionContext;
    
    // Implement all interface methods
}

// GlideClusterClient.java  
public class GlideClusterClient extends BaseClient implements TransactionsClusterCommands {
    private TransactionContext transactionContext;
    
    // Implement all interface methods
}
```

### Step 2: Restore Batch Options Classes

#### 2.1 Copy Options Classes
**Source**: `archive/java-old/client/src/main/java/glide/api/models/commands/batch/`
- `BaseBatchOptions.java`
- `BatchOptions.java`
- `ClusterBatchOptions.java` 
- `ClusterBatchRetryStrategy.java`

#### 2.2 Integration with Exec Methods
Enhance `BaseClient` to support options:
```java
// BaseClient.java enhancement
protected CompletableFuture<Object[]> execWithOptions(BaseBatch batch, boolean raiseOnError, BaseBatchOptions options) {
    // Apply options to execution strategy
    List<Command> commands = batch.getCommands();
    
    // Configure execution based on options
    if (options.isAtomic()) {
        return commandManager.execAtomicBatch(commands, options);
    } else {
        return commandManager.execNonAtomicBatch(commands, options);
    }
}
```

### Step 3: Transaction Semantics Implementation

#### 3.1 MULTI/EXEC Support
Implement proper Redis transaction semantics:
```java
// Example transaction flow
client.multi();                    // Start transaction
client.set(gs("key1"), gs("val1")); // Queue command
client.set(gs("key2"), gs("val2")); // Queue command  
client.exec();                     // Execute transaction atomically
```

#### 3.2 WATCH/UNWATCH Support
Implement optimistic locking:
```java
client.watch(gs("key"));          // Watch key for changes
client.multi();                   // Start transaction
client.set(gs("key"), gs("new"));  // Conditional on watch
client.exec();                    // Execute if key unchanged
```

#### 3.3 Error Handling
Implement transaction-specific error handling:
- `DISCARD` on transaction errors
- Proper cleanup of transaction state
- Watch key management across transaction boundaries

## Testing Strategy

### Unit Tests

#### Transaction Interface Tests
```java
@Test
void transaction_commands_interface_exists() {
    GlideClient client = createTestClient();
    assertTrue(client instanceof TransactionsCommands);
    
    // Verify interface method signatures exist
    assertNotNull(client.getClass().getMethod("multi"));
    assertNotNull(client.getClass().getMethod("exec")); 
}
```

#### Batch Options Tests
```java
@Test  
void batch_options_builder_works() {
    BatchOptions options = BatchOptions.builder()
        .atomic(true)
        .timeout(5000)
        .failFast(false)
        .build();
        
    assertTrue(options.isAtomic());
    assertEquals(5000, options.getTimeout());
    assertFalse(options.isFailFast());
}
```

### Integration Tests Target

#### Transaction Semantics Tests
From `integTest/src/test/java/glide/standalone/BatchTests.java`:
```java
@Test
void transaction_multi_exec() {
    client.multi();
    client.set(gs("key1"), gs("value1"));
    client.set(gs("key2"), gs("value2"));
    Object[] results = client.exec().get();
    
    // Verify transaction executed atomically
    assertEquals("OK", results[0]);
    assertEquals("OK", results[1]);
}
```

#### Batch Options Tests  
```java
@Test
void batch_with_options_atomic() {
    Batch batch = new Batch();
    batch.set(gs("key"), gs("value"));
    batch.get(gs("key"));
    
    BatchOptions options = BatchOptions.builder()
        .atomic(true)
        .build();
        
    Object[] results = client.exec(batch, true, options).get();
    // Should execute atomically
}
```

## Risk Analysis

### Technical Risks

1. **Transaction State Management**: Managing transaction context across method calls
   - **Mitigation**: Use thread-local storage for transaction context
   - **Mitigation**: Clear transaction state on errors

2. **Interface Implementation Complexity**: 200+ methods to implement in transaction interfaces
   - **Mitigation**: Generate interface implementations using templates
   - **Mitigation**: Start with core commands, add incrementally

3. **JNI Transaction Support**: Rust layer must support MULTI/EXEC semantics
   - **Mitigation**: Verify JNI layer supports transaction commands
   - **Mitigation**: Implement fallback to non-atomic execution

### Integration Risks

1. **Options Compatibility**: Batch options may not integrate with JNI layer
   - **Mitigation**: Start with basic options, enhance gradually
   - **Mitigation**: Test options with simple batches first

2. **Legacy API Compatibility**: Transaction interfaces must match legacy exactly
   - **Mitigation**: Compare method signatures systematically
   - **Mitigation**: Run compatibility tests against legacy implementation

## Phase 2 Deliverables

### Code Deliverables
- [ ] `TransactionsCommands.java` - Complete transaction interface
- [ ] `TransactionsClusterCommands.java` - Cluster transaction interface
- [ ] `BatchOptions.java` - Standalone batch options
- [ ] `ClusterBatchOptions.java` - Cluster batch options  
- [ ] `ClusterBatchRetryStrategy.java` - Retry strategy configuration
- [ ] Enhanced `GlideClient` with transaction interface implementation
- [ ] Enhanced `GlideClusterClient` with cluster transaction interface
- [ ] `exec()` methods with options parameters in both clients

### Test Deliverables
- [ ] 25+ transaction-specific integration tests passing
- [ ] Unit tests for all options classes
- [ ] Transaction semantics validation tests
- [ ] Interface implementation verification tests

### Documentation Deliverables
- [ ] Transaction interface javadoc
- [ ] Batch options usage examples
- [ ] Transaction semantics documentation
- [ ] Phase 2 completion report

## Dependencies

### Phase 1 Prerequisites
- Basic `exec()` methods functional
- `Batch` and `ClusterBatch` classes exist
- JNI batch execution working
- Basic integration tests passing

### Phase 3 Handoff
Phase 2 sets up foundation for Phase 3:
- Transaction interfaces available for command implementation
- Batch options system functional
- Transaction context management established
- Advanced `exec()` methods ready for full command coverage

## Success Metrics

### Quantitative Metrics
- **Integration Tests**: ≥25 transaction tests passing  
- **Interface Methods**: 200+ transaction interface methods implemented
- **Options Classes**: 4+ batch options classes functional
- **API Methods**: 4+ new `exec()` methods with options

### Qualitative Metrics
- **Transaction Semantics**: MULTI/EXEC/DISCARD/WATCH working correctly
- **Options Integration**: Batch options properly control execution behavior
- **Interface Compliance**: Transaction interfaces match legacy API exactly
- **Error Handling**: Transaction errors handled appropriately