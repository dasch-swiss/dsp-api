.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

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
---------------------

Start the provided Fuseki triplestore:

::

    $ cd KNORA_PROJECT_DIRECTORY/triplestores/fuseki
    $ ./fuseki-server

Then in another terminal, load some test data into the triplestore:

::

    $ cd KNORA_PROJECT_DIRECTORY/webapi/scripts
    $ ./fuseki-load-test-data.sh

Then go back to the webapi root directory and use SBT to start the API server:

::

    $ cd KNORA_PROJECT_DIRECTORY/webapi
    $ sbt
    > compile
    > re-start allowReloadOverHTTP

Then in another terminal, go to the SIPI project root directory and start the server:

::

    $ ./local/bin/sipi --config=config/sipi.knora-config.lua (for production)
    $ ./local/bin/sipi --config=config/sipi.knora-test-config.lua (for running tests)

Then in another terminal, go to the SALSAH root directory and start the server:

::

    $ cd KNORA_PROJECT_DIRECTORY/salsah
    $ sbt
    > compile
    > re-start


To shut down the SALSAH server:

::

  > re-stop


Run the automated tests
------------------------

In order to run the tests, the Selenium driver for Chrome has to be installed.

It is architecture-dependant, please go to ``salsah/lib/chromedriver`` directory and unzip the distribution that matches your architecture, or download it from `here <https://sites.google.com/a/chromium.org/chromedriver/downloads>`_ and install it in this directory.

Then, launch the services as described above; the triple store with the test data, the api server with the ``allowReloadOverHTTP`` option, sipi with the test configuration and salsah where you can run the tests in the same SBT session:

::

    $ cd KNORA_PROJECT_DIRECTORY/salsah
    $ sbt
    > compile
    > re-start
    > test # or headless:test for running tests in headless mode

Note: please be patient as salsah can take up to one mimute (end of a time-out) before reporting some errors.

Runing automated tests in headless browser mode
-----------------------------------------------

The automated tests can also be run in the headless browser mode, which is supported in Chrome version 59 or higher.

To run the tests in headless mode, execute them with the ``headless`` prefix, e.g., ``$ sbt headless:test``.

SBT Build Configuration
------------------------

.. literalinclude:: ../../../../salsah/SalsahBuild.sbt
