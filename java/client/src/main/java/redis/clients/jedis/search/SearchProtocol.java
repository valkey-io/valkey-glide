/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.search;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;

/** SearchProtocol compatibility stub for Valkey GLIDE wrapper. */
public class SearchProtocol {

    public enum SearchKeyword implements Rawable {
        SEARCH,
        QUERY,
        FILTER,
        WITHCURSOR;

        private final byte[] raw;

        private SearchKeyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum SearchCommand implements Rawable {
        FT_SEARCH,
        FT_CREATE,
        FT_INFO,
        FT_DROP;

        private final byte[] raw;

        private SearchCommand() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
