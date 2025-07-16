# Phase 1: Core Batch System Design

## Overview

Phase 1 focuses on restoring the fundamental batch execution system to enable basic integration tests to pass. This is the critical foundation that all subsequent phases depend on.

## Success Criteria

- ✅ Basic batch operations (SET, GET, DEL) work via `exec()` methods
- ✅ 10+ basic integration tests pass 
- ✅ `Batch` and `ClusterBatch` classes exist with minimal command set
- ✅ `exec()` methods exist in all client classes with correct signatures

## Architecture Design

### Current State
```
Integration Tests
       ↓ 
   [MISSING] client.exec(batch, boolean)
       ↓
   [MISSING] Batch/ClusterBatch classes
       ↓
   [EXISTS] BaseBatch (minimal implementation)
       ↓
   [EXISTS] JNI CommandManager
```

### Target State  
```
Integration Tests
       ↓
   client.exec(batch, boolean) ← [RESTORE]
       ↓
   Batch/ClusterBatch classes ← [RESTORE]
       ↓
   BaseBatch (enhanced) ← [ADAPT]
       ↓
   JNI CommandManager
```

## Component Design

### 1. Batch Class Hierarchy

#### Current Hierarchy
```java
BaseBatch (abstract)
  ├── [MISSING] Batch
  └── [MISSING] ClusterBatch
```

#### Target Hierarchy
```java
BaseBatch (abstract)
  ├── Batch extends BaseBatch
  ├── ClusterBatch extends BaseBatch  
  ├── Transaction extends Batch          // Legacy compatibility
  └── ClusterTransaction extends ClusterBatch // Legacy compatibility
```

### 2. Client Class Integration

#### BaseClient.java
```java
public abstract class BaseClient {
    // NEW: Core batch execution method
    public CompletableFuture<Object[]> exec(BaseBatch batch, boolean raiseOnError) {
        // Convert BaseBatch to command sequence
        // Execute via JNI CommandManager
        // Return results array
    }
}
```

#### GlideClient.java  
```java
public class GlideClient extends BaseClient {
    // NEW: Standalone batch execution
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError) {
        return exec((BaseBatch) batch, raiseOnError);
    }
    
    // NEW: Standalone batch with options (Phase 2)
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError, BatchOptions options) {
        // Implementation deferred to Phase 2
    }
}
```

#### GlideClusterClient.java
```java  
public class GlideClusterClient extends BaseClient {
    // NEW: Cluster batch execution
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError) {
        return exec((BaseBatch) batch, raiseOnError);
    }
    
    // NEW: Cluster batch with options (Phase 2)
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError, ClusterBatchOptions options) {
        // Implementation deferred to Phase 2  
    }
}
```

## Implementation Strategy

### Step 1: Restore Batch Classes

#### 1.1 Copy Base Implementation
**Source**: `archive/java-old/client/src/main/java/glide/api/models/Batch.java`
**Target**: `client/src/main/java/glide/api/models/Batch.java`

**Required Adaptations**:
- Update package imports to match current structure
- Ensure extends current `BaseBatch`
- Remove commands not yet implemented in `BaseBatch`
- Focus on basic commands: GET, SET, DEL, PING

#### 1.2 Copy Cluster Implementation  
**Source**: `archive/java-old/client/src/main/java/glide/api/models/ClusterBatch.java`
**Target**: `client/src/main/java/glide/api/models/ClusterBatch.java`

**Required Adaptations**:
- Same adaptations as `Batch.java`
- Ensure cluster-specific routing works with JNI layer

#### 1.3 Create Legacy Compatibility Classes
```java
// Transaction.java - Legacy compatibility
public class Transaction extends Batch {
    public Transaction() {
        super();
    }
    
    public Transaction(boolean isAtomic) {
        super(isAtomic);
    }
}

// ClusterTransaction.java - Legacy compatibility
public class ClusterTransaction extends ClusterBatch {
    public ClusterTransaction() {
        super();  
    }
    
    public ClusterTransaction(boolean isAtomic) {
        super(isAtomic);
    }
}
```

### Step 2: Implement Exec Methods

#### 2.1 BaseClient.exec() Implementation
```java
public CompletableFuture<Object[]> exec(BaseBatch batch, boolean raiseOnError) {
    // Get command list from batch
    List<Command> commands = batch.getCommands();
    
    // Execute via CommandManager  
    CompletableFuture<Object[]> future = commandManager.execBatch(commands);
    
    // Handle error policy
    if (raiseOnError) {
        return future.exceptionally(throwable -> {
            throw new GlideException("Batch execution failed", throwable);
        });
    }
    
    return future;
}
```

