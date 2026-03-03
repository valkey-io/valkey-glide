/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import java.io.*;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;

/**
 * A simple library class which helps with loading dynamic libraries stored in the JAR archive.
 * These libraries usually contain implementation of some methods in native code (using JNI - Java
 * Native Interface).
 *
 * @see <a
 *     href="https://raw.githubusercontent.com/adamheinrich/native-utils/master/src/main/java/cz/adamh/utils/NativeUtils.java">https://raw.githubusercontent.com/adamheinrich/native-utils/master/src/main/java/cz/adamh/utils/NativeUtils.java</a>
 * @see <a
 *     href="https://github.com/adamheinrich/native-utils">https://github.com/adamheinrich/native-utils</a>
 */
public class NativeUtils {

    /**
     * The minimum length a prefix for a file has to have according to {@link
     * File#createTempFile(String, String)}}.
     */
    private static final int MIN_PREFIX_LENGTH = 3;

    public static final String NATIVE_FOLDER_PATH_PREFIX = "nativeutils";

    /** Temporary directory which will contain the dynamic library files. */
    private static volatile File temporaryDir;

    /** Track if the Glide library has already been loaded */
    private static volatile boolean glideLibLoaded = false;

    /** Private constructor - this class will never be instanced */
    private NativeUtils() {}

    public static synchronized void loadGlideLib() {
        // Check if already loaded to avoid multiple loads
        if (glideLibLoaded) {
            return;
        }

        try {
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            String libraryPath = detectNativeLibraryPath(osName, osArch);
            loadLibraryFromJar(libraryPath);
            glideLibLoaded = true; // Mark as loaded after successful load
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Glide native library", e);
        }
    }

    /**
     * Normalizes architecture identifiers to canonical forms.
     *
     * @param osArch The architecture string from System.getProperty("os.arch")
     * @return Normalized architecture string ("x86_64" or "aarch64")
     * @throws UnsupportedOperationException If the architecture is not supported
     */
    private static String normalizeArch(String osArch) {
        String arch = osArch.toLowerCase();

        // Normalize x86_64 variants
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        }

