/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.examples;

public class ExamplesApp {

    public static void main(String[] args) {
        // You can decide which example to run based on command-line arguments or any other logic
        if (args.length > 0 && args[0].equals("standalone")) {
            StandaloneExample.main(args);
        } else if (args.length > 0 && args[0].equals("cluster")) {
            ClusterExample.main(args);
        } else {
            // Default behavior: Run both examples
            System.out.println("Running StandaloneExample:");
            StandaloneExample.main(args);

            System.out.println("\nRunning ClusterExample:");
            ClusterExample.main(args);
        }
    }
}
