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

# Feature Toggles

Some Knora features can be turned or off on a per-request basis.
This mechanism is based on
[Feature Toggles (aka Feature Flags)](https://martinfowler.com/articles/feature-toggles.html).

For example, a new feature that introduces a breaking API change may first be
introduced with a feature toggle that leaves it disabled by default, so that clients
can continue using the old functionality.

When the new feature is ready to be tested with client code, the Knora release notes
and documentation will indicate that it can be enabled on a per-request basis, as explained
below.

At a later date, the feature may be enabled by default, and the release notes
will indicate that it can still be disabled on a per-request basis by clients
that are not yet ready to use it.

Most feature toggles have an expiration date, after which they will be removed.

## Request Header

A client can override one or more feature toggles by submitting the HTTP header
`X-Knora-Feature-Toggles`. Its value is a comma-separated list of
toggles. Each toggle starts with the name of the feature. Some features
are available in more than one version; in this case, the feature name
is followed by a colon and the version number. The toggle ends with an equals sign
followed by a boolean value, which can be `on`/`off`, `yes`/`no`, or `true`/`false`.
Using `on`/`off` is recommended for clarity.

For example, to enable version 2 of feature `new-foo`, along with feature `fast-bar`
(which does not have versions):

```
X-Knora-Feature-Toggles: new-foo:2=on,fast-bar=on
```

If a toggle has versions, a version number must be given when enabling it
in a request. It is an error to specify a version number when disabling
a toggle in a request.

## Response Header

Knora API v2 and admin API responses contain the header
`X-Knora-Feature-Toggles-Enabled`, whose value is a comma-separated,
unordered list of toggles that are enabled. The response to the
example above would be:

```
X-Knora-Feature-Toggles-Enabled: new-foo:2,fast-bar
```
