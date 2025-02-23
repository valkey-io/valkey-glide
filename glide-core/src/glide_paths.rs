use logger_core::{log_info, log_warn};
use std::path::{Path, PathBuf};

pub const GLIDE_BASE_FOLDER_NAME: &str = "GLIDE";
pub const SOCKET_FILE_NAME: &str = "glide-socket.sock";

/// For ergonomics: Converts a PathBuf into a string.
fn pathbuf_to_string(pathbuf: &Path) -> String {
    pathbuf.as_os_str().to_string_lossy().to_string()
}

/// For ergonomics: Converts a string into a PathBuf.
fn string_to_pathbuf(string: &str) -> PathBuf {
    PathBuf::from(string)
}

/// GlidePaths struct.
/// Contains the path to the file, primarily in order to manage the socket file.
/// The socket file is used to communicate with the GLIDE server.
pub struct GlidePaths {
    pub glide_file: PathBuf,
}
/// GlidePaths struct implementation.
impl GlidePaths {
    /// Creates a new GlidePaths struct with the given file path.
    pub fn new(glide_file: PathBuf) -> Self {
        GlidePaths { glide_file }
    }

    /// Create a new GlidePaths struct from string paths.
    pub fn from_file_path(glide_file: &str) -> Self {
        GlidePaths {
            glide_file: string_to_pathbuf(glide_file),
        }
    }

    /// Create a new GlidePaths struct from string file name.
    pub fn from_file_name(file_name: &str) -> Self {
        GlidePaths {
            glide_file: Self::glide_sock_dir_path().join(file_name),
        }
    }

    /// Returns the path to the temporary directory where the socket file is stored.
    fn glide_sock_dir_path() -> PathBuf {
        let path = std::env::temp_dir()
            .join(GLIDE_BASE_FOLDER_NAME)
            .join(std::process::id().to_string());
        if !path.exists() {
            std::fs::create_dir_all(&path)
                .map_err(|e| {
                    log_warn(
                        "Socket directory creation",
                        format!("Error creating socket directory: {e}"),
                    )
                })
                .ok();
        }
        path
    }

    /// Returns the path to the socket file as PathBuf.
    pub fn glide_sock_file_path() -> PathBuf {
        Self::glide_sock_dir_path().join(SOCKET_FILE_NAME)
    }

    /// Removes the socket file.
    pub fn remove_glide_file(&self) -> Result<(), std::io::Error> {
        let results = std::fs::remove_file(self.glide_file.clone());
        if let Err(e) = results {
            log_warn(
                "Socket file removal",
                format!("Error removing socket file: {e}"),
            );
            return Err(e);
        }
        log_info("Socket file removal", "Socket file removed successfully.");
        Ok(())
    }

    /// Get the glide_file as a string.
    pub fn glide_file(&self) -> String {
        pathbuf_to_string(&self.glide_file)
    }

    /// Replace the glide_file with a new path.
    pub fn set_glide_file_string(&mut self, glide_file: &str) {
        self.glide_file = string_to_pathbuf(glide_file);
    }
}

impl Default for GlidePaths {
    fn default() -> Self {
        GlidePaths {
            glide_file: Self::glide_sock_file_path(),
        }
    }
}
