/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks;

import static glide.benchmarks.utils.Benchmarking.testClientSetGet;
import glide.api.logging.Logger;

import glide.benchmarks.clients.glide.GlideAsyncClient;
import glide.benchmarks.clients.jedis.JedisClient;
import glide.benchmarks.clients.lettuce.LettuceAsyncClient;
import glide.benchmarks.utils.OperationType;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/** Benchmarking app for reporting performance of various Valkey Java-clients */
public class BenchmarkingApp {

    // main application entrypoint
    public static void main(String[] args) {

        // create the parser
        CommandLineParser parser = new DefaultParser();
        Options options = getOptions();
        RunConfiguration runConfiguration = new RunConfiguration();
        // Logger.init(Logger.Level.DEBUG);
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            // generate the help statement
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("glide", options);
                return;
            }

            runConfiguration = verifyOptions(line);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed. Reason: " + exp.getMessage());
        }

        // ==================== MODE SELECTION ====================
        if ("customer-repro".equalsIgnoreCase(runConfiguration.mode)) {
            System.out.println("Running CUSTOMER REPRODUCTION mode");
            try {
                CustomerReproduction.run(runConfiguration);
            } catch (Exception e) {
                System.err.println("Customer reproduction failed: " + e.getMessage());
                e.printStackTrace();
            }
            System.exit(0);
            return;
        }
        // ==================== END MODE SELECTION ====================

        for (ClientName client : runConfiguration.clients) {
            switch (client) {
                case JEDIS:
                    System.out.println("Run JEDIS sync client");
                    testClientSetGet(JedisClient::new, runConfiguration, false);
                    break;
                case LETTUCE:
                    System.out.println("Run LETTUCE async client");
                    testClientSetGet(LettuceAsyncClient::new, runConfiguration, true);
                    break;
                case GLIDE:
                    System.out.println("Valkey-GLIDE async client");
                    testClientSetGet(GlideAsyncClient::new, runConfiguration, true);
                    break;
            }
        }
    }

    private static Options getOptions() {
        // create the Options
        Options options = new Options();

        options.addOption(Option.builder("h").longOpt("help").desc("Print this message").build());
        options.addOption(
                Option.builder()
                        .longOpt("configuration")
                        .hasArg(true)
                        .desc("Configuration flag [Release]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("resultsFile")
                        .hasArg(true)
                        .desc("Result filepath (stdout if empty) []")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("dataSize")
                        .hasArg(true)
                        .desc("Data block size [100 4000]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("concurrentTasks")
                        .hasArg(true)
                        .desc("Number of concurrent tasks [100, 1000]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("clients")
                        .hasArg(true)
                        .desc("one of: all|jedis|lettuce|glide")
                        .build());
        options.addOption(
                Option.builder().longOpt("host").hasArg(true).desc("Hostname [localhost]").build());
        options.addOption(
                Option.builder().longOpt("port").hasArg(true).desc("Port number [6379]").build());
        options.addOption(
                Option.builder()
                        .longOpt("clientCount")
                        .hasArg(true)
                        .desc("Number of clients to run [1]")
                        .build());
        options.addOption(Option.builder().longOpt("tls").hasArg(false).desc("TLS [false]").build());
        options.addOption(
                Option.builder()
                        .longOpt("clusterModeEnabled")
                        .hasArg(false)
                        .desc("Is cluster-mode enabled, other standalone mode is used [false]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("minimal")
                        .hasArg(false)
                        .desc("Run benchmark in minimal mode")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("debugLogging")
                        .hasArg(false)
                        .desc("Verbose logs [false]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("tps")
                        .hasArg(true)
                        .desc("Target transactions per second (0 = unlimited) [0]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("operations")
                        .hasArg(true)
                        .desc("Operation type: all|read|write|delete [all]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("warmupKeys")
                        .hasArg(true)
                        .desc("Number of keys to pre-populate for read benchmarks [100000]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("duration")
                        .hasArg(true)
                        .desc("Duration to run in seconds, 0 = use iterations [0]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("metricsDir")
                        .hasArg(true)
                        .desc("Directory for metrics CSV output [./metrics]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("metricsInterval")
                        .hasArg(true)
                        .desc("Metrics collection interval in seconds [60]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("tcpNoDelay")
                        .hasArg(false)
                        .desc("Enable TCP_NODELAY [false]")
                        .build());

        // ==================== NEW OPTIONS ====================
        options.addOption(
                Option.builder()
                        .longOpt("mode")
                        .hasArg(true)
                        .desc("Run mode: benchmark|customer-repro [benchmark]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("eventRate")
                        .hasArg(true)
                        .desc("(customer-repro) Events per second [5000]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("workerThreads")
                        .hasArg(true)
                        .desc("(customer-repro) Worker threads [60]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("delKeysPerEvent")
                        .hasArg(true)
                        .desc("(customer-repro) Keys to delete per event [3]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("delTimeoutMs")
                        .hasArg(true)
                        .desc("(customer-repro) DEL timeout in ms [1000]")
                        .build());
        options.addOption(
        Option.builder()
                .longOpt("requestTimeout")
                .hasArg(true)
                .desc("Request timeout in ms [1000]")
                .build());
        options.addOption(
                Option.builder()
                        .longOpt("lua")
                        .hasArg(false)
                        .desc("Enable Lua canary thread for timeout verification [false]")
                        .build());
        options.addOption(
                Option.builder()
                        .longOpt("inflightLimit")
                        .hasArg(true)
                        .desc("Max inflight requests (0 = default 1000) [0]")
                        .build());
        // ==================== END NEW OPTIONS ====================

        return options;
    }

    private static RunConfiguration verifyOptions(CommandLine line) throws ParseException {
        RunConfiguration runConfiguration = new RunConfiguration();

        if (line.hasOption("configuration")) {
            String configuration = line.getOptionValue("configuration");
            if (configuration.equalsIgnoreCase("Release") || configuration.equalsIgnoreCase("Debug")) {
                runConfiguration.configuration = configuration;
            } else {
                throw new ParseException(
                        "Invalid run configuration (" + configuration + "), must be (Release|Debug)");
            }
        }

        if (line.hasOption("resultsFile")) {
            runConfiguration.resultsFile = Optional.ofNullable(line.getOptionValue("resultsFile"));
        }

        if (line.hasOption("dataSize")) {
            runConfiguration.dataSize = parseIntListOption(line.getOptionValue("dataSize"));
        }

        if (line.hasOption("concurrentTasks")) {
            runConfiguration.concurrentTasks = parseIntListOption(line.getOptionValue("concurrentTasks"));
        }

        if (line.hasOption("clients")) {
            String[] clients = line.getOptionValue("clients").split(",");
            runConfiguration.clients =
                    Arrays.stream(clients)
                            .map(c -> Enum.valueOf(ClientName.class, c.toUpperCase()))
                            .flatMap(
                                    e -> {
                                        switch (e) {
                                            case ALL:
                                                return Stream.of(ClientName.JEDIS, ClientName.GLIDE, ClientName.LETTUCE);
                                            default:
                                                return Stream.of(e);
                                        }
                                    })
                            .toArray(ClientName[]::new);
        }

        if (line.hasOption("host")) {
            runConfiguration.host = line.getOptionValue("host");
        }

        if (line.hasOption("port")) {
            runConfiguration.port = Integer.parseInt(line.getOptionValue("port"));
        }

        if (line.hasOption("clientCount")) {
            runConfiguration.clientCount = parseIntListOption(line.getOptionValue("clientCount"));
        }

        if (line.hasOption("dataSize")) {
            runConfiguration.dataSize = parseIntListOption(line.getOptionValue("dataSize"));
        }

        if (line.hasOption("tps")) {
            runConfiguration.targetTps = Integer.parseInt(line.getOptionValue("tps"));
        }

        if (line.hasOption("operations")) {
            String ops = line.getOptionValue("operations").toLowerCase();
            switch (ops) {
                case "read":
                    runConfiguration.operationType = OperationType.READ_ONLY;
                    break;
                case "write":
                    runConfiguration.operationType = OperationType.WRITE_ONLY;
                    break;
                case "delete":
                    runConfiguration.operationType = OperationType.DELETE_ONLY;
                    break;
                case "all":
                    runConfiguration.operationType = OperationType.ALL;
                    break;
                default:
                    throw new ParseException(
                            "Invalid operations type (" + ops + "), must be (all|read|write|delete)");
            }
        }

        if (line.hasOption("warmupKeys")) {
            runConfiguration.warmupKeyCount = Integer.parseInt(line.getOptionValue("warmupKeys"));
        }

        if (line.hasOption("duration")) {
            runConfiguration.durationSeconds = Long.parseLong(line.getOptionValue("duration"));
        }

        if (line.hasOption("metricsDir")) {
            runConfiguration.metricsOutputDir = line.getOptionValue("metricsDir");
        }

        if (line.hasOption("metricsInterval")) {
            runConfiguration.metricsIntervalSeconds =
                    Integer.parseInt(line.getOptionValue("metricsInterval"));
        }

        // ==================== NEW OPTION PARSERS ====================
        if (line.hasOption("mode")) {
            runConfiguration.mode = line.getOptionValue("mode");
        }

        if (line.hasOption("eventRate")) {
            runConfiguration.eventRate = Integer.parseInt(line.getOptionValue("eventRate"));
        }

        if (line.hasOption("workerThreads")) {
            runConfiguration.workerThreads = Integer.parseInt(line.getOptionValue("workerThreads"));
        }

        if (line.hasOption("delKeysPerEvent")) {
            runConfiguration.delKeysPerEvent = Integer.parseInt(line.getOptionValue("delKeysPerEvent"));
        }

        if (line.hasOption("delTimeoutMs")) {
            runConfiguration.delTimeoutMs = Long.parseLong(line.getOptionValue("delTimeoutMs"));
        }
        if (line.hasOption("requestTimeout")) {
    runConfiguration.requestTimeoutMs = Integer.parseInt(line.getOptionValue("requestTimeout"));
}
        if (line.hasOption("inflightLimit")) {
            runConfiguration.inflightLimit = Integer.parseInt(line.getOptionValue("inflightLimit"));
        }
        // ==================== END NEW OPTION PARSERS ====================

        runConfiguration.tls = line.hasOption("tls");
        runConfiguration.clusterModeEnabled = line.hasOption("clusterModeEnabled");
        runConfiguration.minimal = line.hasOption("minimal");
        runConfiguration.debugLogging = line.hasOption("debugLogging");
        runConfiguration.tcpNoDelay = line.hasOption("tcpNoDelay");
        runConfiguration.lua = line.hasOption("lua");

        return runConfiguration;
    }

    private static int[] parseIntListOption(String line) throws ParseException {
        String lineValue = line;

        if (lineValue.startsWith("[") && lineValue.endsWith("]")) {
            lineValue = lineValue.substring(1, lineValue.length() - 1);
        }

        lineValue = lineValue.trim();

        if (!lineValue.matches("\\d+(\\s+\\d+)*")) {
            throw new ParseException("Invalid option: " + line);
        }
        return Arrays.stream(lineValue.split("\\s+")).mapToInt(Integer::parseInt).toArray();
    }

    public enum ClientName {
        JEDIS("Jedis"),
        LETTUCE("Lettuce"),
        GLIDE("Glide"),
        ALL("All");

        private String name;

        private ClientName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public boolean isEqual(String other) {
            return this.toString().equalsIgnoreCase(other);
        }
    }

    public static class RunConfiguration {
        public String configuration;
        public Optional<String> resultsFile;
        public int[] dataSize;
        public int[] concurrentTasks;
        public ClientName[] clients;
        public String host;
        public int port;
        public int[] clientCount;
        public boolean tls;
        public boolean clusterModeEnabled;
        public boolean debugLogging = false;
        public boolean minimal = false;
        public int targetTps = 0;
        public OperationType operationType = OperationType.ALL;
        public int warmupKeyCount = 100000;
        public long durationSeconds = 0;
        public String metricsOutputDir = "./metrics";
        public int metricsIntervalSeconds = 60;
        public boolean tcpNoDelay = false;
        public int requestTimeoutMs = 1000; 

        // ==================== NEW FIELDS ====================
        public String mode = "benchmark";      // benchmark or customer-repro
        public int eventRate = 5000;           // events/sec for customer-repro
        public int workerThreads = 60;         // worker threads for customer-repro
        public int delKeysPerEvent = 3;        // keys per DEL
        public long delTimeoutMs = 1000;       // DEL timeout (customer uses 1000ms)
        public boolean lua = false;            // Enable Lua canary thread
        public int inflightLimit = 0;          // Max inflight requests (0 = default 1000)
        // ==================== END NEW FIELDS ====================

        public RunConfiguration() {
            configuration = "Release";
            resultsFile = Optional.empty();
            dataSize = new int[] {100, 4000};
            concurrentTasks = new int[] {1, 10, 100, 1000};
            clients =
                    new ClientName[] {
                        ClientName.ALL,
                    };
            host = "localhost";
            port = 6379;
            clientCount = new int[] {1};
            tls = false;
            clusterModeEnabled = false;
            minimal = false;
            operationType = OperationType.ALL;
            warmupKeyCount = 100000;
            durationSeconds = 0;
            metricsOutputDir = "./metrics";
            metricsIntervalSeconds = 60;
            tcpNoDelay = false;
        }
    }
}