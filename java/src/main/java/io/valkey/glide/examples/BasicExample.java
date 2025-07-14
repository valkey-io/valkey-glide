package io.valkey.glide.examples;

import io.valkey.glide.jni.commands.CommandType;

/**
 * A basic example demonstrating the core JNI functionality
 */
public class BasicExample {

    public static void main(String[] args) {
        System.out.println("Valkey GLIDE JNI Example");

        // Show that we can access the CommandType enum
        System.out.println("Available command types:");
        for (CommandType type : CommandType.values()) {
            System.out.println("  - " + type.name());
        }

        System.out.println("JNI implementation is ready!");
    }
}
