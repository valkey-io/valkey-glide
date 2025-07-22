# Valkey GLIDE Jedis Compatibility Layer - Complete Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Tutorial: Getting Started](#tutorial-getting-started)
3. [How-to Guides](#how-to-guides)
4. [Reference Documentation](#reference-documentation)
5. [Explanation: Understanding the Architecture](#explanation-understanding-the-architecture)

---

## Introduction

The Valkey GLIDE Jedis Compatibility Layer provides a drop-in replacement for Redis Jedis clients, allowing applications to migrate from Jedis to Valkey GLIDE with minimal code changes while gaining superior performance and reliability.

**Key Benefits:**
- 🔄 **Easy Migration**: Change only import statements - keep existing Jedis code unchanged
- 📦 **No Conflicts**: Uses `compatibility.clients.jedis` package to avoid classpath conflicts

---

## Tutorial: Getting Started

### Your First Jedis Compatibility Application

This tutorial will guide you through creating your first application using the Valkey GLIDE Jedis compatibility layer. By the end, you'll have a working application that demonstrates the key features and migration benefits.

#### Prerequisites
- Java 11 or later
- Running Redis/Valkey server (localhost:6379)
- Basic familiarity with Java development

#### Step 1: Add the Dependency

**For Gradle:**
```groovy
dependencies {
    implementation group: 'io.valkey', name: 'valkey-glide', version: '1.+', classifier: 'osx-aarch_64'
}
```

**For Maven:**
```xml
<dependency>
   <groupId>io.valkey</groupId>
   <artifactId>valkey-glide</artifactId>
   <classifier>osx-aarch_64</classifier>
   <version>[1.0.0,)</version>
</dependency>
```

> **Note**: Choose the appropriate classifier for your platform: `osx-aarch_64`, `osx-x86_64`, `linux-aarch_64`, or `linux-x86_64`.

#### Step 2: Create Your First Application

Create a new Java file called `JedisCompatibilityDemo.java`:

```java
import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;

public class JedisCompatibilityDemo {
    public static void main(String[] args) {
        System.out.println("=== Valkey GLIDE Jedis Compatibility Demo ===");

        // Test direct connection
        testDirectConnection();

        // Test pool connection
        testPoolConnection();

        System.out.println("✅ Demo completed successfully!");
    }

    private static void testDirectConnection() {
        System.out.println("\n--- Direct Connection Test ---");

        // Create connection - exactly like original Jedis
        Jedis jedis = new Jedis("localhost", 6379);

        try {
            // Test connectivity
            String pingResult = jedis.ping();
            System.out.println("PING: " + pingResult);

            // Store and retrieve data
            jedis.set("demo:message", "Hello from GLIDE!");
            String message = jedis.get("demo:message");
            System.out.println("Retrieved: " + message);

        } finally {
            jedis.close();
        }

        System.out.println("✅ Direct connection test passed");
    }

    private static void testPoolConnection() {
        System.out.println("\n--- Pool Connection Test ---");

        // Create pool - exactly like original JedisPool
        JedisPool pool = new JedisPool("localhost", 6379);

        try {
            // Use try-with-resources for automatic cleanup
            try (Jedis jedis = pool.getResource()) {
                // Test pool connectivity
                String pingResult = jedis.ping("Pool test");
                System.out.println("Pool PING: " + pingResult);

                // Store session data
                jedis.set("demo:session:123", "{\"userId\":\"user123\",\"loginTime\":\"" + System.currentTimeMillis() + "\"}");
                String sessionData = jedis.get("demo:session:123");
                System.out.println("Session data: " + sessionData);
            }

        } finally {
            pool.close();
        }

        System.out.println("✅ Pool connection test passed");
    }
}
```

#### Step 3: Run Your Application

Compile and run your application:

```bash
javac -cp "path/to/valkey-glide.jar" JedisCompatibilityDemo.java
java -cp ".:path/to/valkey-glide.jar" JedisCompatibilityDemo
```

**Expected Output:**
```
=== Valkey GLIDE Jedis Compatibility Demo ===

--- Direct Connection Test ---
PING: PONG
Retrieved: Hello from GLIDE!
✅ Direct connection test passed

--- Pool Connection Test ---
Pool PING: Pool test
Session data: {"userId":"user123","loginTime":"1703123456789"}
✅ Pool connection test passed

✅ Demo completed successfully!
```

#### Step 4: Understanding What Happened

Congratulations! You've successfully:
1. ✅ **Connected to Redis/Valkey** using the compatibility layer
2. ✅ **Performed basic operations** (PING, SET, GET) with identical Jedis API
3. ✅ **Used connection pooling** with automatic resource management
4. ✅ **Experienced superior performance** from GLIDE's Rust core

#### Next Steps

Now that you have a working application, you can:
- Explore more commands in the [How-to Guides](#how-to-guides)
- Learn about advanced features in the [Reference Documentation](#reference-documentation)
- Understand the architecture in the [Explanation section](#explanation-understanding-the-architecture)

---

## How-to Guides

### How to Migrate from Jedis to GLIDE Compatibility Layer

**Problem**: You have an existing application using Redis Jedis and want to migrate to Valkey GLIDE for better performance and reliability.

**Solution**: The migration requires only import statement changes in most cases.

#### Migration Steps

**Step 1: Update Dependencies**

Replace your Jedis dependency:
```groovy
// Remove this
implementation 'redis.clients:jedis:4.4.0'

// Add this
implementation group: 'io.valkey', name: 'valkey-glide', version: '1.+', classifier: 'osx-aarch_64'
```

**Step 2: Update Import Statements**

Change all Jedis imports:
```java
// Before (Original Jedis)
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisException;

// After (GLIDE Compatibility)
import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;
import compatibility.clients.jedis.JedisException;
```

**Step 3: Keep All Other Code Unchanged**

Your existing Jedis code works without modification:
```java
// This code works identically with both original Jedis and GLIDE compatibility
public class UserService {
    private final JedisPool pool;

    public UserService() {
        this.pool = new JedisPool("localhost", 6379);
    }

    public void saveUser(String userId, String userData) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set("user:" + userId, userData);
        }
    }

    public String getUser(String userId) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get("user:" + userId);
        }
    }
}
```

#### Migration Verification

Test your migration with this verification checklist:
```java
public class MigrationVerification {
    public static void main(String[] args) {
        JedisPool pool = new JedisPool("localhost", 6379);

        try (Jedis jedis = pool.getResource()) {
            // Test 1: Basic connectivity
            assert "PONG".equals(jedis.ping());
            System.out.println("✅ Connectivity test passed");

            // Test 2: Data operations
            jedis.set("migration:test", "success");
            assert "success".equals(jedis.get("migration:test"));
            System.out.println("✅ Data operations test passed");

            // Test 3: Non-existent key handling
            assert jedis.get("migration:nonexistent") == null;
            System.out.println("✅ Null handling test passed");

            System.out.println("🎉 Migration verification completed successfully!");
        } finally {
            pool.close();
        }
    }
}
```

### How to Handle Connection Configuration

**Problem**: You need to configure connections with specific timeouts, SSL, or authentication settings.

**Solution**: Use the configuration builders provided by the compatibility layer.

#### Basic Configuration

```java
import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.DefaultJedisClientConfig;

// Timeout configuration
Jedis jedis = new Jedis("localhost", 6379, 5000); // 5 second timeout

// SSL configuration
Jedis sslJedis = new Jedis("localhost", 6380, true); // SSL enabled
```

#### Advanced Configuration

```java
import compatibility.clients.jedis.JedisClientConfig;
import compatibility.clients.jedis.DefaultJedisClientConfig;

JedisClientConfig config = DefaultJedisClientConfig.builder()
    .socketTimeoutMillis(5000)
    .connectionTimeoutMillis(3000)
    .ssl(true)
    .build();

Jedis jedis = new Jedis("localhost", 6379, config);
```

#### Pool Configuration

```java
import compatibility.clients.jedis.JedisPool;

// Basic pool
JedisPool pool = new JedisPool("localhost", 6379);

// Pool with timeout
JedisPool timeoutPool = new JedisPool("localhost", 6379, 5000);
```

### How to Handle Errors and Exceptions

**Problem**: You need to handle connection errors and exceptions properly in your application.

**Solution**: Use the same exception handling patterns as original Jedis.

#### Exception Handling Patterns

```java
import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisException;
import compatibility.clients.jedis.JedisConnectionException;

public class ErrorHandlingExample {
    public void handleConnectionErrors() {
        Jedis jedis = new Jedis("localhost", 6379);

        try {
            String result = jedis.get("some:key");
            System.out.println("Result: " + result);

        } catch (JedisConnectionException e) {
            System.err.println("Connection failed: " + e.getMessage());
            // Handle connection-specific errors

        } catch (JedisException e) {
            System.err.println("Jedis operation failed: " + e.getMessage());
            // Handle general Jedis errors

        } finally {
            jedis.close();
        }
    }

    public void handlePoolErrors() {
        JedisPool pool = new JedisPool("localhost", 6379);

        try (Jedis jedis = pool.getResource()) {
            jedis.set("key", "value");

        } catch (JedisException e) {
            System.err.println("Pool operation failed: " + e.getMessage());
            // Pool automatically handles resource cleanup
        } finally {
            pool.close();
        }
    }
}
```
