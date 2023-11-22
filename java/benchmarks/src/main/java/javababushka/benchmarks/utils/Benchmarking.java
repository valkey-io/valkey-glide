package javababushka.benchmarks.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javababushka.benchmarks.BenchmarkingApp;
import javababushka.benchmarks.clients.AsyncClient;
import javababushka.benchmarks.clients.Client;
import javababushka.benchmarks.clients.SyncClient;
import org.apache.commons.lang3.tuple.Pair;

/** Class to calculate latency on client-actions */
public class Benchmarking {
  static final double PROB_GET = 0.8;
  static final double PROB_GET_EXISTING_KEY = 0.8;
  static final int SIZE_GET_KEYSPACE = 3750000;
  static final int SIZE_SET_KEYSPACE = 3000000;
  static final int ASYNC_OPERATION_TIMEOUT_SEC = 1;
  static final double LATENCY_NORMALIZATION = 1000000.0;
  static final int LATENCY_MIN = 100000;
  static final int LATENCY_MAX = 10000000;
  static final int LATENCY_MULTIPLIER = 10000;
  static final double TPS_NORMALIZATION = 1000000000.0; // nano to seconds
  // measurements are done in nano-seconds, but it should be converted to seconds later
  static final double SECONDS_IN_NANO = 1e-9;
  public static final double NANO_TO_SECONDS = 1e9;

  private static ChosenAction randomAction() {
    if (Math.random() > PROB_GET) {
      return ChosenAction.SET;
    }
    if (Math.random() > PROB_GET_EXISTING_KEY) {
      return ChosenAction.GET_NON_EXISTING;
    }
    return ChosenAction.GET_EXISTING;
  }

  public static String generateKeyGet() {
    int range = SIZE_GET_KEYSPACE - SIZE_SET_KEYSPACE;
    return Math.floor(Math.random() * range + SIZE_SET_KEYSPACE + 1) + "";
  }

  public static String generateKeySet() {
    return (Math.floor(Math.random() * SIZE_SET_KEYSPACE) + 1) + "";
  }

  public interface Operation {
    void go() throws Exception;
  }

  public static Pair<ChosenAction, Long> measurePerformance(Map<ChosenAction, Operation> actions) {
    var action = randomAction();
    long before = System.nanoTime();
    try {
      actions.get(action).go();
    } catch (Exception e) {
      // timed out - exception from Future::get
      return null;
    }
    long after = System.nanoTime();
    return Pair.of(action, after - before);
  }

  // Assumption: latencies is sorted in ascending order
  private static Long percentile(List<Long> latencies, int percentile) {
    int N = latencies.size();
    double n = (N - 1) * percentile / 100. + 1;
    if (n == 1d) return latencies.get(0);
    else if (n == N) return latencies.get(N - 1);
    int k = (int) n;
    double d = n - k;
    return Math.round(latencies.get(k - 1) + d * (latencies.get(k) - latencies.get(k - 1)));
  }

  private static double stdDeviation(List<Long> latencies, Double avgLatency) {
    double stdDeviation =
        latencies.stream()
            .mapToDouble(Long::doubleValue)
            .reduce(0.0, (stdDev, latency) -> stdDev + Math.pow(latency - avgLatency, 2));
    return Math.sqrt(stdDeviation / latencies.size());
  }

  // This has the side-effect of sorting each latencies ArrayList
  public static Map<ChosenAction, LatencyResults> calculateResults(
      Map<ChosenAction, List<Long>> actionLatencies) {
    Map<ChosenAction, LatencyResults> results = new HashMap<>();

    for (Map.Entry<ChosenAction, List<Long>> entry : actionLatencies.entrySet()) {
      ChosenAction action = entry.getKey();
      List<Long> latencies = entry.getValue();

      if (latencies.size() == 0) {
        results.put(action, new LatencyResults(0, 0, 0, 0, 0, 0));
      } else {
        double avgLatency =
            SECONDS_IN_NANO
                * latencies.stream().mapToLong(Long::longValue).sum()
                / latencies.size();

        Collections.sort(latencies);
        results.put(
            action,
            new LatencyResults(
                avgLatency,
                SECONDS_IN_NANO * percentile(latencies, 50),
                SECONDS_IN_NANO * percentile(latencies, 90),
                SECONDS_IN_NANO * percentile(latencies, 99),
                SECONDS_IN_NANO * stdDeviation(latencies, avgLatency),
                latencies.size()));
      }
    }

    return results;
  }

  public static void printResults(
      Map<ChosenAction, LatencyResults> resultsMap, double duration, int iterations) {
    System.out.printf("Runtime s: %f%n", duration);
    System.out.printf("Iterations: %d%n", iterations);
    System.out.printf("TPS: %f%n", iterations / duration);
    int totalHits = 0;
    for (Map.Entry<ChosenAction, LatencyResults> entry : resultsMap.entrySet()) {
      ChosenAction action = entry.getKey();
      LatencyResults results = entry.getValue();

      System.out.printf("===> %s <===%n", action);
      System.out.printf("avg. time ms: %f%n", results.avgLatency);
      System.out.printf("std dev ms: %f%n", results.stdDeviation);
      System.out.printf("p50 latency ms: %f%n", results.p50Latency);
      System.out.printf("p90 latency ms: %f%n", results.p90Latency);
      System.out.printf("p99 latency ms: %f%n", results.p99Latency);
      System.out.printf("Total hits: %d%n", results.totalHits);
      totalHits += results.totalHits;
    }
    System.out.println("Total hits: " + totalHits);
  }

