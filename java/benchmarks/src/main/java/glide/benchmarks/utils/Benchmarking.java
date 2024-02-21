/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.utils;

import glide.benchmarks.BenchmarkingApp;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.clients.Client;
import glide.benchmarks.clients.SyncClient;
import glide.connectors.resources.EpollResource;
import glide.connectors.resources.KQueuePoolResource;
import glide.connectors.resources.Platform;
import glide.connectors.resources.ThreadPoolResource;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;

/** Class to calculate latency on client-actions */
public class Benchmarking {
    static final double PROB_GET = 0.8;
    static final double PROB_GET_EXISTING_KEY = 0.8;
    static final int SIZE_GET_KEYSPACE = 3750000;
    static final int SIZE_SET_KEYSPACE = 3000000;
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
        void go(Client client) throws InterruptedException, ExecutionException;
    }

    public static Pair<ChosenAction, Long> measurePerformance(
            Client client, Map<ChosenAction, Operation> actions) {
        var action = randomAction();
        long before = System.nanoTime();
        try {
            actions.get(action).go(client);
        } catch (ExecutionException e) {
            throw new RuntimeException("Client error", e);
        } catch (InterruptedException e) {
            if (Thread.currentThread().isInterrupted()) {
                // restore interrupt
                Thread.interrupted();
            }
            throw new RuntimeException("The thread was interrupted", e);
        }
        long after = System.nanoTime();
        return Pair.of(action, after - before);
    }

    public static Map<ChosenAction, LatencyResults> calculateResults(
            Map<ChosenAction, List<Long>> actionLatencies) {
        Map<ChosenAction, LatencyResults> results = new HashMap<>();

        for (Map.Entry<ChosenAction, List<Long>> entry : actionLatencies.entrySet()) {
            ChosenAction action = entry.getKey();
            double[] latencies = entry.getValue().stream().mapToDouble(Long::doubleValue).toArray();

            if (latencies.length != 0) {
                results.put(action, new LatencyResults(latencies));
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

            int numberOfThreads = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            ThreadPoolResource threadPoolResource;
            if (Platform.getCapabilities().isKQueueAvailable()) {
                threadPoolResource =
                        new KQueuePoolResource(new KQueueEventLoopGroup(numberOfThreads, executor));
            } else if (Platform.getCapabilities().isEPollAvailable()) {
                threadPoolResource = new EpollResource(new EpollEventLoopGroup(numberOfThreads, executor));
            } else {
                throw new RuntimeException("Current platform supports no known thread pool resources");
            }

            int iterations =
                    config.minimal ? 1000 : Math.min(Math.max(100000, concurrentNum * 10000), 10000000);
            for (int clientCount : config.clientCount) {
                for (int dataSize : config.dataSize) {
                    // create clients
                    List<Client> clients = new LinkedList<>();
                    for (int cc = 0; cc < clientCount; cc++) {
                        Client newClient = clientCreator.get();
                        newClient.connectToRedis(
                                new ConnectionSettings(
                                        config.host,
                                        config.port,
                                        config.tls,
                                        config.clusterModeEnabled,
                                        threadPoolResource));
                        clients.add(newClient);
                    }

                    var clientName = clients.get(0).getName();

                    System.out.printf(
                            "%n =====> %s <===== %d clients %d concurrent %d data size %n%n",
                            clientName, clientCount, concurrentNum, dataSize);
                    AtomicInteger iterationCounter = new AtomicInteger(0);

                    long started = System.nanoTime();
                    List<CompletableFuture<Map<ChosenAction, ArrayList<Long>>>> asyncTasks =
                            new ArrayList<>();
                    for (int taskNum = 0; taskNum < concurrentNum; taskNum++) {
                        final int taskNumDebugging = taskNum;
                        asyncTasks.add(
                                createTask(
                                        async,
                                        concurrentNum,
                                        clientCount,
                                        dataSize,
                                        iterationCounter,
                                        clients,
                                        taskNumDebugging,
                                        iterations,
                                        executor,
                                        config.debugLogging));
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
                    printResults(calculatedResults, (after - started) / NANO_TO_SECONDS, iterations);
                }
            }
            executor.shutdownNow();
            try {
                threadPoolResource.getEventLoopGroup().shutdownGracefully().get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println();
    }

    private static CompletableFuture<Map<ChosenAction, ArrayList<Long>>> createTask(
            boolean async,
            int concurrentNum,
            int clientCount,
            int dataSize,
            AtomicInteger iterationCounter,
            List<Client> clients,
            int taskNumDebugging,
            int iterations,
            Executor executor,
            boolean debugLogging) {
        return CompletableFuture.supplyAsync(
                () -> {
                    var taskActionResults =
                            Map.of(
                                    ChosenAction.GET_EXISTING, new ArrayList<Long>(),
                                    ChosenAction.GET_NON_EXISTING, new ArrayList<Long>(),
                                    ChosenAction.SET, new ArrayList<Long>());
                    var actions = getActionMap(dataSize, async);

                    if (debugLogging) {
                        System.out.printf("%n concurrent = %d/%d%n", taskNumDebugging, concurrentNum);
                    }
                    while (iterationCounter.get() < iterations) {
                        int iterationIncrement = iterationCounter.getAndIncrement();
                        int clientIndex = iterationIncrement % clients.size();

                        if (debugLogging) {
                            System.out.printf(
                                    "> iteration = %d/%d, client# = %d/%d%n",
                                    iterationIncrement + 1, iterations, clientIndex + 1, clientCount);
                        }

                        // operate and calculate tik-tok
                        Pair<ChosenAction, Long> result = measurePerformance(clients.get(clientIndex), actions);
                        taskActionResults.get(result.getLeft()).add(result.getRight());
                    }
                    return taskActionResults;
                },
                executor);
    }

    public static Map<ChosenAction, Operation> getActionMap(int dataSize, boolean async) {

        String value = "0".repeat(dataSize);
        return Map.of(
                ChosenAction.GET_EXISTING,
                        (client) -> {
                            if (async) {
                                ((AsyncClient) client).asyncGet(generateKeySet()).get();
                            } else {
                                ((SyncClient) client).get(generateKeySet());
                            }
                        },
                ChosenAction.GET_NON_EXISTING,
                        (client) -> {
                            if (async) {
                                ((AsyncClient) client).asyncGet(generateKeyGet()).get();
                            } else {
                                ((SyncClient) client).get(generateKeyGet());
                            }
                        },
                ChosenAction.SET,
                        (client) -> {
                            if (async) {
                                ((AsyncClient) client).asyncSet(generateKeySet(), value).get();
                            } else {
                                ((SyncClient) client).set(generateKeySet(), value);
                            }
                        });
    }
}
