# Draft Design for Feature Toggles

The design here is loosely inspired by the discussion at
[Feature Toggles (aka Feature Flags)](https://martinfowler.com/articles/feature-toggles.html).

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
  on. (Included in the design but not in the proof of concept.)

- A feature toggle should have metadata such as a description,
  an expiration date, developer contact information, etc.

- A feature toggle should have an optional version number, so
  you can get different versions of the same feature.
  
- It should be possible to configure a toggle in `application.conf`
  so that its setting cannot be overridden per request.

## Design

### Configuration

A `FeatureFactoryConfig` reads feature toggles from some
configuration source, and optionally delegates to a parent
`FeatureFactoryConfig`.

The base configuration comes from a `KnoraSettingsFeatureFactoryConfig`,
which can be constructed by a Knora route. It reads
toggles from `application.conf` under `app.feature`. 

When an HTTP request is received, the route can construct
a `RequestContextFeatureFactoryConfig`, giving it the
`KnoraSettingsFeatureFactoryConfig` as its parent. The
`RequestContextFeatureFactoryConfig` reads toggles from
the HTTP header `X-Knora-Feature-Toggle`. Per-request
toggles can then override the ones in `application.conf`.

A `KnoraRoute` can itself be a toggled feature. There is a
proof-of-concept in `ListsRouteADM`. By changing the
setting `app.feature.new-list-admin-routes` in `application.conf`,
you can replace one implementation of `ListsRouteADM`
with another. You can also override this setting by sending
the HTTP header

```
X-Knora-Feature-Toggle: new-list-admin-routes=on
```

or

```
X-Knora-Feature-Toggle: new-list-admin-routes=off
```

Try it with this URL:

```
http://0.0.0.0:3333/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList
```

With `new-list-admin-routes=true`, you should get the message
`You are using the new list API` in the response.

If we decide to add a separate feature toggle configuration
service with its own HTTP API, we would just have to add another
implementation of `FeatureFactoryConfig`, which could sit between
the two mentioned above.

### Feature Factories

The traits `FeatureFactory` and `Feature` are just tagging traits,
to make code clearer. The factory methods in a feature
factory will depend on the feature, and need only be known by
the code that uses the feature. The only requirement is that
each factory method must take a `FeatureFactoryConfig` parameter.
In the example above, `AdminRouteFeatureFactory` has the method:

```
def getListsRoute(featureFactoryConfig: FeatureFactoryConfig): Route
```

The existing `ListsRouteADM` has been refactored so that its
`knoraApiPath` method calls that factory method to get the selected
feature.
