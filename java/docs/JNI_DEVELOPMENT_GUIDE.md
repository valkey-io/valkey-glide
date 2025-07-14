# JNI Development Guide

This guide provides instructions for developers working with the JNI implementation of Valkey GLIDE.

## Environment Setup

### Prerequisites

- Java 11 or newer
- Rust toolchain (recommended via rustup)
- Gradle
- Valkey server (for testing)
- clang/LLVM (for building the native library)

### Building the JNI Client

1. Clone the repository:
   ```bash
   git clone https://github.com/valkey-io/valkey-glide.git
   cd valkey-glide
   ```

2. Build the JNI implementation:
   ```bash
   cd java
   ./gradlew :java-jni:build
   ```

3. Run tests:
   ```bash
   ./gradlew :java-jni:test
   ```

## Architecture Overview

The JNI implementation consists of two main components:

1. **Java Code**: Located in `client/src/main/java/io/valkey/glide/jni`
   - `GlideJniClient.java`: Core JNI client with typed return methods
   - Native method declarations and Java interfaces

2. **Rust Code**: Located in `src/`
   - `client.rs`: JNI implementation for Java methods
   - Integration with glide-core
   - Type conversion logic

## Component Integration

### CommandManager Integration

The `CommandManager` class has been completely rewritten to use JNI typed methods:

```java
// Example of direct typed method calls
public CompletableFuture<String> executeStringCommand(
        RequestType requestType, String[] arguments) {
    CommandSpec spec = getCommandSpec(requestType);
    return jniClient.executeStringCommand(spec.command, arguments);
}
```

### BaseClient Updates

BaseClient methods have been updated to use the new typed API:

```java
// OLD pattern (with response handler)
return commandManager.submitNewCommand(Get, arguments, this::handleStringResponse);

// NEW pattern (with direct typed returns)
return commandManager.executeStringCommand(Get, arguments);
```

## Type Conversion Guide

When working with the JNI client, follow these type conversion patterns:

### String Operations

```java
// String args to String result
CompletableFuture<String> result = commandManager.executeStringCommand(RequestType.Get, new String[]{"key"});
```

### Numeric Operations

```java
// String args to Long result
CompletableFuture<Long> count = commandManager.executeLongCommand(RequestType.Incr, new String[]{"counter"});
```

### Boolean Operations

```java
// String args to Boolean result
CompletableFuture<Boolean> exists = commandManager.executeBooleanCommand(RequestType.Exists, new String[]{"key"});
```

### Array Operations

```java
// String args to Object[] result
CompletableFuture<Object[]> values = commandManager.executeArrayCommand(RequestType.MGet, new String[]{"key1", "key2"});
```

### GlideString Handling

When working with `GlideString` objects:

```java
// Convert GlideString to String for JNI
String[] arguments = new String[glideStrings.length];
for (int i = 0; i < glideStrings.length; i++) {
    arguments[i] = glideStrings[i].toString();
}

// Execute command with String arguments
CompletableFuture<String> result = commandManager.executeStringCommand(RequestType.Get, arguments);

// Convert String result back to GlideString if needed
CompletableFuture<GlideString> glideResult = result.thenApply(r -> 
    r != null ? GlideString.of(r) : null
);
```

## Command Specification

The CommandManager maintains a mapping of all commands to their return types:

```java
// Command specifications with expected return types
private static final Map<RequestType, CommandSpec> COMMAND_SPECS;

static {
    Map<RequestType, CommandSpec> specs = new HashMap<>();
    
    // String commands
    specs.put(RequestType.Get, new CommandSpec("GET", ReturnType.STRING));
    specs.put(RequestType.Set, new CommandSpec("SET", ReturnType.STRING));
    
    // Long commands
    specs.put(RequestType.Incr, new CommandSpec("INCR", ReturnType.LONG));
    
    // Boolean commands
    specs.put(RequestType.Exists, new CommandSpec("EXISTS", ReturnType.BOOLEAN));
    
    // Array commands
    specs.put(RequestType.MGet, new CommandSpec("MGET", ReturnType.ARRAY));
    
    // Object commands
    specs.put(RequestType.Scan, new CommandSpec("SCAN", ReturnType.OBJECT));
    
    // Store as unmodifiable map
    COMMAND_SPECS = Collections.unmodifiableMap(specs);
}
```

## Adding New Commands

To add a new command to the JNI implementation:

1. Add the command specification to `COMMAND_SPECS` in `CommandManager.java`:
   ```java
   specs.put(RequestType.NewCommand, new CommandSpec("NEWCOMMAND", ReturnType.STRING));
   ```

2. Update the BaseClient method to use the appropriate typed execution method:
   ```java
   @Override
   public CompletableFuture<String> newCommand(String arg) {
       return commandManager.executeStringCommand(NewCommand, new String[]{arg});
   }
   ```

## Extending the Native Layer

If you need to add new native methods:

1. Add the native method declaration to `GlideJniClient.java`:
   ```java
   public native CompletableFuture<YourType> executeYourTypeCommand(String command, String[] arguments);
   ```

2. Implement the JNI method in `client.rs`:
   ```rust
   #[no_mangle]
   pub extern "system" fn Java_io_valkey_glide_jni_GlideJniClient_executeYourTypeCommand(
       env: JNIEnv,
       _class: JClass,
       command: JString,
       args: JObjectArray
   ) -> jobject {
       // Implementation
   }
   ```

## Debugging JNI Issues

For JNI debugging:

1. Enable JNI logging by setting environment variable:
   ```bash
   export JAVA_OPTS="-Xcheck:jni -verbose:jni"
   ```

2. Use Rust debug prints that appear in native logs:
   ```rust
   eprintln!("Debug: {}", value);
   ```

3. Use Java System.loadLibrary debug mode:
   ```java
   System.setProperty("java.library.path.debug", "true");
   ```

## Performance Testing

To benchmark the JNI implementation:

```bash
cd java
./benchmark_comparison.sh
```

The script will run benchmarks for both UDS and JNI implementations and generate a comparison report.

## Common Issues and Solutions

### Library Loading Issues

**Problem**: `UnsatisfiedLinkError: no glidejni in java.library.path`

**Solution**: Ensure the native library is properly built and included in the library path:
```java
System.setProperty("java.library.path", "/path/to/native/lib");
```

### Type Conversion Errors

**Problem**: Unexpected type conversion results

**Solution**: Review the type mapping in the `ReturnType` enum and ensure commands are properly mapped in `COMMAND_SPECS`.

### Memory Management

**Problem**: Potential memory leaks with JNI references

**Solution**: The implementation uses the Java 11+ Cleaner API for automatic resource management. Ensure all native resources are properly tracked by the Cleaner.

## Best Practices

1. **String Arguments**: Always convert GlideString to String before passing to JNI
2. **Type Consistency**: Follow the command specification return types
3. **Error Handling**: Handle JNI exceptions and return errors properly
4. **Testing**: Write comprehensive tests for all command types
5. **Performance**: Monitor and benchmark critical path operations
6. **Memory**: Avoid unnecessary object creation in JNI code