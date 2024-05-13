package glide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import glide.api.logging.Logger;
import org.junit.jupiter.api.Test;

public class LoggerTests {

    private final Logger.Level DEFAULT_TEST_LOG_LEVEL = Logger.Level.WARN;

    @Test
    public void init_logger() {
        Logger.init(DEFAULT_TEST_LOG_LEVEL);
        assertEquals(Logger.getLoggerLevel(), DEFAULT_TEST_LOG_LEVEL);
        // The logger is already configured, so calling init again shouldn't modify the log level
        Logger.init(Logger.Level.ERROR);
        assertEquals(Logger.getLoggerLevel(), DEFAULT_TEST_LOG_LEVEL);
    }

    @Test
    public void set_logger_config() {
        Logger.setLoggerConfig(Logger.Level.INFO);
        assertEquals(Logger.getLoggerLevel(), Logger.Level.INFO);
        // Revert to the default test log level
        Logger.setLoggerConfig(DEFAULT_TEST_LOG_LEVEL);
        assertEquals(Logger.getLoggerLevel(), DEFAULT_TEST_LOG_LEVEL);
    }
}
