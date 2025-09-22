# ADR-009: API `v3` for client-optimized endpoints

Date: 2025-09-22  
RFC: [RFC-018](https://www.notion.so/dasch-swiss/approved-RFC-018-PoC-for-a-FE-friendly-v3-route-get-project-information-2408946b7d40800b9e30dbee39202627)

## Status

Accepted

## Context

DSP-APP currently requires excessive API calls to load a single project page. 
The existing v2 and admin APIs present several challenges:

- **API Design Mismatch**: APIs reflect backend/triplestore structure rather than frontend domain concepts
- **Technical Limitations**: RDF-based v2 API doesn't integrate well with OpenAPI, requires JS-Lib intermediary layer
- **Performance Issues**: Single-entity focus leads to excessive round trips and complex state management
- **Stability Constraints**: v2's public status prevents breaking changes needed for frontend optimization

The team needs an API specifically designed for frontend consumption without the constraints of backward compatibility.

## Decision

We will implement a new v3 API with the following characteristics:

- **Internal, non-public API** explicitly flagged as volatile and subject to change without notice
- **Frontend-optimized** design focusing on complete domain entities and data aggregation
- **Plain JSON** requests/responses with comprehensive OpenAPI specifications
- **Incremental development** starting with high-impact endpoints identified by frontend developers
- **Coexistence** with existing APIs rather than replacement - v2 remains for semantic web use cases

The v3 API will share services and repositories with existing APIs in the long term, 
diverging only at endpoint and REST service layers.

While the focus is on DSP-APP, the v3 API will be designed supporting all internal clients, including DSP-TOOLS.

Initial up-front design of the v3 API will be done in a separate project 
in collaboration between frontend and backend developers.
This document will be updated when the design is finalized.

## Consequences

To be seen.
