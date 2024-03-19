<!---
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Authentication

Certain routes are secured and require authentication.
When accessing any secured route we support three options for authentication:

- **Preferred method**: For each request an Access Token is sent in the HTTP authorization header with the
  [HTTP bearer scheme](https://tools.ietf.org/html/rfc6750#section-2.1).
- **Deprecated method**: For each request an Access Token is provided as a cookie in the HTTP request.
- **Deprecated method**: [HTTP basic authentication](https://en.wikipedia.org/wiki/Basic_access_authentication), where
  the username is the user's `email`.

## Access Token / Login and Logout

A client can obtain an *access token* by sending a POST request (e.g., `{"identifier_type":"identifier_value",
"password":"password_value"}`) to the **/v2/authentication** route with
*identifier* and *password* in the body. The `identifier_type` can be `iri`, `email`, or `username`.
If the credentials are valid, a [JSON WEB Token](https://jwt.io) (JWT) will be sent back in the
response (e.g., `{"token": "eyJ0eXAiOiJ..."}`). Additionally, for web browser clients a session cookie
containing the JWT token is also created, containing `KnoraAuthentication=eyJ0eXAiOiJ...`.

To **logout**, the client sends a DELETE request to the same route **/v2/authentication** and
the *access token* in one of the three described ways. This will invalidate the access token,
thus not allowing further request that would supply the invalidated token.

## Checking Credentials

To check the credentials, send a GET request to **/v2/authentication** with the credentials
supplied as URL parameters or HTTP authentication headers as described before.

## Usage Scenarios

1. Create token by logging-in, send token on each subsequent request, and logout when finished.
2. Send email/password credentials on every request.
