/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

/** JNI callback interface for MONITOR messages. Called from Rust on a background thread. */
@FunctionalInterface
public interface MonitorCallback {
    void onMonitorMessage(
            double timestamp, long db, String clientAddr, String command, String argsJson);
}