        // Normalize aarch64 variants
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }

        throw new UnsupportedOperationException(
                "Unsupported architecture: "
                        + osArch
                        + ". "
                        + "Supported architectures: x86_64 (amd64), aarch64 (arm64)");
    }

    /**
     * Detects whether the Linux system uses musl or glibc. Executes "ldd --version" and checks for
     * "musl" in the output.
     *
     * @return true if musl is detected, false otherwise (defaults to glibc on any error)
     */
    private static boolean isMuslLibc() {
        try {
            Process process = Runtime.getRuntime().exec(new String[] {"ldd", "--version"});

            // Read both stdout and stderr (musl outputs to stderr)
            String output =
                    readInputStream(process.getInputStream()) + readInputStream(process.getErrorStream());

            process.waitFor();

            // Check if output contains "musl" (case-insensitive)
            return output.toLowerCase().contains("musl");

        } catch (Exception e) {
            // Default to glibc on any exception
            return false;
        }
    }

    /**
     * Reads all lines from an InputStream and returns them as a single String.
     *
     * @param inputStream The input stream to read from
     * @return The content as a String with newlines preserved
     * @throws IOException If an I/O error occurs
     */
    private static String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    /**
     * Detects the runtime platform and constructs the native library path.
     *
     * @param osName The operating system name from System.getProperty("os.name")
     * @param osArch The architecture from System.getProperty("os.arch")
     * @return The resource path within the JAR (e.g., "/natives/linux/x86_64/libglide_rs.so")
     * @throws UnsupportedOperationException If the platform is not supported
     */
    private static String detectNativeLibraryPath(String osName, String osArch) {
        String os = osName.toLowerCase();
        String arch = normalizeArch(osArch);

        String osPath;
        String libraryFile;

        if (os.contains("linux")) {
            // Detect musl vs glibc
            if (isMuslLibc()) {
                osPath = "linux_musl";
            } else {
                osPath = "linux";
            }
            libraryFile = "libglide_rs.so";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osPath = "osx";
            libraryFile = "libglide_rs.dylib";
        } else if (os.contains("windows")) {
            osPath = "windows";
            libraryFile = "glide_rs.dll";
        } else {
            throw new UnsupportedOperationException(
                    String.format(
                            "Unsupported platform: OS=%s, Architecture=%s. "
                                    + "Supported platforms: Linux (x86_64, aarch64, glibc/musl), "
                                    + "macOS (x86_64, aarch64), Windows (x86_64)",
                            osName, osArch));
        }

        return String.format("/natives/%s/%s/%s", osPath, arch, libraryFile);
    }

    /**
     * Loads library from current JAR archive
     *
     * <p>The file from JAR is copied into system temporary directory and then loaded. The temporary
     * file is deleted after exiting. Method uses String as filename because the pathname is
     * "abstract", not system-dependent.
     *
     * @param path The path of file inside JAR as absolute path (beginning with '/'), e.g.
     *     /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than
     *     <code>MIN_PREFIX_LENGTH</code> (restriction of {@link File#createTempFile(java.lang.String,
     *     java.lang.String)}).
     * @throws FileNotFoundException If the file could not be found inside the JAR.
     */
    public static void loadLibraryFromJar(String path) throws IOException {

        if (null == path || !path.startsWith("/")) {
            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
        }

        // Obtain filename from path
        String[] parts = path.split("/");
        String filename = (parts.length > 1) ? parts[parts.length - 1] : null;

        // Check if the filename is okay
        if (filename == null || filename.length() < MIN_PREFIX_LENGTH) {
            throw new IllegalArgumentException(
                    "The filename has to be at least " + MIN_PREFIX_LENGTH + " characters long.");
        }

        // Prepare temporary file
        File localTempDir;
        synchronized (NativeUtils.class) {
            if (temporaryDir == null) {
                File createdDir = createTempDirectory(NATIVE_FOLDER_PATH_PREFIX);
                createdDir.deleteOnExit();
                temporaryDir = createdDir;
            }
            localTempDir = temporaryDir;
        }

        File temp = new File(localTempDir, filename);

        try (InputStream is = NativeUtils.class.getResourceAsStream(path)) {
            if (is == null) {
                cleanupTempFile(temp);
                throw new FileNotFoundException(
                        String.format(
                                "Native library not found in JAR at path: %s. "
                                        + "This may indicate a build configuration issue or missing platform support.",
                                path));
            }
            Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            cleanupTempFile(temp);
            throw new IOException(
                    String.format(
                            "Failed to extract native library to temporary location: %s", temp.getAbsolutePath()),
                    e);
        }

        try {
            System.load(temp.getAbsolutePath());
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException(
                    String.format(
                            "Failed to load native library from: %s. System error: %s",
                            temp.getAbsolutePath(), e.getMessage()),
                    e);
        } finally {
            if (isPosixCompliant()) {
                // Assume POSIX compliant file system, can be deleted after loading
                cleanupTempFile(temp);
            } else {
                // Assume non-POSIX, and don't delete until last file descriptor closed
                temp.deleteOnExit();
            }
        }
    }

    private static void cleanupTempFile(File temp) {
        if (!temp.delete() && temp.exists()) {
            temp.deleteOnExit();
        }
    }

    private static boolean isPosixCompliant() {
        try {
            return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        } catch (FileSystemNotFoundException | ProviderNotFoundException | SecurityException e) {
            return false;
        }
    }

    private static File createTempDirectory(String prefix) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        File generatedDir = new File(tempDir, prefix + System.nanoTime());

        if (!generatedDir.mkdir())
            throw new IOException("Failed to create temp directory " + generatedDir.getName());

        return generatedDir;
    }
}
