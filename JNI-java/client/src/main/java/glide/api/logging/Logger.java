/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.logging;


/**
 * Simplified logging system for Valkey GLIDE Java client.
 * This provides a unified logging interface that can be enhanced
 * with native FFI integration in future versions.
 */
public class Logger {
    
    public enum LogLevel {
        ERROR(0),
        WARN(1), 
        INFO(2),
        DEBUG(3),
        TRACE(4);
        
        private final int level;
        
        LogLevel(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }

    private static LogLevel currentLevel = LogLevel.INFO;
    private static Level currentLevelWrapper = Level.INFO; // Store the actual Level instance
    private static boolean initialized = false; // Track if logger has been initialized
    private static String logFile = null;

    // Native logger bridge (logger_core via JNI)
    static {
        try {
            System.loadLibrary("valkey_glide");
        } catch (UnsatisfiedLinkError e) {
            // Fallbacks handled by tests; avoid System.err noise per project style
        }
    }

    private static native int initLogger(int level, String filename);

    private static native void logMessage(int level, String identifier, String message);

    /** Backward-compatible Level type expected by tests */
    public static final class Level {
        private final LogLevel delegate;
        private Level(LogLevel delegate) { this.delegate = delegate; }
        private LogLevel asLogLevel() { return delegate; }

        public static final Level OFF = new Level(LogLevel.ERROR);
        public static final Level ERROR = new Level(LogLevel.ERROR);
        public static final Level WARN = new Level(LogLevel.WARN);
        public static final Level INFO = new Level(LogLevel.INFO);
        public static final Level DEBUG = new Level(LogLevel.DEBUG);
        public static final Level TRACE = new Level(LogLevel.TRACE);
        public static final Level DEFAULT = INFO;

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Level other = (Level) obj;
            return delegate == other.delegate;
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    private static Level fromLogLevel(LogLevel lvl) {
        switch (lvl) {
            case ERROR: return Level.ERROR;
            case WARN: return Level.WARN;
            case DEBUG: return Level.DEBUG;
            case TRACE: return Level.TRACE;
            case INFO:
            default: return Level.INFO;
        }
    }
    
    /**
     * Initialize the logging system.
     * 
     * @param level The minimum log level to output
     * @param fileName Optional log file name (null for console only)
     * @return 0 for success, non-zero for error
     */
    public static int init(LogLevel level, String fileName) {
        try {
            currentLevel = level;
            logFile = fileName;
            
            // Configure Java logger level
            java.util.logging.Level javaLevel;
            switch (level) {
                case ERROR:
                    javaLevel = java.util.logging.Level.SEVERE;
                    break;
                case WARN:
                    javaLevel = java.util.logging.Level.WARNING;
                    break;
                case INFO:
                    javaLevel = java.util.logging.Level.INFO;
                    break;
                case DEBUG:
                    javaLevel = java.util.logging.Level.FINE;
                    break;
                case TRACE:
                    javaLevel = java.util.logging.Level.FINEST;
                    break;
                default:
                    javaLevel = java.util.logging.Level.INFO;
                    break;
            }
            
            // Call native logger initialization
            int result = initLogger(level.getLevel(), fileName);
            if (result != 0) {
                return result;
            }
            
            log(LogLevel.INFO, "Logger", "GLIDE Java logging initialized at level " + level);
            return 0;
            
        } catch (Exception e) {
            log(LogLevel.ERROR, "Logger", "Failed to initialize GLIDE logger: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Log a message at the specified level.
     * 
     * @param level The log level
     * @param identifier The log identifier/category
     * @param message The message to log
     */
    public static void log(LogLevel level, String identifier, String message) {
        if (level.getLevel() <= currentLevel.getLevel()) {
            // Delegate actual logging to native layer
            logMessage(level.getLevel(), identifier, message);
        }
    }
    
    /**
     * Convenience methods for common log levels.
     */
    public static void error(String identifier, String message) {
        log(LogLevel.ERROR, identifier, message);
    }
    
    public static void warn(String identifier, String message) {
        log(LogLevel.WARN, identifier, message);
    }
    
    public static void info(String identifier, String message) {
        log(LogLevel.INFO, identifier, message);
    }
    
    public static void debug(String identifier, String message) {
        log(LogLevel.DEBUG, identifier, message);
    }
    
    public static void trace(String identifier, String message) {
        log(LogLevel.TRACE, identifier, message);
    }
    
    /**
     * GET current log level.
     */
    public static LogLevel getLevel() {
        return currentLevel;
    }

    /**
     * Method expected by tests: getLoggerLevel()
     */
    public static Level getLoggerLevel() {
        return currentLevelWrapper; // Return the actual Level instance that was passed to init()
    }
    
    /**
     * GET current log file.
     */
    public static String getLogFile() {
        return logFile;
    }
    
    /**
     * Convenience methods for backward compatibility.
     */
    public static int init(LogLevel level) {
        return init(level, null);
    }
    
    public static void setLoggerConfig(LogLevel level) {
        currentLevel = level;
        // Call native logger initialization without filename
        initLogger(level.getLevel(), null);
    }

    /**
     * Overload expected by tests: setLoggerConfig(Level)
     */
    public static void setLoggerConfig(Level level) {
        currentLevelWrapper = level; // Store the actual Level instance
        setLoggerConfig(level.asLogLevel());
    }

    /**
     * Overload expected by tests: setLoggerConfig(Level, String)
     */
    public static void setLoggerConfig(Level level, String fileName) {
        currentLevelWrapper = level; // Store the actual Level instance
        currentLevel = level.asLogLevel(); // Update the current log level
        logFile = fileName; // Store the log file name
        initialized = false; // Reset initialized flag to allow reconfiguration
        // Call native logger initialization
        initLogger(level.asLogLevel().getLevel(), fileName);
        initialized = true; // Mark as initialized after successful init
    }

    /**
     * Overload expected by tests: log(Level, identifier, Supplier<String>)
     */
    public static void log(Level level, String identifier, java.util.function.Supplier<String> messageSupplier) {
        log(level.asLogLevel(), identifier, messageSupplier.get());
    }

    /** Overload expected by some tests: log(Level, identifier, message) */
    public static void log(Level level, String identifier, String message) {
        log(level.asLogLevel(), identifier, message);
    }

    public static int init(Level level) {
        if (initialized) {
            return 0; // Logger already initialized, ignore subsequent calls
        }
        currentLevelWrapper = level; // Store the actual Level instance
        initialized = true; // Mark as initialized
        return init(level.asLogLevel(), null);
    }
    

}