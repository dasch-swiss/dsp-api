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

# Authentication in Knora

@@toc

## Scope

Authentication is the process of making sure that if someone is
accessing something then this someone is actually also the someone he
pretends to be. The process of making sure that someone is authorized,
i.e. has the permission to access something, is handled as described in
@ref:[Authorisation](../../../02-knora-ontologies/knora-base.md#authorisation)).

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

There are some differences in `V1` and `V2` of the API regarding
authentication. They differ mainly in the format of the response and
that creation of session cookies are only supported in `V1` and tokens
in `V2`. After login via either version, all routes (`V1` and `V2`) are
accessible.

## Skipping Authentication

There is the possibility to turn skipping authentication on and use a
hardcoded user (Test User). In **application.conf** set the
`skip-authentication = true` and Test User will be always assumed.

## Sipi (Media Server)

For authentication to work with the media server, we need to add support
for cookies. At the moment the SALSAH-App would set BasicAuth heathers,
but this only works for AJAX requests using `SALSAH.ApiGet` (`Put`,
etc.). Since the medias are embedded as source tags, the browser would
get them on his own, and doesn't know anything about the needed
AuthHeathers. With cookies, the browser would send those automatically
with every request. The media server can use the credentials of the user
requesting something for accessing the RepresentationsRouteV1, i.e. make
this request in the name of the user so to speak, then the
RepresentationResponderV1 should have all the information it needs to
filter the result based on the users permissions.

## Improving Security

In the first iteration, the email/password would be sent in clear text.
Since we will use HTTPS this shouldn't be a problem. The second
iteration, could encrypt the email/password.
