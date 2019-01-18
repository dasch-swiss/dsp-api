# The Knora ARK Resolver

The program `ark.py` in this directory has two modes of operation:

- When run as an HTTP server, it resolves Knora ARK URLs by redirecting
  to the actual location of each resource. Redirect URLs are generated
  from templates in a configuration file. The hostname used in the
  redirect URL, as well as the whole URL template, can be configured per
  project.

- It can also be used as a command-line tool for converting between
  resource IRIs and ARK URLs, using the same configuration file.

For usage information, run `./ark.py --help`, and see the sample configuration
file `ark-config.ini`.

In the sample configuration, the redirect URLs are Knora API URLs,
but it is recommended that in production, redirect URLs should refer to
human-readable representations provided by a user interface.

Prerequisites:

- Python 3
- [Sanic](https://sanic.readthedocs.io/en/latest/)
