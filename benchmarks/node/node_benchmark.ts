import percentile from "percentile";
import { createClient } from "redis";
import { AsyncClient } from "../../node/index";

const HOST = "localhost";
const PORT = 6379;
const ADDRESS = `redis://${HOST}:${PORT}`;
const PROB_GET = 0.8;
const SIZE_GET_KEYSPACE = 3750000; // 3.75 million
const SIZE_SET_KEYSPACE = 3000000; // 3 million

let counter = 0;
const running_tasks: Promise<void>[] = [];
const bench_str_results: string[] = [];
const bench_json_results: {}[] = [];
const get_latency: Record<string, number[]> = {};
const set_latency: Record<string, number[]> = {};

interface IAsyncClient {
    set: (key: string, value: string) => Promise<any>;
    get: (key: string) => Promise<string | null>;
}

function generate_value(size: number): string {
    return "0".repeat(size);
}

function generate_key_set(): string {
    return (Math.floor(Math.random() * SIZE_SET_KEYSPACE) + 1).toString();
}
function generate_key_get(): string {
    return Math.floor(Math.random() * SIZE_GET_KEYSPACE + 1).toString();
}

function should_get(): boolean {
    return Math.random() < PROB_GET;
}

function calculate_latency(latency_list: number[], percentile_point: number) {
    const percentile_calculation = percentile(percentile_point, latency_list);
    const percentile_value = Array.isArray(percentile_calculation)
        ? percentile_calculation[0]
        : percentile_calculation;
    return Math.round(percentile_value * 100.0) / 100.0; // round to 2 decimal points
}

function print_results() {
    bench_str_results.sort();
    for (const res of bench_str_results) {
        console.log(res);
    }
}

async function redis_benchmark(
    client: IAsyncClient,
    client_name: string,
    total_commands: number,
    data: string
) {
    while (counter < total_commands) {
        let use_get = should_get();
        let tic = process.hrtime();
        if (use_get) {
            await client.get(generate_key_get());
        } else {
            await client.set(generate_key_set(), data);
        }
        let toc = process.hrtime(tic);
        const latency_list = (use_get ? get_latency : set_latency)[client_name];
        latency_list.push(toc[0] * 1000 + toc[1] / 1000000);
        counter += 1;
    }
}

async function create_bench_tasks(
    client: IAsyncClient,
    client_name: string,
    total_commands: number,
    num_of_concurrent_tasks: number,
    data: string
) {
    counter = 0;
    get_latency[client_name] = [];
    set_latency[client_name] = [];
    let tic = process.hrtime();
    for (let i = 0; i < num_of_concurrent_tasks; i++) {
        running_tasks.push(
            redis_benchmark(client, client_name, total_commands, data)
        );
    }
    await Promise.all(running_tasks);
    let toc = process.hrtime(tic);
    return toc[0] + toc[1] / 1000000000;
}

async function run_client(
    client: IAsyncClient,
    client_name: string,
    total_commands: number,
    num_of_concurrent_tasks: number,
    data_size: number,
    data: string
) {
    const time = await create_bench_tasks(
        client,
        client_name,
        total_commands,
        num_of_concurrent_tasks,
        data
    );
    const tps = Math.round(counter / time);
    const get_50 = calculate_latency(get_latency[client_name], 50);
    const get_90 = calculate_latency(get_latency[client_name], 90);
    const get_99 = calculate_latency(get_latency[client_name], 99);
    const set_50 = calculate_latency(set_latency[client_name], 50);
    const set_90 = calculate_latency(set_latency[client_name], 90);
    const set_99 = calculate_latency(set_latency[client_name], 99);
    const json_res = {
        client: client_name,
        num_of_tasks: num_of_concurrent_tasks,
        data_size,
        tps,
        latency: {
            get_50,
            get_90,
            get_99,
            set_50,
            set_90,
            set_99,
        },
    };
    bench_json_results.push(JSON.stringify(json_res));
    bench_str_results.push(
        `client: ${client_name}, event_loop: node, concurrent_tasks: ${num_of_concurrent_tasks}, data_size: ${data_size}, TPS: ${tps}, get_p50: ${get_50}, get_p90: ${get_90}, get_p99: ${get_99}, set_p50: ${set_50}, set_p90: ${set_90}, set_p99: ${set_99}`
    );
}

async function main(
    total_commands: number,
    num_of_concurrent_tasks: number,
    data_size: number
) {
    const data = generate_value(data_size);
    const babushka_client = await AsyncClient.CreateConnection(ADDRESS);
    await run_client(
        babushka_client,
        "babushka",
        total_commands,
        num_of_concurrent_tasks,
        data_size,
        data
    );

    const node_redis_client = createClient({ url: ADDRESS });
    await node_redis_client.connect();
    await run_client(
        node_redis_client,
        "node_redis",
        total_commands,
        num_of_concurrent_tasks,
        data_size,
        data
    );
}

Promise.resolve() // just added to clean the indentation of the rest of the calls
    .then(() => main(100000, 10, 100))
    .then(() => main(1000000, 100, 100))
    .then(() => main(100000, 10, 4000))
    .then(() => main(1000000, 100, 4000))
    .then(() => print_results())
    .then(() => {
        process.exit(0);
    });
