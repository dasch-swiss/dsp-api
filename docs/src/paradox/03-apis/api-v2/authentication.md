<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

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

Access to the Knora API can for certain operations require a user to authenticate.
Authentication can be performed in two ways:

1. By providing an *identifier*/*password* combination where the *identifier* is either
the user's IRI, username, or email address.

2. By providing an *access token*

## Submitting *Identifier/Password* Credentials

When accessing any route and *identifier*/*password* or token credentials would
need to be sent, we support two options to do so:

- in the URL submitting the parameters `identifier` and `password`
  (e.g., <http://knora-host/v1/resources/resIri?identifier=userUrlEncodedIdentifier&password=pw>),
  where the identifier can be the user's IRI, username, or email, and
- in the HTTP header ([HTTP basic
  authentication](https://en.wikipedia.org/wiki/Basic_access_authentication)), where the
  identifier can be the user's username or email (IRI not supported).

When using Python's module `requests`, the credentials can simply be submitted as a tuple with
each request using the param `auth` ([python requests](http://docs.python-requests.org/en/master/user/authentication/#basic-authentication)).

## Access Token / Session / Login and Logout

A client can generate an *access token* by sending a POST request (e.g., `{"identifier":"usersemail",
"password":"userspassword"}`) to the **/v2/authentication** route with
*identifier* and *password* in the body. The identifier can be the user's IRI,
username, or email. If the credentials are valid, a [JSON WEB Token](https://jwt.io) (JWT)
will be sent back in the response (e.g., `{"token": "eyJ0eXAiOiJ..."}`). Additionally, for web browser
clients a session cookie containing the JWT token is also created, containing `KnoraAuthentication=eyJ0eXAiOiJ...`.

When accessing any route, the *access token* would need to be supplied, we support three options to do so:

- the session cookie,
- in the URL submitting the parameter `token` (e.g., <http://knora-host/v1/resources/resIri?token=1234567890>), and
- in the HTTP authorization header with the @extref[HTTP bearer scheme](rfc:6750#section-2.1).

If the token is successfully validated, then the user is deemed authenticated.

To **logout**, the client sends a DELETE request to the same route **/v2/authentication** and
the *access token* in one of the three described ways. This will invalidate the access token,
thus not allowing further request that would supply the invalidated token.

## Checking Credentials

To check the credentials, send a GET request to **/v2/authentication** with the credentials
supplied as URL parameters or HTTP authentication headers as described before.

## Usage Scenarios

1.  Create token by logging-in, send token on each subsequent request, and logout when finished.
2.  Send email/password credentials on every request.
