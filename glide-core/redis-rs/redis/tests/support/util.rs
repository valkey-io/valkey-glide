use std::collections::HashMap;
use versions::Versioning;

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
    // Get the server version
    let (major, minor, patch) = ctx.get_version();
    let server_version = Versioning::new(format!("{major}.{minor}.{patch}")).unwrap();
    let compared_version = Versioning::new(version).unwrap();
    // Compare server version with the specified version
    server_version >= compared_version
}
