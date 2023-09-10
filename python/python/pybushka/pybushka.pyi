from collections.abc import Callable
from enum import Enum
from typing import Optional

from pybushka.constants import TResult

class Level(Enum):
    Error = 0
    Warn = 1
    Info = 2
    Debug = 3
    Trace = 4

    def is_lower(self, level: Level) -> bool: ...

def start_socket_listener_external(init_callback: Callable) -> None: ...
def value_from_pointer(pointer: int) -> TResult: ...
def create_leaked_value(message: str) -> int: ...
def py_init(level: Optional[Level], file_name: Optional[str]) -> Level: ...
def py_log(log_level: Level, log_identifier: str, message: str) -> None: ...
