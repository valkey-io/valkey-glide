/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.resps;

import java.util.Collections;
import java.util.List;
import redis.clients.jedis.HostAndPort;

/** Slowlog compatibility stub for Valkey GLIDE wrapper. */
public class Slowlog {
    private final long id;
    private final long timeStamp;
    private final long executionTime;
    private final List<String> args;
    private final HostAndPort clientIpPort;
    private final String clientName;

    public Slowlog() {
        this.id = 0;
        this.timeStamp = 0;
        this.executionTime = 0;
        this.args = Collections.emptyList();
        this.clientIpPort = new HostAndPort("localhost", 6379);
        this.clientName = "";
    }

    public long getId() {
        return id;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public List<String> getArgs() {
        return args;
    }

    public HostAndPort getClientIpPort() {
        return clientIpPort;
    }

    public String getClientName() {
        return clientName;
    }
}
