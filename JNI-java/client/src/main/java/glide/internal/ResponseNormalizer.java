package glide.internal;

import glide.api.models.GlideString;
import glide.utils.ArrayTransformUtils;

import java.util.*;
import java.util.function.Function;

/**
 * Proof-of-concept response normalization helper centralizing small, previously
 * duplicated shaping routines (GET, HGETALL, LMPOP family). The goal is to
 * demonstrate reduced boilerplate without altering externally observable
 * behavior. Only a narrow command subset is covered; extension should follow
 * once test suite stability is achieved.
 */
public final class ResponseNormalizer {
    private ResponseNormalizer() {}

    /** Functional interface representing a typed adaptor from Object. */
    @FunctionalInterface
    public interface Adapter<T> { T apply(Object raw); }

    // ========== Simple scalar adapters ==========
    public static final Adapter<String> NULLABLE_STRING = raw -> raw == null ? null : raw.toString();
    public static final Adapter<GlideString> NULLABLE_GLIDE_STRING = raw -> raw == null ? null : GlideString.of(raw);

    // ========== Numeric adapters ==========
    public static final Adapter<Long> LONG = raw -> {
        if (raw instanceof Long) return (Long) raw;
        String s = String.valueOf(raw);
        if ("true".equalsIgnoreCase(s)) return 1L; // tolerate boolean style reply
        if ("false".equalsIgnoreCase(s)) return 0L;
        return Long.parseLong(s);
    };

    // ========== Boolean / array adapters ==========
    public static final Adapter<Boolean[]> BOOLEAN_ARRAY = raw -> {
        if (raw == null) return new Boolean[0];
        Object[] arr;
        if (raw instanceof Object[]) {
            arr = (Object[]) raw;
        } else if (raw instanceof Collection) {
            arr = ((Collection<?>) raw).toArray();
        } else {
            return new Boolean[0];
        }
        Boolean[] out = new Boolean[arr.length];
        for (int i = 0; i < arr.length; i++) {
            Object v = arr[i];
            if (v == null) { out[i] = null; continue; }
            String s = String.valueOf(v);
            if ("1".equals(s) || "true".equalsIgnoreCase(s)) out[i] = Boolean.TRUE; else if ("0".equals(s) || "false".equalsIgnoreCase(s)) out[i] = Boolean.FALSE; else out[i] = Boolean.valueOf(s);
        }
        return out;
    };

