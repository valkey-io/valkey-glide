/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.List;
import java.util.Map;
import redis.clients.jedis.resps.*;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.aggr.AggregationResult;
import redis.clients.jedis.util.KeyValue;

/** BuilderFactory compatibility stub for Valkey GLIDE wrapper. */
public class BuilderFactory {

    public static final Builder<List<Object>> RAW_OBJECT_LIST =
            new Builder<List<Object>>() {
                @Override
                public List<Object> build(Object data) {
                    if (data instanceof List) {
                        return (List<Object>) data;
                    }
                    return java.util.Collections.singletonList(data);
                }
            };

    // Basic type builders
    public static final Builder<Object> ENCODED_OBJECT =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<String> STRING =
            new Builder<String>() {
                @Override
                public String build(Object data) {
                    return data != null ? data.toString() : null;
                }
            };

    public static final Builder<Long> LONG =
            new Builder<Long>() {
                @Override
                public Long build(Object data) {
                    return data instanceof Number ? ((Number) data).longValue() : 0L;
                }
            };

    public static final Builder<Double> DOUBLE =
            new Builder<Double>() {
                @Override
                public Double build(Object data) {
                    return data instanceof Number ? ((Number) data).doubleValue() : 0.0;
                }
            };

    public static final Builder<Boolean> BOOLEAN =
            new Builder<Boolean>() {
                @Override
                public Boolean build(Object data) {
                    return Boolean.TRUE.equals(data);
                }
            };

    public static final Builder<byte[]> BYTE_ARRAY =
            new Builder<byte[]>() {
                @Override
                public byte[] build(Object data) {
                    return data instanceof byte[] ? (byte[]) data : new byte[0];
                }
            };

    // List builders
    public static final Builder<List<Object>> ENCODED_OBJECT_LIST =
            new Builder<List<Object>>() {
                @Override
                public List<Object> build(Object data) {
                    return (List<Object>) data;
                }
            };

    public static final Builder<List<String>> STRING_LIST =
            new Builder<List<String>>() {
                @Override
                public List<String> build(Object data) {
                    return (List<String>) data;
                }
            };

    public static final Builder<List<Long>> LONG_LIST =
            new Builder<List<Long>>() {
                @Override
                public List<Long> build(Object data) {
                    return (List<Long>) data;
                }
            };

    public static final Builder<List<Double>> DOUBLE_LIST =
            new Builder<List<Double>>() {
                @Override
                public List<Double> build(Object data) {
                    return (List<Double>) data;
                }
            };

    public static final Builder<List<Boolean>> BOOLEAN_WITH_ERROR_LIST =
            new Builder<List<Boolean>>() {
                @Override
                public List<Boolean> build(Object data) {
                    return (List<Boolean>) data;
                }
            };

    public static final Builder<List<byte[]>> BYTE_ARRAY_LIST =
            new Builder<List<byte[]>>() {
                @Override
                public List<byte[]> build(Object data) {
                    return (List<byte[]>) data;
                }
            };

    public static final Builder<List<List<Object>>> ENCODED_OBJECT_LIST_LIST =
            new Builder<List<List<Object>>>() {
                @Override
                public List<List<Object>> build(Object data) {
                    return (List<List<Object>>) data;
                }
            };

    public static final Builder<List<List<String>>> STRING_LIST_LIST =
            new Builder<List<List<String>>>() {
                @Override
                public List<List<String>> build(Object data) {
                    return (List<List<String>>) data;
                }
            };

    // Map builders
    public static final Builder<Map<String, Object>> ENCODED_OBJECT_MAP =
            new Builder<Map<String, Object>>() {
                @Override
                public Map<String, Object> build(Object data) {
                    return (Map<String, Object>) data;
                }
            };

    public static final Builder<Map<String, String>> STRING_MAP =
            new Builder<Map<String, String>>() {
                @Override
                public Map<String, String> build(Object data) {
                    return (Map<String, String>) data;
                }
            };

    public static final Builder<Map<String, String>> STRING_MAP_FROM_PAIRS =
            new Builder<Map<String, String>>() {
                @Override
                public Map<String, String> build(Object data) {
                    return (Map<String, String>) data;
                }
            };

    // Complex type builders
    public static final Builder<KeyedListElement> KEYED_LIST_ELEMENT =
            new Builder<KeyedListElement>() {
                @Override
                public KeyedListElement build(Object data) {
                    return new KeyedListElement("", "");
                }
            };

    public static final Builder<List<Tuple>> TUPLE_LIST =
            new Builder<List<Tuple>>() {
                @Override
                public List<Tuple> build(Object data) {
                    return (List<Tuple>) data;
                }
            };

    public static final Builder<KeyedZSetElement> KEYED_ZSET_ELEMENT =
            new Builder<KeyedZSetElement>() {
                @Override
                public KeyedZSetElement build(Object data) {
                    return new KeyedZSetElement("", "", 0.0);
                }
            };

    public static final Builder<KeyValue<String, List<String>>> KEYED_STRING_LIST =
            new Builder<KeyValue<String, List<String>>>() {
                @Override
                public KeyValue<String, List<String>> build(Object data) {
                    return new KeyValue<>("", java.util.Collections.emptyList());
                }
            };

    public static final Builder<List<KeyValue<String, List<String>>>> KEYED_STRING_LIST_LIST =
            new Builder<List<KeyValue<String, List<String>>>>() {
                @Override
                public List<KeyValue<String, List<String>>> build(Object data) {
                    return (List<KeyValue<String, List<String>>>) data;
                }
            };

