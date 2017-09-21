package org.knora.webapi

/**
  * Indicates the schema that an ontology conforms to: internal (for use in the triplestore) or external
  * (for use in the Knora API).
  */
sealed trait OntologySchema

/**
  * Indicates that an ontology is for internal use in the triplestore.
  */
case object InternalSchema extends OntologySchema

/**
  * Indicates that an ontology is for use in Knora API v2.
  */
sealed trait ApiV2Schema extends OntologySchema

/**
  * Indicates that an ontology conforms to the simple Knora API v2 schema, which represents values as literals
  * when possible.
  */
case object ApiV2Simple extends ApiV2Schema

/**
  * Indicates that an ontology conforms to the Knora API v2 schema that always represents values as objects.
  */
case object ApiV2WithValueObjects extends ApiV2Schema
