/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.util.ConstructResponseRdfData.*
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

/**
 * Parser phase of `ConstructResponseUtilV2`: turns a [[SparqlExtendedConstructResponse]] into the
 * `ConstructResponseRdfData` tree (resources, their values, nested dependent resources, incoming links).
 *
 * Conversions of repository IRI strings into typed value objects (e.g. [[ResourceIri]]) fail with an
 * [[InconsistentRepositoryDataException]] — a malformed IRI in CONSTRUCT results means the repository
 * is inconsistent, not a client error.
 */
private[util] object ConstructResponseRdfDataParser {

  private val inferredPredicates = Set(
    OntologyConstants.KnoraBase.HasValue,
    OntologyConstants.KnoraBase.IsMainResource,
  )

  /**
   * Transient holder for assertions plus the requesting user's permission on the corresponding entity.
   */
  private final case class RdfWithUserPermission(
    assertions: ConstructPredicateObjects,
    maybeUserPermission: Option[Permission.ObjectAccess],
  )

  private def asResourceIri(iri: IRI): IO[InconsistentRepositoryDataException, ResourceIri] =
    ZIO.fromEither(ResourceIri.from(iri)).mapError(InconsistentRepositoryDataException.apply)

  /**
   * A [[SparqlConstructResponse]] may contain both resources and value RDF data objects as well as standoff.
   * This method turns a graph (i.e. triples) into a structure organized by the principle of resources and their values,
   * i.e. a map of resource Iris to [[ResourceWithValueRdfData]]. Main resources sit at the top level; dependent resources
   * are nested inside link values.
   */
  def splitMainResourcesAndValueRdfData(
    constructQueryResults: SparqlExtendedConstructResponse,
    requestingUser: User,
  )(implicit
    stringFormatter: StringFormatter,
  ): IO[InconsistentRepositoryDataException, MainResourcesAndValueRdfData] = {

    // Make sure all the subjects are IRIs, because blank nodes are not used in resources.
    val resultsWithIriSubjects: Statements = constructQueryResults.statements.map {
      case (iriSubject: IriSubjectV2, statements: ConstructPredicateObjects) => iriSubject.value -> statements
      case (otherSubject: SubjectV2, _: ConstructPredicateObjects)           =>
        throw InconsistentRepositoryDataException(s"Unexpected subject: $otherSubject")
    }

    // Split statements about resources and other statements (value objects and standoff).
    // Resources are identified by the inferred triple "resourceIri a knora-base:Resource".
    val (resourceStatementsRaw: Statements, nonResourceStatements: Statements) = resultsWithIriSubjects.partition {
      case (_: IRI, assertions: ConstructPredicateObjects) =>
        assertions
          .getOrElse(OntologyConstants.Rdf.Type.toSmartIri, Seq.empty)
          .contains(IriLiteralV2(OntologyConstants.KnoraBase.Resource))
    }

    for {
      resourceStatements <- ZIO
                              .foreach(resourceStatementsRaw.toSeq) { case (resourceIri, statements) =>
                                asResourceIri(resourceIri).map(_ -> statements)
                              }
                              .map(_.toMap: ResourceStatements)

      flatResourcesWithValues: RdfResources =
        resourceStatements.map { case (resourceIri: ResourceIri, assertions: ConstructPredicateObjects) =>
          buildResourceWithValueRdfData(
            resourceIri,
            assertions,
            requestingUser,
            nonResourceStatements,
          )
        }

      (visibleResources, hiddenResources) = flatResourcesWithValues.partition { case (_, r) =>
                                              r.userPermission.nonEmpty
                                            }
      (mainResourceIrisVisible, dependentResourceIrisVisible) = visibleResources.toSet.partitionMap { case (iri, r) =>
                                                                  Either.cond(!r.isMainResource, iri, iri)
                                                                }
      (mainResourceIrisNotVisible, dependentResourceIrisNotVisible) = hiddenResources.toSet.partitionMap {
                                                                        case (iri, r) =>
                                                                          Either.cond(!r.isMainResource, iri, iri)
                                                                      }
      incomingLinksForResource = getIncomingLink(visibleResources, flatResourcesWithValues)

      nestedResources <- ZIO
                           .foreach(mainResourceIrisVisible.toSeq) { resourceIri =>
                             nestResources(
                               depth = 0,
                               resourceIri = resourceIri,
                               flatResourcesWithValues = flatResourcesWithValues,
                               visibleResources = visibleResources,
                               dependentResourceIrisVisible = dependentResourceIrisVisible,
                               dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
                               incomingLinksForResource = incomingLinksForResource,
                             ).map(resourceIri -> _)
                           }
                           .map(_.toMap)
    } yield MainResourcesAndValueRdfData(
      resources = nestedResources,
      hiddenResourceIris = mainResourceIrisNotVisible ++ dependentResourceIrisNotVisible,
    )
  }

  /**
   * Builds a single [[ResourceWithValueRdfData]] from a resource's raw assertions. Pure: no IRI parsing
   * happens here because the caller has already constructed the typed [[ResourceIri]].
   */
  private def buildResourceWithValueRdfData(
    resourceIri: ResourceIri,
    assertions: ConstructPredicateObjects,
    requestingUser: User,
    nonResourceStatements: Statements,
  )(implicit stringFormatter: StringFormatter): (ResourceIri, ResourceWithValueRdfData) = {
    // Drop inferred statements (every resource is a knora-base:Resource, every value property is a
    // sub-property of knora-base:hasValue, every main resource has knora-base:isMainResource true).
    val assertionsExplicit: ConstructPredicateObjects = assertions.filterNot { case (pred: SmartIri, _) =>
      inferredPredicates(pred.toString)
    }.map { case (pred: SmartIri, objs: Seq[LiteralV2]) =>
      if (pred.toString == OntologyConstants.Rdf.Type) {
        pred -> objs.filterNot {
          case IriLiteralV2(OntologyConstants.KnoraBase.Resource) => true
          case _                                                  => false
        }
      } else {
        pred -> objs
      }
    }

    val isMainResource: Boolean = assertions.get(OntologyConstants.KnoraBase.IsMainResource.toSmartIri) match {
      case Some(Seq(BooleanLiteralV2(value))) => value
      case _                                  => false
    }

    val valueObjectIris: Set[IRI] = assertions.collect {
      case (pred: SmartIri, objs: Seq[LiteralV2]) if pred.toString == OntologyConstants.KnoraBase.HasValue =>
        objs.map {
          case IriLiteralV2(iri) => iri
          case other             =>
            throw InconsistentRepositoryDataException(
              s"Unexpected object for $resourceIri knora-base:hasValue: $other",
            )
        }
    }.flatten.toSet

    val valuePropertyToObjectIris: Map[SmartIri, Seq[IRI]] =
      mapPropertyIrisToValueIris(assertionsExplicit, valueObjectIris)

    val valuePropertyToValueObject: RdfPropertyValues = makeRdfPropertyValuesForResource(
      valuePropertyToObjectIris = valuePropertyToObjectIris,
      resourceIri = resourceIri,
      requestingUser = requestingUser,
      assertionsExplicit = assertionsExplicit,
      nonResourceStatements = nonResourceStatements,
    )

    val userPermission: Option[Permission.ObjectAccess] =
      PermissionUtilADM.getUserPermissionFromConstructAssertionsADM(resourceIri.value, assertions, requestingUser)

    resourceIri -> ResourceWithValueRdfData(
      resourceIri = resourceIri,
      assertions = assertionsExplicit,
      isMainResource = isMainResource,
      userPermission = userPermission,
      valuePropertyAssertions = valuePropertyToValueObject,
    )
  }

  /**
   * Converts a [[ConstructPredicateObjects]] to a map of property IRIs to sequences of value IRIs,
   * keeping only object IRIs that are known value object IRIs.
   */
  private def mapPropertyIrisToValueIris(
    assertionsExplicit: ConstructPredicateObjects,
    valueObjectIris: Set[IRI],
  ): Map[SmartIri, Seq[IRI]] =
    assertionsExplicit.map { case (pred: SmartIri, objs: Seq[LiteralV2]) =>
      val valueObjIris: Seq[IriLiteralV2] = objs.collect {
        case iriObj: IriLiteralV2 if valueObjectIris(iriObj.value) => iriObj
      }
      pred -> valueObjIris
    }.filter { case (_: SmartIri, objs: Seq[IriLiteralV2]) =>
      objs.nonEmpty
    }.groupBy { case (pred: SmartIri, _: Seq[IriLiteralV2]) =>
      pred
    }.map { case (pred: SmartIri, valueAssertions: Map[SmartIri, Seq[IriLiteralV2]]) =>
      pred -> valueAssertions.values.flatten.map(_.value).toSeq
    }

  /**
   * Given the assertions that describe a resource and its values, builds an [[RdfPropertyValues]]
   * representing the values, filtered by the requesting user's permissions.
   */
  private def makeRdfPropertyValuesForResource(
    valuePropertyToObjectIris: Map[SmartIri, Seq[IRI]],
    resourceIri: ResourceIri,
    requestingUser: User,
    assertionsExplicit: ConstructPredicateObjects,
    nonResourceStatements: Statements,
  )(implicit stringFormatter: StringFormatter): RdfPropertyValues =
    valuePropertyToObjectIris.map { case (property: SmartIri, valObjIris: Seq[IRI]) =>
      val rdfWithUserPermissionsForValues: Seq[(IRI, RdfWithUserPermission)] = valObjIris.map { (valObjIri: IRI) =>
        val valueObjAssertions: ConstructPredicateObjects = nonResourceStatements(valObjIri)

        // Value objects belong to the parent resource's project; inject the project and compute permissions.
        val resourceProjectLiteral: LiteralV2 = assertionsExplicit
          .getOrElse(
            OntologyConstants.KnoraBase.AttachedToProject.toSmartIri,
            throw InconsistentRepositoryDataException(s"Resource $resourceIri has no knora-base:attachedToProject"),
          )
          .head

        val maybeUserPermission = PermissionUtilADM.getUserPermissionFromConstructAssertionsADM(
          entityIri = valObjIri,
          assertions = valueObjAssertions + (OntologyConstants.KnoraBase.AttachedToProject.toSmartIri -> Seq(
            resourceProjectLiteral,
          )),
          requestingUser = requestingUser,
        )

        valObjIri -> RdfWithUserPermission(valueObjAssertions, maybeUserPermission)
      }

      val visibleRdfWithUserPermissionsForValues: Seq[(IRI, RdfWithUserPermission)] =
        rdfWithUserPermissionsForValues.filter { case (_, holder) => holder.maybeUserPermission.nonEmpty }

      val valueRdfDataForProperty: Seq[ValueRdfData] = visibleRdfWithUserPermissionsForValues.flatMap {
        case (valObjIri: IRI, valueRdfWithUserPermission: RdfWithUserPermission) =>
          val valueIri = ValueIri.unsafeFrom(valObjIri)

          val standoffNodeIris: Set[IRI] = valueRdfWithUserPermission.assertions.collect {
            case (pred: SmartIri, objs: Seq[LiteralV2])
                if pred.toString == OntologyConstants.KnoraBase.ValueHasStandoff =>
              objs.map(_.toString)
          }.flatten.toSet

          val standoffAssertions: FlatStatements = nonResourceStatements.collect {
            case (subjIri: IRI, assertions: ConstructPredicateObjects) if standoffNodeIris(subjIri) =>
              subjIri -> assertions.flatMap { case (pred: SmartIri, objs: Seq[LiteralV2]) =>
                objs.map(obj => pred -> obj)
              }
          }

          val rdfTypeLiteral: LiteralV2 = valueRdfWithUserPermission.assertions
            .getOrElse(
              OntologyConstants.Rdf.Type.toSmartIri,
              throw InconsistentRepositoryDataException(s"Value $valueIri has no rdf:type"),
            )
            .head

          val valueObjectClass: SmartIri = rdfTypeLiteral
            .as[IriLiteralV2]()
            .getOrElse(
              throw InconsistentRepositoryDataException(s"Unexpected object of $valueIri rdf:type: $rdfTypeLiteral"),
            )
            .value
            .toSmartIri

          val standoff =
            if (valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue) emptyFlatStatements
            else standoffAssertions

          Some(
            ValueRdfData(
              valueIri = valueIri,
              valueObjectClass = valueObjectClass,
              userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
              assertions = valueRdfWithUserPermission.assertions,
              standoff = standoff,
            ),
          )
      }

      property -> valueRdfDataForProperty
    }.filterNot { case (_, valObjs) => valObjs.isEmpty }

  /**
   * Returns, for each visible resource, the set of resources that link to it via link values.
   */
  private def getIncomingLink(
    visibleResources: RdfResources,
    flatResourcesWithValues: RdfResources,
  )(implicit stringFormatter: StringFormatter): Map[ResourceIri, RdfResources] =
    visibleResources.map { case (resourceIri: ResourceIri, values: ResourceWithValueRdfData) =>
      val incomingLinksForRes: RdfResources = flatResourcesWithValues.foldLeft(emptyRdfResources) {
        case (acc: RdfResources, (otherResourceIri: ResourceIri, otherResource: ResourceWithValueRdfData)) =>
          val incomingLinkPropertyAssertions: RdfPropertyValues =
            otherResource.valuePropertyAssertions.foldLeft(emptyRdfPropertyValues) {
              case (acc: RdfPropertyValues, (prop: SmartIri, otherResourceValues: Seq[ValueRdfData])) =>
                val incomingLinkValues: Seq[ValueRdfData] = otherResourceValues.foldLeft(Seq.empty[ValueRdfData]) {
                  (acc, value: ValueRdfData) =>
                    if (
                      value.valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue && value
                        .requireIriObject(OntologyConstants.Rdf.Object.toSmartIri) == resourceIri.value
                    ) acc :+ value
                    else acc
                }

                if (incomingLinkValues.nonEmpty) acc + (prop -> incomingLinkValues) else acc
            }

          if (incomingLinkPropertyAssertions.nonEmpty)
            acc + (otherResourceIri -> values.copy(valuePropertyAssertions = incomingLinkPropertyAssertions))
          else acc
      }

      resourceIri -> incomingLinksForRes
    }

  /**
   * Embeds dependent resources (and incoming links) into a resource's link values, recursively.
   * Bounded by a depth limit and an `alreadyTraversed` set to break cycles.
   */
  private def nestResources(
    depth: Int,
    resourceIri: ResourceIri,
    flatResourcesWithValues: RdfResources,
    visibleResources: RdfResources,
    dependentResourceIrisVisible: Set[ResourceIri],
    dependentResourceIrisNotVisible: Set[ResourceIri],
    incomingLinksForResource: Map[ResourceIri, RdfResources],
    alreadyTraversed: Set[ResourceIri] = Set.empty,
  )(implicit stringFormatter: StringFormatter): IO[InconsistentRepositoryDataException, ResourceWithValueRdfData] = {
    val resource = visibleResources(resourceIri)

    if (depth > 15) ZIO.succeed(resource)
    else {
      for {
        transformedValuePropertyAssertions <-
          ZIO
            .foreach(resource.valuePropertyAssertions.toSeq) { case (propIri, values) =>
              transformValuesByNestingResources(
                depth = depth + 1,
                values = values,
                flatResourcesWithValues = flatResourcesWithValues,
                visibleResources = visibleResources,
                dependentResourceIrisVisible = dependentResourceIrisVisible,
                dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
                incomingLinksForResource = incomingLinksForResource,
                alreadyTraversed = alreadyTraversed + resourceIri,
              ).map(propIri -> _)
            }
            .map(_.filter { case (_, vs) => vs.nonEmpty }.toMap: RdfPropertyValues)

        // Ignore already-traversed sources and main resources (those are already on the top level and would
        // otherwise create circular dependencies).
        referringResources = incomingLinksForResource(resourceIri).filterNot { case (incomingResIri, _) =>
                               alreadyTraversed(incomingResIri) || flatResourcesWithValues(
                                 incomingResIri,
                               ).isMainResource
                             }

        incomingLinkAssertions: RdfPropertyValues = referringResources.values.foldLeft(emptyRdfPropertyValues) {
                                                      case (acc, assertions) =>
                                                        assertions.valuePropertyAssertions.flatMap {
                                                          case (propIri, values) =>
                                                            if (acc.contains(propIri))
                                                              acc + (propIri -> (acc(propIri) ++ values)
                                                                .sortBy(_.subjectIri))
                                                            else acc + (propIri -> values.sortBy(_.subjectIri))
                                                        }
                                                    }

        result <- if (incomingLinkAssertions.isEmpty)
                    ZIO.succeed(resource.copy(valuePropertyAssertions = transformedValuePropertyAssertions))
                  else
                    ZIO
                      .foreach(incomingLinkAssertions.values.toSeq.flatten) { linkValue =>
                        for {
                          sourceIri <-
                            asResourceIri(linkValue.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri))
                          source <- nestResources(
                                      depth = depth + 1,
                                      resourceIri = sourceIri,
                                      flatResourcesWithValues = flatResourcesWithValues,
                                      visibleResources = visibleResources,
                                      dependentResourceIrisVisible = dependentResourceIrisVisible,
                                      dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
                                      incomingLinksForResource = incomingLinksForResource,
                                      alreadyTraversed = alreadyTraversed + resourceIri,
                                    )
                        } yield linkValue.copy(nestedResource = Some(source), isIncomingLink = true)
                      }
                      .map { incomingLinkValues =>
                        val incomingProps =
                          OntologyConstants.KnoraBase.HasIncomingLinkValue.toSmartIri -> incomingLinkValues
                        resource.copy(
                          valuePropertyAssertions = transformedValuePropertyAssertions + incomingProps,
                        )
                      }
      } yield result
    }
  }

  /**
   * For each link value in `values`, embeds the dependent target resource into the link value when
   * the user is allowed to see it. Drops the link value when the target is hidden; leaves it without
   * a nested resource when the target is deleted.
   */
  private def transformValuesByNestingResources(
    depth: Int,
    values: Seq[ValueRdfData],
    flatResourcesWithValues: RdfResources,
    visibleResources: RdfResources,
    dependentResourceIrisVisible: Set[ResourceIri],
    dependentResourceIrisNotVisible: Set[ResourceIri],
    incomingLinksForResource: Map[ResourceIri, RdfResources],
    alreadyTraversed: Set[ResourceIri],
  )(implicit stringFormatter: StringFormatter): IO[InconsistentRepositoryDataException, Seq[ValueRdfData]] =
    ZIO.foldLeft(values)(Vector.empty[ValueRdfData]) { (acc, value) =>
      if (value.valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue) {
        for {
          dependentResourceIri <- asResourceIri(value.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri))
          result               <-
            if (alreadyTraversed(dependentResourceIri)) ZIO.succeed(acc :+ value)
            else if (dependentResourceIrisVisible.contains(dependentResourceIri))
              nestResources(
                depth = depth + 1,
                resourceIri = dependentResourceIri,
                flatResourcesWithValues = flatResourcesWithValues,
                visibleResources = visibleResources,
                dependentResourceIrisVisible = dependentResourceIrisVisible,
                dependentResourceIrisNotVisible = dependentResourceIrisNotVisible,
                incomingLinksForResource = incomingLinksForResource,
                alreadyTraversed = alreadyTraversed + dependentResourceIri,
              ).map(dep => acc :+ value.copy(nestedResource = Some(dep)))
            // User isn't allowed to see the dependent resource — drop the link value.
            else if (dependentResourceIrisNotVisible.contains(dependentResourceIri)) ZIO.succeed(acc)
            // Dependent resource is marked as deleted — keep the link value without a nested resource.
            else ZIO.succeed(acc :+ value)
        } yield result
      } else {
        ZIO.succeed(acc :+ value)
      }
    }
}
