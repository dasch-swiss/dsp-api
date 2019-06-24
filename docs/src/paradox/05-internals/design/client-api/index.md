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

# Client API Code Generation

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

In the future, it would also be useful to generate project-specific client
APIs, with class definitions corresponding to project-specific classes.
  
## Implementation

Client APIs are defined in Scala and extend the `ClientApi` trait. There
is currently an implementation for the admin API, called `AdminClientApi`.
A `ClientApi` contains one or more classes extending `ClientEndpoint`.
Each endpoint corresponds to a Knora route definition, and defines functions to
be generated for performing API operations that use the corresponding route.

There is a command-line program, `ClientApiGenerator`, which can generate
client API code for a specified target language. This program has a front end
that requests API class definitions from a running Knora instance and transforms
them into a data structure that is suitable for code generation. The program
supports different back ends for different targets. A back end can use Twirl
templates to generate code.

Currently one back end `TypeScriptBackEnd`, is implemented; it generates code
for use with [knora-api-js-lib](https://github.com/dhlab-basel/knora-api-js-lib).

## Client Function DSL

Client function definitions are written in a Scala DSL. A function definition
looks like this:

```scala
private val User = classRef(OntologyConstants.KnoraAdminV2.UserClass.toSmartIri)

private val getUserGroupMemberships = function {
    "getUserGroupMemberships" description "Gets a user's group memberships." params (
        "user" description "The user whose group memberships are to be retrieved." paramType User
        ) doThis {
        httpGet(path = str("iri") / argMember("user", "id") / str("group-memberships"))
    } returns GroupsResponse
}
```

The `description` keyword specifies a documentation comment describing the function.
A function has `params`, each of which also has a `description`, as well as a `paramType`.
Built-in types are defined in `ClientApi.scala` and extend `ClientObjectType`.
Class types can be constructed using the `classRef` function, as shown above.
If a parameter is optional, use `paramOptionType` instead of `paramType`.

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

```scala
private val AdministrativePermissionResponse = classRef(OntologyConstants.KnoraAdminV2.AdministrativePermissionResponse.toSmartIri)

private val getAdministrativePermission = function {
    "getAdministrativePermission" description "Gets the administrative permissions for a project and group." params(
        "project" description "The project." paramType Project,
        "group" description "The group." paramType Group,
        "permissionType" description "The permission type." paramOptionType StringLiteral
    ) doThis {
        httpGet(
            path = argMember("project", "id") / argMember("group", "id"),
            params = Seq(("permissionType", arg("permissionType")))
        )
    } returns AdministrativePermissionResponse
}
```

Here is an example with a request body:

```scala
private val UserResponse = classRef(OntologyConstants.KnoraAdminV2.UserResponse.toSmartIri)

private val createUser = function {
    "createUser" description "Creates a user." params (
        "user" description "The user to be created." paramType User
        ) doThis {
        httpPost(
            path = emptyPath,
            body = Some(arg("user"))
        )
    } returns UserResponse
}
```

In this case, the request body is the `user` argument that was passed to the function.

The request body can also be a constructed JSON object:

```scala
private val updateUserBasicInformation = function {
    "updateUserBasicInformation" description "Updates an existing user's basic information." params (
        "user" description "The user to be updated." paramType User
        ) doThis {
        httpPut(
            path = str("iri") / argMember("user", "id") / str("BasicUserInformation"),
            body = Some(json(
                "username" -> argMember("user", "username"),
                "email" -> argMember("user", "email"),
                "givenName" -> argMember("user", "givenName"),
                "familyName" -> argMember("user", "familyName"),
                "lang" -> argMember("user", "lang")
            ))
        )
    } returns UserResponse
```

### Function Calls

Instead of performing an HTTP operation directly, a function can call another
function, like this:

```scala
private val getUser = function {
    "getUser" description "Gets a user by a property." params(
        "property" description "The name of the property by which the user is identified." paramType enum("iri", "email", "username"),
        "value" description "The value of the property by which the user is identified." paramType StringLiteral
    ) doThis {
        httpGet(arg("property") / arg("value"))
    } returns UserResponse
}

private val getUserByIri = function {
    "getUserByIri" description "Gets a user by IRI." params (
        "iri" description "The IRI of the user." paramType StringLiteral
        ) doThis {
        getUser withArgs(str("iri"), arg("iri"))
    } returns UserResponse
}
```

