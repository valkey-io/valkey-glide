package javababushka.benchmarks.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

public class JsonWriter {

  public static void Write(
      Map<ChosenAction, LatencyResults> calculatedResults,
      String resultsFile,
      boolean isCluster,
      int dataSize,
      String client,
      int clientCount,
      int numOfTasks,
      double tps) {

    try {
      Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
      Collection<Measurements> recordings = new ArrayList<>();

      Path path = Path.of(resultsFile);
      if (Files.exists(path)) {
        TypeToken<Collection<Measurements>> collectionType = new TypeToken<>() {};
        var json = new String(Files.readAllBytes(path));
        recordings = gson.fromJson(json, collectionType);
      }
      var data =
          new Measurements.MeasurementsBuilder()
              .is_cluster(isCluster)
              .data_size(dataSize)
              .client(client)
              .client_count(clientCount)
              .num_of_tasks(numOfTasks)
              .tps(tps)
              .get_existing_average_latency(
                  calculatedResults.get(ChosenAction.GET_EXISTING).avgLatency)
              .get_existing_p50_latency(calculatedResults.get(ChosenAction.GET_EXISTING).p50Latency)
              .get_existing_p90_latency(calculatedResults.get(ChosenAction.GET_EXISTING).p90Latency)
              .get_existing_p99_latency(calculatedResults.get(ChosenAction.GET_EXISTING).p99Latency)
              .get_existing_std_dev(calculatedResults.get(ChosenAction.GET_EXISTING).stdDeviation)
              .get_non_existing_average_latency(
                  calculatedResults.get(ChosenAction.GET_NON_EXISTING).avgLatency)
              .get_non_existing_p50_latency(
                  calculatedResults.get(ChosenAction.GET_NON_EXISTING).p50Latency)
              .get_non_existing_p90_latency(
                  calculatedResults.get(ChosenAction.GET_NON_EXISTING).p90Latency)
              .get_non_existing_p99_latency(
                  calculatedResults.get(ChosenAction.GET_NON_EXISTING).p99Latency)
              .get_non_existing_std_dev(
                  calculatedResults.get(ChosenAction.GET_NON_EXISTING).stdDeviation)
              .set_average_latency(calculatedResults.get(ChosenAction.SET).avgLatency)
              .set_p50_latency(calculatedResults.get(ChosenAction.SET).p50Latency)
              .set_p90_latency(calculatedResults.get(ChosenAction.SET).p90Latency)
              .set_p99_latency(calculatedResults.get(ChosenAction.SET).p99Latency)
              .set_std_dev(calculatedResults.get(ChosenAction.SET).stdDeviation)
              .build();

      recordings.add(data);

      Files.write(path, gson.toJson(recordings).getBytes());
    } catch (IOException e) {
      System.out.printf(
          "Failed to write measurement results into a file '%s': %s%n",
          resultsFile, e.getMessage());
      e.printStackTrace();
    }
  }

  @Getter
  @Builder
  public static class Measurements {
    private String client;
    private int client_count;
    private int data_size;
    private double get_existing_average_latency;
    private double get_existing_p50_latency;
    private double get_existing_p90_latency;
    private double get_existing_p99_latency;
    private double get_existing_std_dev;
    private double get_non_existing_average_latency;
    private double get_non_existing_p50_latency;
    private double get_non_existing_p90_latency;
    private double get_non_existing_p99_latency;
    private double get_non_existing_std_dev;
    private boolean is_cluster;
    private int num_of_tasks;
    private double set_average_latency;
    private double set_p50_latency;
    private double set_p90_latency;
    private double set_p99_latency;
    private double set_std_dev;
    private double tps;
  }
}
