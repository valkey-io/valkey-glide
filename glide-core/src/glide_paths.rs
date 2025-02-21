use logger_core::log_warn;
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

pub struct GlidePaths {
    pub glide_temp_dir: PathBuf,
    pub glide_file: PathBuf,
}
/// GlidePaths is a struct to manage paths.
/// It contains the glide_temp_dir and glide_file.
/// It is used, among other path management tasks, to create the socket file and directory.
///
/// # Fields
/// * glide_temp_dir - A PathBuf that represents the temporary directory.
/// * glide_file - A PathBuf that represents the file.
///
/// Functions:
/// Static Functions:
/// * glide_sock_temp_dir - Returns the path to the temporary directory where the socket file is stored.
/// * glide_socket_path - Returns the path to the socket file.
/// * glide_sock_file_path - Returns the path to the socket file as PathBuf.
/// * remove_socket_file - Removes the socket file.
/// * remove_socket_dir - Removes the socket directory.
///
/// Methods:
/// * new - Creates a new GlidePaths struct with the given glide_temp_dir and glide_file.
/// * from_strings - Create a new GlidePaths struct from string paths.
/// * glide_dir_into_string - Converts the glide_temp_dir into a string.
/// * glide_file_to_string - Converts the glide_file into a string.
/// * Default - Creates a default GlidePaths struct.
impl GlidePaths {
    /// Creates a new GlidePaths struct with the given glide_temp_dir and glide_file.
    /// # Arguments
    /// * `glide_temp_dir` - A PathBuf that represents the temporary directory.
    /// * `glide_file` - A PathBuf that represents the file.
    /// # Returns
    /// * A GlidePaths struct.
    /// # Example
    /// ```
    /// use std::path::PathBuf;
    /// use glide_core::glide_paths::GlidePaths;
    /// let glide_temp_dir = PathBuf::from("/tmp/GLIDE/1234");
    /// let glide_file = PathBuf::from("/tmp/GLIDE/1234/glide-socket.sock");
    /// let glide_paths = GlidePaths::new(glide_temp_dir, glide_file);
    /// ```
    pub fn new(glide_temp_dir: PathBuf, glide_file: PathBuf) -> Self {
        GlidePaths {
            glide_temp_dir,
            glide_file,
        }
    }

    /// Create a new GlidePaths struct from string paths.
    /// # Arguments
    /// * `glide_temp_dir` - A string that represents the temporary directory.
    /// * `glide_file` - A string that represents the file.
    /// # Returns
    /// * A GlidePaths struct.
    /// # Example
    /// ```
    /// use glide_core::glide_paths::GlidePaths;
    /// let glide_temp_dir = "/tmp/GLIDE/1234";
    /// let glide_file = "/tmp/GLIDE/1234/glide-socket.sock";
    /// let glide_paths = GlidePaths::from_strings(glide_temp_dir, glide_file);
    /// ```
    pub fn from_strings(glide_temp_dir: &str, glide_file: &str) -> Self {
        GlidePaths {
            glide_temp_dir: string_to_pathbuf(glide_temp_dir),
            glide_file: string_to_pathbuf(glide_file),
        }
    }

    /// Returns the path to the temporary directory where the socket file is stored.
    pub fn glide_sock_temp_dir() -> PathBuf {
        std::env::temp_dir()
            .join(GLIDE_BASE_FOLDER_NAME)
            .join(std::process::id().to_string())
    }

    /// Returns the path to the socket file.
    pub fn glide_socket_path() -> String {
        pathbuf_to_string(&Self::glide_sock_file_path())
    }

    /// Returns the path to the socket file as PathBuf.
    pub fn glide_sock_file_path() -> PathBuf {
        Self::glide_sock_temp_dir().join(SOCKET_FILE_NAME)
    }

    /// Removes the socket file.
    pub fn remove_socket_file() -> Result<(), std::io::Error> {
        let results = std::fs::remove_file(Self::glide_sock_file_path());
        if let Err(e) = results {
            log_warn(
                "Socket file removal",
                format!("Error removing socket file: {e}"),
            );
            return Err(e);
        }
        Ok(())
    }

    /// Removes the socket directory.
    pub fn remove_socket_dir() -> Result<(), std::io::Error> {
        let results = std::fs::remove_dir_all(Self::glide_sock_temp_dir());
        if let Err(e) = results {
            log_warn(
                "Socket directory removal",
                format!("Error removing socket directory: {e}"),
            );
            return Err(e);
        }
        Ok(())
    }

    /// Ergonomics - Converts the glide_temp_dir into a string.
    pub fn glide_dir_into_string(self) -> String {
        pathbuf_to_string(&self.glide_temp_dir)
    }

    /// Ergonomics - Converts the glide_file into a string.
    pub fn glide_file_to_string(&self) -> String {
        pathbuf_to_string(&self.glide_file)
    }
}

impl Default for GlidePaths {
    fn default() -> Self {
        GlidePaths {
            glide_temp_dir: Self::glide_sock_temp_dir(),
            glide_file: Self::glide_sock_file_path(),
        }
    }
}
