<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Store Module

## Overview

**GraphDB and embedded Jena TDB triplestores support is deprecated** since 
[v20.1.1](https://github.com/dasch-swiss/dsp-api/releases/tag/v20.1.1) of DSP-API.

The store module houses the different types of data stores supported by
Knora. At the moment, only triplestores and IIIF servers (Sipi) are supported.
The triplestore support is implemented in the
`org.knora.webapi.store.triplestore` package and the IIIF server support in
`org.knora.webapi.store.iiif` package.

## Lifecycle

At the top level, the store package houses the `StoreManager`-Actor
which is started when Knora starts. The `StoreManager` then starts the
`TriplestoreManager` and `IIIFManager`, which each in turn starts their
correct actor implementation.

## Triplestores

Currently, the only supported triplestore is [Apache Jena Fuseki](https://jena.apache.org), a HTTP-based triplestore.

HTTP-based triplestore support is implemented in the `org.knora.webapi.triplestore.http` package.

An HTTP-based triplestore is one that is accessed remotely over the HTTP
protocol. `HttpTriplestoreConnector` supports the open source triplestore [Apache Jena Fuseki](https://jena.apache.org).


## IIIF Servers

Currently, only support for SIPI is implemented in `org.knora.webapi.store.iiifSipiConnector`.
