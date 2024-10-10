/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */


export function runJsonTests(config: {
    init: () => Promise<{ client: Client }>;
    close: (testSucceeded: boolean) => void;
    timeout?: number;
}) {
    const runTest = async (test: (client: Client) => Promise<void>) => {
        const { client } = await config.init();
        let testSucceeded = false;

        try {
            await test(client);
            testSucceeded = true;
        } finally {
            config.close(testSucceeded);
        }
    };

    it(
        "test1",
        async () => {
            await runTest((client: Client) => (client));
        },
        config.timeout,
    );
}
