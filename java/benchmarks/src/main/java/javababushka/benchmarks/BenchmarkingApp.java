package javababushka.benchmarks;

import static javababushka.benchmarks.utils.Benchmarking.testClientSetGet;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javababushka.benchmarks.jedis.JedisClient;
import javababushka.benchmarks.jedis.JedisPseudoAsyncClient;
import javababushka.benchmarks.lettuce.LettuceAsyncClient;
import javababushka.benchmarks.lettuce.LettuceClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/** Benchmarking app for reporting performance of various redis-rs Java-clients */
public class BenchmarkingApp {

  // main application entrypoint
  public static void main(String[] args) {

    // create the parser
    CommandLineParser parser = new DefaultParser();
    Options options = getOptions();
    RunConfiguration runConfiguration = new RunConfiguration();
    try {
      // parse the command line arguments
      CommandLine line = parser.parse(options, args);
      runConfiguration = verifyOptions(line);
    } catch (ParseException exp) {
      // oops, something went wrong
      System.err.println("Parsing failed.  Reason: " + exp.getMessage());
    }

    for (ClientName client : runConfiguration.clients) {
      switch (client) {
        case JEDIS:
          testClientSetGet(JedisClient::new, runConfiguration, false);
          break;
        case JEDIS_ASYNC:
          testClientSetGet(JedisPseudoAsyncClient::new, runConfiguration, true);
          break;
        case LETTUCE:
          testClientSetGet(LettuceClient::new, runConfiguration, false);
          break;
        case LETTUCE_ASYNC:
          testClientSetGet(LettuceAsyncClient::new, runConfiguration, true);
          break;
        case BABUSHKA:
          System.out.println("Babushka not yet configured");
          break;
      }
    }

    if (runConfiguration.resultsFile.isPresent()) {
      try {
        runConfiguration.resultsFile.get().close();
      } catch (IOException ioException) {
        System.out.println("Error closing results file");
      }
    }
  }

  private static Options getOptions() {
    // create the Options
    Options options = new Options();

    options.addOption("c", "configuration", true, "Configuration flag [Release]");
    options.addOption("f", "resultsFile", true, "Result filepath []");
    options.addOption("d", "dataSize", true, "Data block size [20]");
    options.addOption("C", "concurrentTasks", true, "Number of concurrent tasks [1 10 100]");
    options.addOption(
        "l", "clients", true, "one of: all|jedis|jedis_async|lettuce|lettuce_async|babushka [all]");
    options.addOption("h", "host", true, "host url [localhost]");
    options.addOption("p", "port", true, "port number [6379]");
    options.addOption("n", "clientCount", true, "Client count [1]");
    options.addOption("t", "tls", false, "TLS [true]");

    return options;
  }

  private static RunConfiguration verifyOptions(CommandLine line) throws ParseException {
    RunConfiguration runConfiguration = new RunConfiguration();
    if (line.hasOption("configuration")) {
      String configuration = line.getOptionValue("configuration");
      if (configuration.equalsIgnoreCase("Release") || configuration.equalsIgnoreCase("Debug")) {
        runConfiguration.configuration = configuration;
      } else {
        throw new ParseException("Invalid run configuration (Release|Debug)");
      }
    }

    if (line.hasOption("resultsFile")) {
      try {
        runConfiguration.resultsFile =
            Optional.of(new FileWriter(line.getOptionValue("resultsFile")));
      } catch (IOException e) {
        throw new ParseException("Unable to write to resultsFile.");
      }
    }

    if (line.hasOption("dataSize")) {
      runConfiguration.dataSize = Integer.parseInt(line.getOptionValue("dataSize"));
    }

    if (line.hasOption("concurrentTasks")) {
      String concurrentTasks = line.getOptionValue("concurrentTasks");

      // remove optional square brackets
      if (concurrentTasks.startsWith("[") && concurrentTasks.endsWith("]")) {
        concurrentTasks = concurrentTasks.substring(1, concurrentTasks.length() - 1);
      }
      // check if it's the correct format
      if (!concurrentTasks.matches("\\d+(\\s+\\d+)?")) {
        throw new ParseException("Invalid concurrentTasks");
      }
      // split the string into a list of integers
      runConfiguration.concurrentTasks =
          Arrays.stream(concurrentTasks.split("\\s+"))
              .map(Integer::parseInt)
              .collect(Collectors.toList());
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
                        return Stream.of(
                            ClientName.JEDIS,
                            ClientName.JEDIS_ASYNC,
                            ClientName.BABUSHKA,
                            ClientName.LETTUCE,
                            ClientName.LETTUCE_ASYNC);
                      case ALL_ASYNC:
                        return Stream.of(
                            ClientName.JEDIS_ASYNC,
                            // ClientName.BABUSHKA,
                            ClientName.LETTUCE_ASYNC);
                      case ALL_SYNC:
                        return Stream.of(
                            ClientName.JEDIS,
                            // ClientName.BABUSHKA,
                            ClientName.LETTUCE);
                      default:
                        return Stream.of(e);
                    }
                  })
              .toArray(ClientName[]::new);
    }

    if (line.hasOption("host")) {
      runConfiguration.host = line.getOptionValue("host");
    }

    if (line.hasOption("clientCount")) {
      String clientCount = line.getOptionValue("clientCount");

      // check if it's the correct format
      if (!clientCount.matches("\\d+(\\s+\\d+)?")) {
        throw new ParseException("Invalid concurrentTasks");
      }
      // split the string into a list of integers
      runConfiguration.clientCount =
          Arrays.stream(clientCount.split("\\s+")).mapToInt(Integer::parseInt).toArray();
    }

    if (line.hasOption("tls")) {
      runConfiguration.tls = Boolean.parseBoolean(line.getOptionValue("tls"));
    }

    return runConfiguration;
  }

  public enum ClientName {
    JEDIS("Jedis"),
    JEDIS_ASYNC("Jedis async"),
    LETTUCE("Lettuce"),
    LETTUCE_ASYNC("Lettuce async"),
    BABUSHKA("Babushka"),
    ALL("All"),
    ALL_SYNC("All sync"),
    ALL_ASYNC("All async");

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
    public Optional<FileWriter> resultsFile;
    public int dataSize;
    public List<Integer> concurrentTasks;
    public ClientName[] clients;
    public String host;
    public int port;
    public int[] clientCount;
    public boolean tls;
    public boolean debugLogging = false;

    public RunConfiguration() {
      configuration = "Release";
      resultsFile = Optional.empty();
      dataSize = 20;
      concurrentTasks = List.of(10, 100);
      clients =
          new ClientName[] {
            // ClientName.BABUSHKA,
            ClientName.JEDIS, ClientName.JEDIS_ASYNC, ClientName.LETTUCE, ClientName.LETTUCE_ASYNC
          };
      host = "localhost";
      port = 6379;
      clientCount = new int[] {1, 2};
      tls = false;
    }
  }
}
