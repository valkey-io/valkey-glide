from typing import Dict, Mapping, Optional, Union


def convert_byte_string_dict(
    byte_string_dict: Optional[Union[Mapping[bytes, bytes], Dict[bytes, bytes]]]
) -> Optional[Dict[str, str]]:
    """
    Convert the keys and values of a dictionary from byte strings to regular strings.

    Args:
        byte_string_dict (Optional[Union[Mapping[bytes, bytes], Dict[bytes, bytes]]]):
        A dictionary where both keys and values are byte strings.

    Returns:
        Optional[Dict[str, str]]:
        A dictionary with keys and values converted to regular strings, or None if input is None.

    Notes:
        This function converts both the keys and values from byte strings to regular strings.
        The input dictionary should have byte strings for both keys and values.

    Example:
        byte_string_dict = {b'key1': b'hello', b'key2': b'world'}
        converted_dict = convert_byte_string_dict(byte_string_dict)
        # converted_dict will be {'key1': 'hello', 'key2': 'world'}
    """
    if byte_string_dict is None:
        return None

    return {
        key.decode("utf-8"): value.decode("utf-8")
        for key, value in byte_string_dict.items()
    }
