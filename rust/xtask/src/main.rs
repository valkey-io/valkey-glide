// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
//! Project task-runner for glide-rust.
//!
//! Run commands by calling `cargo xtask <command> <args>`.
//! Follows [xtask pattern]: https://github.com/matklad/cargo-xtask

use xtask::{RedisCommandTable, parse};

const HELP_MSG: &str = "\
usage: cargo xtask <command> <args>

commands:
  build-redis-rs-command-table --version <x.y.z>
      Builds the redis-rs command table JSON for the specified version";

// TODO #7058: Remove once baseline updated to redis-rs 1.7.0.
/// The vendored redis-rs fork's command table, relative to `rust/`.
const FORK_COMMAND_TABLE: &str = "../glide-core/redis-rs/redis/src/commands/mod.rs";

/// Redis command table, relative to `rust/`.
const REDIS_COMMAND_TABLE_PATH: &str = "tests/parity/fixtures/redis-command-table.json";

fn main() {
    let mut args = std::env::args().skip(1);
    match args.next().as_deref() {
        Some("build-redis-rs-command-table") => build_redis_rs_command_table(args),

        Some("-h") => println!("{HELP_MSG}"),
        Some("--help") => println!("{HELP_MSG}"),

        Some(other) => fail(2, &format!("unknown command: {other}\n\n{HELP_MSG}")),
        None => fail(2, &format!("missing command\n\n{HELP_MSG}")),
    }
}

/// Runs the `build-redis-rs-command-table` command.
fn build_redis_rs_command_table(args: impl Iterator<Item = String>) {
    let version = parse_build_redis_rs_command_table_args(args);

    // TODO #7058: build the baseline from the *upstream* redis-rs release on
    // GitHub matching `version`, rather than the vendored fork — so the baseline
    // reflects the real targeted release. Sketch:
    //
    //     let url = format!(
    //         "https://raw.githubusercontent.com/redis-rs/redis-rs/\
    //          redis-{version}/redis/src/commands/mod.rs"
    //     );
    //     let src = ureq::get(&url).call()?.into_string()?; // needs an HTTP client dep
    //     let table = RedisCommandTable { version, methods: parse(&src) };
    //     std::fs::write(BASELINE_OUT, ...)?;
    //
    // Until then, generate from the in-repo redis-rs fork; `--version` must match
    // the fork's version (currently 0.25.2).
    let src = read(FORK_COMMAND_TABLE);

    let table = RedisCommandTable {
        version,
        methods: parse(&src),
    };

    let text = serde_json::to_string_pretty(&table).expect("serialize JSON");
    write(REDIS_COMMAND_TABLE_PATH, &format!("{text}\n"));

    eprintln!(
        "Success! Generated {} methods to `redis-rs` command table:\n'{REDIS_COMMAND_TABLE_PATH}'",
        table.methods.len()
    );
}

/// Parse the version argument for the `build-redis-rs-command-table` command.
/// Fails script if version could not be parsed.
fn parse_build_redis_rs_command_table_args(mut it: impl Iterator<Item = String>) -> String {
    let mut version = None;
    while let Some(flag) = it.next() {
        match flag.as_str() {
            "--version" => {
                version = Some(it.next().unwrap_or_else(|| {
                    fail(2, &format!("missing value for --version\n\n{HELP_MSG}"))
                }));
            }
            other => fail(2, &format!("unknown argument: {other}\n\n{HELP_MSG}")),
        }
    }
    version.unwrap_or_else(|| fail(2, &format!("missing required --version\n\n{HELP_MSG}")))
}

/// Returns the contents of the file at the specified path.
/// Fails script if the file cannot be read.
fn read(path: &str) -> String {
    std::fs::read_to_string(path).unwrap_or_else(|e| fail(1, &format!("cannot read {path}: {e}")))
}

/// Writes the given contents to the file at the specified path.
/// Fails script if the file cannot be written.
fn write(path: &str, contents: &str) {
    std::fs::write(path, contents)
        .unwrap_or_else(|e| fail(1, &format!("cannot write {path}: {e}")));
}

/// Fails script with the given error code and message.
fn fail(code: i32, msg: &str) -> ! {
    eprintln!("{msg}");
    std::process::exit(code);
}
