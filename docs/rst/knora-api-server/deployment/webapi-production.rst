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

###################################################
Running the Knora API Server on a Production System
###################################################

TODO: Update and complete this page.

Supervisord
===========

For running Knora-API we will user supervisord, which allows us to run
our java application easily as a service.

-  https://serversforhackers.com/monitoring-processes-with-supervisord

Configuration
-------------

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
---------------------

::

    $ supervisorctl reread $ supervisorctl update $ supervisorctl


Tomcat
======

Configuration
-------------

Fuseki and GraphDB are deployed using tomcat.

The relevant directories are as follows:

 * Tomcat Webapps folder, where both the Fuseki and GraphDB ``.war`` file is
     dropped in: ``/var/lib/tomcat/webapps``
 * Fuseki configuration folder: ``/etc/fuseki``
 * Data folder: ``/usr/share/tomcat``
    - for GraphDB: ``/usr/share/tomcat/.aduna`` and ``/usr/share/tomcat/.graphdb-workbench``
    - for Fuseki: ``usr/share/tomcat/.fuseki``


Administration
--------------

 * ``systemctl status tomcat``
 * ``systemctl start tomcat``
 * ``systemctl stop tomcat``

If someting is wrong, first check the log files:
 * ``/var/log/tomcat/``

