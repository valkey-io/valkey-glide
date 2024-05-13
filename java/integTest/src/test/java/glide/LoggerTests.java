package glide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.logging.Logger;
import org.junit.jupiter.api.Test;

public class LoggerTests {

    private final Logger.Level DEFAULT_TEST_LOG_LEVEL = Logger.Level.WARN;

    @Test
    public void init_logger() {
        Logger.init(DEFAULT_TEST_LOG_LEVEL);
        assertEquals(DEFAULT_TEST_LOG_LEVEL, Logger.getLoggerLevel());
        // The logger is already configured, so calling init again shouldn't modify the log level
        Logger.init(Logger.Level.ERROR);
        assertEquals(DEFAULT_TEST_LOG_LEVEL, Logger.getLoggerLevel());
    }

    @Test
    public void set_logger_config() {
        Logger.setLoggerConfig(Logger.Level.INFO);
        assertEquals(Logger.Level.INFO, Logger.getLoggerLevel());
        // Revert to the default test log level
        Logger.setLoggerConfig(DEFAULT_TEST_LOG_LEVEL);
        assertEquals(DEFAULT_TEST_LOG_LEVEL, Logger.getLoggerLevel());
    }
}