#### 2.2 JNI Integration Required
The `CommandManager` needs to support batch execution:

```java
// CommandManager.java enhancement needed
public CompletableFuture<Object[]> execBatch(List<Command> commands) {
    // Convert commands to JNI format
    // Execute as atomic batch via Rust layer
    // Return results array
}
```

### Step 3: Enhanced BaseBatch

#### Current BaseBatch Issues
- Only ~20 commands implemented
- Missing critical commands needed for basic tests

#### Required Enhancements
Add missing basic commands to `BaseBatch`:
- `ping()` - Already exists
- `set(GlideString key, GlideString value)` - Needs implementation 
- `get(GlideString key)` - Needs implementation
- `del(GlideString... keys)` - Needs implementation

## Testing Strategy

### Unit Tests
- Test `Batch` and `ClusterBatch` class creation
- Test command building and serialization
- Test `exec()` method signatures exist

### Integration Tests Target
Focus on these basic integration tests for Phase 1:

#### From `standalone/BatchTests.java`:
```java
// Target: Basic batch execution
@Test 
void batch_basic_commands() {
    Batch batch = new Batch();
    batch.set(gs("key"), gs("value"));
    batch.get(gs("key"));
    
    Object[] result = client.exec(batch, true).get();
    // Should pass after Phase 1
}
```

#### From `cluster/ClusterBatchTests.java`:
```java
// Target: Basic cluster batch execution  
@Test
void cluster_batch_basic_commands() {
    ClusterBatch batch = new ClusterBatch();
    batch.set(gs("key"), gs("value"));
    batch.get(gs("key")); 
    
    Object[] result = clusterClient.exec(batch, true).get();
    // Should pass after Phase 1
}
```

## Risk Analysis

### Technical Risks
1. **JNI Compatibility**: Archived batch implementation may not work with JNI layer
   - **Mitigation**: Focus on minimal command set first, adapt gradually

2. **Command Serialization**: Batch commands may not serialize properly for JNI
   - **Mitigation**: Test with simple commands first, enhance serialization as needed

3. **Error Handling**: Exception propagation between Java and Rust layers
   - **Mitigation**: Implement basic error handling, enhance in later phases

### Integration Risks  
1. **Test Dependencies**: Integration tests may have implicit dependencies on advanced features
   - **Mitigation**: Start with simplest tests, identify dependencies systematically

2. **API Compatibility**: Restored classes may not match exact legacy API
   - **Mitigation**: Compare method signatures carefully, maintain strict compatibility

## Phase 1 Deliverables

### Code Deliverables
- [ ] `Batch.java` - Basic standalone batch implementation
- [ ] `ClusterBatch.java` - Basic cluster batch implementation  
- [ ] `Transaction.java` - Legacy compatibility wrapper
- [ ] `ClusterTransaction.java` - Legacy compatibility wrapper
- [ ] `BaseClient.exec()` - Core batch execution method
- [ ] `GlideClient.exec()` - Standalone batch execution
- [ ] `GlideClusterClient.exec()` - Cluster batch execution
- [ ] Enhanced `BaseBatch` with basic commands

### Test Deliverables
- [ ] 10+ basic integration tests passing
- [ ] Unit tests for new batch classes
- [ ] Verification of `exec()` method signatures

### Documentation Deliverables
- [ ] Updated javadoc for all new methods
- [ ] Phase 1 completion report
- [ ] Known limitations and Phase 2 requirements

## Dependencies

### Prerequisites
- Current JNI implementation must be stable
- `CommandManager` must support basic command execution
- Integration test environment must be functional

### Phase 2 Handoff
Phase 1 sets up foundation for Phase 2:
- Batch class hierarchy established
- `exec()` methods functional with basic commands
- Integration test framework validated
- JNI batch execution proven to work

## Success Metrics

### Quantitative Metrics
- **Integration Tests**: ≥10 basic batch tests passing
- **Command Coverage**: ≥5 basic commands working in batches
- **API Methods**: 6+ new `exec()` methods implemented
- **Build Success**: 100% compilation success

### Qualitative Metrics  
- **Architecture**: Batch system foundation established
- **Compatibility**: Legacy API signatures preserved
- **Performance**: JNI performance benefits maintained
- **Testability**: Integration test framework functional