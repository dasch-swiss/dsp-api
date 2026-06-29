# Getting Lists

## Getting a complete List

In order to request a complete list, make a HTTP GET request to the `lists` route, 
appending the Iri of the list's root node (URL-encoded):

```text
HTTP GET to http://host/v2/lists/listRootNodeIri
```

Lists are only returned in the complex schema. 
The response to a list request is a `List` (see interface `List` in module `ListResponse`). 


## Getting a single Node

In order to request a single node of a list, make a HTTP GET request to the `node` route,
appending the node's Iri (URL-encoded):

```text
HTTP GET to http://host/v2/node/nodeIri
```

Nodes are only returned in the complex schema.
The response to a node request is a `ListNode` (see interface `List` in module `ListResponse`).

## All-languages mode

By default, values for `rdfs:label` and `rdfs:comment` are returned only
in the user's preferred language, or in the system default language. To
obtain these values in all available languages, add the URL parameter
`?allLanguages=true` to either the list or node request:

```text
HTTP GET to http://host/v2/lists/listRootNodeIri?allLanguages=true
HTTP GET to http://host/v2/node/nodeIri?allLanguages=true
```

This mirrors the same query parameter on the ontology-information endpoint
(see [Ontology Information](ontology-information.md)).

In all-languages mode, `rdfs:label` and `rdfs:comment` are returned as
JSON-LD arrays of language-tagged objects, sorted alphabetically by their
BCP-47 language tag. If a node has no comment in any language, the
`rdfs:comment` key is omitted entirely. The response is independent of
the caller's profile language.

### Default mode (legacy)

```json
{
  "rdfs:label": "Tree list root",
  "rdfs:comment": "Anything Tree List",
  "@type": "knora-api:ListNode",
  "@id": "http://rdfh.ch/lists/0001/treeList"
}
```

### All-languages mode response

```json
{
  "rdfs:label": [
    {
      "@value": "Listenwurzel",
      "@language": "de"
    },
    {
      "@value": "Tree list root",
      "@language": "en"
    }
  ],
  "rdfs:comment": [
    {
      "@value": "Anything Tree List",
      "@language": "en"
    }
  ],
  "@type": "knora-api:ListNode",
  "@id": "http://rdfh.ch/lists/0001/treeList"
}
```
