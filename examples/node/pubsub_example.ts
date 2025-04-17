import {
    GlideClient,
    GlideClientConfiguration,
    GlideClusterClientConfiguration,
    PubSubMsg,
} from "@valkey/valkey-glide";

// Callback function to handle incoming messages
const callback = (message: PubSubMsg, context: any) => {
    console.log(`Received message via callback on channel: ${message.channel},
        pattern: ${message.pattern}, message: ${message.message}\n`);
};

// Context object that will be passed to the callback
const context = "Some context obejct";

// Valkey server addresses
const addresses = [
    {
        host: "localhost",
        port: 6379,
    },
];

// Listening client configuration with callback
const listeningConfigWithCallback: GlideClientConfiguration = {
    addresses: addresses,
    pubsubSubscriptions: {
        channelsAndPatterns: {
            [GlideClusterClientConfiguration.PubSubChannelModes.Exact]: new Set(
                ["channel1"],
            ),
        },
        callback: callback,
        context: context,
    },
};

// Listening client configuration without callback
const listeningConfigWithoutCallback: GlideClientConfiguration = {
    addresses: addresses,
    pubsubSubscriptions: {
        channelsAndPatterns: {
            [GlideClusterClientConfiguration.PubSubChannelModes.Pattern]:
                new Set(["channel*"]),
        },
    },
};

// Publishing client configuration
const publishingConfig = {
    addresses: addresses,
};

async function main() {
    // Create clients
    const listeningClientWithCallback = await GlideClient.createClient(
        listeningConfigWithCallback,
    );
    const listeningClientWithoutCallback = await GlideClient.createClient(
        listeningConfigWithoutCallback,
    );
    const publishingClient = await GlideClient.createClient(publishingConfig);

    // Publish message on 'channel1' channel
    await publishingClient.publish("Test message", "channel1");

    // Sleep for 1 second to allow message to be received,
    // callback will be called for listeningClientWithCallback
    await new Promise((resolve) => setTimeout(resolve, 1000));

    // Explicit message retrieval for listeningClientWithoutCallback
    const message = await listeningClientWithoutCallback.getPubSubMessage();
    console.log(`Received message via listeningClientWithoutCallback on channel: ${message.channel},
        pattern: ${message.pattern}, message: ${message.message}\n`);

    // Close clients
    await listeningClientWithCallback.close();
    await listeningClientWithoutCallback.close();
    await publishingClient.close();
}

main().catch(console.error);
