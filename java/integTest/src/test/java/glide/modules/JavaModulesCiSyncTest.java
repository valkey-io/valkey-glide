/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.modules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Single-sources {@code utils/run-modules-ci-locally.sh}'s test invocation against {@code
 * .github/workflows/java-modules.yml}'s "Build and run modules tests" step, so the two cannot
 * silently drift apart (the Java equivalent of Python's {@code test_ci_workflow_sync.py} and Node's
 * {@code ServerModulesCiSync.test.ts}). This test does not run either script -- it just asserts
 * that both sides invoke the same gradle target with the same two endpoint flags, so a future edit
 * to one side that isn't mirrored on the other fails here instead of only showing up as a
 * discrepancy in a future review.
 */
public class JavaModulesCiSyncTest {

    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath().getParent().getParent();
    private static final Path WORKFLOW_FILE = REPO_ROOT.resolve(".github/workflows/java-modules.yml");
    private static final Path LOCAL_RUNNER_FILE =
            REPO_ROOT.resolve("utils/run-modules-ci-locally.sh");

    private static final String[] ENDPOINT_FLAGS = {"-Dstandalone-endpoints", "-Dcluster-endpoints"};

    private static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void bothInvokeModulesTestWithTheSameEndpointFlags() throws IOException {
        String workflowText = readFile(WORKFLOW_FILE);
        String runnerText = readFile(LOCAL_RUNNER_FILE);

        assertTrue(
                workflowText.contains(":integTest:modulesTest"),
                "java-modules.yml no longer invokes :integTest:modulesTest -- update both files"
                        + " to match.");
        assertTrue(
                runnerText.contains(":integTest:modulesTest"),
                "utils/run-modules-ci-locally.sh is missing :integTest:modulesTest, which CI's"
                        + " 'Build and run modules tests' step runs.");

        for (String flag : ENDPOINT_FLAGS) {
            assertTrue(
                    workflowText.contains(flag),
                    "java-modules.yml no longer passes " + flag + " -- update both files to match.");
            assertTrue(
                    runnerText.contains(flag),
                    "utils/run-modules-ci-locally.sh is missing " + flag + ", which CI's step passes.");
        }
    }

    @Test
    public void localRunnerPinsProtoc29ForTheSameReasonCiDoes() throws IOException {
        // CI pins protoc 29.1 (arduino/setup-protoc version "29.1" in java-modules.yml); a newer
        // system protoc generates code the pinned protobuf-java 4.29.6 runtime can't compile. The
        // local runner must pin the same version so a local pass is evidence the CI pass will also
        // succeed, not an artifact of whatever protoc happens to be on this machine's PATH.
        String workflowText = readFile(WORKFLOW_FILE);
        String runnerText = readFile(LOCAL_RUNNER_FILE);

        assertTrue(
                Pattern.compile("version:\\s*[\"']?29\\.1").matcher(workflowText).find(),
                "java-modules.yml no longer pins protoc 29.1 -- update "
                        + "run-modules-ci-locally.sh's pinned version to match.");
        assertTrue(
                runnerText.contains("protoc-29.1"),
                "utils/run-modules-ci-locally.sh no longer references the pinned protoc-29.1 "
                        + "toolchain -- update it to match java-modules.yml's pinned version.");
    }
}
