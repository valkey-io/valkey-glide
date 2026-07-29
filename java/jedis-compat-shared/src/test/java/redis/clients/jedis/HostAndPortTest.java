/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HostAndPortTest {

    @Test
    public void from_parsesIpv4() {
        HostAndPort hp = HostAndPort.from("127.0.0.1:6379");
        assertEquals("127.0.0.1", hp.getHost());
        assertEquals(6379, hp.getPort());
        assertEquals("127.0.0.1:6379", hp.toString());
    }

    @Test
    public void from_parsesHostname() {
        HostAndPort hp = HostAndPort.from("redis.local:6380");
        assertEquals("redis.local", hp.getHost());
        assertEquals(6380, hp.getPort());
    }

    @Test
    public void from_parsesBracketedIpv6() {
        HostAndPort hp = HostAndPort.from("[::1]:6379");
        assertEquals("::1", hp.getHost());
        assertEquals(6379, hp.getPort());
        assertEquals("[::1]:6379", hp.toString());
    }

    @Test
    public void from_parsesUnbracketedIpv6UsingLastColonAsPortSeparator() {
        HostAndPort hp = HostAndPort.from("::1:6379");
        assertEquals("::1", hp.getHost());
        assertEquals(6379, hp.getPort());
    }

    @Test
    public void parseString_matchesFrom() {
        assertEquals(HostAndPort.from("10.0.0.5:7000"), HostAndPort.parseString("10.0.0.5:7000"));
    }
}
