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

For an overview of feature toggles, see
[Feature Toggles (aka Feature Flags)](https://martinfowler.com/articles/feature-toggles.html).
The design presented here is partly inspired by that article.

## Requirements

- It should be possible to turn features on and off by:

  - changing a setting in `application.conf`
  
  - sending a particular HTTP header value with an API request

  - (in the future) using a web-based user interface to configure a
    feature toggle service that multiple subsystems can access
    
    
- Feature implementations should be produced by factory classes,
  so that the code using a feature does not need to know
  about the toggling decision.
  
- Feature factories should use toggle configuration taken
  from different sources, without knowing where the configuration
  came from.
  
- An HTTP response should indicate which features are turned
  on.

- A feature toggle should have metadata such as a description,
  an expiration date, developer contact information, etc.

- A feature toggle should have an optional version number, so
  you can get different versions of the same feature.
  
- It should be possible to configure a toggle in `application.conf`
  so that its setting cannot be overridden per request.

## Design

### Configuration

### Base Configuration

The base configuration of feature toggles is in `application.conf`
under `app.feature-toggles`. Example:

```
app {
    feature-toggles {
        new-foo {
            description = "Replace the old foo routes with new ones."

            available-versions = [
                1, 2
            ]

            developer-emails = [
                "A developer <a.developer@example.org>"
            ]

            expiration-date = "2021-12-01T00:00:00Z"
            enabled-by-default = yes
            default-version = 1
            override-allowed = yes
        }

        fast-bar {
            description = "Replace the slower, more accurate bar route with a faster, less accurate one."

            developer-emails = [
                "A developer <a.developer@example.org>"
            ]

            enabled-by-default = no
            override-allowed = yes
        }
    }
}
```

All fields are required except `available-versions`, `default-version`, and `expiration-date`.

If `available-versions` is provided, `default-version` is required, and vice versa.
Version numbers must be an ascending sequence of consecutive integers starting from 1.

If `expiration-date` is provided, it must be an [`xsd:dateTimeStamp`](http://www.datypic.com/sc/xsd11/t-xsd_dateTimeStamp.html). All feature toggles
should have expiration dates except for long-lived ops toggles like `fast-bar` above.

`KnoraSettingsFeatureFactoryConfig` reads this base configuration on startup. If
a feature toggle has an expiration date in the past, the application will not start.

### Per-Request Configuration

A client can override the base configuration by submitting the HTTP header
`X-Knora-Feature-Toggles`. Its value is a comma-separated list of
toggles. Each toggle starts with the name of the feature. If a specific
version is being requested, this is followed by a colon and the version
number. The toggle ends with an equals sign followed by a boolean
value, which can be `on`/`off`, `yes`/`no`, or `true`/`false`. Using
`on`/`off` is recommended for clarity. For example:

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

## Implementation Framework

A `FeatureFactoryConfig` reads feature toggles from some
configuration source, and optionally delegates to a parent
`FeatureFactoryConfig`.

`KnoraRoute` constructs a `KnoraSettingsFeatureFactoryConfig`
to read the base configuration. For each request, it
constructs a `RequestContextFeatureFactoryConfig`, which
reads the per-request configuration and has the
`KnoraSettingsFeatureFactoryConfig` as its parent.
It then passes the per-request configuration object to the `makeRoute`
method, which can in turn pass it to a feature factory,
or send it in a request message to allow a responder to
use it.

### Feature Factories

The traits `FeatureFactory` and `Feature` are just tagging traits,
to make code clearer. The factory methods in a feature
factory will depend on the feature, and need only be known by
the code that uses the feature. The only requirement is that
each factory method must take a `FeatureFactoryConfig` parameter.

To get a `FeatureToggle`, a feature factory
calls `featureFactoryConfig.getToggle`, passing the name of the toggle.
To test if the toggle is enabled, call `isEnabled` on the toggle.

To get the configured version of the toggle, call its `checkVersion`
method. To allow the compiler to check that matches on version numbers
are exhaustive, this method is designed to be used with a sealed trait
(extending `Version`) that is implemented by case objects representing
the feature's version numbers. For example:

```
// A trait for version numbers of the new 'foo' feature.
sealed trait NewFooVersion extends Version

// Represents version 1 of the new 'foo' feature.
case object NEW_FOO_1 extends NewFooVersion

// Represents version 2 of the new 'foo' feature.
case object NEW_FOO_2 extends NewFooVersion

// The old 'foo' feature implementation.
private val oldFoo = new OldFooFeature

// The new 'foo' feature implementation, version 1.
private val newFoo1 = new NewFooVersion1Feature

// The new 'foo' feature implementation, version 2.
private val newFoo2 = new NewFooVersion2Feature

def makeFoo(featureFactoryConfig: FeatureFactoryConfig): Foo = {
    // Is the 'new-foo' feature toggle enabled?
    val fooToggle: FeatureToggle = featureFactoryConfig.getToggle("new-foo")
    
    if (fooToggle.isEnabled) {
        // Yes. Which version is enabled?
        fooToggle.checkVersion(NEW_FOO_1, NEW_FOO_2) match {
            case NEW_FOO_1 =>
                // Version 1.
                newFoo1
    
            case NEW_FOO_2 =>
                // Version 2.
                newFoo2
        }
    } else {
        // No, the feature is disabled. Use the old implementation.
        oldFoo
    }
}
```

### Routes as Features

To select different routes according to a feature toggle:

- Make a feature factory that extends `KnoraRouteFactory` and `FeatureFactory`,
  and has a `makeRoute` method that returns different implementations,
  each of which extends `KnoraRoute` and `Feature`.

- Make a façade route that extends `KnoraRoute`, is used in
  `ApplicationActor.apiRoutes`, and has a `makeRoute` method that
  delegates to the feature factory.

To avoid constructing redundant route instances, each façade route needs its
own feature factory class.

### Documenting a Feature Toggle

The behaviour of each possible setting of each feature toggle should be
documented. Feature toggles that are configurable per request should be described
in the release notes.

### Removing a Feature Toggle

To facilitate removing a feature toggle, each implementation should have:

- a separate file for its source code

- a separate file for its documentation

When the toggle is removed, the files that are no longer needed can be
deleted.
