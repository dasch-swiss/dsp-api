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

.. _authentication:

Authentication in the Knora API Server
======================================

Scope
------

Authentication is the process of making sure that if someone is
accessing something that then this someone is actually also the someone
he pretends to be. The process of making sure that someone is
authorized, i.e. has the permission to access something, is handled as
described in the section on authorization in the Knora base ontology
document. TODO: add a link to this.

Implementation
---------------

The authentication in Knora is based on Basic Auth
`HTTP basic authentication`_, URL parameters, and cookies. This means that
on every request (to any of the routes), an authentication header, URL
parameters or cookie need to be sent.

All routes are always accessible and if there are no credentials
provided, a default user is assumed. If credentials are sent and they
are not correct (e.g., wrong username, password incorrect), then the
request will end in an error message. This is not true for a cookie
containing an expired session id. In this case, the default user is
assumed.

Usage
------

Checking Credentials
^^^^^^^^^^^^^^^^^^^^^^

To check the credentials and create a 'session', e.g., by a login window
in the client, there is a special route called **/v1/authentication**,
which returns following for each case:

**Credentials correct:**

::

    {
      "status": 0,
      "message": "credentials are OK".
      "sid": "1437643844783"
    }

In this case, the found user profile is written to a cache and stored
under the ''sid'' key. Also a header requesting to store the ''sid'' in
a cookie is sent. On subsequent access to all the other routes, the
''sid'' is used to retrieve the cached user profile. If successful, the
user is deemed authenticated.

**Username wrong:**

::

    {
      "status": 2,
      "message": "bad credentials: user not found"
    }

**Password wrong:**

::

    {
      "status": 2,
      "message": "bad credentials: user found, but password did not match"
    }

**No credentials:**

::

    {
      "status": 999,
      "message": "no credentials found"
    }

Web client (Login/Logout)
^^^^^^^^^^^^^^^^^^^^^^^^^^

When a web client accesses the **/v1/authentication** route
successfully, it gets back a cookie. To **logout** the client can call
the same route and provide the logout parameter
**/v1/authenticate?logout**. This will delete the cache entry and remove
the session id from the cookie on the client.


Submitting Credentials in the URL or in the HTTP Authentication Header
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

As an alternative to creating a session, the client may also submit the credentials:
 - in the URL (when doing a HTTP-GET request) submitting the parameters ``username`` and ``password`` (e.g. http://knora-host/resources/resIri?username=user&password=pw)
 - in the HTTP header (`HTTP basic authentication`_) when doing a HTTP request to the API (all methods). When using Python's module ``requests``,
   the credentials can simply be submitted as a tuple with each request using the param ``auth`` (`python requests`_).

Workflow
^^^^^^^^^^

1. The login form on the client can use */v1/authentication* to check if
   the username/password combination provided by the user is correct. The
   username and password can be provided as URL parameters (see above).

2. on the server, this gets checked and a corresponding result as
   described will be returned

3. all subsequent calls can then send these credentials as
   authentication header or URL parameters (username / password), and in
   the case of a web client just the cookie.

Step 1 and 2 are optional, and can be skipped, if prior checking of the
credentials is not needed. Naturally, this won't work for a web client
using cookies for authentication.

Skipping Authentication
^^^^^^^^^^^^^^^^^^^^^^^^^

There is the possibility to turn skipping authentication on and use a hardcoded
user (Test User). In **application.conf** set the
``skip-authentication = true`` and Test User will be always
assumed.

Sipi (Media Server)
^^^^^^^^^^^^^^^^^^^^^

For authentication to work with the media server, we need to add support
for cookies. At the moment the SALSAH-App would set BasicAuth heathers,
but this only works for AJAX requests using ``SALSAH.ApiGet`` (``Put``, etc.).
Since the medias are embedded as source tags, the browser would get them
on his own, and doesn't know anything about the needed AuthHeathers.
With cookies, the browser would send those automatically with every
request. The media server can the use the credentials of the user
requesting something for accessing the RepresentationsRouteV1, i.e. make
this request in the name of the user so to speak, then the
RepresentationResponderV1 should have all the information it needs to
filter the result based on the users permissions.

Improving Security
^^^^^^^^^^^^^^^^^^^^

In the first iteration, the username/password would be sent in clear
text. Since we will use HTTPS this shouldn't be a problem. The second
iteration, could encrypt the username/password.

.. _HTTP basic authentication: https://en.wikipedia.org/wiki/Basic_access_authentication
.. _python requests: http://docs.python-requests.org/en/master/user/authentication/#basic-authentication
