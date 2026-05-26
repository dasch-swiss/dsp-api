/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import java.time.Instant
import scala.reflect.ClassTag

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

/**
 * Data model produced by `ConstructResponseUtilV2.splitMainResourcesAndValueRdfData` and consumed by the
 * response builder phase. The types here are pure data containers — no service dependencies — and are
 * shared with `MainQueryResultProcessor` and the search responder.
 */
object ConstructResponseRdfData {

  /**
   * A map of resource IRIs to resource RDF data.
   */
  type RdfResources = Map[ResourceIri, ResourceWithValueRdfData]

  /**
   * A map of subject IRIs to [[ConstructPredicateObjects]] instances.
   */
  type Statements         = Map[IRI, ConstructPredicateObjects]
  type ResourceStatements = Map[ResourceIri, ConstructPredicateObjects]

  /**
   * A flattened map of predicates to objects. This assumes that each predicate has
   * only one object.
   */
  type PredicateObjects = Map[SmartIri, Seq[LiteralV2]]

  /**
   * A map of subject IRIs to flattened maps of predicates to objects.
   */
  type FlatStatements = Map[IRI, Map[SmartIri, LiteralV2]]

  /**
   * A map of property IRIs to value RDF data.
   */
  type RdfPropertyValues = Map[SmartIri, Seq[ValueRdfData]]

  /**
   * Makes an empty instance of [[RdfResources]].
   */
  val emptyRdfResources: RdfResources = Map.empty

  /**
   * Makes an empty instance of [[RdfPropertyValues]].
   */
  val emptyRdfPropertyValues: RdfPropertyValues = Map.empty

  /**
   * Makes an empty instance of [[FlatStatements]].
   */
  val emptyFlatStatements: FlatStatements = Map.empty

  /**
   * Represents assertions about an RDF subject.
   */
  sealed trait RdfData {

    /**
     * The IRI of the subject.
     */
    val subjectIri: IRI

    /**
     * Assertions about the subject.
     */
    val assertions: PredicateObjects

    private def maybeSingleAs[A <: LiteralV2](predicateIri: SmartIri)(implicit tag: ClassTag[A]): Option[A] =
      maybeAs[A](predicateIri).flatMap(_.headOption)

    private def requireSingleAs[A <: LiteralV2](predicateIri: SmartIri)(implicit tag: ClassTag[A]): A =
      maybeSingleAs(predicateIri).getOrElse(
        throw InconsistentRepositoryDataException(s"Subject $subjectIri does not have predicate $predicateIri"),
      )

    private def maybeAs[A <: LiteralV2](predicateIri: SmartIri)(implicit tag: ClassTag[A]): Option[Seq[A]] =
      assertions
        .get(predicateIri)
        .map(
          _.map(literal =>
            literal
              .as[A]()
              .getOrElse(
                throw InconsistentRepositoryDataException(s"Unexpected object of $subjectIri $predicateIri: $literal"),
              ),
          ),
        )

    /**
     * Returns the optional string object of the specified predicate. Throws an exception if the object is not a string.
     */
    def maybeStringObject(predicateIri: SmartIri): Option[String] =
      maybeSingleAs[StringLiteralV2](predicateIri).map(_.value)

    def maybeStringListObject(predicateIri: SmartIri): Option[Seq[String]] =
      maybeAs[StringLiteralV2](predicateIri).map(_.map(_.value))

    /**
     * Returns the required string object of the specified predicate. Throws an exception if the object is not found or
     * is not a string.
     */
    def requireStringObject(predicateIri: SmartIri): String =
      requireSingleAs[StringLiteralV2](predicateIri).value

