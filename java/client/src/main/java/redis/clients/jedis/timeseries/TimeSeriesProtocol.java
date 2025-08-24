/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * Time Series protocol commands and keywords for Valkey GLIDE compatibility layer. Based on
 * original Jedis TimeSeriesProtocol.
 */
public class TimeSeriesProtocol {

    public static final byte[] PLUS = "+".getBytes(StandardCharsets.UTF_8);
    public static final byte[] MINUS = "-".getBytes(StandardCharsets.UTF_8);

    public enum TimeSeriesCommand implements ProtocolCommand {
        CREATE("TS.CREATE"),
        RANGE("TS.RANGE"),
        REVRANGE("TS.REVRANGE"),
        MRANGE("TS.MRANGE"),
        MREVRANGE("TS.MREVRANGE"),
        CREATERULE("TS.CREATERULE"),
        DELETERULE("TS.DELETERULE"),
        ADD("TS.ADD"),
        MADD("TS.MADD"),
        DEL("TS.DEL"),
        INCRBY("TS.INCRBY"),
        DECRBY("TS.DECRBY"),
        INFO("TS.INFO"),
        GET("TS.GET"),
        MGET("TS.MGET"),
        ALTER("TS.ALTER"),
        QUERYINDEX("TS.QUERYINDEX");

        private final byte[] raw;

        TimeSeriesCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum TimeSeriesKeyword implements Rawable {
        RESET,
        FILTER,
        AGGREGATION,
        LABELS,
        RETENTION,
        TIMESTAMP,
        WITHLABELS,
        SELECTED_LABELS,
        COUNT,
        ENCODING,
        COMPRESSED,
        UNCOMPRESSED,
        CHUNK_SIZE,
        DUPLICATE_POLICY,
        IGNORE,
        ON_DUPLICATE,
        ALIGN,
        FILTER_BY_TS,
        FILTER_BY_VALUE,
        GROUPBY,
        REDUCE,
        DEBUG,
        LATEST,
        EMPTY,
        BUCKETTIMESTAMP;

        private final byte[] raw;

        TimeSeriesKeyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
