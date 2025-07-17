# Missing API Components in JNI Implementation

This document lists the API components that need to be implemented to make the restored integration tests compile and run successfully.

## Status Summary

✅ **Working**: Core integration tests (BatchTests, SharedCommandTests, ConnectionTests)
🔄 **In Progress**: Fixing compilation errors for restored tests
❌ **Missing**: Architecture-specific and module-specific components

## Missing Components by Category

### 1. Vector Search Module (VectorSearchTests.java)
**Status**: ❌ Missing entire FT module

**Missing Classes:**
- `glide.api.commands.servermodules.FT`
- `glide.api.commands.servermodules.Json`
- `glide.api.models.commands.FT.FTAggregateOptions`
- `glide.api.models.commands.FT.FTCreateOptions`
- `glide.api.models.commands.FT.FTProfileOptions`
- `glide.api.models.commands.FT.FTSearchOptions`
- All FT option subclasses (Apply, GroupBy, SortBy, etc.)

**Impact**: VectorSearchTests cannot compile

### 2. JSON Module (JsonTests.java, JsonTest.java)
**Status**: 🔄 Partially working - JSON package exported but server modules missing

**Missing Classes:**
- `glide.api.commands.servermodules.Json`
- `glide.api.commands.servermodules.JsonBatch`
- `glide.api.models.commands.json.JsonGetOptionsBinary`

**Impact**: JSON tests cannot compile

### 3. Cluster Client Missing Methods
**Status**: ❌ Missing methods in GlideClusterClient

**Missing Methods:**
- `pfcount(String[])`
- `pfcount(GlideString[])`
- `pfmerge(String, String[])`
- `pfmerge(GlideString, GlideString[])`
- `bzpopmax(String[], double)`
- `bzpopmax(GlideString[], double)`
- `bzpopmin(String[], double)`
- `bzpopmin(GlideString[], double)`
- `zmpop(String[], ScoreFilter)`
- `zmpop(GlideString[], ScoreFilter)`
- `bzmpop(String[], ScoreFilter, double)`
- `bzmpop(GlideString[], ScoreFilter, double)`
- `functionListBinary(boolean, Route)`
- `info(Route)` - routing versions

**Impact**: Cluster client tests fail compilation

### 4. Error Handling Compatibility Layer
**Status**: 🔄 Partially implemented - basic structure created

**Missing Classes:**
- Proper `CallbackDispatcher` replacement
- `ChannelHandler` replacement  
- `ChannelFuture` replacement
- `BaseResponseResolver` replacement
- `Platform` utility class
- `ThreadPoolResourceAllocator` replacement

**Impact**: ExceptionHandlingTests cannot compile

### 5. Architecture-Specific Components
**Status**: ❌ Not needed for JNI but tests expect them

**Missing Classes:**
- `glide.ffi.resolvers.SocketListenerResolver`
- `glide.connectors.handlers.*`
- `glide.connectors.resources.*`
- `glide.managers.ConnectionManager`
- `glide.managers.BaseResponseResolver`
- `glide.managers.GlideExceptionCheckedFunction`

**Impact**: Architecture-specific tests cannot compile

### 6. Response Compatibility Layer
**Status**: 🔄 Basic structure created, needs refinement

**Created but may need enhancement:**
- `glide.api.models.Response`
- `glide.api.models.Response.RequestError`
- `glide.api.models.Response.ConstantResponse`
- `glide.api.models.exceptions.RequestErrorType`

**Impact**: Tests expect protobuf-style responses

## Implementation Priority

### High Priority (Required for API compatibility)
1. **Cluster Client Missing Methods** - Essential for cluster functionality
2. **JSON Module Classes** - Core JSON functionality
3. **Enhanced Error Handling** - Proper error response compatibility

### Medium Priority (Test-specific)
1. **Vector Search Module** - Important for search functionality
2. **Response Compatibility Layer** - Better test compatibility

### Low Priority (Architecture-specific)
1. **Architecture-Specific Components** - May need mocking/stubbing approach

## Current Compilation Status

```
Total compilation errors: ~100+ (showing first 100)
Main categories:
- Missing FT module: ~30+ errors
- Missing JSON server modules: ~10 errors  
- Missing cluster client methods: ~20 errors
- Missing architecture classes: ~40+ errors
```

## Next Steps

1. **Complete JSON server modules** - Enable JSON tests
2. **Add missing cluster client methods** - Enable cluster tests
3. **Implement basic vector search stubs** - Enable VectorSearchTests compilation
4. **Enhance error handling** - Make ExceptionHandlingTests work
5. **Create architecture compatibility layer** - Stub/mock old architecture components

## Notes

- Core JNI implementation is working (existing tests pass)
- Focus should be on API compatibility, not internal architecture
- Some tests may need adaptation to work with JNI instead of UDS
- Performance benefits of JNI implementation should be preserved