    public static final Builder<KeyValue<String, List<Tuple>>> KEYED_TUPLE_LIST =
            new Builder<KeyValue<String, List<Tuple>>>() {
                @Override
                public KeyValue<String, List<Tuple>> build(Object data) {
                    return new KeyValue<>("", java.util.Collections.emptyList());
                }
            };

    // Scan builders
    public static final Builder<ScanResult<String>> SCAN_RESPONSE =
            new Builder<ScanResult<String>>() {
                @Override
                public ScanResult<String> build(Object data) {
                    return new ScanResult<>("0", java.util.Collections.emptyList());
                }
            };

    public static final Builder<ScanResult<Tuple>> ZSCAN_RESPONSE =
            new Builder<ScanResult<Tuple>>() {
                @Override
                public ScanResult<Tuple> build(Object data) {
                    return new ScanResult<>("0", java.util.Collections.emptyList());
                }
            };

    public static final Builder<ScanResult<Map.Entry<String, String>>> HSCAN_RESPONSE =
            new Builder<ScanResult<Map.Entry<String, String>>>() {
                @Override
                public ScanResult<Map.Entry<String, String>> build(Object data) {
                    return new ScanResult<>("0", java.util.Collections.emptyList());
                }
            };

    // Stream builders
    public static final Builder<StreamEntryID> STREAM_ENTRY_ID =
            new Builder<StreamEntryID>() {
                @Override
                public StreamEntryID build(Object data) {
                    return new StreamEntryID(0, 0);
                }
            };

    public static final Builder<List<StreamEntryID>> STREAM_ENTRY_ID_LIST =
            new Builder<List<StreamEntryID>>() {
                @Override
                public List<StreamEntryID> build(Object data) {
                    return (List<StreamEntryID>) data;
                }
            };

    public static final Builder<StreamEntry> STREAM_ENTRY =
            new Builder<StreamEntry>() {
                @Override
                public StreamEntry build(Object data) {
                    return new StreamEntry(new StreamEntryID(0, 0), java.util.Collections.emptyMap());
                }
            };

    public static final Builder<List<StreamEntry>> STREAM_ENTRY_LIST =
            new Builder<List<StreamEntry>>() {
                @Override
                public List<StreamEntry> build(Object data) {
                    return (List<StreamEntry>) data;
                }
            };

    public static final Builder<Map.Entry<StreamEntryID, List<StreamEntry>>>
            STREAM_AUTO_CLAIM_RESPONSE =
                    new Builder<Map.Entry<StreamEntryID, List<StreamEntry>>>() {
                        @Override
                        public Map.Entry<StreamEntryID, List<StreamEntry>> build(Object data) {
                            return new java.util.AbstractMap.SimpleEntry<>(
                                    new StreamEntryID(0, 0), java.util.Collections.emptyList());
                        }
                    };

    // Search builders
    public static final Builder<AggregationResult> AGGREGATION_RESULT =
            new Builder<AggregationResult>() {
                @Override
                public AggregationResult build(Object data) {
                    return new AggregationResult(java.util.Collections.emptyList());
                }
            };

    public static final Builder<SearchResult> SEARCH_RESULT =
            new Builder<SearchResult>() {
                @Override
                public SearchResult build(Object data) {
                    return new SearchResult(0, java.util.Collections.emptyList());
                }
            };

    // Additional stream builders
    public static final Builder<Map.Entry<StreamEntryID, List<StreamEntryID>>>
            STREAM_AUTO_CLAIM_ID_RESPONSE =
                    new Builder<Map.Entry<StreamEntryID, List<StreamEntryID>>>() {
                        @Override
                        public Map.Entry<StreamEntryID, List<StreamEntryID>> build(Object data) {
                            return new java.util.AbstractMap.SimpleEntry<>(
                                    new StreamEntryID(0, 0), java.util.Collections.emptyList());
                        }
                    };

    public static final Builder<List<Map.Entry<String, List<StreamEntry>>>> STREAM_READ_RESPONSE =
            new Builder<List<Map.Entry<String, List<StreamEntry>>>>() {
                @Override
                public List<Map.Entry<String, List<StreamEntry>>> build(Object data) {
                    return java.util.Collections.emptyList();
                }
            };

    public static final Builder<List<Object>> STREAM_CONSUMERS_INFO_LIST =
            new Builder<List<Object>>() {
                @Override
                public List<Object> build(Object data) {
                    return java.util.Collections.emptyList();
                }
            };

    public static final Builder<List<Object>> STREAM_GROUP_INFO_LIST =
            new Builder<List<Object>>() {
                @Override
                public List<Object> build(Object data) {
                    return java.util.Collections.emptyList();
                }
            };

    public static final Builder<Object> STREAM_PENDING_SUMMARY =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_PENDING_ENTRY_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_INFO =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_FULL_INFO =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_CONSUMER_INFO =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_GROUP_INFO =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_CONSUMER_FULL_INFO =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> STREAM_PENDING_ENTRY =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    // Additional missing builders
    public static final Builder<Object> STREAM_INFO_FULL =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> GEO_COORDINATE_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> GEORADIUS_WITH_PARAMS_RESULT =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> MODULE_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> ACCESS_CONTROL_USER =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> ACCESS_CONTROL_LOG_ENTRY_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> COMMAND_DOCS_RESPONSE =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> COMMAND_INFO_RESPONSE =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> SLOWLOG_RESULT_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> LIBRARY_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> FUNCTION_LIST =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<Object> FUNCTION_STATS_RESPONSE =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };
}
