# Knora Documentation

## 1 MkDocs Documentation

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

## 4 Prerequisites for building the Knora JSON API V1 / V2 Request and Response Format documentation

The JSON request and response format is formally described using typescript interfaces. To create the docuemntation from these interfaces, we use `typedoc`.

Install `typedoc` using `npm`:

```
npm install --global typedoc    
```

If you do not have `npm` (node package manager), install it first. You will find more information about `npm` here: <https://www.npmjs.com/>.

## 5 Prerequisites for building Paradox-based documentation

You will need [Graphviz](http://www.graphviz.org/). On Mac OS X:

```
$ brew install graphviz
```

On Linux, use your distribution's package manager.

## 6 Jekyll

### Installation

Jekyll is used for building the *Knora Documentation Overview* website, which source can be found under `src/jekyll`. To
install Jekyll run:

```
$ gem install jekyll bundler
```

Also, to install the necessary dependencies, run the following commands from inside the `src/jekyll` folder:

```
$ bundle
```

If you run into any trouble during installation, try to update your local installation beforehand, e.g., on a mac
run `brew update`, `brew upgrade`, `gem update`. Also, if there is a problem installing `nokogiri` try `brew unlink xz`,
then installing `nokogiri` and then `brew link xz`.
