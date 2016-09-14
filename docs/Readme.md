# Knora Documentation Generation #

## 1 Install Python 3 ##

```
$ brew install python3
```

or get the install package from: https://www.python.org


## 2 Installing Sphinx ## 

### 2.1 Globally ###

```
$ pip3 install sphinx
```

### 2.2 In a separate environment ###

To create your own environment, run inside the docs folder:

```
$ python3 -m venv env
```

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


## 3 Generating Documentation ##

### 3.1 Bulding the Documentation ###

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

### 3.2 Problems with Locales ###

Error message when trying to generate the documentation:

```
ValueError: unknown locale: UTF-8
```

Set the locales as environment variables (e.g. in ~/.bash_profile):

```
export LC_ALL=de_CH.UTF-8
export LANG=de_CH.UTF-8
```

## 4 Documentation of the Knora JSON API V1 Request and Response Format

### 4.1 Requirements

The JSON request and response format is formally described using typescript interfaces. To create the docuemntation from these interfaces, we use `typedoc`.

Install `typedoc` using `npm`:

```
npm install --global typedoc    
```

If you do not have `npm` (node package manager), install it first. You will find more information about `npm` here: <https://www.npmjs.com/>.

### 4.2 Building the Documentation

Inside the docs folder type for the html documentation type

```
$ make jsonformat
```

You will then find the documentation in `_format_docu`.