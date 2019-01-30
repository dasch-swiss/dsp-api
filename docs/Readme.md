# Knora Documentation

## 1 Paradox Documentation

This folder contains the sources to the Knora documentation website published under
[http://docs.knora.org](http://docs.knora.org). The `src` folder contains the following documentation sources:

 - `src/jekyll`: The Knora Documentation Overview Website
 - `src/paradox`: The Paradox-Mardown-based Knora general documentation
 - `src/api-v1`: The Knora JSON API V1 Request and Response Format documentation. Source can be found in `salsah1/src/typescript_interfaces`
 - `src/api-v2`: The Knora JSON-LD API V2 Request and Response Format documentation.
 - `src/api-admin`: The Knora JSON Admin API Request and Response format Swagger-based documentation.

All the different documentations are build by invoking the following command:

```
$ sbt docs/makeSite
```

The generated documentation can be found under `target/site/`. To open it locally, load `target/site/paradox/index.html`.

## 2 Previewing the Documentation Site

To preview your generated site, you can run the following command:

```
$ sbt docs/previewSite
```

which launches a static web server, or:

```
$ sbt docs/previewAuto
```

which launches a dynamic server updating its content at each modification in your source files. Both launch the server
on port `4000` and attempt to connect your browser to `http://localhost:4000/`.

## 3 Publishing

To publish the documentation, you need to be on the `develop` branch inside the `docs` folder and then execute the following
command:

```
$ sbt docs/ghpagesPushSite
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
