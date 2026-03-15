package repro;

public class Main {
    public static void main(String[] args) throws Exception {
        String role = System.getenv().getOrDefault("ROLE", "worker");
        String endpoint = System.getenv().getOrDefault("CLUSTER_ENDPOINT",
                "clustercfg.testing-cluster.ey5v7d.use2.cache.amazonaws.com");
        int port = Integer.parseInt(System.getenv().getOrDefault("CLUSTER_PORT", "6379"));

        switch (role) {
            case "worker":
                new Worker(endpoint, port).run();
                break;
            case "producer":
                new Producer(endpoint, port).run();
                break;
            case "watcher":
                new FreezeWatcher().run();
                break;
            default:
                System.err.println("Unknown ROLE: " + role + ". Use worker|producer|watcher");
                System.exit(1);
        }
    }
}
