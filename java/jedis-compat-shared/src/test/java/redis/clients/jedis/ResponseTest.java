/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.models.exceptions.RequestException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisDataException;

class ResponseTest {

    @Test
    void getWrapsRequestExceptionAsJedisDataException() {
        Response<String> response = new Response<>(BuilderFactory.STRING);
        response.set(new RequestException("ERR wrong type"));

        JedisDataException ex = assertThrows(JedisDataException.class, response::get);
        assertTrue(ex.getMessage().contains("ERR wrong type"));
        assertEquals(RequestException.class, ex.getCause().getClass());
    }
}
