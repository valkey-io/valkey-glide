# Database Tracking and Restoration Feature

## Overview

The Valkey GLIDE client now automatically tracks database changes made via `SELECT` commands and restores the correct database after reconnection. This ensures session consistency when clients change databases during their lifetime.

## How It Works

### Database Tracking
- The client tracks the current database index whenever a `SELECT` command succeeds
- Only successful single-request `SELECT` commands are tracked (not in pipelines/transactions)
- The tracking happens automatically without any configuration needed

### Automatic Restoration
- After a connection is reestablished (due to network issues, server restart, etc.), the client automatically issues a `SELECT` command to restore the last selected database
- This happens transparently before the first command after reconnection
- The restoration is attempted once per connection to avoid performance overhead

### Supported Modes
- **Standalone Mode**: Full support for database tracking and restoration
- **Cluster Mode**: Database tracking works, but restoration is logged only (cluster mode may not support database selection depending on configuration)

## API

### Automatic Behavior
No code changes are required. The feature works automatically:

```rust
use glide_core::client::{Client, ConnectionRequest, NodeAddress};

// Create client
let mut client = Client::new(request, None).await?;

// Change database - this is automatically tracked
let mut select_cmd = redis::cmd("SELECT");
select_cmd.arg(2);
client.send_command(&select_cmd, None).await?;

// If connection is lost and reestablished, the client will
// automatically restore database 2 before the next command
let mut get_cmd = redis::cmd("GET");
get_cmd.arg("some_key");
client.send_command(&get_cmd, None).await?; // Will be executed in database 2
```

### Manual Control
For advanced use cases, you can manually restore the database:

```rust
// Get the currently tracked database
let current_db = client.get_current_database();

// Manually restore the database (useful after explicit reconnection)
client.restore_database().await?;
```

## Implementation Details

### Database Tracking
- The client tracks the database in an atomic `i64` field
- When a `SELECT` command returns `"OK"`, the target database is extracted from the command arguments and stored
- The tracking is thread-safe and works across all connection types

### Reconnection Detection
- Connection errors are detected via error analysis
- When a connection error occurs, the restoration flag is reset
- The next command will trigger automatic database restoration

### Performance Considerations
- Database restoration happens at most once per connection
- No overhead for commands when the database doesn't need restoration
- Failed restoration attempts don't block the main command execution

## Testing

### Unit Tests
Basic functionality is tested without requiring a Redis server:
```bash
cargo test test_database_tracking
```

### Integration Tests
Full functionality tests require a running Redis server:
```bash
# Start Redis server
redis-server --port 6379

# Run integration tests
cargo test --test test_database_integration -- --ignored
```

## Limitations

1. **Pipeline/Transaction Support**: Database changes within pipelines or transactions are not tracked to avoid complexity
2. **Cluster Mode**: Database restoration is not performed in cluster mode (depends on cluster configuration)
3. **Error Handling**: If database restoration fails, the main command still proceeds (restoration failure is logged)

## Migration Guide

This feature is backwards compatible and requires no code changes. Existing applications will automatically benefit from database tracking and restoration.

### Before
```rust
// After reconnection, client would be in the initial database (usually 0)
// regardless of previous SELECT commands
```

### After
```rust
// After reconnection, client automatically restores the last selected database
// No code changes needed - works transparently
```

## Examples

### Basic Usage
```rust
use glide_core::client::{Client, ConnectionRequest, NodeAddress};

async fn example_database_tracking() -> Result<(), Box<dyn std::error::Error>> {
    let mut request = ConnectionRequest::default();
    request.addresses = vec![NodeAddress {
        host: "localhost".to_string(),
        port: 6379,
    }];
    
    let mut client = Client::new(request, None).await?;
    
    // Switch to database 5
    let mut select_cmd = redis::cmd("SELECT");
    select_cmd.arg(5);
    client.send_command(&select_cmd, None).await?;
    
    // Set a value in database 5
    let mut set_cmd = redis::cmd("SET");
    set_cmd.arg("mykey").arg("myvalue");
    client.send_command(&set_cmd, None).await?;
    
    // Even if connection is lost and reestablished,
    // subsequent commands will execute in database 5
    let mut get_cmd = redis::cmd("GET");
    get_cmd.arg("mykey");
    let value = client.send_command(&get_cmd, None).await?;
    
    Ok(())
}
```

### Multiple Database Changes
```rust
async fn example_multiple_databases() -> Result<(), Box<dyn std::error::Error>> {
    let mut client = create_client().await?;
    
    // Switch through multiple databases
    for db in 1..=3 {
        let mut select_cmd = redis::cmd("SELECT");
        select_cmd.arg(db);
        client.send_command(&select_cmd, None).await?;
        
        // Set a marker in each database
        let mut set_cmd = redis::cmd("SET");
        set_cmd.arg("db_marker").arg(format!("database_{}", db));
        client.send_command(&set_cmd, None).await?;
    }
    
    // Client is now tracking database 3
    // After reconnection, commands will execute in database 3
    
    Ok(())
}
```