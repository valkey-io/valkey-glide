/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.timeseries;

import java.util.List;
import java.util.Map;

public class TSKeyedElements extends TSKeyValue<List<TSElement>> {

    public TSKeyedElements(String key, Map<String, String> labels, List<TSElement> elements) {
        super(key, labels, elements);
    }
}
