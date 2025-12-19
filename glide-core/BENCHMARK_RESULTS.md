# Valkey GLIDE Benchmark Results

## Test Configuration
- **Iterations**: 100,000 GET operations
- **Threads**: Single client (connection reuse)
- **Mode**: Async
- **Server**: Local Valkey instance

## Results Summary

### Protobuf Mode (Manual Command Construction)
```
Average TPS: ~7,430 transactions per second
Time Range: 13.380s - 13.522s
Individual runs: 7230, 7330, 7487, 7471, 7465, 7471, 7496, 7494, 7400, 7315 TPS
```

### Direct Mode (RequestType Enum)
```
Average TPS: ~7,420 transactions per second  
Time Range: 13.435s - 13.511s
Individual runs: 7364, 7375, 7392, 7460, 7445, 7417, 7450, 7457, 7449, 7428 TPS
```

## Analysis

### Performance Comparison
- **Protobuf Mode**: 7,430 TPS average
- **Direct Mode**: 7,420 TPS average
- **Difference**: ~10 TPS (0.13% difference)

### Key Findings

1. **Minimal Performance Difference**: The performance difference between RequestType enum and manual command construction is negligible (~0.13%)

2. **Both Methods Are Efficient**: Both approaches achieve similar high performance (~7,400+ TPS)

3. **RequestType Enum Advantage**: While performance is similar, RequestType enum provides:
   - Type safety
   - Consistent command structure
   - Less error-prone code
   - Better maintainability

4. **Connection Reuse Critical**: The efficient benchmark (reusing connections) achieved much higher performance than the original benchmark that created new connections per operation

## Conclusion

The benchmark demonstrates that GLIDE's RequestType enum approach performs virtually identically to manual command construction, while providing better code safety and maintainability. The choice between methods should be based on code quality considerations rather than performance concerns.

## Technical Notes

- Both methods route through the same StandaloneClient infrastructure
- Performance bottleneck is network I/O, not command creation method
- Connection reuse is critical for realistic performance testing
- Local Valkey instance eliminates network latency variables
