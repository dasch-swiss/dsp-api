# Knora Documentation

## 1 Paradox (new) and Sphynx (old) Style Documentation

This folder contains the sources to the Knora documentation website published under
[http://docs.knora.org](http://docs.knora.org). The `src` folder contains the following documentation sources:

 - `src/jekyll`: The Knora Documentation Overview Website
 - `src/paradox`: The Paradox-Mardown-based newer style Knora general documentation
 - `src/sphinx`: The Sphinx-based older style Knora general documentation
 - `src/api-v1`: The Knora JSON API V1 Request and Response Format documentation. Source can be found in `salsah1/src/typescript_interfaces`
 - `src/api-v2`: The Knora JSON-LD API V2 Request and Response Format documentation.
 - `src/api-admin`: The Knora JSON Admin API Request and Response format Swagger-based documentation.

We are currently in a transitioning phase, where we slowly move chapters from Sphinx to Paradox. The process of 
moving chapter involves the following Pandoc command for converting `rst` documents to `md` documents:

```
$ pandoc filename.rst -f rst -t gfm -o filename.md
```

All the different documentations are build by invoking the following command:

```
$ sbt makeSite
```

The generated documentation can be found under `target/site/`.

## 2 Previewing the Documentation Site

To preview your generated site, you can run the following command:

```
$ sbt previewSite
```

which launches a static web server, or:

```
$ sbt previewAuto
```

which launches a dynamic server updating its content at each modification in your source files. Both launch the server
on port `4000` and attempts to connect your browser to `http://localhost:4000/`. 

## 4 Publishing

To publish the documentation, you need to be on the `develop` branch inside the `docs` folder and then execute the following
command:

```
$ sbt ghpagesPushSite
```

This command will build all documentations (paradox, sphinx, api) and publish it to the `gh-pages`
branch.

## 4 Prerequisites for building the Knora JSON API V1 / V2 Request and Response Format documentation

### 4.1 Requirements

The JSON request and response format is formally described using typescript interfaces. To create the docuemntation from these interfaces, we use `typedoc`.

Install `typedoc` using `npm`:

```
npm install --global typedoc    
```

If you do not have `npm` (node package manager), install it first. You will find more information about `npm` here: <https://www.npmjs.com/>.

## 5 Prerequisites for building Sphinx based documentation

### 5.1 Install Python 3 ##

On Mac OS X:

```
$ brew install python3
```

On Linux, use your distribution's package manager, or see [the Python web site](https://www.python.org).

### 5.2 Installing Sphinx ##

```
$ pip3 install sphinx
```

### 5.3 Install Graphviz ##

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
