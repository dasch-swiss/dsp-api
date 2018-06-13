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

# Frequently Asked Questions

@@toc { depth=2 }

## Knora Ontologies

### Why doesn't Knora use `rdfs:domain` and `rdfs:range` for consistency checking?

Knora's consistency checking uses Knora-specific properties, which are called
`knora-base:subjectClassConstraint` and `knora-base:objectClassConstraint` in
the `knora-base` ontology, and `knora-api:subjectType` and `knora-api:objectType`
in the `knora-api` ontologies. These properties express *restrictions* on the
possible subjects and objects of a property. If a property's subject or object
does not conform to the specified restrictions, Knora considers it an error.

In contrast,
[the RDF Schema specification says](https://www.w3.org/TR/rdf-schema/#ch_domainrange)
that `rdfs:domain` and `rdfs:range` can be used to "infer additional information"
about the subjects and objects of properties, rather than to enforce restrictions.
This is, in fact, what RDFS reasoners do in practice. For example, consider these
statements:

```
example:hasAuthor rdfs:range example:Person .
data:book1 example:hasAuthor data:oxygen .
```

To an RDFS reasoner, the first statement means: if something is used as
the object of `example:hasAuthor`, we can infer that it's an
`example:Person`.

The second statement is a mistake; oxygen is not a person. But
an RDFS reasoner would infer that `data:oxygen` is actually an
`example:Person`, since it is used as the object of
`example:hasAuthor`. Queries looking for persons would then get
`data:oxygen` in their results, which would be incorrect.

Therefore, `rdfs:domain` and `rdfs:range` are not suitable for consistency
checking.

Knora therefore uses its own properties, along with
OWL cardinalities, which it interprets according to a "closed world"
assumption. Knora performs its own consistency checks to enforce
these restrictions. Knora repositories can also take advantage of
triplestore-specific consistency checking mechanisms.

The constraint language [SHACL](https://www.w3.org/TR/shacl/) may someday
provide a standard, triplestore-independent way to implement consistency
checks, if the obstacles to its adoption can be overcome
(see [Diverging views of SHACL](https://research.nuance.com/diverging-views-of-shacl/)).
For further discussion of these issues, see
[SHACL and OWL Compared](http://spinrdf.org/shacl-and-owl.html).
