from typing import Any, Dict, Mapping, Optional, Union
from glide.constants import TClusterDecodedResponse, TClusterResponse


def convert_bytes_to_string_dict(
    byte_string_dict: Optional[Union[Mapping[bytes, Any], Dict[bytes, Any]]]
) -> Optional[Dict[str, Any]]:
    """
    Recursively convert the keys and values of a dictionary from byte strings to regular strings,
    handling nested dictionaries of any depth.

    Args:
        byte_string_dict (Optional[Union[Mapping[bytes, Any], Dict[bytes, Any]]]):
        A dictionary where keys and values can be byte strings or nested dictionaries.

    Returns:
        Optional[Dict[str, Any]]:
        A dictionary with keys and values converted to regular strings, or None if input is None.
    """
    if byte_string_dict is None:
        return None

    def convert(item: Any) -> Any:
        if isinstance(item, dict):
            return {convert(key): convert(value) for key, value in item.items()}
        elif isinstance(item, bytes):
            return item.decode("utf-8")
        elif isinstance(item, list):
            return [convert(elem) for elem in item]
        else:
            return item

    return convert(byte_string_dict)


def convert_bytes_to_string_cluster_response(
    cluster_response: Optional[TClusterResponse],
) -> Optional[TClusterDecodedResponse]:
    """
    Convert a TClusterResponse type with byte strings to a TClusterResponse type with regular strings,
    handling nested dictionaries of any depth.

    Args:
        cluster_response (Optional[Union[T, Dict[bytes, T]]]):
        A cluster response which can be of type T or a dictionary with byte string keys and values.

    Returns:
        Optional[TClusterResponse]:
        A cluster response with all byte strings converted to regular strings.
    """
    if cluster_response is None:
        return None

    if isinstance(cluster_response, dict):
        return convert_bytes_to_string_dict(cluster_response)

    return cluster_response
