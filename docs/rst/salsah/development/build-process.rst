.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

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
    > re-start allowResetTriplestoreContentOperationOverHTTP

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

Make sure you've started Fuseki and the API server as shown above.
In order to run the tests, the Selenium driver for Chrome has to be installed.
Please download it from `here <https://sites.google.com/a/chromium.org/chromedriver/downloads>`_ and save it
as ``salsah/lib/chromedriver``. Also, please make sure to start the API server with the
``allowResetTriplestoreContentOperationOverHTTP`` flag. For more information about this flag, see :ref:`webapi-server-startup-flags`

Then at the SBT prompt:

::

    > test


SBT Build Configuration
------------------------

.. literalinclude:: ../../../../salsah/SalsahBuild.sbt
