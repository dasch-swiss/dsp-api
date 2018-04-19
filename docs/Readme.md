# Knora Documentation

## 1 Paradox (new) and Sphynx (old) Style Documentation

This folder contains the sources to the Knora documentation website. Currently, there are two
flavors. The first is Sphinx-based, which is our original or older style to write documentation,
and second the Paradox-Markdown-based newer style. We are currently in a transitioning phase,
where we slowly move chapters from Sphinx to Paradox.

Some chapters are easier to move then others. The process involves the following Pandoc
command:
```
$ pandoc filename.rst -f rst -t markdown -o filename.md
```
and possibly some cleanup by hand.

Both the Sphinx and Paradox based documentations are build by invoking the following command:

```
$ sbt makeSite
```

The generated documentation can be found under `target/site/paradox` and `target/site/sphinx`.

## 2 Previewing the Documentation Site

To preview your generated site, you can run `previewSite` which launches a static web server,
or `previewAuto` which launches a dynamic server updating its content at each modification in
your source files. Both launch the server on port `4000` and attempts to connect your browser
to `http://localhost:4000/`. 

## 3 Documentation of the Knora JSON API V1 Request and Response Format

### 3.1 Requirements

The JSON request and response format is formally described using typescript interfaces. To create the docuemntation from these interfaces, we use `typedoc`.

Install `typedoc` using `npm`:

```
npm install --global typedoc    
```

If you do not have `npm` (node package manager), install it first. You will find more information about `npm` here: <https://www.npmjs.com/>.

### 3.2 Building the Documentation

Inside the docs folder type for the html documentation type

```
$ make jsonformat
```

You will then find the documentation in `_format_docu`.

## 4 Publishing

To publish the documentation, you need to be on the `develop` branch and then execute the following
command:

```
$ update-gh-pages.sh
```

This command will build all documentations (paradox, sphinx, api) and publish it to the `gh-pages`
branch.

## 5 Prerequisites for building Sphinx based documentation

### 5.1 Install Python 3 ##

On Mac OS X:

```
$ brew install python3
```

On Linux, use your distribution's package manager, or see [the Python web site](https://www.python.org).

### 5.2 Installing Sphinx ##

#### 5.2.1 Globally ###

```
$ pip3 install sphinx
```

#### 5.2.2 In a separate environment ###
To create your own environment, run inside the docs folder:

```
$ python3 -m venv env
```

**Remark for Anaconda users on Mac OS and Linux**

Anaconda3/2 for Linux and Mac OS do not have ensurepip installed.
So instead of running pyvenv env, you can first run pyvenv env --without-pip, then download the get-pip.py from [pip's homepage](https://pip.pypa.io/en/stable/installing/#installing-with-get-pip-py), and install the pip in activated env venv.

To create virtual environment without pip, run inside  doc folder:

```
$ pyvenv env --without-pip
```

Activating the env:

```
$ source ./env/bin/activate
```

Install pip in your venv, run inside the env folder:

```
$ python ~/Downloads/get-pip.py
```

Now you can continue with pip commands below   

Then, each developer will set up their own virtualenv and run:

```
$ ./env/bin/pip install -r requirements.txt
```

To activate the environment, do:

```
$ source ./env/bin/activate
```

To generate a "requirements" file (usually requirements.txt), that you commit with your project, do:

```
$ ./env/bin/pip freeze > requirements.txt
```

### 5.3 Install Graphviz ##

You will need [Graphviz](http://www.graphviz.org/). On Mac OS X:

```
$ brew install graphviz
```

On Linux, use your distribution's package manager.

### 5.4 Generating Documentation ##

#### 5.4.1 Bulding the Documentation ###

Inside the docs folder type for the html documentation type

```
$ make html
```

or

```
$ make latex
$ make latexpdf
```

for generating a PDF. If there are errors during the latex compilation, skip them with 'R'. Also, run ```make latex``` a
few times, so that the index is properly generated.

The generated documentation can be found under  ```_build/latex```

#### 5.4.2 Problems with Locales ###

Error message when trying to generate the documentation:

```
ValueError: unknown locale: UTF-8
```

Set the locales as environment variables (e.g. in ~/.bash_profile):

```
export LC_ALL=de_CH.UTF-8
export LANG=de_CH.UTF-8
```


## 6 Jekyll

### Installation

Jekyll is used for building the *Knora Documentation Overview* website, which source can be found under `src/jekyll`.
To install the necessary dependencies, run the following commands from inside the `src/jekyll` folder:

```
$ gem install jekyll bundler
$ bundle
```

If you run into any trouble during installation, try to update your local installation beforehand, e.g., on a mac
run `brew update`, `brew upgrade`, `gem update`. Also, if there is a problem installing `nokogiri` try `brew unlink xz`,
then installing `nokogiri` and then `brew link xz`.
