# DSP-API Documentation

## MkDocs Documentation

This folder contains the sources to the DSP-API documentation website published
under <https://docs.dasch.swiss/>

The `src` folder contains the following documentation sources:

- `src/api-v1`: The DSP-API V1 (JSON) Request and Response Format documentation. Source can be found in `salsah1/src/typescript_interfaces`
- `src/api-v2`: The DSP-API V2 (JSON-LD) Request and Response Format documentation.

All the different documentations are build by invoking the following make
commands from the project root directory:

```shell
make docs-build # build the documentation
make docs-serve # serve it locally
make docs-publish # publish it do Github pages
make docs-install-requirements: ## install requirements
```

This command will build all documentation and publish it to the `gh-pages` branch.

## Prerequisites

1. You will need [Graphviz](http://www.graphviz.org/). On macOS:

    ```shell
    brew install graphviz
    ```
  
    On Linux, use your distribution's package manager.

1. The DSP-API V1 / V2 Request and Response Format documentation is
formally described using typescript interfaces. To create the documentation
from these interfaces, we use `typedoc`.

    Install `typedoc` using `npm`:

    ```shell
    npm install --global typedoc
    ```

    If you do not have `npm` (node package manager), install it first. You will
    find more information about `npm` here: <https://www.npmjs.com/>.
