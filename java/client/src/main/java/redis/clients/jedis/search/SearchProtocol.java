/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.search;

import java.nio.charset.StandardCharsets;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

/**
 * Search protocol commands and keywords for Valkey GLIDE compatibility layer. Based on original
 * Jedis SearchProtocol.
 */
public class SearchProtocol {

    public enum SearchCommand implements ProtocolCommand {
        CREATE("FT.CREATE"),
        ALTER("FT.ALTER"),
        INFO("FT.INFO"),
        SEARCH("FT.SEARCH"),
        EXPLAIN("FT.EXPLAIN"),
        EXPLAINCLI("FT.EXPLAINCLI"),
        AGGREGATE("FT.AGGREGATE"),
        CURSOR("FT.CURSOR"),
        CONFIG("FT.CONFIG"),
        ALIASADD("FT.ALIASADD"),
        ALIASUPDATE("FT.ALIASUPDATE"),
        ALIASDEL("FT.ALIASDEL"),
        SYNUPDATE("FT.SYNUPDATE"),
        SYNDUMP("FT.SYNDUMP"),
        SUGADD("FT.SUGADD"),
        SUGGET("FT.SUGGET"),
        SUGDEL("FT.SUGDEL"),
        SUGLEN("FT.SUGLEN"),
        DROPINDEX("FT.DROPINDEX"),
        DICTADD("FT.DICTADD"),
        DICTDEL("FT.DICTDEL"),
        DICTDUMP("FT.DICTDUMP"),
        SPELLCHECK("FT.SPELLCHECK"),
        TAGVALS("FT.TAGVALS"),
        PROFILE("FT.PROFILE"),
        _LIST("FT._LIST");

        private final byte[] raw;

        SearchCommand(String alt) {
            raw = alt.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }

    public enum SearchKeyword implements Rawable {
        SCHEMA,
        TEXT,
        TAG,
        NUMERIC,
        GEO,
        GEOSHAPE,
        VECTOR,
        VERBATIM,
        NOCONTENT,
        NOSTOPWORDS,
        WITHSCORES,
        LANGUAGE,
        INFIELDS,
        SORTBY,
        ASC,
        DESC,
        LIMIT,
        HIGHLIGHT,
        FIELDS,
        TAGS,
        SUMMARIZE,
        FRAGS,
        LEN,
        SEPARATOR,
        INKEYS,
        RETURN,
        FILTER,
        GEOFILTER,
        ADD,
        INCR,
        MAX,
        FUZZY,
        READ,
        DEL,
        DD,
        TEMPORARY,
        STOPWORDS,
        NOFREQS,
        NOFIELDS,
        NOOFFSETS,
        NOHL,
        ON,
        SORTABLE,
        UNF,
        PREFIX,
        LANGUAGE_FIELD,
        SCORE,
        SCORE_FIELD,
        SCORER,
        PARAMS,
        AS,
        DIALECT,
        SLOP,
        TIMEOUT,
        INORDER,
        EXPANDER,
        MAXTEXTFIELDS,
        SKIPINITIALSCAN,
        WITHSUFFIXTRIE,
        NOSTEM,
        NOINDEX,
        PHONETIC,
        WEIGHT,
        CASESENSITIVE,
        LOAD,
        APPLY,
        GROUPBY,
        MAXIDLE,
        WITHCURSOR,
        DISTANCE,
        TERMS,
        INCLUDE,
        EXCLUDE,
        SEARCH,
        AGGREGATE,
        QUERY,
        LIMITED,
        COUNT,
        REDUCE,
        INDEXMISSING,
        INDEXEMPTY,
        ADDSCORES,
        WITHPAYLOADS,
        SET,
        GET;

        private final byte[] raw;

        SearchKeyword() {
            raw = name().getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getRaw() {
            return raw;
        }
    }
}
