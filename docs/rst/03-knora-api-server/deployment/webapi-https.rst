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


Using HTTPS in the Knora API Server
===================================

.. contents:: :local:

Enabling HTTPS
--------------

The Knora API server can be configured to accept requests over HTTP, HTTPS, or
both. In the ``app.http`` section of ``application.conf``, the relevant
configuration options look like this by default:

::

  https {
     keystore = "https/localhost.jks"
     keystore-password = "test keystore password"
  }

  knora-api {
     host = "localhost"
     http-port = 3333
     https-port = 3334
     use-http = true
     use-https = false
  }

On a production system, you should enable HTTPS and disable HTTP, to protect
passwords and other private data from being intercepted in transit.

To enable HTTPS, you will need an SSL/TLS certificate, signed by a certificate
authority (CA) and stored in a Java KeyStore (JKS) file. For information on
storing a certificate in a JKS file, see the `Oracle keytool documentation`_.
Once you have a JKS, you can configure the Knora API Server to load it by
changing the ``https`` configuration in ``application.conf``. You can then set
``use-https`` to ``true``. The HTTP and HTTPS ports can be any ports you
choose.

Creating a Self-Signed Certificate for Testing
----------------------------------------------

For testing purposes, you can create your own CA and self-signed certificate.
Open a terminal in the Knora source directory
``webapi/src/main/resources/https``, and type:

::

  $ ./generate-test-ca.sh
  $ ./generate-test-cert.sh

This will create a CA, then create an SSL/TLS certificate signed by the CA, in
the file ``localhost.jks``, matching the ``https`` configuration in
``application.conf`` shown above. You can then set ``use-https`` to ``true``.

Configuring A Web Browser to Accept a Self-Signed Certificate
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

If you are using a self-signed certificate, you must configure your web
browser to accept it.

Chrome
~~~~~~

To configure the Chrome browser to accept self-signed certificates for
``localhost``, type this in the location bar:

::

  chrome://flags/#allow-insecure-localhost

Click on **Enable** to enable the option, then restart the browser.

Firefox
~~~~~~~

Make a request to the API server over HTTPS by typing a Knora API URL into
the browser's location bar, e.g.:

::

  https://localhost:3334/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a

Firefox will say that your connection is not secure. Click **Advanced**, then
**Add Exception**, then **Confirm Security Exception**.

Configuring the SALSAH GUI to Connect to the Knora API Server over HTTPS
------------------------------------------------------------------------

In the file ``salsah/src/public/js/00_init_javascript.js``, change the value
of the variable ``API_URL`` to specify ``https`` instead of ``http``, along
with the HTTPS port configured in the Knora API server's ``application.conf``.
For example:

::

  var API_URL = 'https://localhost:3334';

Note that this only affects the communication between the SALSAH GUI and the
Knora API server. On a production system, you should also use a web server
that serves the SALSAH GUI itself over HTTPS, to protect private data from
being intercepted in transit. You must then set ``http.salsah.base-url`` in
``application.conf`` to the base HTTPS URL of the SALSAH GUI.

.. _Oracle keytool documentation: https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html
