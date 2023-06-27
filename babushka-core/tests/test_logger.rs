#[cfg(test)]
mod tests {

    use std::fs::{read_dir, read_to_string};

    use logger_core::{init, log_trace};
    use rand::{distributions::Alphanumeric, Rng};

    const FILE_DIRECTORY: &str = "babushka-logs";

    fn generate_random_string(length: usize) -> String {
        rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(length)
            .map(char::from)
            .collect()
    }

    fn get_file_contents(file_name: &str) -> String {
        let files = read_dir(FILE_DIRECTORY).unwrap();
        let file = files
            .into_iter()
            .find(|path| {
                path.as_ref()
                    .unwrap()
                    .path()
                    .file_name()
                    .unwrap()
                    .to_str()
                    .unwrap()
                    .starts_with(file_name)
            })
            .unwrap();
        read_to_string(file.unwrap().path()).unwrap()
    }
    #[test]
    fn log_to_console_works_after_multiple_inits_diff_log_level() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Debug), None);
        init(Some(logger_core::Level::Trace), None);
        log_trace(identifier.clone(), "foo");
    }
    #[test]
    fn log_to_console_works_after_multiple_inits() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), None);
        init(Some(logger_core::Level::Trace), None);
        log_trace(identifier.clone(), "foo");
    }
    #[test]
    fn log_to_file_works_after_multiple_inits() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), Some(identifier.as_str()));
        log_trace(identifier.clone(), "foo");

        init(Some(logger_core::Level::Trace), Some(identifier.as_str()));
        log_trace(identifier.clone(), "foo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contens: {}",
            contents
        );
        assert!(contents.contains("foo"), "Contens: {}", contents);
    }

    #[test]
    fn log_to_console_works_after_file_init() {}

    #[test]
    fn log_to_file_works_after_console_init() {
        let identifier = generate_random_string(10);
        init(Some(logger_core::Level::Trace), None);
        init(Some(logger_core::Level::Trace), Some(identifier.as_str()));
        log_trace(identifier.clone(), "foo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contens: {}",
            contents
        );
        assert!(contents.contains("foo"), "Contens: {}", contents);
    }
}
