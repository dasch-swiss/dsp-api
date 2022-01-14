<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Feature Toggles

Some Knora features can be turned on or off on a per-request basis.
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

There may be more than one version of a feature toggle. Every feature
toggle has at least one version number, which is an integer. The first
version is 1.

Most feature toggles have an expiration date, after which they will be removed.

## Request Header

A client can override one or more feature toggles by submitting the HTTP header
`X-Knora-Feature-Toggles`. Its value is a comma-separated list of
toggles. Each toggle consists of:

1. its name
2. a colon
3. the version number
4. an equals sign
5. a boolean value, which can be `on`/`off`, `yes`/`no`, or `true`/`false`

Using `on`/`off` is recommended for clarity. For example:

```
X-Knora-Feature-Toggles: new-foo:2=on,new-bar=off,fast-baz:1=on
```

A version number must be given when enabling a toggle.
Only one version of each toggle can be enabled at a time.
If a toggle is enabled by default, and you want a version
other than the default version, simply enable the toggle,
specifying the desired version number. The version number
you specify overrides the default. 

Disabling a toggle means disabling all its versions. When
a toggle is disabled, you will get the functionality that you would have
got before the toggle existed. Therefore, a version number cannot
be given when disabling a toggle.

## Response Header

DSP-API v2 and admin API responses contain the header
`X-Knora-Feature-Toggles`. It lists all configured toggles,
in the same format as the corresponding request header.
