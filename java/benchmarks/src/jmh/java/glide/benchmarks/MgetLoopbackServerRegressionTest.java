/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Regression coverage for the loopback benchmark's bounded connection handling. */
public final class MgetLoopbackServerRegressionTest {

    private MgetLoopbackServerRegressionTest() {}

    public static void main(String[] arguments) throws Exception {
        MgetLoopbackBenchmark.MockRespServer server = new MgetLoopbackBenchmark.MockRespServer(1, 32);
        try (Socket first = connect(server);
                Socket second = connect(server);
                Socket rejected = connect(server)) {
            rejected.setSoTimeout(500);
            assertEquals(-1, rejected.getInputStream().read());

            first.close();
            assertPingAcceptedAfterRelease(server);
        } finally {
            server.close();
        }
    }

    private static Socket connect(MgetLoopbackBenchmark.MockRespServer server) throws IOException {
        return new Socket("127.0.0.1", server.port());
    }

    private static String readResponse(Socket socket) throws IOException {
        socket.setSoTimeout(500);
        byte[] response = new byte[7];
        int offset = 0;
        while (offset < response.length) {
            int read = socket.getInputStream().read(response, offset, response.length - offset);
            if (read == -1) {
                throw new IOException("Unexpected end of response");
            }
            offset += read;
        }
        return new String(response, StandardCharsets.US_ASCII);
    }

    private static void assertPingAcceptedAfterRelease(MgetLoopbackBenchmark.MockRespServer server)
            throws Exception {
        IOException lastError = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            try (Socket acceptedAfterRelease = connect(server)) {
                acceptedAfterRelease
                        .getOutputStream()
                        .write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
                acceptedAfterRelease.getOutputStream().flush();
                assertEquals("+PONG\r\n", readResponse(acceptedAfterRelease));
                return;
            } catch (IOException error) {
                lastError = error;
                Thread.sleep(10);
            }
        }
        throw lastError;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
