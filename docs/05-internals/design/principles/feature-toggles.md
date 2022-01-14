<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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

- A feature toggle should have a version number, so
  you can get different versions of the same feature.
  
- It should be possible to configure a toggle in `application.conf`
  so that its setting cannot be overridden per request.

- The design of feature toggles should avoid ambiguity and
  try to prevent situations where clients might be surprised by
  unexpected functionality. It should be clear what will change
  when a client requests a particular toggle setting. Therefore,
  per-request settings should require the client to be explicit
  about what is being requested.

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

            available-versions = [ 1, 2 ]
            default-version = 1
            enabled-by-default = yes
            override-allowed = yes

            expiration-date = "2021-12-01T00:00:00Z"

            developer-emails = [
                "A developer <a.developer@example.org>"
            ]
        }

        new-bar {
            description = "Replace the old bar routes with new ones."

            available-versions = [ 1, 2, 3 ]
            default-version = 3
            enabled-by-default = yes
            override-allowed = yes

            expiration-date = "2021-12-01T00:00:00Z"

            developer-emails = [
                "A developer <a.developer@example.org>"
            ]
        }

        fast-baz {
            description = "Replace the slower, more accurate baz route with a faster, less accurate one."

            available-versions = [ 1 ]
            default-version = 1
            enabled-by-default = no
            override-allowed = yes

            developer-emails = [
                "A developer <a.developer@example.org>"
            ]
        }
    }
}
```

All fields are required except `expiration-date`.

Since it may not be possible to predict which toggles will need versions,
all toggles must have at least one version. (If a toggle could be created
without versions, and then get versions later, it would not be obvious
what should happen if a client then requested the toggle without specifying
a version number.) Version numbers must be an ascending sequence of
consecutive integers starting from 1.

If `expiration-date` is provided, it must be an [`xsd:dateTimeStamp`](http://www.datypic.com/sc/xsd11/t-xsd_dateTimeStamp.html). All feature toggles
should have expiration dates except for long-lived ops toggles like `fast-baz` above.

`KnoraSettingsFeatureFactoryConfig` reads this base configuration on startup. If
a feature toggle has an expiration date in the past, a warning is logged
on startup.

### Per-Request Configuration

A client can override the base configuration by submitting the HTTP header
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
got before the toggle existed. A version number cannot
be given when disabling a toggle, because it would not
be obvious what this would mean (disable all versions
or only the specified version).

## Response Header

DSP-API v2 and admin API responses contain the header
`X-Knora-Feature-Toggles`. It lists all configured toggles,
in the same format as the corresponding request header.

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
If a feature toggle has only one version, it is enough to test
whether test if the toggle is enabled, by calling `isEnabled` on the toggle.

If the feature toggle has more than one version, call its `getMatchableState`
method. To allow the compiler to check that matches on version numbers
are exhaustive, this method is designed to be used with a sealed trait
(extending `Version`) that is implemented by case objects representing
the feature's version numbers. The method returns an instance of
`MatchableState`, which is analogous to `Option`: it is either `Off`
or `On`, and an instance of `On` contains one of the version objects.
For example:

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
    // Get the 'new-foo' feature toggle.
    val fooToggle: FeatureToggle = featureFactoryConfig.getToggle("new-foo")
    
    // Choose an implementation according to the toggle state.
    fooToggle.getMatchableState(NEW_FOO_1, NEW_FOO_2) match {
        case Off => oldFoo
        case On(NEW_FOO_1) => newFoo1
        case On(NEW_FOO_2) => newFoo2
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
