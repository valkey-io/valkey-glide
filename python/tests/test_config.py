from src.config import DEFAULT_HOST, DEFAULT_PORT, ClientConfiguration


def test_default_client_config():
    config = ClientConfiguration.get_default_config()
    assert config.config_args["host"] == DEFAULT_HOST
    assert config.config_args["port"] == DEFAULT_PORT
    assert config.config_args["db"] == 0
    assert "read_from_replicas" not in config.config_args
