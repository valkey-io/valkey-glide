/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.List;
import java.util.Map;
import java.util.Set;
import redis.clients.jedis.resps.*;
import redis.clients.jedis.util.KeyValue;

/** BuilderFactory compatibility class for Valkey GLIDE wrapper. Based on Jedis 4.4.3 */
public final class BuilderFactory {

    public static final Builder<Object> RAW_OBJECT =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<List<Object>> RAW_OBJECT_LIST =
            new Builder<List<Object>>() {
                @Override
                public List<Object> build(Object data) {
                    return (List<Object>) data;
                }
            };

    public static final Builder<Object> ENCODED_OBJECT =
            new Builder<Object>() {
                @Override
                public Object build(Object data) {
                    return data;
                }
            };

    public static final Builder<List<Object>> ENCODED_OBJECT_LIST =
            new Builder<List<Object>>() {
                @Override
                public List<Object> build(Object data) {
                    return (List<Object>) data;
                }
            };

    public static final Builder<Map<String, Object>> ENCODED_OBJECT_MAP =
            new Builder<Map<String, Object>>() {
                @Override
                public Map<String, Object> build(Object data) {
                    return (Map<String, Object>) data;
                }
            };

    public static final Builder<Long> LONG =
            new Builder<Long>() {
                @Override
                public Long build(Object data) {
                    return data instanceof Long
                            ? (Long) data
                            : data instanceof Integer ? ((Integer) data).longValue() : null;
                }
            };

    public static final Builder<List<Long>> LONG_LIST =
            new Builder<List<Long>>() {
                @Override
                public List<Long> build(Object data) {
                    return (List<Long>) data;
                }
            };

    public static final Builder<Double> DOUBLE =
            new Builder<Double>() {
                @Override
                public Double build(Object data) {
                    return data instanceof Double ? (Double) data : null;
                }
            };

    public static final Builder<List<Double>> DOUBLE_LIST =
            new Builder<List<Double>>() {
                @Override
                public List<Double> build(Object data) {
                    return (List<Double>) data;
                }
            };

    public static final Builder<Boolean> BOOLEAN =
            new Builder<Boolean>() {
                @Override
                public Boolean build(Object data) {
                    return data instanceof Boolean ? (Boolean) data : null;
                }
            };

    public static final Builder<List<Boolean>> BOOLEAN_LIST =
            new Builder<List<Boolean>>() {
                @Override
                public List<Boolean> build(Object data) {
                    return (List<Boolean>) data;
                }
            };

    public static final Builder<List<Boolean>> BOOLEAN_WITH_ERROR_LIST =
            new Builder<List<Boolean>>() {
                @Override
                public List<Boolean> build(Object data) {
                    return (List<Boolean>) data;
                }
            };

    public static final Builder<byte[]> BYTE_ARRAY =
            new Builder<byte[]>() {
                @Override
                public byte[] build(Object data) {
                    return (byte[]) data;
                }
            };

    public static final Builder<List<byte[]>> BYTE_ARRAY_LIST =
            new Builder<List<byte[]>>() {
                @Override
                public List<byte[]> build(Object data) {
                    return (List<byte[]>) data;
                }
            };

    public static final Builder<byte[]> BINARY =
            new Builder<byte[]>() {
                @Override
                public byte[] build(Object data) {
                    return (byte[]) data;
                }
            };

    public static final Builder<List<byte[]>> BINARY_LIST =
            new Builder<List<byte[]>>() {
                @Override
                public List<byte[]> build(Object data) {
                    return (List<byte[]>) data;
                }
            };

    public static final Builder<Set<byte[]>> BINARY_SET =
            new Builder<Set<byte[]>>() {
                @Override
                public Set<byte[]> build(Object data) {
                    return (Set<byte[]>) data;
                }
            };

    public static final Builder<Map<byte[], byte[]>> BINARY_MAP =
            new Builder<Map<byte[], byte[]>>() {
                @Override
                public Map<byte[], byte[]> build(Object data) {
                    return (Map<byte[], byte[]>) data;
                }
            };

    public static final Builder<String> STRING =
            new Builder<String>() {
                @Override
                public String build(Object data) {
                    return data != null ? data.toString() : null;
                }
            };

