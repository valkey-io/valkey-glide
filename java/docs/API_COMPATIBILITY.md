# API Compatibility Analysis

## Overview

This document analyzes the compatibility between the current JNI-based implementation and the legacy UDS-based implementation to ensure 100% public API compatibility.

## Compatibility Goals

- ✅ **Method Signatures**: All public methods must have identical signatures
- ✅ **Exception Types**: Same exception types thrown in same scenarios  
- ✅ **Return Types**: Identical return types and semantics
- ✅ **Package Structure**: Same package names and class locations
- ✅ **Legacy Support**: Support for deprecated APIs during transition

## Public API Surface Analysis

### Core Client Classes

#### BaseClient Compatibility

**Legacy UDS API** (from `archive/java-old/`):
```java
public abstract class BaseClient implements AutoCloseable {
    // Basic connection management
    public abstract CompletableFuture<String> ping();
    public abstract CompletableFuture<String> ping(GlideString message);
    
    // Batch execution - CRITICAL MISSING
    public abstract CompletableFuture<Object[]> exec(BaseBatch batch, boolean raiseOnError);
    
    // String operations
    public abstract CompletableFuture<String> set(GlideString key, GlideString value);
    public abstract CompletableFuture<GlideString> get(GlideString key);
    // ... 200+ methods
}
```

**Current JNI API**:
```java
public abstract class BaseClient implements AutoCloseable {
    // Basic connection management - ✅ COMPATIBLE
    public abstract CompletableFuture<String> ping();
    public abstract CompletableFuture<String> ping(GlideString message);
    
    // Batch execution - ❌ MISSING (Phase 1)
    // public abstract CompletableFuture<Object[]> exec(BaseBatch batch, boolean raiseOnError);
    
    // String operations - ⚠️ REDUCED SET  
    // Currently only ~20 methods vs expected 200+
}
```

#### GlideClient Compatibility

**Legacy UDS API**:
```java
public class GlideClient extends BaseClient {
    // Factory methods - ✅ IDENTICAL
    public static CompletableFuture<GlideClient> createClient(GlideClientConfiguration config);
    
    // Batch execution - ❌ MISSING (Phase 1)
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError);
    public CompletableFuture<Object[]> exec(Batch batch, boolean raiseOnError, BatchOptions options);
    
    // Transaction support - ❌ MISSING (Phase 2)
    // Implements TransactionsCommands interface
}
```

**Current JNI API**:
```java
public class GlideClient extends BaseClient {
    // Factory methods - ✅ IDENTICAL
    public static CompletableFuture<GlideClient> createClient(GlideClientConfiguration config);
    
    // Batch execution - ❌ MISSING
    // Transaction support - ❌ MISSING
}
```

#### GlideClusterClient Compatibility

**Legacy UDS API**:
```java
public class GlideClusterClient extends BaseClient {
    // Factory methods - ✅ IDENTICAL
    public static CompletableFuture<GlideClusterClient> createClient(GlideClusterClientConfiguration config);
    
    // Cluster batch execution - ❌ MISSING (Phase 1)
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError);
    public CompletableFuture<Object[]> exec(ClusterBatch batch, boolean raiseOnError, ClusterBatchOptions options);
    
    // Cluster transaction support - ❌ MISSING (Phase 2)
    // Implements TransactionsClusterCommands interface
}
```

### Batch and Transaction Classes

#### Batch Class Hierarchy

**Legacy UDS API**:
```java
// Core batch classes
public abstract class BaseBatch { ... }
public class Batch extends BaseBatch { ... }
public class ClusterBatch extends BaseBatch { ... }

// Legacy transaction classes
public class Transaction extends Batch { ... }
public class ClusterTransaction extends ClusterBatch { ... }
```

**Current JNI API**:
```java
// Core batch classes
public abstract class BaseBatch { ... }  // ✅ EXISTS (limited functionality)
// ❌ MISSING: Batch, ClusterBatch, Transaction, ClusterTransaction
```

#### Command Coverage Analysis

