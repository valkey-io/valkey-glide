from setuptools import find_packages, setup

setup(
    name="pybabushka",
    description="Python client for Redis database and key-value store based on redis-rs",
    long_description=open("README.md").read().strip(),
    long_description_content_type="text/markdown",
    keywords=["Redis", "Babushka", "key-value store", "database"],
    license="",
    version="1.0.0",
    packages=find_packages(include=["src", "src.commands"]),
    setup_requires=["pytest-runner"],
    tests_require=["pytest"],
    url="https://github.com/barshaul/babushka",
    python_requires=">=3.6",
    install_requires=[
        "deprecated>=1.2.3",
        "packaging>=20.4",
        'typing-extensions; python_version<"3.8"',
        "async-timeout>=4.0.2",
        "setuptools==45",
    ],
)
