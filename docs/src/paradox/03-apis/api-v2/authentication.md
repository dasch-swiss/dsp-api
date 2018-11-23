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

A client sends a POST request (e.g., `{"identifier":"usersemail",
"password":"userspassword"}`) to the **/v2/authentication** route with
*identifier* and *password* in the body. The identifier can be the user's IRI,
username, or email. If the credentials are valid, a [JSON WEB Token](https://jwt.io) (JWT)
will be sent back in the response (e.g., `{"token": "eyJ0eXAiOiJ..."}`).
On all subsequent calls to any route, this token can be sent with each request (instead of
*identifier*/*password*). If the token is successfully validated, then the user is deemed
authenticated.

To **logout**, the client sends a DELETE request to the same route **/v2/authentication**
and the token (either as an URL parameter or authorization header). This will invalidate
the token.

## Submitting Credentials

When accessing any route and *identifier*/*password* credentials would
need to be sent, we support two options to do so:

- in the URL submitting the parameters `identifier` and `password`
  (e.g., <http://knora-host/v1/resources/resIri?identifier=userUrlEncodedEmail&password=pw>),
  where the identifier can be the user's IRI, username, or email, and
- in the HTTP header ([HTTP basic
  authentication](https://en.wikipedia.org/wiki/Basic_access_authentication)), where the
  identifier can be the user's username or email (IRI not supported).

When using Python's module `requests`, the credentials can simply be submitted as a tuple with
each request using the param `auth` ([python requests](http://docs.python-requests.org/en/master/user/authentication/#basic-authentication)).

When accessing any route and the *token* would need to be sent, we support two options to do so:

- in the URL submitting the parameter `token` (e.g.,
  <http://knora-host/v1/resources/resIri?token=1234567890>)
- in the HTTP authorization header with the
  @extref[HTTP bearer scheme](rfc:6750#section-2.1).

## Checking Credentials

To check the credentials, send a GET request to **/v2/authentication** with the credentials
supplied as URL parameters or HTTP authentication headers as described before, .

## Usage Scenarios

1.  Create token by logging-in, send token on each subsequent request,
    and logout when finished.
2.  Send email/password credentials on every request.

## Sipi (Media Server)

TODO (Should all be done with JWT)