    // ========== Function stats (binary) helper ==========
    @SuppressWarnings("unchecked")
    public static Map<GlideString, Map<GlideString, Object>> functionStatsBinary(Object raw) {
        if (raw == null) return new HashMap<>();
        Map<String, Map<String, Object>> stringLevel;
        try {
            stringLevel = (Map<String, Map<String, Object>>) raw; // unchecked
        } catch (ClassCastException e) {
            return new HashMap<>();
        }
        Map<GlideString, Map<GlideString, Object>> out = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : stringLevel.entrySet()) {
            Map<GlideString, Object> inner = new HashMap<>();
            Map<String, Object> value = e.getValue();
            if (value != null) {
                for (Map.Entry<String, Object> innerEntry : value.entrySet()) {
                    inner.put(GlideString.of(innerEntry.getKey()), innerEntry.getValue());
                }
            }
            out.put(GlideString.of(e.getKey()), inner);
        }
        return out;
    }

    // ========== Custom command info ==========
    public static Object customCommandInfo(Object raw, boolean binary) {
        if (raw == null) return null;
        if (binary) {
            if (raw instanceof GlideString) return raw;
            return GlideString.of(raw);
        } else {
            if (raw instanceof String) return raw;
            return String.valueOf(raw);
        }
    }
    public static final Adapter<Long> NULLABLE_LONG = raw -> {
        if (raw == null) return null;
        if (raw instanceof Long) return (Long) raw;
        String s = String.valueOf(raw);
        if ("true".equalsIgnoreCase(s)) return 1L;
        if ("false".equalsIgnoreCase(s)) return 0L;
        return Long.parseLong(s);
    };
    public static final Adapter<Double> DOUBLE = raw -> {
        if (raw instanceof Double) return (Double) raw;
        if (raw instanceof Long) return ((Long) raw).doubleValue();
        return Double.parseDouble(String.valueOf(raw));
    };
    public static final Adapter<Double> NULLABLE_DOUBLE = raw -> {
        if (raw == null) return null;
        if (raw instanceof Double) return (Double) raw;
        if (raw instanceof Long) return ((Long) raw).doubleValue();
        return Double.parseDouble(String.valueOf(raw));
    };

    // ========== Map builders (HGETALL style flat array -> map) ==========
    public static Map<String,String> hgetAllString(Object raw) {
            if (raw == null) return new HashMap<>();
            if (raw instanceof Map) {
                Map<?,?> in = (Map<?,?>) raw;
                Map<String,String> out = new HashMap<>();
                for (Map.Entry<?,?> e : in.entrySet()) {
                    out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
                return out;
            }
            Object[] arr;
            if (raw instanceof Object[]) {
                arr = (Object[]) raw;
            } else if (raw instanceof java.util.List) {
                arr = ((java.util.List<?>) raw).toArray();
            } else {
                return new HashMap<>();
            }
            Map<String,String> map = new HashMap<>();
            for (int i=0;i<arr.length;i+=2) {
                if (i+1 < arr.length) map.put(String.valueOf(arr[i]), String.valueOf(arr[i+1]));
            }
        return map; // parity: never null
    }

    public static Map<GlideString,GlideString> hgetAllGlide(Object raw) {
            if (raw == null) return new HashMap<>(); // parity: empty map
            if (raw instanceof Map) {
                // Assume already Map<GlideString,GlideString> or convertible
                Map<?,?> in = (Map<?,?>) raw;
                Map<GlideString,GlideString> out = new HashMap<>();
                for (Map.Entry<?,?> e : in.entrySet()) {
                    out.put(GlideString.of(e.getKey()), GlideString.of(e.getValue()));
                }
                return out;
            }
            Object[] arr;
            if (raw instanceof Object[]) {
                arr = (Object[]) raw;
            } else if (raw instanceof java.util.List) {
                arr = ((java.util.List<?>) raw).toArray();
            } else {
                return new HashMap<>();
            }
            Map<GlideString,GlideString> map = new HashMap<>();
            for (int i=0;i<arr.length;i+=2) {
                if (i+1 < arr.length) map.put(GlideString.of(arr[i]), GlideString.of(arr[i+1]));
            }
        return map;
    }

    // ========== LMPOP / BLMPOP parsers ==========
    /** Raw form: [ key, [ values... ] ] or null. */
    public static Map<String,String[]> lmPopString(Object raw) {
            if (raw == null) return null; // parity
            // Check if it's already a Map (which might happen when response is pre-processed)
            if (raw instanceof Map) {
                Map<?, ?> rawMap = (Map<?, ?>) raw;
                if (rawMap.isEmpty()) return null;
                // Convert the Map to the expected format
                Map<String, String[]> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object value = entry.getValue();
                    String[] values;
                    if (value instanceof Object[]) {
                        values = ArrayTransformUtils.toStringArray(value);
                    } else if (value instanceof List) {
                        values = ArrayTransformUtils.toStringArray(((List<?>) value).toArray());
                    } else {
                        values = new String[0];
                    }
                    result.put(key, values);
                }
                return result;
            }
            if (!(raw instanceof Object[])) return null;
            Object[] top = (Object[]) raw;
            if (top.length != 2) return null;
            String key = String.valueOf(top[0]);
            Object valuesObj = top[1];
            String[] values;
            if (valuesObj instanceof Object[]) {
                values = ArrayTransformUtils.toStringArray(valuesObj);
            } else if (valuesObj instanceof List) {
                values = ArrayTransformUtils.toStringArray(((List<?>) valuesObj).toArray());
            } else {
                values = new String[0];
            }
            Map<String,String[]> map = new HashMap<>();
            map.put(key, values);
        return map;
    }

    public static Map<GlideString,GlideString[]> lmPopGlide(Object raw) {
            if (raw == null) return null;
            // Check if it's already a Map (which might happen when response is pre-processed)
            if (raw instanceof Map) {
                Map<?, ?> rawMap = (Map<?, ?>) raw;
                if (rawMap.isEmpty()) return null;
                // Convert the Map to the expected format
                Map<GlideString, GlideString[]> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    GlideString key = GlideString.of(entry.getKey());
                    Object value = entry.getValue();
                    GlideString[] values;
                    if (value instanceof Object[]) {
                        values = ArrayTransformUtils.toGlideStringArray(value);
                    } else if (value instanceof List) {
                        values = ArrayTransformUtils.toGlideStringArray(((List<?>) value).toArray());
                    } else {
                        values = new GlideString[0];
                    }
                    result.put(key, values);
                }
                return result;
            }
            if (!(raw instanceof Object[])) return null;
            Object[] top = (Object[]) raw;
            if (top.length != 2) return null;
            GlideString key = GlideString.of(top[0]);
            Object valuesObj = top[1];
            GlideString[] values;
            if (valuesObj instanceof Object[]) {
                values = ArrayTransformUtils.toGlideStringArray(valuesObj);
            } else if (valuesObj instanceof List) {
                values = ArrayTransformUtils.toGlideStringArray(((List<?>) valuesObj).toArray());
            } else {
                values = new GlideString[0];
            }
            Map<GlideString,GlideString[]> map = new HashMap<>();
            map.put(key, values);
        return map;
    }

    /**
     * Normalizes the response from a ZMPOP/BZMPOP command.
     * Converts from Object[] [key, [[member, score], ...]] to Map<String, Object[][]>
     */
    public static Map<String, Object> zmpopString(Object raw) {
        if (raw == null) return null;
        
        // Check if it's already a Map (which might happen when response is pre-processed)
        if (raw instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) raw;
            if (rawMap.isEmpty()) return null;
            // Convert the Map to the expected format
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
            return result;
        }
        
        // Expected format: [key, [[member, score], ...]]
        if (!(raw instanceof Object[])) return null;
        Object[] arr = (Object[]) raw;
        if (arr.length < 2) return null;
        
        String key = arr[0].toString();
        Object memberScorePairs = arr[1];
        
        Map<String, Object> result = new HashMap<>();
        result.put(key, memberScorePairs);
        return result;
    }
    
    /**
     * Normalizes the response from a ZMPOP/BZMPOP command (binary version).
     */
    public static Map<GlideString, Object> zmpopGlide(Object raw) {
        if (raw == null) return null;
        
        // Check if it's already a Map
        if (raw instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) raw;
            if (rawMap.isEmpty()) return null;
            Map<GlideString, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                // Convert inner value if it's a Map (member-score pairs)
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<?, ?> innerMap = (Map<?, ?>) value;
                    Map<GlideString, Object> convertedInner = new HashMap<>();
                    for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                        convertedInner.put(GlideString.of(innerEntry.getKey()), innerEntry.getValue());
                    }
                    result.put(GlideString.of(entry.getKey()), convertedInner);
                } else {
                    result.put(GlideString.of(entry.getKey()), value);
                }
            }
            return result;
        }
        
        // Expected format: [key, [[member, score], ...]]
        if (!(raw instanceof Object[])) return null;
        Object[] arr = (Object[]) raw;
        if (arr.length < 2) return null;
        
        GlideString key = GlideString.of(arr[0]);
        Object memberScorePairs = arr[1];
        
        // Convert member-score pairs to Map<GlideString, Object>
        if (memberScorePairs instanceof Object[][]) {
            Object[][] pairs = (Object[][]) memberScorePairs;
            Map<GlideString, Object> innerMap = new HashMap<>();
            for (Object[] pair : pairs) {
                if (pair != null && pair.length >= 2) {
                    innerMap.put(GlideString.of(pair[0]), pair[1]);
                }
            }
            Map<GlideString, Object> result = new HashMap<>();
            result.put(key, innerMap);
            return result;
        } else if (memberScorePairs instanceof Map) {
            Map<?, ?> innerMap = (Map<?, ?>) memberScorePairs;
            Map<GlideString, Object> convertedInner = new HashMap<>();
            for (Map.Entry<?, ?> entry : innerMap.entrySet()) {
                convertedInner.put(GlideString.of(entry.getKey()), entry.getValue());
            }
            Map<GlideString, Object> result = new HashMap<>();
            result.put(key, convertedInner);
            return result;
        }
        
        Map<GlideString, Object> result = new HashMap<>();
        result.put(key, memberScorePairs);
        return result;
    }

    // ================= INFO + verbatim handling (consolidated) =================
    private static final boolean DEBUG_INFO = Boolean.getBoolean("glide.debugInfoRaw") || System.getenv("GLIDE_DEBUG_INFO") != null;

    /** Generic RESP3 verbatim wrapper unwrap: {format -> text, text -> body}. */
    @SuppressWarnings({"rawtypes","unchecked"})
    static Object unwrapVerbatim(Object value) { // package-private so other classes reuse
        if (value instanceof Map) {
            Map m = (Map) value;
            if (m.size() == 2 && m.containsKey("text") && m.containsKey("format")) {
                Object body = m.get("text");
                return body == null ? null : body.toString();
            }
        }
        return value;
    }

    /** Format an INFO-like value (standalone) into legacy multi-section text. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static String formatInfo(Object raw) {
        if (raw == null) return null;
        raw = unwrapVerbatim(raw);
        if (raw instanceof GlideString) return raw.toString();
        if (raw instanceof String) return (String) raw;
        if (raw instanceof Map) {
            Map m = (Map) raw;
            boolean anySection = false;
            for (Object v : m.values()) { if (v instanceof Map) { anySection = true; break; } }
            if (anySection) {
                StringBuilder sb = new StringBuilder();
                for (Object entryObj : m.entrySet()) {
                    Map.Entry e = (Map.Entry) entryObj;
                    Object val = e.getValue();
                    if (!(val instanceof Map)) continue; // skip scalar meta keys
                    String sectionName = String.valueOf(e.getKey());
                    if (!sectionName.isEmpty()) {
                        sectionName = sectionName.substring(0,1).toUpperCase() + sectionName.substring(1);
                    }
                    sb.append("# ").append(sectionName).append('\n');
                    Map inner = (Map) val;
                    for (Object k : inner.keySet()) {
                        Object iv = inner.get(k);
                        sb.append(k).append(":").append(iv).append('\n');
                    }
                    sb.append('\n');
                }
                String legacy = sb.toString();
                if (legacy.isEmpty()) {
                    Object first = m.isEmpty() ? null : m.values().iterator().next();
                    return first == null ? null : String.valueOf(first);
                }
                return legacy;
            }
            if (!m.isEmpty()) {
                Object first = m.values().iterator().next();
                return first == null ? null : String.valueOf(first);
            }
            return null;
        }
        return String.valueOf(raw);
    }

    /** Cluster INFO normalization: per-node map -> map of node -> legacy INFO text. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Map<String,Object> formatClusterInfoMulti(Object raw) {
        if (!(raw instanceof Map)) return null;
        Map in = (Map) raw;
        Map<String,Object> out = new LinkedHashMap<>();
        for (Object entryObj : in.entrySet()) {
            Map.Entry e = (Map.Entry) entryObj;
            out.put(String.valueOf(e.getKey()), formatInfo(e.getValue()));
        }
        return out;
    }

    // ================= Additional generic helpers (consolidation) =================
    /** Collapse a map of node->value (or any single-entry map) to the first non-null value's toString(), else null. */
    @SuppressWarnings({"rawtypes"})
    public static String collapseFirstValue(Object raw) {
        if (!(raw instanceof Map)) return raw == null ? null : String.valueOf(raw);
        Map m = (Map) raw;
        if (m.isEmpty()) return null;
        for (Object v : m.values()) { if (v != null) return String.valueOf(unwrapVerbatim(v)); }
        return null;
    }

    /** Collapse CLIENT INFO response, preferring nodes with library identification. */
    @SuppressWarnings({"rawtypes"})
    public static String collapseClientInfo(Object raw) {
        if (!(raw instanceof Map)) return raw == null ? null : String.valueOf(raw);
        Map m = (Map) raw;
        if (m.isEmpty()) return null;
        
        // First pass: look for a response that contains library identification
        for (Object v : m.values()) {
            if (v != null) {
                String response = String.valueOf(unwrapVerbatim(v));
                if (response.contains("lib-name=") && response.contains("lib-ver=")) {
                    return response;
                }
            }
        }
        
        // Fallback: return first non-null response (original behavior)
        for (Object v : m.values()) { if (v != null) return String.valueOf(unwrapVerbatim(v)); }
        return null;
    }

    /** Collapse to GlideString (wrapping string result). */
    public static GlideString collapseFirstValueGlide(Object raw) {
        String s = collapseFirstValue(raw);
        return s == null ? null : GlideString.of(s);
    }

    /** Apply a value mapping function to each value of a map if raw is a map; otherwise return null. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static Map<String,Object> mapValues(Object raw, java.util.function.Function<Object,Object> mapper) {
        if (!(raw instanceof Map)) return null;
        Map in = (Map) raw;
        Map<String,Object> out = new LinkedHashMap<>();
        for (Object entryObj : in.entrySet()) {
            Map.Entry e = (Map.Entry) entryObj;
            out.put(String.valueOf(e.getKey()), mapper.apply(e.getValue()));
        }
        return out;
    }

    /** Convenience: convert map values to String via toString (after verbatim unwrap). */
    public static Map<String,Object> mapValuesToString(Object raw) {
        return mapValues(raw, v -> v == null ? null : String.valueOf(unwrapVerbatim(v)));
    }

    /** Convenience: convert map values to GlideString (after verbatim unwrap). */
    public static Map<String,Object> mapValuesToGlideString(Object raw) {
        return mapValues(raw, v -> {
            if (v == null) return null;
            if (v instanceof GlideString) return v;
            Object unwrapped = unwrapVerbatim(v);
            return GlideString.of(String.valueOf(unwrapped));
        });
    }

    /** Build string map from flat pair array/list/map for generic use (CONFIG GET style). */
    public static Map<String,String> flatPairsToStringMap(Object raw) {
        if (raw == null) return new LinkedHashMap<>();
        if (raw instanceof Map) {
            Map<?,?> in = (Map<?,?>) raw;
            Map<String,String> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : in.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            return out;
        }
        Object[] arr;
        if (raw instanceof Object[]) {
            arr = (Object[]) raw;
        } else if (raw instanceof java.util.List) {
            arr = ((java.util.List<?>) raw).toArray();
        } else {
            return new LinkedHashMap<>();
        }
        Map<String,String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < arr.length; i += 2) {
            out.put(String.valueOf(arr[i]), arr[i+1] == null ? null : String.valueOf(arr[i+1]));
        }
        return out;
    }

    /** Flat pairs -> Map<GlideString,Object> for binary CONFIG GET style commands. Values kept as raw objects (String/number). */
    public static Map<GlideString,Object> flatPairsToGlideMap(Object raw) {
        return flatPairsToMap(raw, k -> GlideString.of(String.valueOf(k)), v -> v);
    }

    /** Generic flat pairs to map with key/value converters. */
    @SuppressWarnings({"rawtypes","unchecked"})
    public static <K,V> Map<K,V> flatPairsToMap(Object raw, java.util.function.Function<Object,K> kFn, java.util.function.Function<Object,V> vFn) {
        if (raw == null) return new LinkedHashMap<>();
        if (raw instanceof Map) {
            Map in = (Map) raw;
            Map<K,V> out = new LinkedHashMap<>();
            for (Object entryObj : in.entrySet()) {
                Map.Entry e = (Map.Entry) entryObj;
                out.put(kFn.apply(e.getKey()), vFn.apply(e.getValue()));
            }
            return out;
        }
        Object[] arr;
        if (raw instanceof Object[]) {
            arr = (Object[]) raw;
        } else if (raw instanceof java.util.List) {
            arr = ((java.util.List<?>) raw).toArray();
        } else {
            return new LinkedHashMap<>();
        }
        Map<K,V> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < arr.length; i += 2) {
            out.put(kFn.apply(arr[i]), vFn.apply(arr[i+1]));
        }
        return out;
    }

    /** CLUSTER NODES normalization extracted from cluster client. */
    @SuppressWarnings({"rawtypes"})
    public static String formatClusterNodes(Object raw) {
        java.util.LinkedHashSet<String> allLines = new java.util.LinkedHashSet<>();
        if (raw instanceof Map) {
            Map m = (Map) raw;
            for (Object v : m.values()) {
                if (v == null) continue;
                String s = v.toString();
                if (s == null || s.isBlank()) continue;
                for (String ln : s.split("\\r?\\n")) {
                    String t = ln.trim();
                    if (!t.isEmpty()) allLines.add(t);
                }
            }
        } else if (raw != null) {
            String s = raw.toString();
            for (String ln : s.split("\\r?\\n")) {
                String t = ln.trim();
                if (!t.isEmpty()) allLines.add(t);
            }
        }
        java.util.LinkedHashSet<String> mastersOnly = new java.util.LinkedHashSet<>();
        for (String t : allLines) {
            String[] toks = t.split(" ");
            if (toks.length >= 3) {
                String fl = toks[2].toLowerCase();
                boolean isMaster = fl.contains("master");
                boolean isReplica = fl.contains("slave") || fl.contains("replica");
                boolean noAddr = fl.contains("noaddr");
                boolean handshake = fl.contains("handshake");
                if (isMaster && !isReplica && !noAddr && !handshake) {
                    mastersOnly.add(t);
                }
            }
        }
        if (!mastersOnly.isEmpty()) return String.join("\n", mastersOnly);
        if (!allLines.isEmpty()) return String.join("\n", allLines);
        return null;
    }
}
