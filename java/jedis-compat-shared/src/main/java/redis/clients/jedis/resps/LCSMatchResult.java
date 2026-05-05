/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.List;

/** LCSMatchResult compatibility stub for Valkey GLIDE wrapper. */
public class LCSMatchResult {

    /** Position compatibility stub. */
    public static class Position {
        private final long start;
        private final long end;

        public Position(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return "Position{start=" + start + ", end=" + end + "}";
        }
    }

    /** MatchedPosition compatibility stub. */
    public static class MatchedPosition {
        private final Position a;
        private final Position b;
        private final long matchLen;

        public MatchedPosition(Position a, Position b, long matchLen) {
            this.a = a;
            this.b = b;
            this.matchLen = matchLen;
        }

        public Position getA() {
            return a;
        }

        public Position getB() {
            return b;
        }

        public long getMatchLen() {
            return matchLen;
        }

        @Override
        public String toString() {
            return "MatchedPosition{a=" + a + ", b=" + b + ", matchLen=" + matchLen + "}";
        }
    }

    private final String matchString;
    private final List<MatchedPosition> matches;
    private final long len;

    public LCSMatchResult(String matchString, List<MatchedPosition> matches, long len) {
        this.matchString = matchString;
        this.matches = matches;
        this.len = len;
    }

    public String getMatchString() {
        return matchString;
    }

    public List<MatchedPosition> getMatches() {
        return matches;
    }

    public long getLen() {
        return len;
    }

    @Override
    public String toString() {
        return "LCSMatchResult{matchString='"
                + matchString
                + "', matches="
                + matches
                + ", len="
                + len
                + "}";
    }
}
