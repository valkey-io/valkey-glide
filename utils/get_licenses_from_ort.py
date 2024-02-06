# Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

import json
import os

"""
This script should be used after all specific langauge folders were scanned by the analyzer of the OSS review tool (ORT).
The analyzer tool reports to analyzer-result.json files, which the script expect to be found under the <language_folder>/ort_results path.
The script outputs a set of licenses identified by the analyzer. GLIDE maintainers should review the returned list to ensure that all licenses are approved.
"""

APPROVED_LICENSES = [
    "Unicode-DFS-2016",
    "(Apache-2.0 OR MIT) AND Unicode-DFS-2016",
    "0BSD OR Apache-2.0 OR MIT",
    "Apache-2.0",
    "Apache-2.0 AND (Apache-2.0 OR BSD-2-Clause)",
    "Apache-2.0 AND (Apache-2.0 OR BSD-3-Clause)",
    "Apache-2.0 OR Apache-2.0 WITH LLVM-exception OR MIT",
    "Apache-2.0 OR BSD-2-Clause OR MIT",
    "Apache-2.0 OR BSL-1.0",
    "Apache-2.0 OR ISC OR MIT",
    "Apache-2.0 OR MIT",
    "Apache-2.0 OR MIT OR Zlib",
    "BSD License",
    "BSD-2-Clause",
    "BSD-2-Clause OR Apache-2.0",
    "BSD-3-Clause",
    "BSD-3-Clause OR Apache-2.0",
    "ISC",
    "MIT",
    "Zlib",
    "MIT OR Unlicense",
    "PSF-2.0",
]

class OrtResults:
    def __init__(self, name: str, ort_results_folder: str) -> None:
        """
        Args:
            name (str): the language name.
            ort_results_folder (str): The relative path to the ort results folder from the root of the glide-for-redis directory.
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

for ort_result in ort_results_per_lang:
    with open(ort_result.analyzer_result_file, "r") as ort_results, open(
        ort_result.notice_file, "r"
    ) as notice_file:
        json_file = json.load(ort_results)
        notice_file = notice_file.read()
        for package in json_file["analyzer"]["result"]["packages"]:
            package_name = package["id"].split(":")[2]
            if package_name not in notice_file:
                # skip packages not in the final report
                print(f"Skipping package {package_name}")
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
                print(f"Received error for package {package} used by {ort_result.name}\n Found license={license}")
                raise

print("\n\n#### Found Licenses #####\n")
licenses_set = sorted(licenses_set)
for license in licenses_set:
    print(f"{license}")

print("\n\n#### New / Not Approved Licenses #####\n")
for license in licenses_set:
    if license not in APPROVED_LICENSES:
        print(f"{license}")
