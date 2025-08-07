/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;

/** TimeSeriesProtocol compatibility stub for Valkey GLIDE wrapper. */
public class TimeSeriesProtocol {

    public enum TimeSeriesKeyword implements Rawable {
        TS_ADD,
        TS_GET,
        TS_RANGE,
        WITHLABELS,
        SELECTED_LABELS;

        private final byte[] raw;

        private TimeSeriesKeyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum TimeSeriesCommand implements Rawable {
        TS_ADD,
        TS_GET,
        TS_RANGE,
        TS_CREATE;

        private final byte[] raw;

        private TimeSeriesCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
