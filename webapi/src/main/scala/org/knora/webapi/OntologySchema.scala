package org.knora.webapi

/**
  * Indicates the schema that an ontology or ontology entity conforms to.
  */
sealed trait OntologySchema

/**
  * The schema of Knora ontologies and entities that are used in the triplestore.
  */
case object InternalSchema extends OntologySchema

/**
  * The schema of Knora ontologies and entities that are used in API v2.
  */
sealed trait ApiV2Schema extends OntologySchema

/**
  * The simple schema for representing Knora ontologies and entities. This schema represents values as literals
  * when possible.
  */
case object ApiV2Simple extends ApiV2Schema

/**
  * The default (or complex) schema for representing Knora ontologies and entities. This
  * schema always represents values as objects.
  */
case object ApiV2WithValueObjects extends ApiV2Schema

/**
  * The schema of non-Knora ontologies and entities.
  */
case object NonKnoraSchema extends OntologySchema
