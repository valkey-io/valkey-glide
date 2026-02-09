package com.company.glide.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ValkeyLambda implements RequestHandler<Map<String, String>, Map<String, String>> {

    private static GlideClusterClient valkey;

    static {
        try {
            System.out.println("Initializing Valkey client...");
            String host = System.getenv("VALKEY_HOST") != null ? System.getenv("VALKEY_HOST") : "localhost";
            int port = System.getenv("VALKEY_PORT") != null ? Integer.parseInt(System.getenv("VALKEY_PORT")) : 7001;
            System.out.println("Using Valkey host: " + host + ":" + port);
            GlideClusterClientConfiguration config = GlideClusterClientConfiguration.builder()
                    .address(NodeAddress.builder()
                            .host(host)
                            .port(port)
                            .build())
                    .useTLS(false)
                    .requestTimeout(30000)
                    .readFrom(glide.api.models.configuration.ReadFrom.PRIMARY)
                    .build();
            valkey = GlideClusterClient.createClient(config).get();
            System.out.println("Valkey client initialized successfully");

            try {
                valkey.ping();
                System.out.println("Ping successful");
            } catch (Exception pingException) {
                System.err.println("Failed to ping Valkey: " + pingException.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Failed to initialize Valkey connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        if (valkey == null) {
            response.put("statusCode", "500");
            response.put("message", "Valkey client not initialized");
            return response;
        }
        try {
            String testKey = "test:connection:" + System.currentTimeMillis();
            int testSize = 16386;
            String testValue = "c".repeat(testSize);

            System.out.println("Setting key with value size: " + testSize);
            long startSet = System.currentTimeMillis();
            valkey.set(testKey, testValue).get();
            System.out.println("Set completed in " + (System.currentTimeMillis() - startSet) + "ms");
            
            System.out.println("Getting key...");
            long startGet = System.currentTimeMillis();
            CompletableFuture<String> getFuture = valkey.get(testKey);
            String retrievedValue;
            try {
                retrievedValue = getFuture.get(20, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                System.err.println("GET operation timed out after 20 seconds");
                response.put("statusCode", "500");
                response.put("message", "GET timeout - likely Glide bug with values > 16KB");
                return response;
            }
            System.out.println("Get completed in " + (System.currentTimeMillis() - startGet) + "ms");

            if (testValue.equals(retrievedValue)) {
                response.put("statusCode", "200");
                response.put("message", "Valkey operations successful. Length: " + retrievedValue.length());
            } else {
                response.put("statusCode", "500");
                response.put("message", "Values don't match");
            }

            valkey.del(new String[]{testKey}).get();
        } catch (ExecutionException | InterruptedException e) {
            System.err.println("Exception occurred: " + e.getMessage());
            e.printStackTrace();
            response.put("statusCode", "500");
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }
}
