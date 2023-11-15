import os
from distutils.core import setup


def read(fname):
    return open(os.path.join(os.path.dirname(__file__), fname)).read()


install_requires = [
    "async-timeout>=4.0.2",
    "attrs>=21.4.0",
    "google-api-python-client>=2.85.0",
    "iniconfig>=1.1.1",
    "maturin>=0.13.0",
    "packaging>=21.3",
    "pluggy>=1.0.0",
    "protobuf>=3.20.*",
    "py>=1.11.0",
    "pyparsing>=3.0.9",
    "pytest>=7.1.2",
    "pytest-asyncio>=0.19.0",
    "tomli>=2.0.1",
    "typing_extensions>=4.8.0",
]

setup(
    name="babushka",
    packages=["babushka"],
    version="1.0.0",
    description="Python client for Redis database and key-value store",
    long_description=read("README.md"),
    author="Amazon Web Services",  # TODO: verify the author.
    author_email="",  # TODO: create the author email and add it.
    url="https://github.com/aws/babushka",
    keywords=["babushka", "redis", "key-value store", "database"],
    license="Apache License 2.0",
    install_requires=install_requires,
    python_requires=">= 3.8",
    classifiers=[
        "Development Status :: 4 - Beta",
        "Topic :: Database",
        "Topic :: Utilities",
        "License :: OSI Approved :: Apache Software License",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
    ],
)
