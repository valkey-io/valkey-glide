import { ClusterScanCursor, Script, ValkeyWrapper } from "@test/common";
import winston from "winston";

// Set up logger
const logger = winston.createLogger({
    level: "info",
    format: winston.format.json(),
    transports: [new winston.transports.Console()],
});

async function main() {
    logger.info("Starting application");

    // Test native API imports
    logger.info("Script class available:", typeof Script);
    logger.info("ClusterScanCursor class available:", typeof ClusterScanCursor);

    // Configure Valkey directly with ClientConfig
    try {
        // This will use the ValkeyWrapper from @test/common which depends on valkey-glide
        const valkeyClient = await ValkeyWrapper.create({
            addresses: [{ host: "localhost", port: 6379 }],
            clientName: "test-client",
        });
        logger.info("Valkey client connected");

        // Store a value
        await valkeyClient.set("test-key", "Hello from consumer app!");
        logger.info("Value stored in Valkey");

        // Retrieve the value
        const value = await valkeyClient.get("test-key");
        logger.info(`Retrieved value: ${value}`);

        valkeyClient.disconnect();
        logger.info("Valkey client disconnected");
    } catch (error) {
        logger.error("Error in application:", error);
        process.exit(1);
    }
}

main()
    .then(() => logger.info("Application completed successfully"))
    .catch((error) => {
        logger.error("Unhandled error:", error);
        process.exit(1);
    });
