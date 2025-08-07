/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/** Module compatibility class for Valkey GLIDE wrapper. Represents a Redis module. */
public class Module {

    private final String name;
    private final int version;

    public Module(String name, int version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Module{name='" + name + "', version=" + version + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Module module = (Module) obj;
        return version == module.version && java.util.Objects.equals(name, module.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, version);
    }
}
