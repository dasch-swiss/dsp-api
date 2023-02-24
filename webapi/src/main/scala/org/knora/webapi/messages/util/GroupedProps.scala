package org.knora.webapi.messages.util
import org.knora.webapi.IRI

/**
 * Represents SPARQL results to be converted into [[org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1]] objects.
 */
object GroupedProps {

  /**
   * Contains the three types of [[GroupedProperties]] returned by a SPARQL query.
   *
   * @param groupedOrdinaryValueProperties properties pointing to ordinary Knora values (i.e. not link values).
   * @param groupedLinkValueProperties     properties pointing to link value objects (reifications of links to resources).
   * @param groupedLinkProperties          properties pointing to resources.
   */
  case class GroupedPropertiesByType(
    groupedOrdinaryValueProperties: GroupedProperties,
    groupedLinkValueProperties: GroupedProperties,
    groupedLinkProperties: GroupedProperties
  )

  /**
   * Represents the grouped properties of one of the three types.
   *
   * @param groupedProperties The grouped properties: The Map's keys (IRI) consist of resource properties (e.g. http://www.knora.org/ontology/knora-base#hasComment).
   */
  case class GroupedProperties(groupedProperties: Map[IRI, ValueObjects])

  /**
   * Represents the value objects belonging to a resource property
   *
   * @param valueObjects The value objects: The Map's keys consist of value object Iris.
   */
  case class ValueObjects(valueObjects: Map[IRI, ValueProps])

  /**
   * Represents the grouped values of a value object.
   *
   * @param valuesLiterals the values (literal or linking).
   * @param standoff       standoff nodes, if any.
   */
  case class GroupedValueObject(valuesLiterals: Map[String, ValueLiterals], standoff: Map[IRI, Map[IRI, String]])

  /**
   * Represents the object properties belonging to a value object
   *
   * @param valueIri    the IRI of the value object.
   * @param literalData the value properties: The Map's keys (IRI) consist of value object properties (e.g. http://www.knora.org/ontology/knora-base#String).
   * @param standoff    the keys of the first Map are the standoff node Iris, the second Map contains all the predicates and objects related to one standoff node.
   */
  case class ValueProps(
    valueIri: IRI,
    literalData: Map[IRI, ValueLiterals],
    standoff: Map[IRI, Map[IRI, String]] = Map.empty[IRI, Map[IRI, String]]
  )

  /**
   * Represents the literal values of a property (e.g. a number or a string)
   *
   * @param literals the literal values of a property.
   */
  case class ValueLiterals(literals: Seq[String])
}
