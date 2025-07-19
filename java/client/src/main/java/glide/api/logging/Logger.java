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
    
    private static final java.util.logging.Logger javaLogger = 
        java.util.logging.Logger.getLogger("glide");
    private static LogLevel currentLevel = LogLevel.INFO;
    private static String logFile = null;
    
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
            java.util.logging.Level javaLevel = switch (level) {
                case ERROR -> java.util.logging.Level.SEVERE;
                case WARN -> java.util.logging.Level.WARNING;
                case INFO -> java.util.logging.Level.INFO;
                case DEBUG -> java.util.logging.Level.FINE;
                case TRACE -> java.util.logging.Level.FINEST;
            };
            
            javaLogger.setLevel(javaLevel);
            
            // Add file handler if specified
            if (fileName != null) {
                java.util.logging.FileHandler fileHandler = 
                    new java.util.logging.FileHandler(fileName, true);
                fileHandler.setFormatter(new java.util.logging.SimpleFormatter());
                javaLogger.addHandler(fileHandler);
            }
            
            log(LogLevel.INFO, "Logger", "GLIDE Java logging initialized at level " + level);
            return 0;
            
        } catch (Exception e) {
            System.err.println("Failed to initialize GLIDE logger: " + e.getMessage());
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
            String formattedMessage = String.format("[%s] %s: %s", 
                level.name(), identifier, message);
            
            java.util.logging.Level javaLevel = switch (level) {
                case ERROR -> java.util.logging.Level.SEVERE;
                case WARN -> java.util.logging.Level.WARNING;
                case INFO -> java.util.logging.Level.INFO;
                case DEBUG -> java.util.logging.Level.FINE;
                case TRACE -> java.util.logging.Level.FINEST;
            };
            
            javaLogger.log(javaLevel, formattedMessage);
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
        java.util.logging.Level javaLevel = switch (level) {
            case ERROR -> java.util.logging.Level.SEVERE;
            case WARN -> java.util.logging.Level.WARNING;
            case INFO -> java.util.logging.Level.INFO;
            case DEBUG -> java.util.logging.Level.FINE;
            case TRACE -> java.util.logging.Level.FINEST;
        };
        javaLogger.setLevel(javaLevel);
    }
    
    /**
     * Level enum alias for backward compatibility.
     */
    public static class Level {
        public static final LogLevel OFF = LogLevel.ERROR;
        public static final LogLevel ERROR = LogLevel.ERROR;
        public static final LogLevel WARN = LogLevel.WARN;
        public static final LogLevel INFO = LogLevel.INFO;
        public static final LogLevel DEBUG = LogLevel.DEBUG;
        public static final LogLevel TRACE = LogLevel.TRACE;
    }
}