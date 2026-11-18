/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */
use test_env_helpers::*;

#[cfg(test)]
#[after_all]
#[before_all]
mod tests {
    use glide_logger::{init, log_debug, log_error, log_trace};
    use rand::{Rng, distributions::Alphanumeric};
    use std::{
        fs::{read_dir, read_to_string, remove_dir_all},
        path::Path,
    };
    const FILE_DIRECTORY: &str = "glide-logs";

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
    fn init_does_not_create_log_directory_when_console_init() {
        init(Some(glide_logger::Level::Trace), None).unwrap();
        let dir_exists = Path::new(FILE_DIRECTORY).is_dir();
        assert!(!dir_exists);
    }

    #[test]
    fn log_to_console_works_after_multiple_inits_diff_log_level() {
        let identifier = generate_random_string(10);
        init(Some(glide_logger::Level::Trace), None).unwrap();
        init(Some(glide_logger::Level::Debug), None).unwrap();
        // you should see in the console something like '2023-07-07T06:57:54.446236Z DEBUG glide_logger: e49NaJ5J41 - foo'
        log_debug(identifier.clone(), "foo");
        // make sure that something like '2023-07-07T06:57:54.446236Z DEBUG glide_logger: e49NaJ5J41 - boo' does not appear
        log_trace(identifier, "boo");
    }

    #[test]
    fn log_to_console_does_not_create_log_directory_when_console_init() {
        let _ = clean();
        let identifier = generate_random_string(10);
        init(Some(glide_logger::Level::Trace), None).unwrap();
        // you should see in the console something like '2023-07-07T06:57:54.446236Z TRACE glide_logger: e49NaJ5J41 - foo'
        log_trace(identifier.clone(), "foo");
        let dir_exists = Path::new(FILE_DIRECTORY).is_dir();
        assert!(!dir_exists);
    }

    #[test]
    fn log_to_file_works_after_multiple_inits() {
        let identifier = generate_random_string(10);
        init(Some(glide_logger::Level::Trace), Some(identifier.as_str())).unwrap();
        init(Some(glide_logger::Level::Debug), Some(identifier.as_str())).unwrap();
        log_debug(identifier.clone(), "foo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contents: {contents}"
        );
        assert!(contents.contains("foo"), "Contents: {contents}");
    }

    #[test]
    fn log_to_file_works_after_console_init() {
        let identifier = generate_random_string(10);
        init(Some(glide_logger::Level::Trace), None).unwrap();
        init(Some(glide_logger::Level::Trace), Some(identifier.as_str())).unwrap();
        let identifier = generate_random_string(10);
        init(Some(glide_logger::Level::Debug), Some(identifier.as_str())).unwrap();
        log_debug(identifier.clone(), "foo");
        log_trace(identifier.clone(), "boo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contents: {contents}"
        );
        assert!(contents.contains("foo"), "Contents: {contents}");
        assert!(!contents.contains("boo"));
    }

    #[test]
    fn log_to_file_disabled_after_console_init() {
        let identifier = generate_random_string(10);
        init(Some(glide_logger::Level::Trace), Some(identifier.as_str())).unwrap();
        log_trace(identifier.clone(), "foo");
        init(Some(glide_logger::Level::Trace), None).unwrap();
        log_trace(identifier.clone(), "boo");
        let contents = get_file_contents(identifier.as_str());
        assert!(
            contents.contains(identifier.as_str()),
            "Contents: {contents}"
        );
        assert!(contents.contains("foo"), "Contents: {contents}");
        assert!(!contents.contains("boo"), "Contents: {contents}");
    }

    #[test]
    fn init_fails_on_unwritable_log_file_path() {
        let test_dir =
            std::env::temp_dir().join(format!("glide_test_{}", generate_random_string(8)));
        let _ = std::fs::create_dir_all(&test_dir);
        let blocked_file = test_dir.join("regular_file_blocking_dir");
        std::fs::write(&blocked_file, b"content").unwrap();
        // A path whose parent component is a regular file cannot be created as a directory
        let invalid_path = blocked_file.join("app.log");
        let res = init(
            Some(glide_logger::Level::Error),
            Some(invalid_path.to_str().unwrap()),
        );
        assert!(
            res.is_err(),
            "Expected error for unwritable path, got {:?}",
            res
        );
        // Logging afterward should not panic or abort the process
        log_error("test", "this should not panic");
        let _ = std::fs::remove_file(&blocked_file);
        let _ = std::fs::remove_dir_all(&test_dir);
    }

    #[test]
    fn init_fails_on_empty_filename_prefix() {
        let res = init(Some(glide_logger::Level::Error), Some("some_dir/"));
        assert!(res.is_err());
    }

    fn clean() -> Result<(), std::io::Error> {
        remove_dir_all(FILE_DIRECTORY)
    }

    fn after_all() {
        let _ = clean();
    }

    fn before_all() {
        let _ = clean();
    }
}