**Legacy Batch Commands** (~200 methods):
```java
// String commands (20+ methods)
Batch set(GlideString key, GlideString value);
Batch get(GlideString key);
Batch mset(Map<GlideString, GlideString> keyValueMap);
Batch mget(GlideString[] keys);
// ... 16+ more string commands

// Hash commands (25+ methods)  
Batch hset(GlideString key, Map<GlideString, GlideString> fieldValueMap);
Batch hget(GlideString key, GlideString field);
Batch hmget(GlideString key, GlideString[] fields);
// ... 22+ more hash commands

// List commands (20+ methods)
Batch lpush(GlideString key, GlideString[] elements);
Batch rpush(GlideString key, GlideString[] elements);
Batch lpop(GlideString key);
// ... 17+ more list commands

// Set commands (15+ methods)
Batch sadd(GlideString key, GlideString[] members);
Batch srem(GlideString key, GlideString[] members);
Batch smembers(GlideString key);
// ... 12+ more set commands

// Sorted Set commands (25+ methods)
Batch zadd(GlideString key, Map<GlideString, Double> memberScores);
Batch zrange(GlideString key, RangeOptions rangeOptions);
Batch zrem(GlideString key, GlideString[] members);
// ... 22+ more sorted set commands

// Stream commands (20+ methods)
Batch xadd(GlideString key, Map<GlideString, GlideString> values);
Batch xread(Map<GlideString, GlideString> keysAndIds);
Batch xlen(GlideString key);
// ... 17+ more stream commands

// Server commands (15+ methods)
Batch info();
Batch info(InfoOptions options);
Batch dbsize();
// ... 12+ more server commands

// Additional command categories:
// - Bitmap commands (10+ methods)
// - Geospatial commands (10+ methods)  
// - HyperLogLog commands (5+ methods)
// - Pub/Sub commands (10+ methods)
// - Scripting commands (8+ methods)
```

**Current BaseBatch Commands** (~20 methods):
```java
// Limited command set - mostly basic operations
public BaseBatch ping();
public BaseBatch ping(GlideString message);
// Missing: 95% of expected commands
```

### Model Classes Compatibility

#### Configuration Classes

**Legacy UDS API**:
```java
// ✅ IDENTICAL - Configuration classes unchanged
public class GlideClientConfiguration { ... }
public class GlideClusterClientConfiguration { ... }
public class NodeAddress { ... }
public class ServerCredentials { ... }
// ... all configuration classes match
```

#### Options Classes

**Legacy UDS API**:
```java
// Batch options - ❌ MISSING (Phase 2)
public class BatchOptions extends BaseBatchOptions { ... }
public class ClusterBatchOptions extends BaseBatchOptions { ... }
public class ClusterBatchRetryStrategy { ... }

// Command options - ✅ MOSTLY PRESENT
public class SetOptions { ... }
public class GetExOptions { ... }
public class RangeOptions { ... }
// ... most option classes exist
```

#### Exception Classes

**Legacy UDS API**:
```java
// ✅ IDENTICAL - Exception hierarchy unchanged
public class GlideException extends RuntimeException { ... }
public class ConnectionException extends GlideException { ... }
public class RequestException extends GlideException { ... }
public class TimeoutException extends GlideException { ... }
// ... all exception classes match
```

### Interface Compatibility

#### Command Interfaces

**Legacy UDS API**:
```java
// Transaction interfaces - ❌ MISSING (Phase 2)
public interface TransactionsCommands {
    TransactionsCommands multi();
    TransactionsCommands exec();
    TransactionsCommands set(GlideString key, GlideString value);
    // ... 200+ command methods
}

public interface TransactionsClusterCommands extends TransactionsCommands {
    // Cluster-specific transaction methods
}
```

**Current JNI API**:
```java
// ❌ MISSING: All transaction interfaces
```

#### Module Interfaces

**Legacy UDS API**:
```java
// Server modules - ❌ MISSING (Phase 4)
public class Json {
    public static Batch jsonGet(GlideString key);
    public static Batch jsonSet(GlideString key, GlideString path, GlideString value);
    // ... JSON module commands
}

public class FT {
    public static Batch ftSearch(GlideString index, GlideString query);
    public static Batch ftCreate(GlideString index, FTCreateOptions options);
    // ... Search module commands  
}
```

## Compatibility Matrix

