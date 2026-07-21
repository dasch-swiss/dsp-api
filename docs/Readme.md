# DSP-API Documentation

This folder contains the sources to the DSP-API part of documentation published
under <https://docs.dasch.swiss/> and managed by
[DSP-DOCS](https://github.com/dasch-swiss/dsp-docs) repository.

## Build and serve the docs locally

Documentation can be built by invoking the following `just` recipes from
the project root directory:

```shell
just docs-install-requirements  # install requirements
just docs-build                 # build the documentation
just docs-serve                 # serve it locally
```

### Prerequisites

You will need [Graphviz](http://www.graphviz.org/). On macOS:

```shell
brew install graphviz
```

On Linux, use your distribution's package manager.
