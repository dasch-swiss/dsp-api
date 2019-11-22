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

# Client API Code Generation Framework

@@toc

## Requirements

* Simplify the development of clients that work with Knora APIs.
* Reduce the need for manual changes in client code when Knora APIs change.
* At minimum, generate client API code in
    * TypeScript
    * Python
* Generate:
    * Endpoint definitions containing function definitions in the target language.
    * Class definitions corresponding to the built-in classes that Knora uses in its APIs.
* Include client function definitions in Knora route definitions.

In the future, it would also be useful to generate project-specific client
APIs, with class definitions corresponding to project-specific classes.
  
## Implementation

Client APIs are defined in Scala and extend the `ClientApi` trait. There
is currently an implementation for the admin API, called `AdminClientApi`.
A `ClientApi` contains one or more `KnoraRoute` implementations that extend
`ClientEndpoint`. Each endpoint defines functions to be generated for performing
API operations that use the route.

The route `ClientApiRoute` generates all available client APIs for a specified
target, returning source code in a Zip file. For instructions on using
this route, see
@ref:[Generating Client API Code](../../development/generating-client-apis.md).

This route has a front end, `GeneratorFrontEnd`, which that gets API class
definitions from `OntologyResponderV2` and transforms them into a data structure
that is suitable for code generation. The route supports different back ends for
different targets. A back end determines which files need to be generated,
generates each file using a Twirl template, and arranges the files in the
correct directory structure.

Currently one back end, `TypeScriptBackEnd`, is implemented; it generates code
for use with [knora-api-js-lib](https://github.com/dasch-swiss/knora-api-js-lib).

## Client Function DSL

Client function definitions are written in a Scala DSL. A function definition
looks like this:

@@snip [UsersRouteADM.scala]($src$/org/knora/webapi/routing/admin/UsersRouteADM.scala) { #getUserGroupMembershipsFunction }

The `description` keyword specifies a documentation comment describing the function.
A function has `params`, each of which also has a `description`, as well as a `paramType`.
Built-in types are defined in `ClientApi.scala` and extend `ClientObjectType`.
Class types can be constructed using the `classRef` function, as shown above.

The `doThis` keyword introduces the body of a function, which can be either
an HTTP operation or a function call. After the `doThis` block, `returns`
specifies the return type of the function.

### HTTP Operations

An HTTP operation is introduced by `httpGet`, `httpPost`, `httpPut`, or
`httpDelete`; it takes a `path` and (if it `httpPost` or `httpPut`) an optional
request body. The path consists of elements separated by slashes. Each element
is either `str()` representing a string literal, `arg` representing an argument
that was passed to the function, or `argMember()` representing a member of an
argument.

URL parameters can be added like this:

@@snip [PermissionsRouteADM.scala]($src$/org/knora/webapi/routing/admin/PermissionsRouteADM.scala) { #getAdministrativePermissionByTypeFunction }

Here is an example with a request body:

@@snip [UsersRouteADM.scala]($src$/org/knora/webapi/routing/admin/UsersRouteADM.scala) { #createUserFunction }

In this case, the request body is the `user` argument that was passed to the function.

The request body can also be a constructed JSON object:

@@snip [UsersRouteADM.scala]($src$/org/knora/webapi/routing/admin/UsersRouteADM.scala) { #updateUserPasswordFunction }

### Function Calls

Instead of performing an HTTP operation directly, a function can call another
function, like this:

@@snip [UsersRouteADM.scala]($src$/org/knora/webapi/routing/admin/UsersRouteADM.scala) { #getUserFunction }
@@snip [UsersRouteADM.scala]($src$/org/knora/webapi/routing/admin/UsersRouteADM.scala) { #getUserByIriFunction }

If an argument of the calling function needs to be converted to another type
for the function call, use the `as` keyword as shown above.

## Generated Classes

Many objects have a unique ID, which is present when the object is read or
updated, but not when it is created.

API classes can also have read-only properties. For example, in the admin API,
the `User` class has a `projects` property, whose objects are instances of
`Project`. Similarly, the `Projects` class has a `members` property, whose
objects are instances of `User`. However, when users and projects are created or
updated, these properties are not used.

In TypeScript, it is necessary to avoid circular imports. If the TypeScript
definition of `User` imports the definition of `Project`, the definition of
`Project` cannot import the definition of `User`.

The structure of the generated classes is intended to deal with these issues.
Taking `User` and `Project` as an example:

- The `User` class does not contain an ID or any read-only properties.

- A `StoredUser` class is generated as a subclass of `User`. It provides
  the user's ID, and can be submitted in update operations.

- A `ReadUser` class is generated as a subclass of `StoredUser`. It provides
  the read-only properties.
  
In `ReadUser`, the `projects` property is a collection of `StoredProject`
objects. Since `StoredProject` does not have any read-only properties, it
does not have a property referring to users. This prevents circular imports.

This design works because in the Knora API, a circular reference always involves
a read-only property. For example, the `projects` property of `User` is
read-only, as is the `members` property of `Project`. In the case of a resource,
the property pointing from a resource to a link value is not read-only (you can
submit a resource with an embedded link value), but the property pointing from a
link value to an embedded resource is read-only.

The read-only properties and ID properties are specified in each `ClientApi`.

## Collection Types

`Array[T]` and `Map[K, V]` collection types can be generated and used as the object types
of properties in ordinary classes. The collection type is specified in the IRI of the
property object type, using a Scala-like type annotation syntax, like this:

```
http://api.knora.org/ontology/knora-admin/v2#collection: Map[URI, Array[Permission]]
```

(The local part of the IRI can also be URL-encoded.) The keyword `collection:` indicates
that the rest of the IRI specifies a collection type, which must be an `Array` or `Map` type.
The following literal types can be used:

- `String`
- `Boolean`
- `Integer`
- `Decimal`
- `URI`
- `DateTimeStamp`

Class names (like `Permission`) in the example above refer to classes in the same IRI
namespace as the collection type. The keys of a `Map` must be `String` or `URI`.

`ClientCollectionTypeParser` parses these definitions into `MapType` and `ArrayType`
objects, which can then be used by a language-specific back end to generate type signatures
in the target language.

## Testing

### Library Test Stubs

The generated code depends on handwritten library code to work, but stubs can
be provided to test for compile errors in the generated code.

The directory `webapi/_test_data/typescript-client-mock-src` in the Knora source
tree contains test stubs for the TypeScript client library.

### Test Requests and Responses

The generated code includes a directory `test-data`, containing sample requests
and responses, which can be used to test the generated code without Knora.
