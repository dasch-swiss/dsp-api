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


Running the Knora API Server on a Production System
====================================================

This section describes possible ways of running the Knora API server in
an production environment. The description should only be taken as a first
short introduction to this topic. Further reading of the referenced materials
is advised.

.. note::
    Our platform of choice is Linux CentOS 7 and is thus assumed in the
    description. The generall idea should be usable on all platforms with
    small changes.

To run the Knora API server, we have two main components. First, the ``jar``
distribution of the server and second a supported triplestore.

.. todo::
    Add link to where the official Knora API server distributions can be
    downloaded and to the description of how to create a distribution.

The jar distribution of the server can be either run manually or as a service
for which we will use ``supervisord`` as described in the
:ref:`supervisord-label` section.

The supported triplestore can also be run manually (as described in the
documentation of each distribution) or it can be run under an application
server as described in the :ref:`tomcat-application-label` section.

.. _supervisord-label:

Supervisord
-----------

For running Knora-API we will use supervisord, which allows us to run
our java application easily as a service.

-  https://serversforhackers.com/monitoring-processes-with-supervisord

Configuration
^^^^^^^^^^^^^^

-  /etc/supervisord.d/knora-api.conf

::

    [program:knora-api]
    command=sh run.sh
    directory=/var/www/vhosts/api.knora.org
    autostart=true
    autorestart=true
    startretries=3
    stderr_logfile=/var/log/knora-api/knora-api.err.log
    stdout_logfile=/var/log/knora-api/knora-api.out.log
    user=root
    environment=

We need to create the directory for the log files!

Controlling Processes
^^^^^^^^^^^^^^^^^^^^^^

::

    $ supervisorctl reread $ supervisorctl update $ supervisorctl

.. _tomcat-application-label:

Tomcat Application Server (Fuseki 2 and GraphDB)
-------------------------------------------------

The supported triplestores for the Knora API server are Fuseki 2 and GraphDB.
Both come with a ``.war`` packaged distribution, which alows a deployment under
an application server. We chose Tomcat, but there are other options available,
e.g., Glassfish, Jetty, etc.

Installation
^^^^^^^^^^^^^^

We use yum to install Tomcat:

::
    $ yum install tomcat


Configuration
^^^^^^^^^^^^^^

Fuseki 2 and GraphDB are deployed using tomcat.

The relevant directories are as follows:

 * Tomcat Webapps folder, where both the Fuseki 2 and GraphDB ``.war`` file is
     dropped in: ``/var/lib/tomcat/webapps``
 * Fuseki configuration folder: ``/etc/fuseki``
 * Data folder: ``/usr/share/tomcat``
    - for GraphDB: ``/usr/share/tomcat/.aduna`` and ``/usr/share/tomcat/.graphdb-workbench``
    - for Fuseki: ``usr/share/tomcat/.fuseki``


Administration
^^^^^^^^^^^^^^^^

 * ``systemctl status tomcat``
 * ``systemctl start tomcat``
 * ``systemctl stop tomcat``

If someting is wrong, first check the log files:
 * ``/var/log/tomcat/``