    public static final Builder<List<String>> STRING_LIST =
            new Builder<List<String>>() {
                @Override
                public List<String> build(Object data) {
                    return (List<String>) data;
                }
            };

    public static final Builder<Set<String>> STRING_SET =
            new Builder<Set<String>>() {
                @Override
                public Set<String> build(Object data) {
                    return (Set<String>) data;
                }
            };

    public static final Builder<Set<String>> STRING_ORDERED_SET =
            new Builder<Set<String>>() {
                @Override
                public Set<String> build(Object data) {
                    return (Set<String>) data;
                }
            };

    public static final Builder<Map<String, String>> STRING_MAP =
            new Builder<Map<String, String>>() {
                @Override
                public Map<String, String> build(Object data) {
                    return (Map<String, String>) data;
                }
            };

    public static final Builder<KeyedListElement> KEYED_LIST_ELEMENT =
            new Builder<KeyedListElement>() {
                @Override
                public KeyedListElement build(Object data) {
                    return (KeyedListElement) data;
                }
            };

    public static final Builder<KeyValue<Long, Double>> ZRANK_WITHSCORE_PAIR =
            new Builder<KeyValue<Long, Double>>() {
                @Override
                public KeyValue<Long, Double> build(Object data) {
                    return (KeyValue<Long, Double>) data;
                }
            };

    public static final Builder<KeyValue<String, List<String>>> KEYED_STRING_LIST =
            new Builder<KeyValue<String, List<String>>>() {
                @Override
                public KeyValue<String, List<String>> build(Object data) {
                    return (KeyValue<String, List<String>>) data;
                }
            };

    public static final Builder<KeyValue<Long, Long>> LONG_LONG_PAIR =
            new Builder<KeyValue<Long, Long>>() {
                @Override
                public KeyValue<Long, Long> build(Object data) {
                    return (KeyValue<Long, Long>) data;
                }
            };

    public static final Builder<List<KeyValue<String, List<String>>>> KEYED_STRING_LIST_LIST =
            new Builder<List<KeyValue<String, List<String>>>>() {
                @Override
                public List<KeyValue<String, List<String>>> build(Object data) {
                    return (List<KeyValue<String, List<String>>>) data;
                }
            };

    public static final Builder<KeyValue<byte[], List<byte[]>>> KEYED_BINARY_LIST =
            new Builder<KeyValue<byte[], List<byte[]>>>() {
                @Override
                public KeyValue<byte[], List<byte[]>> build(Object data) {
                    return (KeyValue<byte[], List<byte[]>>) data;
                }
            };

    public static final Builder<Tuple> TUPLE =
            new Builder<Tuple>() {
                @Override
                public Tuple build(Object data) {
                    return (Tuple) data;
                }
            };

    public static final Builder<KeyedZSetElement> KEYED_ZSET_ELEMENT =
            new Builder<KeyedZSetElement>() {
                @Override
                public KeyedZSetElement build(Object data) {
                    return (KeyedZSetElement) data;
                }
            };

    public static final Builder<List<Tuple>> TUPLE_LIST =
            new Builder<List<Tuple>>() {
                @Override
                public List<Tuple> build(Object data) {
                    return (List<Tuple>) data;
                }
            };

    public static final Builder<Set<Tuple>> TUPLE_ZSET =
            new Builder<Set<Tuple>>() {
                @Override
                public Set<Tuple> build(Object data) {
                    return (Set<Tuple>) data;
                }
            };

    public static final Builder<KeyValue<String, List<Tuple>>> KEYED_TUPLE_LIST =
            new Builder<KeyValue<String, List<Tuple>>>() {
                @Override
                public KeyValue<String, List<Tuple>> build(Object data) {
                    return (KeyValue<String, List<Tuple>>) data;
                }
            };

    public static final Builder<KeyValue<byte[], List<Tuple>>> BINARY_KEYED_TUPLE_LIST =
            new Builder<KeyValue<byte[], List<Tuple>>>() {
                @Override
                public KeyValue<byte[], List<Tuple>> build(Object data) {
                    return (KeyValue<byte[], List<Tuple>>) data;
                }
            };

