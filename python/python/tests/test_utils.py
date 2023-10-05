from pybushka.logger import Level, Logger
from tests.conftest import DEFAULT_TEST_LOG_LEVEL


class TestLogger:
    def test_init_logger(self):
        # The logger is already configured in the conftest file, so calling init again shouldn't modify the log level
        Logger.init(Level.ERROR)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL.value

    def test_set_logger_config(self):
        Logger.set_logger_config(Level.INFO)
        assert Logger.logger_level == Level.INFO.value
        # Revert to the tests default log level
        Logger.set_logger_config(DEFAULT_TEST_LOG_LEVEL)
        assert Logger.logger_level == DEFAULT_TEST_LOG_LEVEL.value
