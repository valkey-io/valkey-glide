import csv
import json
import os
import sys

output_file_name = sys.argv[-1]
with open(output_file_name, "w+") as output_file:
    writer = csv.writer(output_file)
    base_fields = [
        "language",
        "client",
        "num_of_tasks",
        "data_size",
        "tps",
        "get_p50_latency",
        "get_p90_latency",
        "get_p99_latency",
        "set_p50_latency",
        "set_p90_latency",
        "set_p99_latency",
    ]
    python_fields = base_fields + ["loop"]

    writer.writerow(python_fields)

    for json_file_full_path in sys.argv[1:-1]:
        with open(json_file_full_path) as file:

            json_objects = json.load(file)

            json_file_name = os.path.basename(json_file_full_path)
            language = json_file_name.split("-")[0]
            for json_object in json_objects:
                json_object["language"] = language
                relevant_fields = python_fields if language == "python" else base_fields
                values = [json_object[field] for field in relevant_fields]
                writer.writerow(values)
