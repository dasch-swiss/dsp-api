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

*********************
Setup Sipi for Knora
*********************


Setup and Execution
===================

In order to serve files to the client application like the Salsah GUI, Sipi must be set up and running.
Sipi can be downloaded from its own github-repository: https://github.com/dhlab-basel/Sipi.
Please follow the instructions given in the README to compile it on your system.

Once it is compiled, you can run Sipi with the following option: ``local/bin/sipi -config sipi.knora-config.lua``. Please see ``sipi.knora-config.lua`` for the settings like URL, port number etc.
These settings need to be set accordingly in Knora's ``application.conf``. If you use the default settings both in Sipi and Knora, there is no need to change these settings.

Whenever a file is requested from Sipi (e.g. a browser trying to dereference an image link served by Knora), a preflight function is called.
This function is defined in ``sipi.init-knora.lua`` present in the Sipi root directory. It takes three parameters: ``prefix``, ``identifier`` (the name of the requested file), and ``cookie``. File links created by Knora use the prefix ``knora``, e.g. ``http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg``.

Given these information, Sipi asks Knora about the current's users permissions on the given file.
The cookie contains the current user's Knora session id, so Knora can match Sipi's request with a given user profile and determine the permissions this user has on the file.
If the Knora response grants sufficient permissions, the file is served in the requested quality. If the suer has preview rights, Sipi serves a reduced quality or integrates a watermark.
If the user has no permissions, Sipi refuses to serve the file. However, all of this behaviour is defined in the preflight function in Sipi and not controlled by Knora. Knora only provides the permission code.

See :ref:`sharing_sessionid_with_sipi` for more information about sharing the session id.


Using Sipi in Test Mode
=======================

If you just want to test Sipi with Knora without serving the actual files (e.g. when executing browser tests), you can simply start Sipi like this: ``local/bin/sipi -config sipi.knora-test-config.lua``.
Then always the same test file will be served which is included in Sipi. In test mode, Sipi will not aks Knora about the user's permission on the requested file.
