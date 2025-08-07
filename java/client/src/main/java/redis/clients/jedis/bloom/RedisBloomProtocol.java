/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.bloom;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;

/** RedisBloomProtocol compatibility stub for Valkey GLIDE wrapper. */
public class RedisBloomProtocol {

    public enum BloomFilterCommand implements Rawable {
        BF_ADD,
        BF_EXISTS,
        BF_RESERVE;

        private final byte[] raw;

        private BloomFilterCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum CuckooFilterCommand implements Rawable {
        CF_ADD,
        CF_EXISTS,
        CF_RESERVE;

        private final byte[] raw;

        private CuckooFilterCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum CountMinSketchCommand implements Rawable {
        CMS_INITBYDIM,
        CMS_INCRBY,
        CMS_QUERY;

        private final byte[] raw;

        private CountMinSketchCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum TopKCommand implements Rawable {
        TOPK_RESERVE,
        TOPK_ADD,
        TOPK_QUERY;

        private final byte[] raw;

        private TopKCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum TDigestCommand implements Rawable {
        TDIGEST_CREATE,
        TDIGEST_ADD,
        TDIGEST_QUANTILE;

        private final byte[] raw;

        private TDigestCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