| Component | Legacy UDS | Current JNI | Status | Restoration Phase |
|-----------|------------|-------------|---------|-------------------|
| **Core Client Classes** |
| BaseClient basic methods | ✅ | ✅ | Compatible | N/A |
| BaseClient exec methods | ✅ | ❌ | Missing | Phase 1 |
| GlideClient factory | ✅ | ✅ | Compatible | N/A |
| GlideClient exec methods | ✅ | ❌ | Missing | Phase 1 |
| GlideClusterClient factory | ✅ | ✅ | Compatible | N/A |
| GlideClusterClient exec methods | ✅ | ❌ | Missing | Phase 1 |
| **Batch System** |
| BaseBatch framework | ✅ | ⚠️ | Limited | Phase 1 |
| Batch class | ✅ | ❌ | Missing | Phase 1 |
| ClusterBatch class | ✅ | ❌ | Missing | Phase 1 |
| Transaction class | ✅ | ❌ | Missing | Phase 1 |
| ClusterTransaction class | ✅ | ❌ | Missing | Phase 1 |
| **Command Coverage** |
| String commands | ✅ | ⚠️ | Partial (5%) | Phase 3 |
| Hash commands | ✅ | ❌ | Missing | Phase 3 |
| List commands | ✅ | ❌ | Missing | Phase 3 |
| Set commands | ✅ | ❌ | Missing | Phase 3 |
| Sorted Set commands | ✅ | ❌ | Missing | Phase 3 |
| Stream commands | ✅ | ❌ | Missing | Phase 3 |
| Bitmap commands | ✅ | ❌ | Missing | Phase 3 |
| Geospatial commands | ✅ | ❌ | Missing | Phase 3 |
| Server commands | ✅ | ⚠️ | Partial (10%) | Phase 3 |
| **Transaction Interfaces** |
| TransactionsCommands | ✅ | ❌ | Missing | Phase 2 |
| TransactionsClusterCommands | ✅ | ❌ | Missing | Phase 2 |
| **Batch Options** |
| BatchOptions | ✅ | ❌ | Missing | Phase 2 |
| ClusterBatchOptions | ✅ | ❌ | Missing | Phase 2 |
| ClusterBatchRetryStrategy | ✅ | ❌ | Missing | Phase 2 |
| **Server Modules** |
| JSON module | ✅ | ❌ | Missing | Phase 4 |
| FT (search) module | ✅ | ❌ | Missing | Phase 4 |
| **Configuration & Models** |
| Client configurations | ✅ | ✅ | Compatible | N/A |
| Command options | ✅ | ✅ | Compatible | N/A |
| Exception classes | ✅ | ✅ | Compatible | N/A |
| Utility classes | ✅ | ✅ | Compatible | N/A |

## Breaking Changes Analysis

### Intentional Breaking Changes
**None planned** - Goal is 100% backward compatibility

### Potential Compatibility Risks

#### 1. JNI Performance Characteristics
- **Risk**: Different performance profile may affect timing-sensitive code
- **Mitigation**: Performance tests to ensure JNI benefits don't break existing applications

#### 2. Error Message Changes  
- **Risk**: JNI layer may produce different error messages
- **Mitigation**: Map JNI errors to match legacy error messages exactly

#### 3. Memory Management Changes
- **Risk**: Different resource cleanup patterns with JNI vs UDS
- **Mitigation**: Ensure resource cleanup behavior is identical to legacy

#### 4. Threading Model Changes
- **Risk**: JNI threading model may differ from UDS
- **Mitigation**: Verify thread safety guarantees match legacy implementation

## Migration Path

### Phase 1: Basic Compatibility
- Restore core `exec()` methods with identical signatures
- Ensure basic batch operations work exactly like legacy

### Phase 2: Interface Compatibility  
- Restore transaction interfaces with identical method signatures
- Ensure batch options behave identically to legacy

### Phase 3: Command Compatibility
- Restore all 200+ commands with identical behavior
- Ensure command results match legacy format exactly

### Phase 4: Feature Compatibility
- Restore server modules with identical APIs
- Ensure advanced features behave identically

### Validation Strategy
- **Side-by-side Testing**: Run same integration tests against both implementations
- **API Signature Verification**: Automated checks for method signature compatibility  
- **Behavior Verification**: Functional tests to ensure identical behavior
- **Performance Validation**: Ensure JNI benefits don't break compatibility

## Compatibility Checklist

### API Surface Compatibility
- [ ] All public method signatures identical
- [ ] All public class hierarchies identical  
- [ ] All public exception types identical
- [ ] All public return types identical

### Behavioral Compatibility
- [ ] Command results format identical
- [ ] Error conditions trigger identical exceptions
- [ ] Resource cleanup behavior identical
- [ ] Threading behavior identical

### Performance Compatibility
- [ ] No functionality regressions due to performance changes
- [ ] Timeout behavior identical
- [ ] Memory usage patterns compatible

### Integration Compatibility  
- [ ] All legacy integration tests pass without modification
- [ ] Maven/Gradle dependencies work identically
- [ ] Module system compatibility maintained
- [ ] Serialization compatibility preserved (if applicable)

## Success Criteria

### Quantitative Metrics
- **API Coverage**: 100% of legacy public methods available
- **Test Compatibility**: 100% of legacy integration tests pass
- **Signature Match**: 100% of method signatures identical
- **Exception Match**: 100% of exception scenarios identical

### Qualitative Metrics
- **Drop-in Replacement**: JNI implementation can replace UDS with zero code changes
- **Behavioral Equivalence**: All operations behave identically to legacy
- **Performance Preservation**: JNI benefits don't compromise compatibility
- **Future Compatibility**: Architecture supports future enhancements without breaking changes