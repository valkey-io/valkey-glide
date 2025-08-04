# IAM Token Manager Simplification

## Current Complex Type Analysis

The current `iam_token_manager` type is:
```rust
iam_token_manager: Arc<RwLock<Option<Arc<tokio::sync::RwLock<crate::iam::IAMTokenManager>>>>>
```

This is a "double RwLock" structure that exists for these reasons:

### Why the Complexity Exists

1. **Client Level** (`Arc<RwLock<Option<...>>>`)
   - Tracks if IAM token manager exists for this client
   - `Option` allows clients without IAM authentication
   - `RwLock` allows concurrent reads, exclusive writes
   - `Arc` enables sharing across threads

2. **Manager Level** (`Arc<tokio::sync::RwLock<IAMTokenManager>>`)
   - Protects the token manager instance
   - Coordinates refresh scheduling and token access
   - Uses async `tokio::sync::RwLock` for async operations
   - `Arc` allows sharing with background refresh task

3. **Token Level** (inside `IAMTokenManager`)
   - Internal `cached_token: Arc<RwLock<String>>`
   - Fast concurrent reads for token string
   - Atomic updates during refresh

## Problems with Current Design

1. **Complexity**: Nested locks are hard to understand/maintain
2. **Performance**: Multiple lock acquisitions for simple operations  
3. **Deadlock Risk**: Complex lock ordering requirements
4. **Code Duplication**: Similar patterns in lazy client handling

## Proposed Simplified Solution

### Option 1: Single Manager with Internal State Management

```rust
// Simplified client field
iam_token_manager: Option<Arc<IAMTokenManager>>,

// Enhanced IAMTokenManager with internal state management
pub struct IAMTokenManager {
    // Internal state protected by a single RwLock
    state: Arc<RwLock<IAMTokenState>>,
    // Background task handle
    refresh_task: Option<JoinHandle<()>>,
    shutdown_notify: Arc<Notify>,
}

struct IAMTokenState {
    region: String,
    cluster_name: String, 
    username: String,
    cached_token: String,
    refresh_interval_minutes: u32,
}

impl IAMTokenManager {
    pub async fn get_token(&self) -> String {
        let state = self.state.read().await;
        state.cached_token.clone()
    }
    
    pub async fn refresh_token(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let new_token = {
            let state = self.state.read().await;
            Self::generate_token_static(&state.region, &state.cluster_name, &state.username).await?
        };
        
        let mut state = self.state.write().await;
        state.cached_token = new_token;
        Ok(())
    }
}
```

### Option 2: Arc<RwLock<Option<T>>> Helper Pattern

```rust
// Create a reusable pattern for optional shared resources
pub struct OptionalShared<T> {
    inner: Arc<RwLock<Option<Arc<T>>>>,
}

impl<T> OptionalShared<T> {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(RwLock::new(None)),
        }
    }
    
    pub async fn get(&self) -> Option<Arc<T>> {
        let guard = self.inner.read().await;
        guard.clone()
    }
    
    pub async fn set(&self, value: Arc<T>) {
        let mut guard = self.inner.write().await;
        *guard = Some(value);
    }
    
    pub async fn is_some(&self) -> bool {
        let guard = self.inner.read().await;
        guard.is_some()
    }
}

// Usage in Client
pub struct Client {
    // Much cleaner!
    iam_token_manager: OptionalShared<IAMTokenManager>,
    // ... other fields
}
```

### Option 3: State Machine Approach

```rust
pub enum IAMState {
    Disabled,
    Enabled(Arc<IAMTokenManager>),
}

pub struct Client {
    iam_state: Arc<RwLock<IAMState>>,
    // ... other fields
}

impl Client {
    async fn get_iam_token(&self) -> Option<String> {
        let state = self.iam_state.read().await;
        match &*state {
            IAMState::Disabled => None,
            IAMState::Enabled(manager) => Some(manager.get_token().await),
        }
    }
}
```

## Recommended Approach: Option 1

**Option 1** is recommended because:

1. **Simplicity**: Single lock per manager, clear ownership
2. **Performance**: Fewer lock acquisitions
3. **Safety**: Eliminates deadlock risks from nested locks
4. **Maintainability**: Easier to understand and modify
5. **Testability**: Simpler to unit test individual components

## Implementation Plan

1. **Phase 1**: Refactor `IAMTokenManager` to use internal state management
2. **Phase 2**: Update `Client` to use simplified `Option<Arc<IAMTokenManager>>`
3. **Phase 3**: Update lazy client handling to match new pattern
4. **Phase 4**: Add comprehensive tests for the simplified design
5. **Phase 5**: Update documentation and examples

## Migration Strategy

The change can be made incrementally:

1. Keep the current interface working
2. Add new simplified methods alongside existing ones
3. Migrate internal usage to new methods
4. Remove old complex methods once migration is complete
5. Update wrapper language bindings

This approach maintains backward compatibility while improving the internal architecture.
