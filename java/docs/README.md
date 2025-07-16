# Valkey GLIDE Java Documentation

This directory contains documentation for the Java implementation of Valkey GLIDE.

## Documentation Structure

- [`CURRENT_STATUS.md`](CURRENT_STATUS.md) - Current implementation status and architecture overview
- [`RESTORATION_PLAN.md`](RESTORATION_PLAN.md) - Detailed plan for restoring legacy functionality
- [`DESIGN/`](DESIGN/) - Design documents for each restoration phase
- [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md) - API compatibility analysis with legacy implementation
- [`MIGRATION_GUIDE.md`](MIGRATION_GUIDE.md) - Guide for migrating from UDS to JNI implementation

## Quick Links

- [Project README](../README.md) - Main project documentation
- [Integration Tests](../integTest/) - Test suite that validates functionality
- [Legacy Implementation](../archive/java-old/) - Archived UDS-based implementation
- [Current Implementation](../client/) - JNI-based implementation

## Implementation Overview

The current Java client uses a JNI-based architecture instead of Unix Domain Sockets (UDS), providing:

- **Performance**: 1.8-2.9x better performance than UDS implementation
- **Direct Integration**: Eliminates inter-process communication overhead
- **Modern Java**: Uses Java 11+ Cleaner API for resource management
- **Configuration-Based API**: Matches glide-core design patterns

## Status

- ✅ **Complete**: JNI infrastructure and basic operations
- 🔄 **In Progress**: Batch/transaction system restoration
- ⏳ **Planned**: Full command coverage and advanced features

See [`CURRENT_STATUS.md`](CURRENT_STATUS.md) for detailed status information.