use std::collections::HashMap;

use super::TestContext;

#[macro_export]
macro_rules! assert_args {
    ($value:expr, $($args:expr),+) => {
        let args = $value.to_redis_args();
        let strings: Vec<_> = args.iter()
                                .map(|a| std::str::from_utf8(a.as_ref()).unwrap())
                                .collect();
        assert_eq!(strings, vec![$($args),+]);
    }
}

pub fn parse_client_info(client_info: &str) -> HashMap<String, String> {
    let mut res = HashMap::new();

    for line in client_info.split(' ') {
        let this_attr: Vec<&str> = line.split('=').collect();
        res.insert(this_attr[0].to_string(), this_attr[1].to_string());
    }

    res
}

pub fn version_greater_or_equal(ctx: &TestContext, version: &str) -> bool {
    // Parse the provided version string into major, minor, and patch
    let parsed_version: Vec<&str> = version.split('.').collect();
    if parsed_version.len() != 3 {
        panic!("Version string must be in the format 'major.minor.patch'");
    }

    let major: u16 = parsed_version[0].parse().expect("Failed to parse version");
    let minor: u16 = parsed_version[1].parse().expect("Failed to parse version");
    let patch: u16 = parsed_version[2].parse().expect("Failed to parse version");

    // Get the server version
    let server_version = ctx.get_version();

    // Compare server version with the specified version
    (server_version.0 > major)
        || (server_version.0 == major && server_version.1 > minor)
        || (server_version.0 == major && server_version.1 == minor && server_version.2 >= patch)
}
