module glide.integTest {
    opens glide;
    opens glide.cluster;
    opens glide.modules;
    opens glide.standalone;
    opens compatibility.jedis;

    requires glide.api;
    requires com.google.gson;
    requires static lombok;
    requires net.bytebuddy;
    requires org.apache.commons.lang3;
    requires org.apache.commons.pool2;
    requires org.junit.jupiter.api; // Added: Required for JUnit tests
    requires org.junit.jupiter.params;
    requires org.semver4j;
}
