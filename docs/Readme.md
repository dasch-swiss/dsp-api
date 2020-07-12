# Knora Documentation

## MkDocs Documentation

This folder contains the sources to the Knora documentation website published
under [http://docs.knora.org](http://docs.knora.org).

The `src` folder contains the following documentation sources:

 - `src/api-v1`: The Knora JSON API V1 Request and Response Format documentation. Source can be found in `salsah1/src/typescript_interfaces`
 - `src/api-v2`: The Knora JSON-LD API V2 Request and Response Format documentation.
 - `src/api-admin`: The Knora JSON Admin API Request and Response format Swagger-based documentation.

All the different documentations are build by invoking the following make
commands from the project root directory:

```
$ make docs-buld # build the documentation
$ make docs-serve # serve it locally
$ make docs-publish # publish it do Github pages
$ make docs-install-requirements: ## install requirements
```

This command will build all documentation and publish it to the `gh-pages` branch.

## Prerequisites 

1. You will need [Graphviz](http://www.graphviz.org/). On macOS:

    ```
    $ brew install graphviz
    ```
    
    On Linux, use your distribution's package manager.

1. The Knora JSON API V1 / V2 Request and Response Format documentation is
formally described using typescript interfaces. To create the documentation
from these interfaces, we use `typedoc`.

    Install `typedoc` using `npm`:

    ```
    npm install --global typedoc    
    ```

    If you do not have `npm` (node package manager), install it first. You will
    find more information about `npm` here: <https://www.npmjs.com/>.




