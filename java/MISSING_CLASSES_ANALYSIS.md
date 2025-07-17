# Missing Classes Analysis for Java Valkey GLIDE JNI Implementation

## Overview
This document catalogs all missing Java classes required for full integration test compatibility. The current JNI implementation has excellent core functionality but lacks specific API configuration/option classes.

## Category 1: Function/Script Management (7 classes)
**Purpose**: Support for FUNCTION family commands and script execution with options

1. **FunctionRestorePolicy.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/function/`
   - Purpose: Enum for FUNCTION RESTORE policy (APPEND, FLUSH, REPLACE)
   - Required by: Function command tests

2. **FunctionListOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/function/`
   - Purpose: Options for FUNCTION LIST command
   - Required by: Function listing tests

3. **FunctionLoadOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/function/`
   - Purpose: Options for FUNCTION LOAD command
   - Required by: Function loading tests

4. **ScriptOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/`
   - Purpose: Options for script execution commands
   - Required by: Script execution tests

5. **ScriptOptionsGlideString.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/`
   - Purpose: Binary-safe script options
   - Required by: Binary script tests

6. **ScriptArgOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/`
   - Purpose: Base class for script argument options
   - Required by: ScriptOptions inheritance

7. **ScriptArgOptionsGlideString.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/`
   - Purpose: Binary-safe script argument options
   - Required by: ScriptOptionsGlideString inheritance

## Category 2: Full-Text Search (FT) Module (4 classes)
**Purpose**: Support for RediSearch/FT module commands

1. **FTCreateOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/FT/`
   - Purpose: Complex options for FT.CREATE command with nested classes
   - Required by: Vector search tests

2. **FTSearchOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/FT/`
   - Purpose: Options for FT.SEARCH command
   - Required by: Search tests

3. **FTAggregateOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/FT/`
   - Purpose: Options for FT.AGGREGATE command
   - Required by: Aggregation tests

4. **FTProfileOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/FT/`
   - Purpose: Options for FT.PROFILE command
   - Required by: Profiling tests

## Category 3: JSON Module (3 classes)
**Purpose**: Support for RedisJSON module commands

1. **JsonGetOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/json/`
   - Purpose: Options for JSON.GET command
   - Required by: JSON retrieval tests

2. **JsonGetOptionsBinary.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/json/`
   - Purpose: Binary-safe JSON GET options
   - Required by: Binary JSON tests

3. **JsonArrindexOptions.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/models/commands/json/`
   - Purpose: Options for JSON.ARRINDEX command
   - Required by: JSON array tests

## Category 4: Server Module Interfaces (3 classes)
**Purpose**: Command interfaces for server modules

1. **FT.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/commands/servermodules/`
   - Purpose: Interface for FT (RediSearch) commands
   - Required by: All FT tests

2. **Json.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/commands/servermodules/`
   - Purpose: Interface for JSON module commands
   - Required by: All JSON tests

3. **JsonBatch.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/commands/servermodules/`
   - Purpose: Batch operations for JSON commands
   - Required by: JSON batch tests

## Category 5: Enhanced Scan Operations (1 class)
**Purpose**: Complete scan functionality

1. **ScanOptions.ObjectType enum**
   - Location: Current `ScanOptions.java` is incomplete stub
   - Purpose: ObjectType enum for SCAN command filtering
   - Required by: Scan filtering tests
   - **Action**: Enhance existing class rather than replace

## Category 6: Base Command Interfaces (3 classes)
**Purpose**: Foundation interfaces for scripting and functions

1. **ScriptingAndFunctionsBaseCommands.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/commands/`
   - Purpose: Base interface for scripting/function commands
   - Required by: All script/function option classes

2. **ScriptingAndFunctionsCommands.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/commands/`
   - Purpose: Standalone scripting/function commands
   - Required by: Standalone script tests

3. **ScriptingAndFunctionsClusterCommands.java**
   - Location: `archive/java-old/client/src/main/java/glide/api/commands/`
   - Purpose: Cluster scripting/function commands
   - Required by: Cluster script tests

## Missing Package Structure
The following packages need to be created/populated:
- `glide.api.models.commands.function` (new package)
- `glide.api.models.commands.FT` (new package)
- `glide.api.models.commands.json` (new package)
- `glide.api.commands.servermodules` (new package)
- `glide.api.commands` (needs 3 additional interfaces)

## Integration Test Impact
**Current Status**: 64+ compilation errors
**Expected After Restoration**: 0 compilation errors
**Test Coverage**: Complete compatibility with legacy UDS implementation

## Next Steps
1. Copy all 26 missing classes from archive locations
2. Update module-info.java to export new packages
3. Rebuild client with complete API surface
4. Run full integration test suite
5. Fix any remaining runtime issues

## Validation Strategy
After restoration, the integration tests should run exactly as they did with the UDS implementation, validating that our JNI bridge properly supports all command families and options.