import {
    ClusterScanCursor,
    GlideClient,
    GlideClientConfiguration,
    Script,
} from "@valkey/valkey-glide";
import winston from "winston";

/**
 * A Valkey client wrapper using valkey-glide
 */
export class ValkeyWrapper {
    private client: GlideClient;
    private logger = winston.createLogger({
        level: "info",
        format: winston.format.json(),
        transports: [new winston.transports.Console()],
    });

    // make ctor private and accept an already-created client
    private constructor(client: GlideClient) {
        this.client = client;
    }

    // async factory replaces constructor’s await
    static async create(
        config: GlideClientConfiguration,
    ): Promise<ValkeyWrapper> {
        const client = await GlideClient.createClient(config);
        return new ValkeyWrapper(client);
    }

    async get(key: string): Promise<string | null> {
        try {
            return (await this.client.get(key)) as string | null;
        } catch (error) {
            this.logger.error("Failed to get value from Valkey", error);
            throw error;
        }
    }

    async set(key: string, value: string): Promise<void> {
        try {
            await this.client.set(key, value);
        } catch (error) {
            this.logger.error("Failed to set value in Valkey", error);
            throw error;
        }
    }

    disconnect() {
        this.client.close();
        this.logger.info("Valkey client disconnected");
    }
}

// Export native APIs for testing
export { ClusterScanCursor, Script };

// Export other utilities
export * from "./types";
