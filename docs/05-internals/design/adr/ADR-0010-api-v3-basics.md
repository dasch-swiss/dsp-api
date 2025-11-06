# ADR-0010: API `v3` basics 

Date: 2025-11- 05 

RFC: [RFC-020](https://www.notion.so/dasch-swiss/review-RFC-020-Api-V3-basics-28b8946b7d40807aa189f3d28beee0b8)

## Context

We want to build a [V3 API (ADR-0009)](./ADR-0009-api-v3.md). 
In this ADR the foundational basics on which we can agree on before modelling specific endpoints in detail are laid out.
This will describe content types, error types and authentication as well as a general approach on how to model the API in the long run.

**ADR-009** has decided the V3 API should be based on **plain JSON requests/responses** with a comprehensive **OpenAPI specification**.

Identified Problems with existing API v2:

- JSON-LD is hard to handle
- Requesting multiple entities is cumbersome
- API design confusing sometimes
    - Supporting too many different identifiers for an entity
    - Overloading responsibilities in the permissions api for example
    - Unclear how certain parameters are passed (header, body, implicit by passing a certain IRI schema…)
    - Projects are not well separated in resource/values api

## Authentication

V3 will accept JWT Bearer tokens in the header only.

## Identifiers for Entities

As an identifier for any entity we will use existing IRIs in the current complex schema.

***Alternative (kind of but not really)***

Create new external identifiers for existing entities.

Advantage:

- New ids could be shorter, more human readable, no need for URL encoding

Drawback:

- Huge effort in data migration.
- Not easy to use with existing code already integrated with  v2 (which supports these iris  supported for all entities).
- It is confusing to have too many different identifiers for an entity.

## **API Design Guidelines**

Generally we would like to follow a RESTful approach to API design with the focus on entities being part of the REST resource.

### Naming Conventions

Entities are always named in plural in the path, e.g. `/v3/projects/`.

Compound words are in camel case, e.g. `resourceIri` `resourceClassIri` in the path, query params as well as in payloads.

### Design Examples

As an example for retrieving information using GET requests for projects, resources and values:

`/v3/projects/:projectIri/resources/:resourceIri/values/:valueIri` - returns a single Value

`/v3/projects/:projectIri/resources/:resourceIri`  - returns a single Resource

`/v3/projects/:projectIri` - returns a single Project

Currently the a Value IRI by design contains the information to which resource/project it belongs to. 
The proposed API design reflects that: a value can only exist by belonging to a resource that itself must belong to a single project.

Retrieving multiple entities within its context is done using a GET request on the respective path using query parameters, for example:
`/v3/projects/:projectIri/resources/?resourceClassIri=???`

Other aspects as for example project boundary crossing searches or exports can be modelled using new paths on the v3 base level:

- `/v3/search/`
- `/v3/export/resources`

### Pagination

Retrieving multiple entities MUST be done using pagination as is currently the case with the [project legal info endpoints](https://api.dasch.swiss/api/docs/#/Admin%20Projects%20(Legal%20Info)).

The default page SHOULD be 1 (i.e. the first page, one indexed), and the default page-size SHOULD be 25.

A paged response look like this:

```json
{
  "data": [
    /** some entity JSON objects **/
  ],
  "pagination": {
    "pageSize": 25,
    "totalItems": 75,
    "totalPages": 3,
    "currentPage": 2
  }
}
```

These endpoints MUST share the common vocabulary for page and size query params `page` and `page-size` .

Designing filtering and ordering commonalities should be considered a separate ADR, if that is even possible.

### Error Responses

A [new error response model has already been discussed in more detail](https://linear.app/dasch/document/error-response-model-b19c53cf4767).

In general error responses:

- MUST allow to provide multiple problems at once.
- MUST contain a code identifying the error which will be translated by the app
- SHOULD contain error details which may be interpolated while translating the app
- SHOULD be human readable for developers

Example:

```json
{
  "message" : "Unable to authenticate",
  "errors" : [
     {
       "code" : "user_not_found",
       "message" : "User https://example.org/userIri not found",
       "details" : {
         "userIri" : "https://example.org/userIri"
       }
  ]
}
```

### Language support

Where the api currently supports different languages we will keep on supporting them and will have to make it explicit in the responses, for the time being we should return all available languages immediately. 
For multilanguage properties the responses should look something like the following example, if a language is not present in the data it may be omitted in the response, as to indicate the lack of a value.

```json
{
   "someValueWithLanguageSupport" : {
     "en" : "English",
     "de" : "Deutsch"
   },
   "someValueWithoutLanguage" : "the string is the value, no knowlegde of what language"
 }
```
