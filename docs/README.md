# Valkey-Glide Java Documentation

This directory contains comprehensive documentation for the Valkey-Glide Java client implementation.

## Directory Structure

### Main Documentation
- **JNI_PROJECT_CONTEXT.md** - Overall project context and objectives
- **PROJECT_OVERVIEW.md** - High-level project overview and status
- **INTEGRATION_API_REQUIREMENTS.md** - API integration requirements and specifications
- **TECHNICAL_REFERENCE.md** - Technical reference and implementation details

### Architecture Documentation (`architecture/`)
- **JNI_ARCHITECTURE.md** - Detailed JNI architecture and design decisions

### Development Documentation (`development/`)
- **JNI_DEVELOPMENT_GUIDE.md** - Guide for developers working on JNI implementation
- **COMPLETED_PHASES.md** - Record of completed development phases
- **NEXT_DEVELOPMENT_STEPS.md** - Next steps and development roadmap
- **NEXT_SESSION_CHECKLIST.md** - Checklist for upcoming development sessions
- **SESSION_HANDOFF.md** - Session handoff information and context

### Implementation Documentation (`implementation/`)
- **COMMAND_TYPE_IMPLEMENTATION.md** - Command type system implementation details
- **COMMAND_BATCHES.md** - Command batching implementation and design
- **BATCH_IMPLEMENTATION_DESIGN.md** - Detailed batch implementation design

## Project Status

The Java client is implemented using direct JNI integration with the Rust core, bypassing the UDS/protobuf layer for improved performance. The implementation includes:

- **BaseClient.java**: 22 Redis API methods with CompletableFuture support
- **GlideClient.java**: Core JNI client with direct native integration
- **Command/CommandType System**: Comprehensive command execution framework
- **Modular Structure**: Clean separation between Rust (src/) and Java (client/) code

## Getting Started

1. Start with `PROJECT_OVERVIEW.md` for a high-level understanding
2. Review `JNI_PROJECT_CONTEXT.md` for detailed context
3. Check `architecture/JNI_ARCHITECTURE.md` for architectural details
4. Follow `development/JNI_DEVELOPMENT_GUIDE.md` for development setup

## Development Workflow

For active development, consult:
- `development/NEXT_DEVELOPMENT_STEPS.md` for current priorities
- `development/NEXT_SESSION_CHECKLIST.md` for session preparation
- `development/SESSION_HANDOFF.md` for context between sessions
