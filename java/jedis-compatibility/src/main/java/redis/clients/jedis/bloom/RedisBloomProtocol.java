/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.bloom;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * Redis Bloom filter protocol commands and keywords for Valkey GLIDE compatibility layer. Based on
 * original Jedis RedisBloomProtocol.
 */
public class RedisBloomProtocol {

    public enum BloomFilterCommand implements ProtocolCommand {
        RESERVE("BF.RESERVE"),
        ADD("BF.ADD"),
        MADD("BF.MADD"),
        EXISTS("BF.EXISTS"),
        MEXISTS("BF.MEXISTS"),
        INSERT("BF.INSERT"),
        SCANDUMP("BF.SCANDUMP"),
        LOADCHUNK("BF.LOADCHUNK"),
        CARD("BF.CARD"),
        INFO("BF.INFO");

        private final byte[] raw;

        BloomFilterCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw.clone(); // ✅ Return defensive copy to prevent external modification
        }
    }

    public enum CuckooFilterCommand implements ProtocolCommand {
        RESERVE("CF.RESERVE"),
        ADD("CF.ADD"),
        ADDNX("CF.ADDNX"),
        INSERT("CF.INSERT"),
        INSERTNX("CF.INSERTNX"),
        EXISTS("CF.EXISTS"),
        MEXISTS("CF.MEXISTS"),
        DEL("CF.DEL"),
        COUNT("CF.COUNT"),
        SCANDUMP("CF.SCANDUMP"),
        LOADCHUNK("CF.LOADCHUNK"),
        INFO("CF.INFO");

        private final byte[] raw;

        CuckooFilterCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw.clone(); // ✅ Return defensive copy to prevent external modification
        }
    }

    public enum CountMinSketchCommand implements ProtocolCommand {
        INITBYDIM("CMS.INITBYDIM"),
        INITBYPROB("CMS.INITBYPROB"),
        INCRBY("CMS.INCRBY"),
        QUERY("CMS.QUERY"),
        MERGE("CMS.MERGE"),
        INFO("CMS.INFO");

        private final byte[] raw;

        CountMinSketchCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw.clone(); // ✅ Return defensive copy to prevent external modification
        }
    }

    public enum TopKCommand implements ProtocolCommand {
        RESERVE("TOPK.RESERVE"),
        ADD("TOPK.ADD"),
        INCRBY("TOPK.INCRBY"),
        QUERY("TOPK.QUERY"),
        COUNT("TOPK.COUNT"),
        LIST("TOPK.LIST"),
        INFO("TOPK.INFO");

        private final byte[] raw;

        TopKCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw.clone(); // ✅ Return defensive copy to prevent external modification
        }
    }

    public enum TDigestCommand implements ProtocolCommand {
        CREATE,
        INFO,
        ADD,
        RESET,
        MERGE,
        CDF,
        QUANTILE,
        MIN,
        MAX,
        TRIMMED_MEAN,
        RANK,
        REVRANK,
        BYRANK,
        BYREVRANK;

        private final byte[] raw;

        TDigestCommand() {
            raw = ("TDIGEST." + name()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw.clone(); // ✅ Return defensive copy to prevent external modification
        }
    }

    public enum RedisBloomKeyword implements Rawable {
        CAPACITY,
        ERROR,
        NOCREATE,
        EXPANSION,
        NONSCALING,
        BUCKETSIZE,
        MAXITERATIONS,
        ITEMS,
        WEIGHTS,
        COMPRESSION,
        OVERRIDE,
        WITHCOUNT;

        private final byte[] raw;

        RedisBloomKeyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw.clone(); // ✅ Return defensive copy to prevent external modification
        }
    }
}
