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

.. _webapi-build-process:

Build Process
=============

TODO: complete this file.
    - SBT
    - Using GraphDB for development and how to initializing the 'knora-test-unit' repository
    - Using Fuseki for development

Building and Running
---------------------

Using Fuseki
^^^^^^^^^^^^^

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
    > re-start

To shut down the Knora API server:

::
  
  > re-stop


Using GraphDB
^^^^^^^^^^^^^^

The archive with the newest supported version of the GraphDB-SE triplestore is provided under
```triplestores/graphdb-se-7```. Please keep in mind, that GraphDB-SE must be licensed separately by the user, and that
no license file is provided in the repository. GraphDB-SE will not run without a license file.

To be able to successfully run GraphDB inside docker three important steps need to be done beforhand:

  1. Install Docker from http://docker.com.
  2. Copy the GraphDB-SE license file into this folder and name it ``GRAPHDB_SE.license``. It is already added to a
     local ``.gitignore`` file which can be found inside this folder. Under no circumstance should the license file be
     committed to Github.
  3. (optional) The current version of ``KnoraRules.pie`` from the ``webapi/scripts`` needs to be copied to this folder
     each time it was changed. This file needs to be copied into the docker image, which can only be done if it is found
     inside this folder.

From inside this folder, type:

```
$ docker build -t graphdb .
$ docker run --rm -it -p 7200:7200 graphdb
```

Do not forget the '.' in the first command.

 - ```--rm``` removes the container as soon as you stop it
 - ```-p``` forwards the exposed port to your host (or if you use boot2docker to this IP)
 - ```-it``` allows interactive mode, so you see if something get's deployed

After the GraphDB inside the docker container has started, you can find the GraphDB workbench here: http://localhost:7200



Run the automated tests
------------------------

Using Fuseki
^^^^^^^^^^^^^

Make sure you've started Fuseki as shown above. Then at the SBT prompt:

::

    > fuseki:test



Continuous Integration
-----------------------

For continuous integration testing, we use Travis-CI. Every commit pushed to the git repository or every
pull request, triggers the build. Additionaly, in Github there is a litle checkmark beside every commit, signaling the
status of the build (successful, unsucessful, ongoing).

The build that is executed on Travis-CI is defined in ``.travis.yml`` situated in the root folder of the project, and
looks like this:

.. literalinclude:: ../../../../.travis.yml
    :language: yaml
    :linenos:
    
It basically means:

 - use the virtual machine based environment (line 1)
 - checkout git with a shorter history (lines 2-3)
 - add scala libraries (lines 4-6)
 - add oracle jdk version 8 (lines 7-8)
 - cache some directories between builds to make it faster (line 9-11)
 - start fuseki and afterwards start all tests (lines 12-14)
 - send notification to our slack channel (lines 15-17)
 
SBT Build Configuration
------------------------
 
.. literalinclude:: ../../../../webapi/WebapiBuild.sbt


.. _webapi-server-startup-flags:

Webapi Server Startup-Flags
----------------------------

The Webapi-Server can be started with a number of flags. These flags can be supplied either to the ``reStart`` or the
``run`` command in sbt, e.g.,:

::

    $ sbt
    > reStart flag

or

::

    $sbt
    > run flag


``loadDemoData`` - Flag
^^^^^^^^^^^^^^^^^^^^^^^^

When the webapi-server is started with the ``loadDemoData`` flag, then at startup, the data which is configured in
``application.conf`` under the ``app.triplestore.rdf-data`` key is loaded into the triplestore, and any data in the
triplestore is removed beforehand.

Usage:

::

    $ sbt
    > reStart loadDemoData


``allowResetTriplestoreContentOperationOverHTTP`` - Flag
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When the webapi.server is started with the ``allowResetTriplestoreContentOperationOverHTTP`` flag, then the
``v1/store/ResetTriplestoreContent`` route is activated. This route accepts a ``POST`` request, with a json payload
consisting of the following exemplary content:

::

    [
      {
        "path": "../knora-ontologies/knora-base.ttl",
        "name": "http://www.knora.org/ontology/knora-base"
      },
      {
        "path": "../knora-ontologies/knora-dc.ttl",
        "name": "http://www.knora.org/ontology/dc"
      },
      {
        "path": "../knora-ontologies/salsah-gui.ttl",
        "name": "http://www.knora.org/ontology/salsah-gui"
      },
      {
        "path": "_test_data/ontologies/incunabula-onto.ttl",
        "name": "http://www.knora.org/ontology/incunabula"
      },
      {
        "path": "_test_data/all_data/incunabula-data.ttl",
        "name": "http://www.knora.org/data/incunabula"
      }
    ]

This content corresponds to the payload sent with the ``ResetTriplestoreContent`` message, defined inside the
``org.knora.webapi.messages.v1.store.triplestoremessages`` package. The ``path`` being the relative path to the ``ttl``
file which will be loaded into a named graph by the name of ``name``.

Usage:

::

    $ sbt
    > reStart allowResetTriplestoreContentOperationOverHTTP
