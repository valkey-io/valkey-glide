package glide;

public class TestConfiguration {
    // All redis servers are hosted on localhost
    public static final int STANDALONE_PORT =
            Integer.parseInt(System.getProperty("test.redis.standalone.port"));
    public static final int CLUSTER_PORT =
            Integer.parseInt(System.getProperty("test.redis.cluster.port"));
}
