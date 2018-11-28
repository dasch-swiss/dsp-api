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

### JSON in API v1

Knora API v1 parses and generates JSON using the
[spray-json](https://github.com/spray/spray-json) library.

The triplestore returns results in JSON, and these are parsed into
`SparqlSelectResponse` objects in the `store` package (by `SparqlUtils`,
which can be used by any actor in that package). A
`SparqlSelectResponse` has a structure that's very close to the JSON
returned by a triplestore via the [SPARQL 1.1
Protocol](http://www.w3.org/TR/sparql11-protocol/): it contains a header
(listing the variables that were used in the query) and a body
(containing rows of query results). Each row of query results is
represented by a `VariableResultsRow`, which contains a `Map[String,
String]` of variable names to values.

The `Jsonable` trait marks classes that can convert themselves into
spray-json AST objects when you call their `toJsValue` method; it
returns a `JsValue` object, which can then be converted to text by
calling its `prettyPrint` or `compactPrint` methods. Case classes
representing complete API responses extend the `KnoraResponseV1` trait,
which extends `Jsonable`. Case classes representing Knora values extend
the `ApiValueV1` trait, which also extends `Jsonable`. To make the
responders reusable, the JSON for API responses is generated only at the
last moment, by the `RouteUtilV1.runJsonRoute()` function.
