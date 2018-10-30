<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# The SALSAH 1 GUI

## Running the automated tests

In order to run the tests, the Selenium driver for Chrome has to be
installed.

It is architecture-dependent, so go to the `salsah1/lib/chromedriver`
directory and unzip the distribution that matches your architecture, or
download it from
[here](https://sites.google.com/a/chromium.org/chromedriver/downloads)
and install it in this directory.

Then, launch the services as described in other parts of this document;
- the triple store with the test data,
- the Knora server with `reStart -r` (`allowReloadOverHTTP`-flag) from SBT (from ``KNORA_PROJECT_DIRECTORY/webapi``),
- Sipi with the test configuration (either run the docker instance as described in @ref:[The Sipi Media Server](../07-sipi/setup-sipi-for-knora.md) or run Sipi with the argument: `--config=config/sipi.knora-docker-test-config.lua`, the required config files can be found ``KNORA_PROJECT_DIRECTORY/sipi`` and should be copied to your sipi directory)
- and SALSAH 1 where you can run the tests in the same SBT session:

```
$ cd KNORA_PROJECT_DIRECTORY/salsah1
$ sbt
> compile
> reStart
> test
```

Note: please be patient as SALSAH 1 can take up to one minute (end of a
time-out) before reporting some errors.
