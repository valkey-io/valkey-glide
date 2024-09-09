/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
package glide;

import com.vdurmont.semver4j.Semver;
import glide.api.GlideClient;
import glide.api.models.commands.InfoOptions;

import java.util.Arrays;

import static glide.TestUtilities.commonClientConfig;

public final class TestConfiguration {
    // All servers are hosted on localhost
    public static final int[] STANDALONE_PORTS = getPortsFromProperty("test.server.standalone.ports");
    public static final int[] CLUSTER_PORTS = getPortsFromProperty("test.server.cluster.ports");
    private static final GlideClient standaloneClient;
    public static final Semver SERVER_VERSION;

    static {
        try {
            standaloneClient = GlideClient.createClient(commonClientConfig().build()).get();
            String infoResponse = standaloneClient.info(
                InfoOptions.builder()
                    .section(InfoOptions.Section.SERVER)
                    .build()
            ).get();
            String serverVersion = null;
            String[] serverSectionArray = infoResponse.split("\n");

            for (int i = 0; i < serverSectionArray.length; i++) {
                if (serverSectionArray[i].contains("redis_version")) {
                    String[] versionKeyValue = serverSectionArray[i].split(":");
                    if (versionKeyValue.length > 1) {
                        serverVersion = versionKeyValue[1];
                    }
                    break;
                }
            }
            if (serverVersion != null) {
                SERVER_VERSION = new Semver(serverVersion);
            } else {
                throw new Exception("Error in getting server version");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static int[] getPortsFromProperty(String propName) {
        return Arrays.stream(System.getProperty(propName).split(","))
            .mapToInt(Integer::parseInt)
            .toArray();
    }
}
