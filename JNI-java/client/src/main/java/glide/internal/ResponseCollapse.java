package glide.internal;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles response collapsing for multi-node operations in cluster mode.
 * 
 * When commands are sent to multiple nodes (ALL_PRIMARIES, ALL_NODES), 
 * the responses need to be aggregated properly. This utility provides
 * the collapsing logic that exists in the UDS implementation but was
 * missing in the JNI implementation.
 */
public final class ResponseCollapse {
    private ResponseCollapse() {}
    
    /**
     * Collapse responses from multiple nodes for pfadd operation.
     * Returns true if at least one node modified the HyperLogLog.
     */
    public static Boolean collapsePfaddResponses(Object response) {
        if (response == null) return false;
        
        // Single node response
        if (response instanceof Boolean) {
            return (Boolean) response;
        }
        if (response instanceof Long) {
            return ((Long) response) == 1L;
        }
        
        // Multi-node response (array/collection)
        if (response instanceof Object[]) {
            Object[] arr = (Object[]) response;
            for (Object item : arr) {
                if (item instanceof Boolean && (Boolean) item) return true;
                if (item instanceof Long && ((Long) item) == 1L) return true;
            }
            return false;
        }
        
        if (response instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) response;
            for (Object item : coll) {
                if (item instanceof Boolean && (Boolean) item) return true;
                if (item instanceof Long && ((Long) item) == 1L) return true;
            }
            return false;
        }
        
