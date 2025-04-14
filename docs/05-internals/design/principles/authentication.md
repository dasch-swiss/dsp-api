# Authentication in Knora

## Scope

Authentication is the process of making sure that if someone is
accessing something then this someone is actually also the person they
pretend to be. The process of making sure that someone is authorized,
(i.e. has the permission to access something, is handled as described
in [Authorisation](../../../02-dsp-ontologies/knora-base.md#authorisation)).

## Implementation

The authentication in Knora is based on Basic Auth [HTTP basic
authentication](https://en.wikipedia.org/wiki/Basic_access_authentication),
URL parameters, [JSON Web Token](https://jwt.io), and cookies. This means
that on every request (to any of the routes), credentials need to be
sent either via authorization header, URL parameters or cookie header.

All routes are always accessible and if there are no credentials
provided, a default user is assumed. If credentials are sent and they
are not correct (e.g., wrong username, password incorrect, token
expired), then the request will end in an error message.

## Skipping Authentication

There is the possibility to turn skipping authentication on and use a
hardcoded user (Test User). In **application.conf** set the
`skip-authentication = true` and Test User will be always assumed.
