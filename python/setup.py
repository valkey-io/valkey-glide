from setuptools import find_packages, setup

setup(
    name="babushkapy",
    description="Python client for Redis database and key-value store based on redis-rs",
    long_description=open("README.md").read().strip(),
    long_description_content_type="text/markdown",
    keywords=["Redis", "Babushka", "key-value store", "database"],
    license="",
    packages=find_packages(include=["babushkapy", "babushkapy.commands"]),
    setup_requires=["pytest-runner"],
    tests_require=["pytest"],
    url="https://github.com/barshaul/babushka",
    python_requires=">=3.7",
    install_requires=[
        "deprecated>=1.2.3",
        "packaging>=20.4",
        'typing-extensions; python_version<"3.8"',
        "async-timeout>=4.0.2",
        "maturin>=0.13,<0.14",
    ],
)
