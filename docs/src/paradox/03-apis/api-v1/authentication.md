<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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
-->

# Authentication

@@toc

## Login and Logout

When a client accesses the **/v1/session?login** route successfully, it
gets back headers requesting that a cookie is created, which will store
the session token. On all subsequent calls to any route, this session
token needs to be sent with each request. Normally, a web browser does
this automatically, i.e. sends the cookie on every request. The session
token is used by the server to retrieve the user profile. If successful,
the user is deemed authenticated.

To **logout** the client can call the same route and provide the logout
parameter **/v1/session?logout**. This will invalidate the session token
and return headers for removing the cookie on the client.

## Submitting Credentials

For **login**, credentials in form of *email* and *password* need to be
sent with the request.

There are two possibilities to do so:

  - in the URL submitting the parameters `email` and `password`
    (e.g.,
    <http://knora-host/v1/resources/resIri?email=userUrlEncodedEmail&password=pw>)
  - in the HTTP authorization header ([HTTP basic
    authentication](https://en.wikipedia.org/wiki/Basic_access_authentication))
    when doing a HTTP request to the API When using Python's module
    `requests`, the credentials can simply be submitted as a tuple
    with each request using the param `auth` ([python
    requests](http://docs.python-requests.org/en/master/user/authentication/#basic-authentication)).

An alternative way for accessing all routes is to simply supply the
*email* and *password* credentials on each request either as URL
parameters or in the HTTP authorization header.

## Checking Credentials

To check the credentials, there is a special route called
**/v1/authenticate**, which can be used to check if the credentials are
valid.

## Usage Scenarios

1.  Create session by logging-in, send session token on each subsequent
    request, and logout when finished.
2.  Send email/password credentials on every request.

## Sipi (Media Server)

TODO: document these Sipi routes:

- `/Knora_login`
- `/Knora_logout`
