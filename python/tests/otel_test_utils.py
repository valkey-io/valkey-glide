# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

"""
Shared OpenTelemetry test utilities for both async and sync clients.
"""

import json
import os
from typing import Dict, List, Optional, Tuple


def read_and_parse_span_file(path: str) -> Tuple[str, List[Dict], List[str]]:
    """
    Reads and parses a span file, extracting span data and names.
    Args:
        path: The path to the span file

    Returns:
        A tuple containing the raw span data, array of spans, and array of
        span names

    Raises:
        Exception: If the file cannot be read or parsed
    """
    try:
        with open(path, "r") as f:
            span_data = f.read()
    except Exception as e:
        raise Exception(f"Failed to read or validate file: {str(e)}")

    spans = [line for line in span_data.split("\n") if line.strip()]

    # Check that we have spans
    if not spans:
        raise Exception("No spans found in the span file")

    # Parse and extract span names
    span_objects = []
    span_names = []
    for line in spans:
        try:
            span = json.loads(line)
            span_objects.append(span)
            span_names.append(span.get("name"))
        except json.JSONDecodeError:
            continue

    return span_data, span_objects, [name for name in span_names if name]


def check_span_counts(
    span_names: List[str], expected_span_counts: Dict[str, int]
) -> List[str]:
    """Check if span counts meet expectations."""
    insufficient_counts = []
    for span_name, expected_count in expected_span_counts.items():
        actual_count = span_names.count(span_name)
        if actual_count < expected_count:
            insufficient_counts.append(
                f"{span_name} (expected: {expected_count}, actual: {actual_count})"
            )
    return insufficient_counts


def check_spans_ready(
    span_names: List[str],
    expected_span_names: List[str],
    expected_span_counts: Optional[Dict[str, int]] = None,
) -> bool:
    """Check if all expected spans are ready."""
    missing_spans = [name for name in expected_span_names if name not in span_names]

    if expected_span_counts:
        insufficient_counts = check_span_counts(span_names, expected_span_counts)
        return not missing_spans and not insufficient_counts
    else:
        return not missing_spans


def build_timeout_error(
    span_file_path: str,
    expected_span_names: List[str],
    expected_span_counts: Optional[Dict[str, int]] = None,
) -> Exception:
    """Build appropriate timeout error message."""
    if not os.path.exists(span_file_path):
        return Exception(
            f"Timeout waiting for spans. Span file {span_file_path} does not exist"
        )

    try:
        _, _, span_names = read_and_parse_span_file(span_file_path)
        if expected_span_counts:
            count_info = {}
            for span_name in expected_span_names:
                count_info[span_name] = span_names.count(span_name)
            return Exception(
                f"Timeout waiting for spans. Expected {expected_span_names} "
                f"with counts {expected_span_counts}, but found {count_info}"
            )
        else:
            return Exception(
                f"Timeout waiting for spans. Expected {expected_span_names}, "
                f"but found {span_names}"
            )
    except Exception as e:
        return Exception(
            f"Timeout waiting for spans. File exists but couldn't be read: {str(e)}"
        )
