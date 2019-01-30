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

# Ontology Management

@@toc

The core of Knora's ontology management logic is `OntologyResponderV2`.
It is responsible for:

- Loading ontologies from the triplestore when Knora starts.
- Maintaining an ontology cache to improve performance.
- Returning requested ontology entities from the cache. Requests for ontology
  information never access the triplestore.
- Creating and updating ontologies in response to API requests.
- Ensuring that all user-created ontologies are consistent and conform to @ref:[knora-base](../../../02-knora-ontologies/knora-base.md).

When Knora starts, the ontology responder receives a `LoadOntologiesRequestV2`
message. It then:

1. Loads all ontologies found in the triplestore into suitable Scala data structures,
   which include indexes of relations between entities (e.g. `rdfs:subClassOf` relations),
   to facilitate validity checks.
2. Checks user-created ontologies for consistency and conformance to `knora-base`,
   according to the rules described in
   @ref:[Summary of Restrictions on User-Created Ontologies](../../../02-knora-ontologies/knora-base.md#summary-of-restrictions-on-user-created-ontologies).
3. Caches all the loaded ontologies using `CacheUtil`.

The ontology responder assumes that nothing except itself modifies ontologies
in the triplestore while Knora is running. Therefore, the ontology cache is updated
only when the ontology responder processes a request to update an ontology.

By design, the ontology responder can update only one ontology entity per request,
to simplify the necessary validity checks. This requires the client to
construct an ontology by submitting a sequence of requests in a certain order,
as explained in
@ref:[Ontology Updates](../../../03-apis/api-v2/ontology-information.md#ontology-updates).

The ontology responder mainly works with ontologies in the internal schema.
However, it knows that some entities in built-in ontologies have hard-coded
definitions in external schemas, and it checks the relevant
transformation rules and returns those entities directly when they are requested
(see @ref:[Generation of Ontologies in External Schemas](ontology-schemas.md#generation-of-ontologies-in-external-schemas)).