  public static void testClientSetGet(
      Supplier<Client> clientCreator, BenchmarkingApp.RunConfiguration config, boolean async) {
    for (int concurrentNum : config.concurrentTasks) {
      int iterations = Math.min(Math.max(100000, concurrentNum * 10000), 10000000);
      for (int clientCount : config.clientCount) {
        for (int dataSize : config.dataSize) {
          // create clients
          List<Client> clients = new LinkedList<>();
          for (int cc = 0; cc < clientCount; cc++) {
            Client newClient = clientCreator.get();
            newClient.connectToRedis(
                new ConnectionSettings(
                    config.host, config.port, config.tls, config.clusterModeEnabled));
            clients.add(newClient);
          }

          var clientName = clients.get(0).getName();

          System.out.printf(
              "%n =====> %s <===== %d clients %d concurrent %n%n",
              clientName, clientCount, concurrentNum);
          AtomicInteger iterationCounter = new AtomicInteger(0);

          long started = System.nanoTime();
          List<CompletableFuture<Map<ChosenAction, ArrayList<Long>>>> asyncTasks =
              new ArrayList<>();
          for (int taskNum = 0; taskNum < concurrentNum; taskNum++) {
            final int taskNumDebugging = taskNum;
            asyncTasks.add(
                CompletableFuture.supplyAsync(
                    () -> {
                      Map<ChosenAction, ArrayList<Long>> taskActionResults =
                          Map.of(
                              ChosenAction.GET_EXISTING, new ArrayList<>(),
                              ChosenAction.GET_NON_EXISTING, new ArrayList<>(),
                              ChosenAction.SET, new ArrayList<>());
                      int iterationIncrement = iterationCounter.getAndIncrement();
                      int clientIndex = iterationIncrement % clients.size();

                      if (config.debugLogging) {
                        System.out.printf(
                            "%n concurrent = %d/%d, client# = %d/%d%n",
                            taskNumDebugging, concurrentNum, clientIndex + 1, clientCount);
                      }
                      while (iterationIncrement < iterations) {
                        if (config.debugLogging) {
                          System.out.printf(
                              "> iteration = %d/%d, client# = %d/%d%n",
                              iterationIncrement + 1, iterations, clientIndex + 1, clientCount);
                        }

                        var actions = getActionMap(clients.get(clientIndex), dataSize, async);
                        // operate and calculate tik-tok
                        Pair<ChosenAction, Long> result = measurePerformance(actions);
                        if (result != null) {
                          taskActionResults.get(result.getLeft()).add(result.getRight());
                        }

                        iterationIncrement = iterationCounter.getAndIncrement();
                        clientIndex = iterationIncrement % clients.size();
                      }
                      return taskActionResults;
                    }));
          }
          if (config.debugLogging) {
            System.out.printf("%s client Benchmarking: %n", clientName);
            System.out.printf(
                "===> concurrentNum = %d, clientNum = %d, tasks = %d%n",
                concurrentNum, clientCount, asyncTasks.size());
          }

          // This will start execution of all the concurrent tasks asynchronously
          CompletableFuture<Map<ChosenAction, ArrayList<Long>>>[] completableAsyncTaskArray =
              asyncTasks.toArray(new CompletableFuture[asyncTasks.size()]);
          try {
            // wait for all futures to complete
            CompletableFuture.allOf(completableAsyncTaskArray).get();
          } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
          }
          long after = System.nanoTime();

          // Map to save latency results separately for each action
          Map<ChosenAction, List<Long>> actionResults =
              Map.of(
                  ChosenAction.GET_EXISTING, new ArrayList<>(),
                  ChosenAction.GET_NON_EXISTING, new ArrayList<>(),
                  ChosenAction.SET, new ArrayList<>());

          // for each task, call future.get() to retrieve & save the result in the map
          asyncTasks.forEach(
              future -> {
                try {
                  var futureResult = future.get();
                  futureResult.forEach(
                      (action, result) -> actionResults.get(action).addAll(result));
                } catch (Exception e) {
                  e.printStackTrace();
                }
              });
          var calculatedResults = calculateResults(actionResults);

          clients.forEach(Client::closeConnection);

          if (config.resultsFile.isPresent()) {
            double tps = iterationCounter.get() * NANO_TO_SECONDS / (after - started);
            JsonWriter.Write(
                calculatedResults,
                config.resultsFile.get(),
                config.clusterModeEnabled,
                dataSize,
                clientName,
                clientCount,
                concurrentNum,
                tps);
          }
          printResults(calculatedResults, (after - started) / TPS_NORMALIZATION, iterations);
        }
      }
    }

    System.out.println();
  }

  public static Map<ChosenAction, Operation> getActionMap(
      Client client, int dataSize, boolean async) {

    String value = "0".repeat(dataSize);
    Map<ChosenAction, Operation> actions = new HashMap<>();
    actions.put(
        ChosenAction.GET_EXISTING,
        async
            ? () ->
                ((AsyncClient) client)
                    .asyncGet(generateKeySet())
                    .get(ASYNC_OPERATION_TIMEOUT_SEC, TimeUnit.SECONDS)
            : () -> ((SyncClient) client).get(generateKeySet()));
    actions.put(
        ChosenAction.GET_NON_EXISTING,
        async
            ? () ->
                ((AsyncClient) client)
                    .asyncGet(generateKeyGet())
                    .get(ASYNC_OPERATION_TIMEOUT_SEC, TimeUnit.SECONDS)
            : () -> ((SyncClient) client).get(generateKeyGet()));
    actions.put(
        ChosenAction.SET,
        async
            ? () ->
                ((AsyncClient) client)
                    .asyncSet(generateKeySet(), value)
                    .get(ASYNC_OPERATION_TIMEOUT_SEC, TimeUnit.SECONDS)
            : () -> ((SyncClient) client).set(generateKeySet(), value));
    return actions;
  }
}