    public static final Builder<ScanResult<String>> SCAN_RESPONSE =
            new Builder<ScanResult<String>>() {
                @Override
                public ScanResult<String> build(Object data) {
                    return (ScanResult<String>) data;
                }
            };

    public static final Builder<ScanResult<Map.Entry<String, String>>> HSCAN_RESPONSE =
            new Builder<ScanResult<Map.Entry<String, String>>>() {
                @Override
                public ScanResult<Map.Entry<String, String>> build(Object data) {
                    return (ScanResult<Map.Entry<String, String>>) data;
                }
            };

    public static final Builder<ScanResult<String>> SSCAN_RESPONSE =
            new Builder<ScanResult<String>>() {
                @Override
                public ScanResult<String> build(Object data) {
                    return (ScanResult<String>) data;
                }
            };

    public static final Builder<ScanResult<Tuple>> ZSCAN_RESPONSE =
            new Builder<ScanResult<Tuple>>() {
                @Override
                public ScanResult<Tuple> build(Object data) {
                    return (ScanResult<Tuple>) data;
                }
            };

    public static final Builder<ScanResult<byte[]>> SCAN_BINARY_RESPONSE =
            new Builder<ScanResult<byte[]>>() {
                @Override
                public ScanResult<byte[]> build(Object data) {
                    return (ScanResult<byte[]>) data;
                }
            };

    public static final Builder<ScanResult<Map.Entry<byte[], byte[]>>> HSCAN_BINARY_RESPONSE =
            new Builder<ScanResult<Map.Entry<byte[], byte[]>>>() {
                @Override
                public ScanResult<Map.Entry<byte[], byte[]>> build(Object data) {
                    return (ScanResult<Map.Entry<byte[], byte[]>>) data;
                }
            };

    public static final Builder<ScanResult<byte[]>> SSCAN_BINARY_RESPONSE =
            new Builder<ScanResult<byte[]>>() {
                @Override
                public ScanResult<byte[]> build(Object data) {
                    return (ScanResult<byte[]>) data;
                }
            };

    public static final Builder<Map<String, Long>> PUBSUB_NUMSUB_MAP =
            new Builder<Map<String, Long>>() {
                @Override
                public Map<String, Long> build(Object data) {
                    return (Map<String, Long>) data;
                }
            };

    public static final Builder<List<GeoCoordinate>> GEO_COORDINATE_LIST =
            new Builder<List<GeoCoordinate>>() {
                @Override
                public List<GeoCoordinate> build(Object data) {
                    return (List<GeoCoordinate>) data;
                }
            };

    public static final Builder<List<GeoRadiusResponse>> GEORADIUS_WITH_PARAMS_RESULT =
            new Builder<List<GeoRadiusResponse>>() {
                @Override
                public List<GeoRadiusResponse> build(Object data) {
                    return (List<GeoRadiusResponse>) data;
                }
            };

    public static final Builder<Map<String, CommandDocument>> COMMAND_DOCS_RESPONSE =
            new Builder<Map<String, CommandDocument>>() {
                @Override
                public Map<String, CommandDocument> build(Object data) {
                    return (Map<String, CommandDocument>) data;
                }
            };

    public static final Builder<Map<String, CommandInfo>> COMMAND_INFO_RESPONSE =
            new Builder<Map<String, CommandInfo>>() {
                @Override
                public Map<String, CommandInfo> build(Object data) {
                    return (Map<String, CommandInfo>) data;
                }
            };

    public static final Builder<List<Module>> MODULE_LIST =
            new Builder<List<Module>>() {
                @Override
                public List<Module> build(Object data) {
                    return (List<Module>) data;
                }
            };

    public static final Builder<AccessControlUser> ACCESS_CONTROL_USER =
            new Builder<AccessControlUser>() {
                @Override
                public AccessControlUser build(Object data) {
                    return (AccessControlUser) data;
                }
            };

    public static final Builder<List<AccessControlLogEntry>> ACCESS_CONTROL_LOG_ENTRY_LIST =
            new Builder<List<AccessControlLogEntry>>() {
                @Override
                public List<AccessControlLogEntry> build(Object data) {
                    return (List<AccessControlLogEntry>) data;
                }
            };

