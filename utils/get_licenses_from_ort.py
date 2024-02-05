import json
import os

"""
"""


class OrtResults:
    def __init__(self, name: str, ort_results_folder: str) -> None:
        """
        Args:
            name (str): the language name.
            ort_results_folder (str): The path to the ort results folder relative to the root glide-for-redis folder.
        """
        script_path = os.path.dirname(os.path.realpath(__file__))
        folder_path = f"{script_path}/../{ort_results_folder}"
        self.analyzer_result_file = f"{folder_path}/analyzer-result.json"
        self.notice_file = f"{folder_path}/NOTICE_DEFAULT"
        self.name = name


ort_results_per_lang = [
    OrtResults("Python", "python/ort_results"),
    OrtResults("Node", "node/ort_results"),
    OrtResults("Rust", "glide-core/ort_results"),
]

licenses_set = set()

for ort_files in ort_results_per_lang:
    print(f"Starting {ort_files.name}")
    with open(ort_files.analyzer_result_file, "r") as ort_results, open(
        ort_files.notice_file, "r"
    ) as notice_file:
        json_file = json.load(ort_results)
        notice_file = notice_file.read()
        for package in json_file["analyzer"]["result"]["packages"]:
            package_name = package["id"].split(":")[2]
            if package_name not in notice_file:
                # skip packages not in the final report
                continue
            try:
                for license in package["declared_licenses_processed"].values():
                    if isinstance(license, list) or isinstance(license, dict):
                        license = (
                            license.values() if isinstance(license, dict) else license
                        )
                        for inner_license in license:
                            licenses_set.add(inner_license)
                    else:
                        licenses_set.add(license)
            except Exception:
                print(f"Received error for package {package}\n Found license={license}")
                raise

print("\n#### Found Licenses #####\n")
licenses_set = sorted(licenses_set)
for license in licenses_set:
    print(f"{license}")
