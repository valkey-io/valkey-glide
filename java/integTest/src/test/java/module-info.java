module glide.integTest {
    opens glide;
    opens glide.cluster;
    opens glide.modules;
    opens glide.standalone;

    requires glide.client;
    requires com.google.gson;
    requires static lombok;
    requires net.bytebuddy;
    requires org.apache.commons.lang3;
    requires org.junit.jupiter.params;
    requires org.semver4j;
}
