from abc import ABC, abstractmethod

BASE_ALLOWED_KEYS = {
    "host",
    "port",
    "tls_enabled",
    "user",
    "password",
    "retry",
    "timeout",
}

DEFAULT_HOST = "localhost"
DEFAULT_PORT = 6379


class BaseClientConfiguration(ABC):
    def __init__(self, allowed_keys: set, **kwargs):
        # TODO: Add default values
        self.config_args = {k: v for k, v in kwargs.items() if k in allowed_keys}
        for key, value in self.config_args.items():
            print(key, " : ", value)
        self.__dict__.update(**self.config_args)

    @abstractmethod
    def get_default_config():
        ...


class ClientConfiguration(BaseClientConfiguration):
    CLIENT_SPECIFIC_KEYS = {"db"}

    def __init__(self, **kwargs):
        allowed_keys = ClientConfiguration.CLIENT_SPECIFIC_KEYS.copy().union(
            BASE_ALLOWED_KEYS
        )
        super(ClientConfiguration, self).__init__(allowed_keys=allowed_keys, **kwargs)

    @staticmethod
    def get_default_config():
        return ClientConfiguration(
            host=DEFAULT_HOST, port=DEFAULT_PORT, db=0, tls_enabled=False
        )


class ClusterClientConfiguration(BaseClientConfiguration):
    CLUSTER_SPECIFIC_KEYS = {"read_from_replicas"}

    def __init__(self, **kwargs):
        allowed_keys = ClusterClientConfiguration.CLUSTER_SPECIFIC_KEYS.copy().union(
            BASE_ALLOWED_KEYS
        )
        super(ClusterClientConfiguration, self).__init__(
            allowed_keys=allowed_keys, **kwargs
        )

    @staticmethod
    def get_default_config():
        return ClusterClientConfiguration(
            host=DEFAULT_HOST,
            port=DEFAULT_PORT,
            tls_enabled=False,
            read_from_replicas=False,
        )
