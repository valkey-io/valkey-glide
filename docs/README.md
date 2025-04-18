# Valkey GLIDE MkDocs Website

This directory contains everything needed to build and deploy the MkDocs-based documentation site hosted on Valkey GLIDE's GitHub Pages.

## How It Works

The documentation is built and deployed using the [`./build-docs.sh`](./build-docs.sh) script. 

- **Python documentation** is generated using the [`mkdocstrings`](https://mkdocstrings.github.io/) plugin for MkDocs. See more about the Python documentation [here](https://github.com/valkey-io/valkey-glide/blob/main/python/DEVELOPER.md#documentation).
- **Node.js documentation** is generated using [`TypeDoc`](https://typedoc.org/), which produces Markdown files that are then processed by MkDocs.

## Building and Serving Locally

To **build** the site locally, run the following from the `docs` directory:

```bash
./build-docs.sh build
```

To **build and serve** the site locally (deploy to localhost), run the following from the `docs` directory:
```bash
./build-docs.sh serve
```
