from typing import List, Union


class JsonPath:
    def __init__(self, path: Union[str, List[str]], json_path: bool = True):
        self.json_path = json_path
        if isinstance(path, str):
            if path.startswith("$."):
                self.components = ["$"] + path[2:].split(".")
            else:
                self.components = path.split(".")
        elif isinstance(path, list):
            self.components = path
        else:
            raise TypeError("Path must be a string or a list of strings")

    def __str__(self) -> str:
        if self.json_path and self.components[0] != "$":
            self.components = ["$"] + self.components
        return ".".join(self.components)

    def append(self, component: str) -> None:
        self.components.append(component)

    def extend(self, path: Union[str, List[str]]) -> None:
        if isinstance(path, str):
            self.components.extend(path.split("."))
        elif isinstance(path, list):
            self.components.extend(path)
        else:
            raise TypeError("Path must be a string or a list of strings")
