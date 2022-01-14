<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Authentication

Access to the DSP-API can for certain operations require a user to authenticate.
Authentication can be performed in two ways:

1. By providing *password credentials*, which are a combination of a *identifier* and
   *password*. The user *identifier* can be one of the following:
    - the user's IRI,
    - the user's Email, or
    - the user's Username.

2. By providing an *access token*

## Submitting Password Credentials

When accessing any route and password credentials would need to be sent,
we support two options to do so:

- in the URL submitting the parameters `iri` / `email` / `username` and `password`
  (e.g., <http://knora-host/v1/resources/resIri?email=userUrlEncodedIdentifier&password=pw>), and
- in the HTTP header ([HTTP basic
  authentication](https://en.wikipedia.org/wiki/Basic_access_authentication)), where the
  identifier can be the user's `email` (IRI and username not supported).

When using Python's module `requests`, the credentials can simply be submitted as a tuple with
each request using the param `auth` ([python requests](http://docs.python-requests.org/en/master/user/authentication/#basic-authentication)).

## Access Token / Session / Login and Logout

A client can generate an *access token* by sending a POST request (e.g., `{"identifier_type":"identifier_value",
"password":"password_value"}`) to the **/v2/authentication** route with
*identifier* and *password* in the body. The `identifier_type` can be `iri`, `email`, or `username`.
If the credentials are valid, a [JSON WEB Token](https://jwt.io) (JWT) will be sent back in the
response (e.g., `{"token": "eyJ0eXAiOiJ..."}`). Additionally, for web browser clients a session cookie
containing the JWT token is also created, containing `KnoraAuthentication=eyJ0eXAiOiJ...`.

When accessing any route, the *access token* would need to be supplied, we support three options to do so:

- the session cookie,
- in the URL submitting the parameter `token` (e.g., <http://knora-host/v1/resources/resIri?token=1234567890>), and
- in the HTTP authorization header with the [HTTP bearer scheme](https://tools.ietf.org/html/rfc6750#section-2.1).

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