    // Stream Builders
    public static final Builder<StreamEntryID> STREAM_ENTRY_ID =
            new Builder<StreamEntryID>() {
                @Override
                public StreamEntryID build(Object data) {
                    return (StreamEntryID) data;
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
                    return (StreamEntry) data;
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
                            return (Map.Entry<StreamEntryID, List<StreamEntry>>) data;
                        }
                    };

    public static final Builder<Map.Entry<StreamEntryID, List<StreamEntryID>>>
            STREAM_AUTO_CLAIM_ID_RESPONSE =
                    new Builder<Map.Entry<StreamEntryID, List<StreamEntryID>>>() {
                        @Override
                        public Map.Entry<StreamEntryID, List<StreamEntryID>> build(Object data) {
                            return (Map.Entry<StreamEntryID, List<StreamEntryID>>) data;
                        }
                    };

    public static final Builder<List<Map.Entry<String, List<StreamEntry>>>> STREAM_READ_RESPONSE =
            new Builder<List<Map.Entry<String, List<StreamEntry>>>>() {
                @Override
                public List<Map.Entry<String, List<StreamEntry>>> build(Object data) {
                    return (List<Map.Entry<String, List<StreamEntry>>>) data;
                }
            };

    public static final Builder<List<StreamPendingEntry>> STREAM_PENDING_ENTRY_LIST =
            new Builder<List<StreamPendingEntry>>() {
                @Override
                public List<StreamPendingEntry> build(Object data) {
                    return (List<StreamPendingEntry>) data;
                }
            };

    public static final Builder<StreamInfo> STREAM_INFO =
            new Builder<StreamInfo>() {
                @Override
                public StreamInfo build(Object data) {
                    return (StreamInfo) data;
                }
            };

    public static final Builder<List<StreamGroupInfo>> STREAM_GROUP_INFO_LIST =
            new Builder<List<StreamGroupInfo>>() {
                @Override
                public List<StreamGroupInfo> build(Object data) {
                    return (List<StreamGroupInfo>) data;
                }
            };

    public static final Builder<List<StreamConsumersInfo>> STREAM_CONSUMERS_INFO_LIST =
            new Builder<List<StreamConsumersInfo>>() {
                @Override
                public List<StreamConsumersInfo> build(Object data) {
                    return (List<StreamConsumersInfo>) data;
                }
            };

    public static final Builder<StreamFullInfo> STREAM_INFO_FULL =
            new Builder<StreamFullInfo>() {
                @Override
                public StreamFullInfo build(Object data) {
                    return (StreamFullInfo) data;
                }
            };

    public static final Builder<StreamPendingSummary> STREAM_PENDING_SUMMARY =
            new Builder<StreamPendingSummary>() {
                @Override
                public StreamPendingSummary build(Object data) {
                    return (StreamPendingSummary) data;
                }
            };

    public static final Builder<LCSMatchResult> STR_ALGO_LCS_RESULT_BUILDER =
            new Builder<LCSMatchResult>() {
                @Override
                public LCSMatchResult build(Object data) {
                    return (LCSMatchResult) data;
                }
            };

    public static final Builder<Map<String, String>> STRING_MAP_FROM_PAIRS =
            new Builder<Map<String, String>>() {
                @Override
                public Map<String, String> build(Object data) {
                    return (Map<String, String>) data;
                }
            };

    public static final Builder<List<LibraryInfo>> LIBRARY_LIST =
            new Builder<List<LibraryInfo>>() {
                @Override
                public List<LibraryInfo> build(Object data) {
                    return (List<LibraryInfo>) data;
                }
            };

    public static final Builder<List<List<String>>> STRING_LIST_LIST =
            new Builder<List<List<String>>>() {
                @Override
                public List<List<String>> build(Object data) {
                    return (List<List<String>>) data;
                }
            };

    public static final Builder<List<List<Object>>> ENCODED_OBJECT_LIST_LIST =
            new Builder<List<List<Object>>>() {
                @Override
                public List<List<Object>> build(Object data) {
                    return (List<List<Object>>) data;
                }
            };
}