        // Fallback to single value interpretation
        return response.equals(1L) || response.equals(true);
    }
    
    /**
     * Collapse responses from multiple nodes for pfcount operation.
     * Returns the sum of counts from all nodes.
     */
    public static Long collapsePfcountResponses(Object response) {
        if (response == null) return 0L;
        
        // Single node response
        if (response instanceof Long) {
            return (Long) response;
        }
        if (response instanceof Integer) {
            return ((Integer) response).longValue();
        }
        
        // Multi-node response (array/collection)
        if (response instanceof Object[]) {
            Object[] arr = (Object[]) response;
            long sum = 0L;
            for (Object item : arr) {
                if (item instanceof Long) sum += (Long) item;
                else if (item instanceof Integer) sum += ((Integer) item).longValue();
                else if (item instanceof Number) sum += ((Number) item).longValue();
            }
            return sum;
        }
        
        if (response instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) response;
            long sum = 0L;
            for (Object item : coll) {
                if (item instanceof Long) sum += (Long) item;
                else if (item instanceof Integer) sum += ((Integer) item).longValue();
                else if (item instanceof Number) sum += ((Number) item).longValue();
            }
            return sum;
        }
        
        // Fallback
        if (response instanceof Number) {
            return ((Number) response).longValue();
        }
        return 0L;
    }
    
    /**
     * Collapse responses from multiple nodes for flush operations (flushall/flushdb).
     * Returns "OK" if all nodes responded with "OK".
     */
    public static String collapseFlushResponses(Object response) {
        if (response == null) return null;
        
        // Single node response
        if (response instanceof String) {
            return (String) response;
        }
        
        // Multi-node response as Map (glide-core returns Map<address, response>)
        if (response instanceof Map) {
            Map<?, ?> nodeResponses = (Map<?, ?>) response;
            for (Object value : nodeResponses.values()) {
                String strValue = value != null ? value.toString() : null;
                if (!"OK".equals(strValue)) {
                    return strValue != null ? strValue : "ERROR";
                }
            }
            return "OK";
        }
        
        // Multi-node response (array/collection)
        if (response instanceof Object[]) {
            Object[] arr = (Object[]) response;
            for (Object item : arr) {
                if (!"OK".equals(item)) {
                    return item != null ? item.toString() : "ERROR";
                }
            }
            return "OK";
        }
        
        if (response instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) response;
            for (Object item : coll) {
                if (!"OK".equals(item)) {
                    return item != null ? item.toString() : "ERROR";
                }
            }
            return "OK";
        }
        
        return response.toString();
    }
    
    /**
     * Collapse responses from multiple nodes for configSet operation.
     * Returns "OK" if all nodes responded with "OK".
     */
    public static String collapseConfigSetResponses(Object response) {
        if (response == null) return null;
        
        // Single node response
        if (response instanceof String) {
            return (String) response;
        }
        
        // Multi-node response as Map (glide-core returns Map<address, response>)
        if (response instanceof Map) {
            Map<?, ?> nodeResponses = (Map<?, ?>) response;
            for (Object value : nodeResponses.values()) {
                String strValue = value != null ? value.toString() : null;
                if (!"OK".equals(strValue)) {
                    return strValue != null ? strValue : "ERROR";
                }
            }
            return "OK";
        }
        
        // Multi-node response (array/collection)
        if (response instanceof Object[]) {
            Object[] arr = (Object[]) response;
            for (Object item : arr) {
                if (!"OK".equals(item)) {
                    return item != null ? item.toString() : "ERROR";
                }
            }
            return "OK";
        }
        
        if (response instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) response;
            for (Object item : coll) {
                if (!"OK".equals(item)) {
                    return item != null ? item.toString() : "ERROR";
                }
            }
            return "OK";
        }
        
        return response.toString();
    }
    
    /**
     * Collapse responses from multiple nodes for functionStats operation.
     * Merges maps from all nodes into a single map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> collapseFunctionStatsResponses(Object response) {
        if (response == null) return new HashMap<>();
        
        // Check if response is a Map with node addresses as keys
        if (response instanceof Map) {
            Map<?, ?> topLevel = (Map<?, ?>) response;
            
            // Check if this is a multi-node response (keys are addresses like "127.0.0.1:6379")
            boolean isMultiNode = false;
            for (Object key : topLevel.keySet()) {
                if (key instanceof String && ((String) key).contains(":")) {
                    isMultiNode = true;
                    break;
                }
            }
            
            if (isMultiNode) {
                // Multi-node response - merge all node stats
                Map<String, Map<String, Object>> merged = new HashMap<>();
                for (Object nodeStats : topLevel.values()) {
                    if (nodeStats instanceof Map) {
                        mergeStats(merged, (Map<String, Map<String, Object>>) nodeStats);
                    }
                }
                return merged;
            } else {
                // Single node response - return as-is
                return (Map<String, Map<String, Object>>) response;
            }
        }
        
        // Multi-node response (array/collection)
        Map<String, Map<String, Object>> merged = new HashMap<>();
        
        if (response instanceof Object[]) {
            Object[] arr = (Object[]) response;
            for (Object item : arr) {
                if (item instanceof Map) {
                    mergeStats(merged, (Map<String, Map<String, Object>>) item);
                }
            }
        } else if (response instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) response;
            for (Object item : coll) {
                if (item instanceof Map) {
                    mergeStats(merged, (Map<String, Map<String, Object>>) item);
                }
            }
        }
        
        return merged;
    }
    
    private static void mergeStats(Map<String, Map<String, Object>> target, 
                                   Map<String, Map<String, Object>> source) {
        if (source == null) {
            return;
        }
        
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> sourceValue = entry.getValue();
            
            if (sourceValue == null) {
                continue;
            }
            
            if (target.containsKey(key)) {
                // Merge inner maps - combine metrics
                Map<String, Object> targetValue = target.get(key);
                for (Map.Entry<String, Object> innerEntry : sourceValue.entrySet()) {
                    String innerKey = innerEntry.getKey();
                    Object innerValue = innerEntry.getValue();
                    
                    if (targetValue.containsKey(innerKey)) {
                        // Sum numeric values
                        Object existingValue = targetValue.get(innerKey);
                        if (existingValue instanceof Number && innerValue instanceof Number) {
                            long sum = ((Number) existingValue).longValue() + 
                                      ((Number) innerValue).longValue();
                            targetValue.put(innerKey, sum);
                        } else {
                            // Keep the first non-null value
                            if (targetValue.get(innerKey) == null) {
                                targetValue.put(innerKey, innerValue);
                            }
                        }
                    } else {
                        targetValue.put(innerKey, innerValue);
                    }
                }
            } else {
                // Add new entry
                target.put(key, new HashMap<>(sourceValue));
            }
        }
    }
    
    /**
     * Generic response collapsing for commands that expect a single value from multiple nodes.
     * Returns the first non-null response.
     */
    public static Object collapseFirstNonNull(Object response) {
        if (response == null) return null;
        
        // Already a single value
        if (!(response instanceof Object[]) && !(response instanceof Collection<?>)) {
            return response;
        }
        
        // Multi-node response - return first non-null
        if (response instanceof Object[]) {
            Object[] arr = (Object[]) response;
            for (Object item : arr) {
                if (item != null) return item;
            }
        } else if (response instanceof Collection<?>) {
            Collection<?> coll = (Collection<?>) response;
            for (Object item : coll) {
                if (item != null) return item;
            }
        }
        
        return null;
    }
    
    /**
     * Check if response is from multiple nodes
     */
    public static boolean isMultiNodeResponse(Object response) {
        return response instanceof Object[] || response instanceof Collection<?>;
    }
}