    /**
     * Returns the optional IRI object of the specified predicate. Throws an exception if the object is not an IRI.
     */
    def maybeIriObject(predicateIri: SmartIri): Option[IRI] =
      maybeSingleAs[IriLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required IRI object of the specified predicate. Throws an exception if the object is not found or
     * is not an IRI.
     */
    def requireIriObject(predicateIri: SmartIri): IRI =
      requireSingleAs[IriLiteralV2](predicateIri).value

    /**
     * Returns the optional integer object of the specified predicate. Throws an exception if the object is not an integer.
     */
    def maybeIntObject(predicateIri: SmartIri): Option[Int] =
      maybeSingleAs[IntLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required integer object of the specified predicate. Throws an exception if the object is not found or
     * is not an integer.
     */
    def requireIntObject(predicateIri: SmartIri): Int =
      requireSingleAs[IntLiteralV2](predicateIri).value

    /**
     * Returns the optional boolean object of the specified predicate. Throws an exception if the object is not a boolean.
     */
    def maybeBooleanObject(predicateIri: SmartIri): Option[Boolean] =
      maybeSingleAs[BooleanLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required boolean object of the specified predicate. Throws an exception if the object is not found or
     * is not a boolean value.
     */
    def requireBooleanObject(predicateIri: SmartIri): Boolean =
      requireSingleAs[BooleanLiteralV2](predicateIri).value

    /**
     * Returns the optional decimal object of the specified predicate. Throws an exception if the object is not a decimal.
     */
    def maybeDecimalObject(predicateIri: SmartIri): Option[BigDecimal] =
      maybeSingleAs[DecimalLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required decimal object of the specified predicate. Throws an exception if the object is not found or
     * is not a decimal value.
     */
    def requireDecimalObject(predicateIri: SmartIri): BigDecimal =
      requireSingleAs[DecimalLiteralV2](predicateIri).value

    /**
     * Returns the optional timestamp object of the specified predicate. Throws an exception if the object is not a timestamp.
     */
    def maybeDateTimeObject(predicateIri: SmartIri): Option[Instant] =
      maybeSingleAs[DateTimeLiteralV2](predicateIri).map(_.value)

    /**
     * Returns the required timestamp object of the specified predicate. Throws an exception if the object is not found or
     * is not a timestamp value.
     */
    def requireDateTimeObject(predicateIri: SmartIri): Instant =
      requireSingleAs[DateTimeLiteralV2](predicateIri).value
  }

  /**
   * Represents the RDF data about a value, possibly including standoff.
   *
   * @param valueIri         the value object's IRI.
   * @param valueObjectClass the type (class) of the value object.
   * @param nestedResource   the nested resource in case of a link value (either the source or the target of a link value, depending on [[isIncomingLink]]).
   * @param isIncomingLink   indicates if it is an incoming or outgoing link in case of a link value.
   * @param userPermission   the permission that the requesting user has on the value.
   * @param assertions       the value objects assertions.
   * @param standoff         standoff assertions, if any.
   */
  case class ValueRdfData(
    valueIri: ValueIri,
    valueObjectClass: SmartIri,
    nestedResource: Option[ResourceWithValueRdfData] = None,
    isIncomingLink: Boolean = false,
    userPermission: Permission.ObjectAccess,
    assertions: PredicateObjects,
    standoff: FlatStatements,
  ) extends RdfData {
    override val subjectIri: IRI = valueIri.value
  }

  /**
   * Represents a resource and its values.
   *
   * @param resourceIri             the resource IRI.
   * @param assertions              assertions about the resource (direct statements).
   * @param isMainResource          indicates if this represents a top level resource or a referred resource (depending on the query).
   * @param userPermission          the permission that the requesting user has on the resource.
   * @param valuePropertyAssertions assertions about value properties.
   */
  case class ResourceWithValueRdfData(
    resourceIri: ResourceIri,
    assertions: PredicateObjects,
    isMainResource: Boolean,
    userPermission: Option[Permission.ObjectAccess],
    valuePropertyAssertions: RdfPropertyValues,
  ) extends RdfData {
    override val subjectIri: IRI = resourceIri.value
  }

  /**
   * Represents a mapping including information about the standoff entities.
   * May include a default XSL transformation.
   *
   * @param mapping           the mapping from XML to standoff and vice versa.
   * @param standoffEntities  information about the standoff entities referred to in the mapping.
   * @param XSLTransformation the default XSL transformation to convert the resulting XML (e.g., to HTML), if any.
   */
  case class MappingAndXSLTransformation(
    mapping: MappingXMLtoStandoff,
    standoffEntities: StandoffEntityInfoGetResponseV2,
    XSLTransformation: Option[String],
  )

  /**
   * Represents a tree structure of resources, values and dependent resources returned by a SPARQL CONSTRUCT query.
   *
   * @param resources          a map of resource Iris to [[ResourceWithValueRdfData]]. The resource Iris represent main resources, dependent
   *                           resources are contained in the link values as nested structures.
   * @param hiddenResourceIris the IRIs of resources that were hidden because the user does not have permission
   *                           to see them.
   */
  case class MainResourcesAndValueRdfData(resources: RdfResources, hiddenResourceIris: Set[ResourceIri] = Set.empty)
}
