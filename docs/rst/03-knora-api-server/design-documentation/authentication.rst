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

.. _authentication:

Authentication in the Knora API Server
======================================

.. contents:: :local:

Scope
------

Authentication is the process of making sure that if someone is accessing something then this someone is actually also
the someone he pretends to be. The process of making sure that someone is authorized, i.e. has the permission to access
something, is handled as described in the section on authorization (see: :ref:`administration`) in the Knora base
ontology document.

Implementation
--------------

The authentication in Knora is based on Basic Auth `HTTP basic authentication`_, URL parameters, `JSON Web Token`_ and
cookies. This means that on every request (to any of the routes), credentials need to be sent either via authorization
header, URL parameters or cookie header.

All routes are always accessible and if there are no credentials provided, a default user is assumed. If credentials
are sent and they are not correct (e.g., wrong username, password incorrect, token expired), then the request will end
in an error message.

There are some differences in ``V1`` and ``V2`` of the API regarding authentication. They differ mainly in the format
of the response and that creation of session cookies are only supported in ``V1`` and tokens in ``V2``. After `login`
via either version, all routes (``V1`` and ``V2``) are accessible.

Usage V1
--------

Login and Logout
^^^^^^^^^^^^^^^^

When a client accesses the **/v1/session?login** route successfully, it gets back headers requesting that a cookie
is created, which will store the session token. On all subsequent calls to any route, this session token needs to be
sent with each request. Normally, a web browser does this automatically, i.e. sends the cookie on every request. The
session token is used by the server to retrieve the user profile. If successful, the user is deemed authenticated.

To **logout** the client can call the same route and provide the logout parameter **/v1/session?logout**. This will
invalidate the session token and return headers for removing the cookie on the client.


Submitting Credentials
^^^^^^^^^^^^^^^^^^^^^^

For **login**, credentials in form of *email* and *password* need to be sent with the request.

There are two possibilities to do so:
 - in the URL submitting the parameters ``email`` and ``password`` (e.g., http://knora-host/v1/resources/resIri?email=userUrlEncodedEmail&password=pw)
 - in the HTTP authorization header (`HTTP basic authentication`_) when doing a HTTP request to the API
   When using Python's module ``requests``, the credentials can simply be submitted as a tuple with each request using
   the param ``auth`` (`python requests`_).

An alternative way for accessing all routes is to simply supply the *email* and *password* credentials on each request
either as URL parameters or in the HTTP authorization header.


Checking Credentials
^^^^^^^^^^^^^^^^^^^^

To check the credentials, there is a special route called **/v1/authenticate**, which can be used to check if the
credentials are valid.

Usage Scenarios
^^^^^^^^^^^^^^^

1. Create session by logging-in, send session token on each subsequent request, and logout when finished.
2. Send *email*/*password* credentials on every request.


Usage V2
--------

Login and Logout
^^^^^^^^^^^^^^^^

A client sends a POST request (e.g., ``{"email":"usersemail", "password":"userspassword"}``) to the
**/v2/authentication** route with *email* and *password* in the body. If the credentials are valid,
a `JSON WEB Token`_ (JWT) will be sent back in the response (e.g., ``{"token": "eyJ0eXAiOiJ..."}``). On all subsequent
calls to any route, this token can be sent with each request (instead of *email*/*password*). If the token is
successfully validated, then the user is deemed authenticated.

To **logout** the client sends a DELETE request to the same route **/v2/authentication** and the token (either as an
URL parameter or authorization header). This will invalidate the token.


Submitting Credentials
^^^^^^^^^^^^^^^^^^^^^^

When accessing any route and *email*/*password* credentials would need to be sent, we support two options to do so:
 - in the URL submitting the parameters ``email`` and ``password`` (e.g., http://knora-host/v1/resources/resIri?email=userUrlEncodedEmail&password=pw)
 - in the HTTP header (`HTTP basic authentication`_) when doing a HTTP request to the API
   When using Python's module ``requests``, the credentials can simply be submitted as a tuple with each request using
   the param ``auth`` (`python requests`_).

When accessing any route and the *token* would need to be sent, we support two options to do so:
 - in the URL submitting the parameter ``token`` (e.g., http://knora-host/v1/resources/resIri?token=1234567890)
 - in the HTTP authorization header with the ```Bearer``` scheme (`HTTP bearer scheme`_).


Checking Credentials
^^^^^^^^^^^^^^^^^^^^

To check the credentials, there is a special route called **/v1/authenticate**, which can be used to check if the
credentials are valid.

Usage Scenarios
^^^^^^^^^^^^^^^

1. Create token by logging-in, send token on each subsequent request, and logout when finished.
2. Send *email*/*password* credentials on every request.



Skipping Authentication
-----------------------

There is the possibility to turn skipping authentication on and use a hardcoded
user (Test User). In **application.conf** set the ``skip-authentication = true``
and Test User will be always assumed.



Sipi (Media Server)
-------------------

For authentication to work with the media server, we need to add support for cookies. At the moment the SALSAH-App
would set BasicAuth heathers, but this only works for AJAX requests using ``SALSAH.ApiGet`` (``Put``, etc.).
Since the medias are embedded as source tags, the browser would get them on his own, and doesn't know anything about
the needed AuthHeathers. With cookies, the browser would send those automatically with every request. The media server
can use the credentials of the user requesting something for accessing the RepresentationsRouteV1, i.e. make this
request in the name of the user so to speak, then the RepresentationResponderV1 should have all the information it
needs to filter the result based on the users permissions.

Improving Security
------------------

In the first iteration, the email/password would be sent in clear text. Since we will use HTTPS this shouldn't be
a problem. The second iteration, could encrypt the email/password.

.. _HTTP basic authentication: https://en.wikipedia.org/wiki/Basic_access_authentication
.. _JSON WEB Token: https://jwt.io
.. _HTTP bearer scheme: https://tools.ietf.org/html/rfc6750#section-2.1
.. _python requests: http://docs.python-requests.org/en/master/user/authentication/#basic-authentication
