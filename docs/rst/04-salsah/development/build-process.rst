.. Copyright © 2015-2018 the contributors (see Contributors.md).

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

.. _salsah-build-process:

Build Process
=============

TODO: complete this file.
    - SBT


Building and Running
--------------------

Start a triplestore (GraphDB-Free or GraphDB-SE). Download distribution from Ontotext_. Unzip distribution
to a place of your choosing and run the following:

::

    $ cd /to/unziped/location
    $ ./bin/graphdb -Dgraphdb.license.file=/path/to/GRAPHDB_SE.license

Here we use GraphDB-SE which needs to be licensed separately.

Then in another terminal, initialize the data repository and load some test data:

::

    $ cd KNORA_PROJECT_DIRECTORY/webapi/scripts
    $ ./graphdb-se-local-init-knora-test.sh

Then go back to the webapi root directory and use SBT to start the API server:

::

    $ cd KNORA_PROJECT_DIRECTORY/webapi
    $ sbt
    > compile
    > reStart

Then in another terminal, start the SIPI server by using Docker:

::

    $ docker run -d --add-host webapihost:[local-IP-address] -v /tmp:/tmp -v $HOME:$HOME -p 1024:1024 dhlabbasel/sipi:develop /sipi/local/bin/sipi --config=/sipi/config/sipi.knora-docker-config.lua

Don't forget to set ``local-IP-address`` with the IP address of your computer.

Then in another terminal, go to the SALSAH1 root directory and start the server:

::

    $ cd KNORA_PROJECT_DIRECTORY/salsah1
    $ sbt
    > compile
    > reStart


To shut down the SALSAH server:

::

  > reStop


Run the automated tests
-----------------------

In order to run the tests, the Selenium driver for Chrome has to be installed.

It is architecture-dependent, please go to ``salsah1/lib/chromedriver`` directory and unzip the distribution that
matches your architecture, or download it from `here <https://sites.google.com/a/chromium.org/chromedriver/downloads>`_
and install it in this directory.

Then, launch the services as described above with some slight changes:

- run the ``Webapi`` server with ``reStart -r`` (with ``allowReloadOverHTTP``-flag) from SBT (from ``KNORA_PROJECT_DIRECTORY/webapi``)
- run ``Sipi`` with the test configuration (``--config=config/sipi.knora-test-docker-config.lua``)

Then start Salsah1 it it is not running and run the tests from SBT:

::

    $ cd KNORA_PROJECT_DIRECTORY/salsah1
    $ sbt
    > reStart
    > test # or headless:test for running tests in headless mode

Note: please be patient as Salsah1 can take up to one minute (end of a time-out) before reporting some errors.

Runing automated tests in headless browser mode
-----------------------------------------------

The automated tests can also be run in the headless browser mode, which is supported in Chrome version 59 or higher.

To run the tests in headless mode, execute them with the ``headless`` prefix, e.g., ``$ sbt headless:test``.

SBT Build Configuration
------------------------

.. literalinclude:: ../../../../salsah1/SalsahBuild.sbt

.. _Ontotext: http://ontotext.com
