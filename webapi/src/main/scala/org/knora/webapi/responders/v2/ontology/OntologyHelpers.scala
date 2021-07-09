/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter.{SalsahGuiAttribute, SalsahGuiAttributeDefinition}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.{SparqlSelectResult, VariableResultsRow}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.responders.v2.ontology.Cache.OntologyCacheData
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.{ApiV2Complex, ApiV2Schema, ApiV2Simple, IRI, InternalSchema, OntologySchema}

import java.time.Instant
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object OntologyHelpers {

  /**
   * Represents the contents of a named graph representing an ontology.
   *
   * @param ontologyIri       the ontology IRI, which is also the IRI of the named graph.
   * @param constructResponse the triplestore's response to a CONSTRUCT query that gets the contents of the named graph.
   */
  case class OntologyGraph(ontologyIri: SmartIri, constructResponse: SparqlExtendedConstructResponse)

  /**
   * Given the triplestore's response to `getAllOntologyMetadata.scala.txt`, constructs a map of ontology IRIs
   * to ontology metadata for the ontology cache.
   *
   * @param allOntologyMetadataResponse the triplestore's response to the SPARQL query `getAllOntologyMetadata.scala.txt`.
   * @return a map of ontology IRIs to ontology metadata.
   */
  def buildOntologyMetadata(
    allOntologyMetadataResponse: SparqlSelectResult
  )(implicit stringFormatter: StringFormatter): Map[SmartIri, OntologyMetadataV2] =
    allOntologyMetadataResponse.results.bindings.groupBy(_.rowMap("ontologyGraph")).map {
      case (ontologyGraph: IRI, rows: Seq[VariableResultsRow]) =>
        val ontologyIri = rows.head.rowMap("ontologyIri")

        if (ontologyIri != ontologyGraph) {
          throw InconsistentRepositoryDataException(
            s"Ontology $ontologyIri must be stored in named graph $ontologyIri, but it is in $ontologyGraph"
          )
        }

        val ontologySmartIri = ontologyIri.toSmartIri

        if (!ontologySmartIri.isKnoraOntologyIri) {
          throw InconsistentRepositoryDataException(s"Ontology $ontologySmartIri is not a Knora ontology")
        }

        val ontologyMetadataMap: Map[IRI, String] = rows.map { row =>
          val pred =
            row.rowMap.getOrElse(
              "ontologyPred",
              throw InconsistentRepositoryDataException(s"Empty predicate in ontology $ontologyIri")
            )
          val obj = row.rowMap.getOrElse(
            "ontologyObj",
            throw InconsistentRepositoryDataException(s"Empty object for predicate $pred in ontology $ontologyIri")
          )
          pred -> obj
        }.toMap

        val projectIri: SmartIri = ontologyMetadataMap
          .getOrElse(
            OntologyConstants.KnoraBase.AttachedToProject,
            throw InconsistentRepositoryDataException(s"Ontology $ontologyIri has no knora-base:attachedToProject")
          )
          .toSmartIri
        val ontologyLabel: String =
          ontologyMetadataMap.getOrElse(OntologyConstants.Rdfs.Label, ontologySmartIri.getOntologyName)
        val lastModificationDate: Option[Instant] = ontologyMetadataMap
          .get(OntologyConstants.KnoraBase.LastModificationDate)
          .map(instant =>
            stringFormatter.xsdDateTimeStampToInstant(
              instant,
              throw InconsistentRepositoryDataException(s"Invalid UTC instant: $instant")
            )
          )
        val ontologyVersion: Option[String] = ontologyMetadataMap.get(OntologyConstants.KnoraBase.OntologyVersion)

        ontologySmartIri -> OntologyMetadataV2(
          ontologyIri = ontologySmartIri,
          projectIri = Some(projectIri),
          label = Some(ontologyLabel),
          lastModificationDate = lastModificationDate,
          ontologyVersion = ontologyVersion
        )
    }

  /**
   * Reads an ontology's metadata.
   *
   * @param internalOntologyIri  the ontology's internal IRI.
   * @param featureFactoryConfig the feature factory configuration.
   * @return an [[OntologyMetadataV2]], or [[None]] if the ontology is not found.
   */
  def loadOntologyMetadata(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
    internalOntologyIri: SmartIri,
    featureFactoryConfig: FeatureFactoryConfig
  )(implicit
    executionContext: ExecutionContext,
    stringFormatter: StringFormatter,
    timeout: Timeout
  ): Future[Option[OntologyMetadataV2]] = {
    for {
      _ <- Future {
             if (!internalOntologyIri.getOntologySchema.contains(InternalSchema)) {
               throw AssertionException(s"Expected an internal ontology IRI: $internalOntologyIri")
             }
           }

      getOntologyInfoSparql = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                                .getOntologyInfo(
                                  triplestore = settings.triplestoreType,
                                  ontologyIri = internalOntologyIri
                                )
                                .toString()

      getOntologyInfoResponse <- (storeManager ? SparqlConstructRequest(
                                   sparql = getOntologyInfoSparql,
                                   featureFactoryConfig = featureFactoryConfig
                                 )).mapTo[SparqlConstructResponse]

      metadata: Option[OntologyMetadataV2] = if (getOntologyInfoResponse.statements.isEmpty) {
                                               None
                                             } else {
                                               getOntologyInfoResponse.statements.get(
                                                 internalOntologyIri.toString
                                               ) match {
                                                 case Some(statements: Seq[(IRI, String)]) =>
                                                   val statementMap: Map[IRI, Seq[String]] = statements.groupBy {
                                                     case (pred, _) => pred
                                                   }.map { case (pred, predStatements) =>
                                                     pred -> predStatements.map { case (_, obj) =>
                                                       obj
                                                     }
                                                   }

                                                   val projectIris: Seq[String] = statementMap.getOrElse(
                                                     OntologyConstants.KnoraBase.AttachedToProject,
                                                     throw InconsistentRepositoryDataException(
                                                       s"Ontology $internalOntologyIri has no knora-base:attachedToProject"
                                                     )
                                                   )
                                                   val labels: Seq[String] = statementMap.getOrElse(
                                                     OntologyConstants.Rdfs.Label,
                                                     Seq.empty[String]
                                                   )
                                                   val comments: Seq[String] = statementMap.getOrElse(
                                                     OntologyConstants.Rdfs.Comment,
                                                     Seq.empty[String]
                                                   )
                                                   val lastModDates: Seq[String] =
                                                     statementMap.getOrElse(
                                                       OntologyConstants.KnoraBase.LastModificationDate,
                                                       Seq.empty[String]
                                                     )

                                                   val projectIri = if (projectIris.size > 1) {
                                                     throw InconsistentRepositoryDataException(
                                                       s"Ontology $internalOntologyIri has more than one knora-base:attachedToProject"
                                                     )
                                                   } else {
                                                     projectIris.head.toSmartIri
                                                   }

                                                   if (!internalOntologyIri.isKnoraBuiltInDefinitionIri) {
                                                     if (
                                                       projectIri.toString == OntologyConstants.KnoraAdmin.SystemProject
                                                     ) {
                                                       throw InconsistentRepositoryDataException(
                                                         s"Ontology $internalOntologyIri cannot be in project ${OntologyConstants.KnoraAdmin.SystemProject}"
                                                       )
                                                     }

                                                     if (
                                                       internalOntologyIri.isKnoraSharedDefinitionIri && projectIri.toString != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
                                                     ) {
                                                       throw InconsistentRepositoryDataException(
                                                         s"Shared ontology $internalOntologyIri must be in project ${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}"
                                                       )
                                                     }
                                                   }

                                                   val label: String = if (labels.size > 1) {
                                                     throw InconsistentRepositoryDataException(
                                                       s"Ontology $internalOntologyIri has more than one rdfs:label"
                                                     )
                                                   } else if (labels.isEmpty) {
                                                     internalOntologyIri.getOntologyName
                                                   } else {
                                                     labels.head
                                                   }

                                                   val comment: Option[String] = if (comments.size > 1) {
                                                     throw InconsistentRepositoryDataException(
                                                       s"Ontology $internalOntologyIri has more than one rdfs:comment"
                                                     )
                                                   } else comments.headOption

                                                   val lastModificationDate: Option[Instant] =
                                                     if (lastModDates.size > 1) {
                                                       throw InconsistentRepositoryDataException(
                                                         s"Ontology $internalOntologyIri has more than one ${OntologyConstants.KnoraBase.LastModificationDate}"
                                                       )
                                                     } else if (lastModDates.isEmpty) {
                                                       None
                                                     } else {
                                                       val dateStr = lastModDates.head
                                                       Some(
                                                         stringFormatter.xsdDateTimeStampToInstant(
                                                           dateStr,
                                                           throw InconsistentRepositoryDataException(
                                                             s"Invalid ${OntologyConstants.KnoraBase.LastModificationDate}: $dateStr"
                                                           )
                                                         )
                                                       )
                                                     }

                                                   Some(
                                                     OntologyMetadataV2(
                                                       ontologyIri = internalOntologyIri,
                                                       projectIri = Some(projectIri),
                                                       label = Some(label),
                                                       comment = comment,
                                                       lastModificationDate = lastModificationDate
                                                     )
                                                   )

                                                 case None => None
                                               }
                                             }
    } yield metadata
  }

  /**
   * Given a list of ontology graphs, finds the IRIs of all subjects whose `rdf:type` is contained in a given set of types.
   *
   * @param ontologyGraphs a list of ontology graphs.
   * @param entityTypes    the types of entities to be found.
   * @return a map of ontology IRIs to sets of the IRIs of entities with matching types in each ontology.
   */
  def getEntityIrisFromOntologyGraphs(ontologyGraphs: Iterable[OntologyGraph], entityTypes: Set[IRI])(implicit
    stringFormatter: StringFormatter
  ): Map[SmartIri, Set[SmartIri]] = {
    val entityTypesAsIriLiterals = entityTypes.map(entityType => IriLiteralV2(entityType))

    ontologyGraphs.map { ontologyGraph =>
      val entityIrisInGraph: Set[SmartIri] =
        ontologyGraph.constructResponse.statements.foldLeft(Set.empty[SmartIri]) {
          case (acc, (subjectIri: IriSubjectV2, subjectStatements: Map[SmartIri, Seq[LiteralV2]])) =>
            val subjectTypeLiterals: Seq[IriLiteralV2] = subjectStatements
              .getOrElse(
                OntologyConstants.Rdf.Type.toSmartIri,
                throw InconsistentRepositoryDataException(s"Subject $subjectIri has no rdf:type")
              )
              .collect { case iriLiteral: IriLiteralV2 =>
                iriLiteral
              }

            if (subjectTypeLiterals.exists(entityTypesAsIriLiterals.contains)) {
              acc + subjectIri.value.toSmartIri
            } else {
              acc
            }

          case (acc, _) => acc
        }

      ontologyGraph.ontologyIri -> entityIrisInGraph
    }.toMap
  }

  /**
   * Constructs a map of class IRIs to [[ReadClassInfoV2]] instances, based on class definitions loaded from the
   * triplestore.
   *
   * @param classDefs                         a map of class IRIs to class definitions.
   * @param directClassCardinalities          a map of the cardinalities defined directly on each class. Each resource class
   *                                          IRI points to a map of property IRIs to [[KnoraCardinalityInfo]] objects.
   * @param classCardinalitiesWithInheritance a map of the cardinalities defined directly on each class or inherited from
   *                                          base classes. Each class IRI points to a map of property IRIs to
   *                                          [[KnoraCardinalityInfo]] objects.
   * @param directSubClassOfRelations         a map of class IRIs to their immediate base classes.
   * @param allSubClassOfRelations            a map of class IRIs to all their base classes.
   * @param allSubPropertyOfRelations         a map of property IRIs to all their base properties.
   * @param allPropertyDefs                   a map of property IRIs to property definitions.
   * @param allKnoraResourceProps             a set of the IRIs of all Knora resource properties.
   * @param allLinkProps                      a set of the IRIs of all link properties.
   * @param allLinkValueProps                 a set of the IRIs of link value properties.
   * @param allFileValueProps                 a set of the IRIs of all file value properties.
   * @return a map of resource class IRIs to their definitions.
   */
  def makeReadClassInfos(
    classDefs: Map[SmartIri, ClassInfoContentV2],
    directClassCardinalities: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]],
    classCardinalitiesWithInheritance: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]],
    directSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
    allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]],
    allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
    allPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
    allKnoraResourceProps: Set[SmartIri],
    allLinkProps: Set[SmartIri],
    allLinkValueProps: Set[SmartIri],
    allFileValueProps: Set[SmartIri]
  )(implicit stringFormatter: StringFormatter): Map[SmartIri, ReadClassInfoV2] = {
    classDefs.map { case (classIri, classDef) =>
      val ontologyIri = classIri.getOntologyFromEntity

      // Get the OWL cardinalities for the class.
      val allOwlCardinalitiesForClass: Map[SmartIri, KnoraCardinalityInfo] =
        classCardinalitiesWithInheritance(classIri)
      val allPropertyIrisForCardinalitiesInClass: Set[SmartIri] = allOwlCardinalitiesForClass.keys.toSet

      // Identify the Knora resource properties, link properties, link value properties, and file value properties in the cardinalities.
      val knoraResourcePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allKnoraResourceProps)
      val linkPropsInClass          = allPropertyIrisForCardinalitiesInClass.filter(allLinkProps)
      val linkValuePropsInClass     = allPropertyIrisForCardinalitiesInClass.filter(allLinkValueProps)
      val fileValuePropsInClass     = allPropertyIrisForCardinalitiesInClass.filter(allFileValueProps)

      // Make sure there is a link value property for each link property.

      val missingLinkValueProps = linkPropsInClass.map(_.fromLinkPropToLinkValueProp) -- linkValuePropsInClass

      if (missingLinkValueProps.nonEmpty) {
        throw InconsistentRepositoryDataException(
          s"Resource class $classIri has cardinalities for one or more link properties without corresponding link value properties. The missing (or incorrectly defined) property or properties: ${missingLinkValueProps
            .mkString(", ")}"
        )
      }

      // Make sure there is a link property for each link value property.

      val missingLinkProps = linkValuePropsInClass.map(_.fromLinkValuePropToLinkProp) -- linkPropsInClass

      if (missingLinkProps.nonEmpty) {
        throw InconsistentRepositoryDataException(
          s"Resource class $classIri has cardinalities for one or more link value properties without corresponding link properties. The missing (or incorrectly defined) property or properties: ${missingLinkProps
            .mkString(", ")}"
        )
      }

      // Make sure that the cardinality for each link property is the same as the cardinality for the corresponding link value property.
      for (linkProp <- linkPropsInClass) {
        val linkValueProp: SmartIri                        = linkProp.fromLinkPropToLinkValueProp
        val linkPropCardinality: KnoraCardinalityInfo      = allOwlCardinalitiesForClass(linkProp)
        val linkValuePropCardinality: KnoraCardinalityInfo = allOwlCardinalitiesForClass(linkValueProp)

        if (!linkPropCardinality.equalsWithoutGuiOrder(linkValuePropCardinality)) {
          throw InconsistentRepositoryDataException(
            s"In class $classIri, the cardinality for $linkProp is different from the cardinality for $linkValueProp"
          )
        }
      }

      // The class's direct cardinalities.
      val directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = directClassCardinalities(classIri)

      val directCardinalityPropertyIris = directCardinalities.keySet
      val allBaseClasses: Seq[SmartIri] = allSubClassOfRelations(classIri)
      val isKnoraResourceClass          = allBaseClasses.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)
      val isStandoffClass               = allBaseClasses.contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri)
      val isValueClass = !(isKnoraResourceClass || isStandoffClass) && allBaseClasses.contains(
        OntologyConstants.KnoraBase.Value.toSmartIri
      )

      // If the class is defined in project-specific ontology, do the following checks.
      if (!ontologyIri.isKnoraBuiltInDefinitionIri) {
        // It must be either a resource class or a standoff class, but not both.
        if (!(isKnoraResourceClass ^ isStandoffClass)) {
          throw InconsistentRepositoryDataException(
            s"Class $classIri must be a subclass either of knora-base:Resource or of knora-base:StandoffTag (but not both)"
          )
        }

        // All its cardinalities must be on properties that are defined.
        val cardinalitiesOnMissingProps = directCardinalityPropertyIris.filterNot(allPropertyDefs.keySet)

        if (cardinalitiesOnMissingProps.nonEmpty) {
          throw InconsistentRepositoryDataException(
            s"Class $classIri has one or more cardinalities on undefined properties: ${cardinalitiesOnMissingProps
              .mkString(", ")}"
          )
        }

        // It cannot have cardinalities both on property P and on a subproperty of P.

        val maybePropertyAndSubproperty: Option[(SmartIri, SmartIri)] = findPropertyAndSubproperty(
          propertyIris = allPropertyIrisForCardinalitiesInClass,
          subPropertyOfRelations = allSubPropertyOfRelations
        )

        maybePropertyAndSubproperty match {
          case Some((basePropertyIri, propertyIri)) =>
            throw InconsistentRepositoryDataException(
              s"Class $classIri has a cardinality on property $basePropertyIri and on its subproperty $propertyIri"
            )

          case None => ()
        }

        if (isKnoraResourceClass) {
          // If it's a resource class, all its directly defined cardinalities must be on Knora resource properties, not including knora-base:resourceProperty or knora-base:hasValue.

          val cardinalitiesOnInvalidProps = directCardinalityPropertyIris.filterNot(allKnoraResourceProps)

          if (cardinalitiesOnInvalidProps.nonEmpty) {
            throw InconsistentRepositoryDataException(
              s"Resource class $classIri has one or more cardinalities on properties that are not Knora resource properties: ${cardinalitiesOnInvalidProps
                .mkString(", ")}"
            )
          }

          Set(OntologyConstants.KnoraBase.ResourceProperty, OntologyConstants.KnoraBase.HasValue).foreach {
            invalidProp =>
              if (directCardinalityPropertyIris.contains(invalidProp.toSmartIri)) {
                throw InconsistentRepositoryDataException(
                  s"Class $classIri has a cardinality on property $invalidProp, which is not allowed"
                )
              }
          }

          // Check for invalid cardinalities on boolean properties.
          checkForInvalidBooleanCardinalities(
            classIri = classIri,
            directCardinalities = directCardinalities,
            allPropertyDefs = allPropertyDefs,
            schemaForErrors = InternalSchema,
            errorFun = { msg: String =>
              throw InconsistentRepositoryDataException(msg)
            }
          )

          // All its base classes with Knora IRIs must also be resource classes.
          for (baseClass <- classDef.subClassOf) {
            if (
              baseClass.isKnoraDefinitionIri && !allSubClassOfRelations(baseClass).contains(
                OntologyConstants.KnoraBase.Resource.toSmartIri
              )
            ) {
              throw InconsistentRepositoryDataException(
                s"Class $classIri is a subclass of knora-base:Resource, but its base class $baseClass is not"
              )
            }
          }

          // It must have an rdfs:label.
          if (!classDef.predicates.contains(OntologyConstants.Rdfs.Label.toSmartIri)) {
            throw InconsistentRepositoryDataException(s"Class $classIri has no rdfs:label")
          }
        } else {
          // If it's a standoff class, none of its cardinalities must be on Knora resource properties.

          val cardinalitiesOnInvalidProps = directCardinalityPropertyIris.filter(allKnoraResourceProps)

          if (cardinalitiesOnInvalidProps.nonEmpty) {
            throw InconsistentRepositoryDataException(
              s"Standoff class $classIri has one or more cardinalities on properties that are Knora resource properties: ${cardinalitiesOnInvalidProps
                .mkString(", ")}"
            )
          }

          // All its base classes with Knora IRIs must also be standoff classes.
          for (baseClass <- classDef.subClassOf) {
            if (baseClass.isKnoraDefinitionIri) {
              if (
                isStandoffClass && !allSubClassOfRelations(baseClass).contains(
                  OntologyConstants.KnoraBase.StandoffTag.toSmartIri
                )
              ) {
                throw InconsistentRepositoryDataException(
                  s"Class $classIri is a subclass of knora-base:StandoffTag, but its base class $baseClass is not"
                )
              }
            }
          }
        }
      }

      // Each class must be a subclass of all the classes that are subject class constraints of the properties in its cardinalities.
      checkSubjectClassConstraintsViaCardinalities(
        internalClassDef = classDef,
        allBaseClassIris = allBaseClasses.toSet,
        allClassCardinalityKnoraPropertyDefs =
          allPropertyDefs.view.filterKeys(allOwlCardinalitiesForClass.keySet).toMap,
        errorSchema = InternalSchema,
        errorFun = { msg: String =>
          throw InconsistentRepositoryDataException(msg)
        }
      )

      val inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = allOwlCardinalitiesForClass.filterNot {
        case (propertyIri, _) => directCardinalityPropertyIris.contains(propertyIri)
      }

      // Get the class's standoff data type, if any. A standoff class that has a datatype is a subclass of one of the classes
      // in StandoffDataTypeClasses.

      val standoffDataType: Set[SmartIri] = allSubClassOfRelations(classIri).toSet
        .intersect(StandoffDataTypeClasses.getStandoffClassIris.map(_.toSmartIri))

      if (standoffDataType.size > 1) {
        throw InconsistentRepositoryDataException(
          s"Class $classIri is a subclass of more than one standoff datatype: ${standoffDataType.mkString(", ")}"
        )
      }

      // A class can be instantiated if it's in a built-in ontology and marked with knora-base:canBeInstantiated, or if it's
      // a resource class in a project-specific ontology.
      val canBeInstantiated = if (ontologyIri.isKnoraBuiltInDefinitionIri) {
        classDef.predicates
          .get(OntologyConstants.KnoraBase.CanBeInstantiated.toSmartIri)
          .flatMap(_.objects.headOption) match {
          case Some(booleanLiteral: BooleanLiteralV2) => booleanLiteral.value
          case _                                      => false
        }
      } else {
        isKnoraResourceClass
      }

      val readClassInfo = ReadClassInfoV2(
        entityInfoContent = classDef,
        allBaseClasses = allBaseClasses,
        isResourceClass = isKnoraResourceClass,
        isStandoffClass = isStandoffClass,
        isValueClass = isValueClass,
        canBeInstantiated = canBeInstantiated,
        inheritedCardinalities = inheritedCardinalities,
        knoraResourceProperties = knoraResourcePropsInClass,
        linkProperties = linkPropsInClass,
        linkValueProperties = linkValuePropsInClass,
        fileValueProperties = fileValuePropsInClass,
        standoffDataType = standoffDataType.headOption match {
          case Some(dataType: SmartIri) =>
            Some(
              StandoffDataTypeClasses.lookup(
                dataType.toString,
                throw InconsistentRepositoryDataException(s"$dataType is not a valid standoff datatype")
              )
            )

          case None => None
        }
      )

      classIri -> readClassInfo
    }
  }

  /**
   * Checks that a class is a subclass of all the classes that are subject class constraints of the Knora resource properties in its cardinalities.
   *
   * @param internalClassDef                     the class definition.
   * @param allBaseClassIris                     the IRIs of all the class's base classes.
   * @param allClassCardinalityKnoraPropertyDefs the definitions of all the Knora resource properties on which the class has cardinalities (whether directly defined
   *                                             or inherited).
   * @param errorSchema                          the ontology schema to be used in error messages.
   * @param errorFun                             a function that throws an exception. It will be called with an error message argument if the cardinalities are invalid.
   */
  private def checkSubjectClassConstraintsViaCardinalities(
    internalClassDef: ClassInfoContentV2,
    allBaseClassIris: Set[SmartIri],
    allClassCardinalityKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
    errorSchema: OntologySchema,
    errorFun: String => Nothing
  )(implicit stringFormatter: StringFormatter): Unit =
    allClassCardinalityKnoraPropertyDefs.foreach { case (propertyIri, propertyDef) =>
      propertyDef.predicates.get(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
        case Some(subjectClassConstraintPred) =>
          val subjectClassConstraint = subjectClassConstraintPred.requireIriObject(
            throw InconsistentRepositoryDataException(
              s"Property $propertyIri has an invalid object for ${OntologyConstants.KnoraBase.SubjectClassConstraint}"
            )
          )

          if (!allBaseClassIris.contains(subjectClassConstraint)) {
            val hasOrWouldInherit = if (internalClassDef.directCardinalities.contains(propertyIri)) {
              "has"
            } else {
              "would inherit"
            }

            errorFun(
              s"Class ${internalClassDef.classIri.toOntologySchema(errorSchema)} $hasOrWouldInherit a cardinality for property ${propertyIri
                .toOntologySchema(errorSchema)}, but is not a subclass of that property's ${OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri
                .toOntologySchema(errorSchema)}, ${subjectClassConstraint.toOntologySchema(errorSchema)}"
            )
          }

        case None => ()
      }
    }

  /**
   * Checks for invalid cardinalities on boolean properties.
   *
   * @param classIri the class IRI.
   * @param directCardinalities the cardinalities directly defined on the class.
   * @param allPropertyDefs all property definitions.
   */
  def checkForInvalidBooleanCardinalities(
    classIri: SmartIri,
    directCardinalities: Map[SmartIri, KnoraCardinalityInfo],
    allPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
    schemaForErrors: OntologySchema,
    errorFun: String => Nothing
  )(implicit stringFormatter: StringFormatter): Unit = {
    // A cardinality on a property with a boolean object must be 1 or 0-1.

    val invalidCardinalitiesOnBooleanProps: Set[SmartIri] = directCardinalities.filter {
      case (propertyIri, knoraCardinalityInfo) =>
        val objectClassConstraintIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri

        val propertyObjectClassConstraint: SmartIri = allPropertyDefs(propertyIri).requireIriObject(
          objectClassConstraintIri,
          errorFun(s"Property ${propertyIri
            .toOntologySchema(schemaForErrors)} has no ${objectClassConstraintIri.toOntologySchema(schemaForErrors)}")
        )

        propertyObjectClassConstraint == OntologyConstants.KnoraBase.BooleanValue.toSmartIri &&
        !(knoraCardinalityInfo.cardinality == Cardinality.MustHaveOne || knoraCardinalityInfo.cardinality == Cardinality.MayHaveOne)
    }.keySet

    if (invalidCardinalitiesOnBooleanProps.nonEmpty) {
      errorFun(
        s"Class ${classIri.toOntologySchema(schemaForErrors).toSparql} has one or more invalid cardinalities on boolean properties: ${invalidCardinalitiesOnBooleanProps
          .map(_.toOntologySchema(schemaForErrors).toSparql)
          .mkString(", ")}"
      )
    }
  }

  /**
   * Constructs a map of property IRIs to [[ReadPropertyInfoV2]] instances, based on property definitions loaded from the
   * triplestore.
   *
   * @param propertyDefs                 a map of property IRIs to property definitions.
   * @param directSubPropertyOfRelations a map of property IRIs to their immediate base properties.
   * @param allSubPropertyOfRelations    a map of property IRIs to all their base properties.
   * @param allSubClassOfRelations       a map of class IRIs to all their base classes.
   * @param allGuiAttributeDefinitions   a map of `Guielement` IRIs to sets of [[SalsahGuiAttributeDefinition]].
   * @param allKnoraResourceProps        a set of the IRIs of all Knora resource properties.
   * @param allLinkProps                 a set of the IRIs of all link properties.
   * @param allLinkValueProps            a set of the IRIs of link value properties.
   * @param allFileValueProps            a set of the IRIs of all file value properties.
   * @return a map of property IRIs to [[ReadPropertyInfoV2]] instances.
   */
  def makeReadPropertyInfos(
    propertyDefs: Map[SmartIri, PropertyInfoContentV2],
    directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
    allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
    allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]],
    allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
    allKnoraResourceProps: Set[SmartIri],
    allLinkProps: Set[SmartIri],
    allLinkValueProps: Set[SmartIri],
    allFileValueProps: Set[SmartIri]
  )(implicit stringFormatter: StringFormatter): Map[SmartIri, ReadPropertyInfoV2] =
    propertyDefs.map { case (propertyIri, propertyDef) =>
      val ontologyIri = propertyIri.getOntologyFromEntity

      validateGuiAttributes(
        propertyInfoContent = propertyDef,
        allGuiAttributeDefinitions = allGuiAttributeDefinitions,
        errorFun = { msg: String =>
          throw InconsistentRepositoryDataException(msg)
        }
      )

      val isResourceProp = allKnoraResourceProps.contains(propertyIri)
      val isValueProp =
        allSubPropertyOfRelations(propertyIri).contains(OntologyConstants.KnoraBase.HasValue.toSmartIri)
      val isLinkProp      = allLinkProps.contains(propertyIri)
      val isLinkValueProp = allLinkValueProps.contains(propertyIri)
      val isFileValueProp = allFileValueProps.contains(propertyIri)

      // If the property is defined in a project-specific ontology and is a Knora resource property (a subproperty of knora-base:hasValue or knora-base:hasLinkTo), do the following checks.
      if (!propertyIri.isKnoraBuiltInDefinitionIri && isResourceProp) {
        // The property must be a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.
        if (isValueProp && isLinkProp) {
          throw InconsistentRepositoryDataException(
            s"Property $propertyIri cannot be a subproperty of both knora-base:hasValue and knora-base:hasLinkTo"
          )
        }

        // It can't be a subproperty of knora-base:hasFileValue.
        if (isFileValueProp) {
          throw InconsistentRepositoryDataException(
            s"Property $propertyIri cannot be a subproperty of knora-base:hasFileValue"
          )
        }

        // Each of its base properties that has a Knora IRI must also be a Knora resource property.
        for (baseProperty <- propertyDef.subPropertyOf) {
          if (baseProperty.isKnoraDefinitionIri && !allKnoraResourceProps.contains(baseProperty)) {
            throw InconsistentRepositoryDataException(
              s"Property $propertyIri is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but its base property $baseProperty is not"
            )
          }
        }

        // It must have an rdfs:label.
        if (!propertyDef.predicates.contains(OntologyConstants.Rdfs.Label.toSmartIri)) {
          throw InconsistentRepositoryDataException(s"Property $propertyIri has no rdfs:label")
        }
      }

      // A property is editable if it's in a built-in ontology and marked with knora-base:isEditable,
      // or if it's a resource property in a project-specific ontology.
      val isEditable = if (ontologyIri.isKnoraBuiltInDefinitionIri) {
        propertyDef.predicates
          .get(OntologyConstants.KnoraBase.IsEditable.toSmartIri)
          .flatMap(_.objects.headOption) match {
          case Some(booleanLiteral: BooleanLiteralV2) => booleanLiteral.value
          case _                                      => false
        }
      } else {
        isResourceProp
      }

      val propertyEntityInfo = ReadPropertyInfoV2(
        entityInfoContent = propertyDef,
        isResourceProp = isResourceProp,
        isEditable = isEditable,
        isLinkProp = isLinkProp,
        isLinkValueProp = isLinkValueProp,
        isFileValueProp = isFileValueProp,
        isStandoffInternalReferenceProperty = allSubPropertyOfRelations(propertyIri).contains(
          OntologyConstants.KnoraBase.StandoffTagHasInternalReference.toSmartIri
        )
      )

      propertyIri -> propertyEntityInfo
    }

  /**
   * Constructs a map of OWL named individual IRIs to [[ReadIndividualInfoV2]] instances.
   *
   * @param individualDefs a map of OWL named individual IRIs to named individuals.
   * @return a map of individual IRIs to [[ReadIndividualInfoV2]] instances.
   */
  def makeReadIndividualInfos(
    individualDefs: Map[SmartIri, IndividualInfoContentV2]
  ): Map[SmartIri, ReadIndividualInfoV2] =
    individualDefs.map { case (individualIri, individual) =>
      individualIri -> ReadIndividualInfoV2(individual)
    }

  /**
   * Given all the OWL named individuals available, constructs a map of `salsah-gui:Guielement` individuals to
   * their GUI attribute definitions.
   *
   * @param allIndividuals all the OWL named individuals available.
   * @return a map of `salsah-gui:Guielement` individuals to their GUI attribute definitions.
   */
  def makeGuiAttributeDefinitions(
    allIndividuals: Map[SmartIri, IndividualInfoContentV2]
  )(implicit stringFormatter: StringFormatter): Map[SmartIri, Set[SalsahGuiAttributeDefinition]] = {
    val guiElementIndividuals: Map[SmartIri, IndividualInfoContentV2] = allIndividuals.filter { case (_, individual) =>
      individual.getRdfType.toString == OntologyConstants.SalsahGui.GuiElementClass
    }

    guiElementIndividuals.map { case (guiElementIri, guiElementIndividual) =>
      val attributeDefs: Set[SalsahGuiAttributeDefinition] =
        guiElementIndividual.predicates.get(OntologyConstants.SalsahGui.GuiAttributeDefinition.toSmartIri) match {
          case Some(predicateInfo) =>
            predicateInfo.objects.map {
              case StringLiteralV2(attributeDefStr, None) =>
                stringFormatter.toSalsahGuiAttributeDefinition(
                  attributeDefStr,
                  throw InconsistentRepositoryDataException(
                    s"Invalid salsah-gui:guiAttributeDefinition in $guiElementIri: $attributeDefStr"
                  )
                )

              case other =>
                throw InconsistentRepositoryDataException(
                  s"Invalid salsah-gui:guiAttributeDefinition in $guiElementIri: $other"
                )
            }.toSet

          case None => Set.empty[SalsahGuiAttributeDefinition]
        }

      guiElementIri -> attributeDefs
    }
  }

  /**
   * Validates the GUI attributes of a resource class property.
   *
   * @param propertyInfoContent        the property definition.
   * @param allGuiAttributeDefinitions the GUI attribute definitions for each GUI element.
   * @param errorFun                   a function that throws an exception. It will be passed the message to be included in the exception.
   */
  def validateGuiAttributes(
    propertyInfoContent: PropertyInfoContentV2,
    allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
    errorFun: String => Nothing
  )(implicit stringFormatter: StringFormatter): Unit = {
    val propertyIri = propertyInfoContent.propertyIri
    val predicates  = propertyInfoContent.predicates

    // Find out which salsah-gui:Guielement the property uses, if any.
    val maybeGuiElementPred: Option[PredicateInfoV2] =
      predicates.get(OntologyConstants.SalsahGui.GuiElementProp.toSmartIri)
    val maybeGuiElementIri: Option[SmartIri] = maybeGuiElementPred.map(
      _.requireIriObject(
        throw InconsistentRepositoryDataException(
          s"Property $propertyIri has an invalid object for ${OntologyConstants.SalsahGui.GuiElementProp}"
        )
      )
    )

    // Get that Guielement's attribute definitions, if any.
    val guiAttributeDefs: Set[SalsahGuiAttributeDefinition] = maybeGuiElementIri match {
      case Some(guiElementIri) =>
        allGuiAttributeDefinitions.getOrElse(
          guiElementIri,
          errorFun(s"Property $propertyIri has salsah-gui:guiElement $guiElementIri, which doesn't exist")
        )

      case None => Set.empty[SalsahGuiAttributeDefinition]
    }

    // If the property has the predicate salsah-gui:guiAttribute, syntactically validate the objects of that predicate.
    val guiAttributes: Set[SalsahGuiAttribute] =
      predicates.get(OntologyConstants.SalsahGui.GuiAttribute.toSmartIri) match {
        case Some(guiAttributePred) =>
          val guiElementIri = maybeGuiElementIri.getOrElse(
            errorFun(s"Property $propertyIri has salsah-gui:guiAttribute, but no salsah-gui:guiElement")
          )

          if (guiAttributeDefs.isEmpty) {
            errorFun(
              s"Property $propertyIri has salsah-gui:guiAttribute, but $guiElementIri has no salsah-gui:guiAttributeDefinition"
            )
          }

          // Syntactically validate each attribute.
          guiAttributePred.objects.map {
            case StringLiteralV2(guiAttributeObj, None) =>
              stringFormatter.toSalsahGuiAttribute(
                s = guiAttributeObj,
                attributeDefs = guiAttributeDefs,
                errorFun =
                  errorFun(s"Property $propertyIri contains an invalid salsah-gui:guiAttribute: $guiAttributeObj")
              )

            case other =>
              errorFun(s"Property $propertyIri contains an invalid salsah-gui:guiAttribute: $other")
          }.toSet

        case None => Set.empty[SalsahGuiAttribute]
      }

    // Check that all required GUI attributes are provided.
    val requiredAttributeNames             = guiAttributeDefs.filter(_.isRequired).map(_.attributeName)
    val providedAttributeNames             = guiAttributes.map(_.attributeName)
    val missingAttributeNames: Set[String] = requiredAttributeNames -- providedAttributeNames

    if (missingAttributeNames.nonEmpty) {
      errorFun(
        s"Property $propertyIri has one or more missing objects of salsah-gui:guiAttribute: ${missingAttributeNames
          .mkString(", ")}"
      )
    }
  }

  /**
   * Before creating a new class or adding cardinalities to an existing class, checks the validity of the
   * cardinalities directly defined on the class. Adds link value properties for the corresponding
   * link properties.
   *
   * @param internalClassDef        the internal definition of the class.
   * @param allBaseClassIris        the IRIs of all the class's base classes, including the class itself.
   * @param cacheData               the ontology cache.
   * @param existingLinkPropsToKeep the link properties that are already defined on the class and that
   *                                will be kept after the update.
   * @return the updated class definition, and the cardinalities resulting from inheritance.
   */
  def checkCardinalitiesBeforeAdding(
    internalClassDef: ClassInfoContentV2,
    allBaseClassIris: Set[SmartIri],
    cacheData: OntologyCacheData,
    existingLinkPropsToKeep: Set[SmartIri] = Set.empty
  )(implicit stringFormatter: StringFormatter): (ClassInfoContentV2, Map[SmartIri, KnoraCardinalityInfo]) = {
    // If the class has cardinalities, check that the properties are already defined as Knora properties.

    val propertyDefsForDirectCardinalities: Set[ReadPropertyInfoV2] = internalClassDef.directCardinalities.keySet.map {
      propertyIri =>
        if (
          !isKnoraResourceProperty(
            propertyIri,
            cacheData
          ) || propertyIri.toString == OntologyConstants.KnoraBase.ResourceProperty || propertyIri.toString == OntologyConstants.KnoraBase.HasValue
        ) {
          throw NotFoundException(s"Invalid property for cardinality: <${propertyIri.toOntologySchema(ApiV2Complex)}>")
        }

        cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri)
    }

    val existingLinkValuePropsToKeep = existingLinkPropsToKeep.map(_.fromLinkPropToLinkValueProp)
    val newLinkPropsInClass: Set[SmartIri] = propertyDefsForDirectCardinalities
      .filter(_.isLinkProp)
      .map(_.entityInfoContent.propertyIri) -- existingLinkValuePropsToKeep
    val newLinkValuePropsInClass: Set[SmartIri] = propertyDefsForDirectCardinalities
      .filter(_.isLinkValueProp)
      .map(_.entityInfoContent.propertyIri) -- existingLinkValuePropsToKeep

    // Don't allow link value prop cardinalities to be included in the request.

    if (newLinkValuePropsInClass.nonEmpty) {
      throw BadRequestException(
        s"In class ${internalClassDef.classIri.toOntologySchema(ApiV2Complex)}, cardinalities have been submitted for one or more link value properties: ${newLinkValuePropsInClass
          .map(_.toOntologySchema(ApiV2Complex))
          .mkString(", ")}. Just submit the link properties, and the link value properties will be included automatically."
      )
    }

    // Add a link value prop cardinality for each new link prop cardinality.

    val linkValuePropCardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] = newLinkPropsInClass.map { linkPropIri =>
      val linkValuePropIri = linkPropIri.fromLinkPropToLinkValueProp

      // Ensure that the link value prop exists.
      cacheData
        .ontologies(linkValuePropIri.getOntologyFromEntity)
        .properties
        .getOrElse(linkValuePropIri, throw NotFoundException(s"Link value property <$linkValuePropIri> not found"))

      linkValuePropIri -> internalClassDef.directCardinalities(linkPropIri)
    }.toMap

    val classDefWithAddedLinkValueProps: ClassInfoContentV2 = internalClassDef.copy(
      directCardinalities = internalClassDef.directCardinalities ++ linkValuePropCardinalitiesToAdd
    )

    // Get the cardinalities that the class can inherit.

    val cardinalitiesAvailableToInherit: Map[SmartIri, KnoraCardinalityInfo] =
      classDefWithAddedLinkValueProps.subClassOf.flatMap { baseClassIri =>
        cacheData.ontologies(baseClassIri.getOntologyFromEntity).classes(baseClassIri).allCardinalities
      }.toMap

    // Check that the cardinalities directly defined on the class are compatible with any inheritable
    // cardinalities, and let directly-defined cardinalities override cardinalities in base classes.

    val cardinalitiesForClassWithInheritance: Map[SmartIri, KnoraCardinalityInfo] = overrideCardinalities(
      classIri = internalClassDef.classIri,
      thisClassCardinalities = classDefWithAddedLinkValueProps.directCardinalities,
      inheritableCardinalities = cardinalitiesAvailableToInherit,
      allSubPropertyOfRelations = cacheData.subPropertyOfRelations,
      errorSchema = ApiV2Complex,
      errorFun = { msg: String =>
        throw BadRequestException(msg)
      }
    )

    // Check that the class is a subclass of all the classes that are subject class constraints of the Knora resource properties in its cardinalities.

    val knoraResourcePropertyIrisInCardinalities = cardinalitiesForClassWithInheritance.keySet.filter { propertyIri =>
      isKnoraResourceProperty(
        propertyIri = propertyIri,
        cacheData = cacheData
      )
    }

    val allClassCardinalityKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2] =
      knoraResourcePropertyIrisInCardinalities.map { propertyIri =>
        propertyIri -> cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri).entityInfoContent
      }.toMap

    checkSubjectClassConstraintsViaCardinalities(
      internalClassDef = classDefWithAddedLinkValueProps,
      allBaseClassIris = allBaseClassIris,
      allClassCardinalityKnoraPropertyDefs = allClassCardinalityKnoraPropertyDefs,
      errorSchema = ApiV2Complex,
      errorFun = { msg: String =>
        throw BadRequestException(msg)
      }
    )

    // It cannot have cardinalities both on property P and on a subproperty of P.

    val maybePropertyAndSubproperty: Option[(SmartIri, SmartIri)] = findPropertyAndSubproperty(
      propertyIris = cardinalitiesForClassWithInheritance.keySet,
      subPropertyOfRelations = cacheData.subPropertyOfRelations
    )

    maybePropertyAndSubproperty match {
      case Some((basePropertyIri, propertyIri)) =>
        throw BadRequestException(
          s"Class <${classDefWithAddedLinkValueProps.classIri.toOntologySchema(ApiV2Complex)}> has a cardinality on property <${basePropertyIri
            .toOntologySchema(ApiV2Complex)}> and on its subproperty <${propertyIri.toOntologySchema(ApiV2Complex)}>"
        )

      case None => ()
    }

    // Check for invalid cardinalities on boolean properties.
    checkForInvalidBooleanCardinalities(
      classIri = internalClassDef.classIri,
      directCardinalities = internalClassDef.directCardinalities,
      allPropertyDefs = cacheData.allPropertyDefs,
      schemaForErrors = ApiV2Complex,
      errorFun = { msg: String =>
        throw BadRequestException(msg)
      }
    )

    (classDefWithAddedLinkValueProps, cardinalitiesForClassWithInheritance)
  }

  /**
   * Given a set of property IRIs, determines whether the set contains a property P and a subproperty of P.
   *
   * @param propertyIris           the set of property IRIs.
   * @param subPropertyOfRelations all the subproperty relations in the triplestore.
   * @return a property and its subproperty, if found.
   */
  private def findPropertyAndSubproperty(
    propertyIris: Set[SmartIri],
    subPropertyOfRelations: Map[SmartIri, Set[SmartIri]]
  ): Option[(SmartIri, SmartIri)] =
    propertyIris.flatMap { propertyIri =>
      val maybeBasePropertyIri: Option[SmartIri] = (propertyIris - propertyIri).find { otherPropertyIri =>
        subPropertyOfRelations.get(propertyIri).exists { baseProperties: Set[SmartIri] =>
          baseProperties.contains(otherPropertyIri)
        }
      }

      maybeBasePropertyIri.map { basePropertyIri =>
        (basePropertyIri, propertyIri)
      }
    }.headOption

  /**
   * Gets the set of subjects that refer to an ontology or its entities.
   *
   * @param ontology the ontology.
   * @return the set of subjects that refer to the ontology or its entities.
   */
  def getSubjectsUsingOntology(settings: KnoraSettingsImpl, storeManager: ActorRef, ontology: ReadOntologyV2)(implicit
    ec: ExecutionContext,
    timeout: Timeout
  ): Future[Set[IRI]] =
    for {
      isOntologyUsedSparql <- Future(
                                org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                                  .isOntologyUsed(
                                    triplestore = settings.triplestoreType,
                                    ontologyNamedGraphIri = ontology.ontologyMetadata.ontologyIri,
                                    classIris = ontology.classes.keySet,
                                    propertyIris = ontology.properties.keySet
                                  )
                                  .toString()
                              )

      isOntologyUsedResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(isOntologyUsedSparql))
                                                      .mapTo[SparqlSelectResult]

      subjects = isOntologyUsedResponse.results.bindings.map { row =>
                   row.rowMap("s")
                 }.toSet
    } yield subjects

  /**
   * Before an update of an ontology entity, checks that the entity's external IRI, and that of its ontology,
   * are valid, and checks that the user has permission to update the ontology.
   *
   * @param externalOntologyIri the external IRI of the ontology.
   * @param externalEntityIri   the external IRI of the entity.
   * @param requestingUser      the user making the request.
   */
  def checkOntologyAndEntityIrisForUpdate(
    externalOntologyIri: SmartIri,
    externalEntityIri: SmartIri,
    requestingUser: UserADM
  )(implicit ex: ExecutionContext): Future[Unit] =
    for {
      _ <- checkExternalOntologyIriForUpdate(externalOntologyIri)
      _ <- checkExternalEntityIriForUpdate(externalEntityIri = externalEntityIri)
      _ <- checkPermissionsForOntologyUpdate(
             internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema),
             requestingUser = requestingUser
           )
    } yield ()

  /**
   * Loads a property definition from the triplestore and converts it to a [[PropertyInfoContentV2]].
   *
   * @param propertyIri the IRI of the property to be loaded.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a [[PropertyInfoContentV2]] representing the property definition.
   */
  def loadPropertyDefinition(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
    propertyIri: SmartIri,
    featureFactoryConfig: FeatureFactoryConfig
  )(implicit ex: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[PropertyInfoContentV2] =
    for {
      sparql <- Future(
                  org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                    .getPropertyDefinition(
                      triplestore = settings.triplestoreType,
                      propertyIri = propertyIri
                    )
                    .toString()
                )

      constructResponse <- (storeManager ? SparqlExtendedConstructRequest(
                             sparql = sparql,
                             featureFactoryConfig = featureFactoryConfig
                           )).mapTo[SparqlExtendedConstructResponse]
    } yield constructResponseToPropertyDefinition(
      propertyIri = propertyIri,
      constructResponse = constructResponse
    )

  /**
   * Given a map of predicate IRIs to predicate objects describing an entity, returns a map of smart IRIs to [[PredicateInfoV2]]
   * objects that can be used to construct an [[EntityInfoContentV2]].
   *
   * @param entityDefMap a map of predicate IRIs to predicate objects.
   * @return a map of smart IRIs to [[PredicateInfoV2]] objects.
   */
  private def getEntityPredicatesFromConstructResponse(entityDefMap: Map[SmartIri, Seq[LiteralV2]])(implicit
    stringFormatter: StringFormatter
  ): Map[SmartIri, PredicateInfoV2] =
    entityDefMap.map { case (predicateIri: SmartIri, predObjs: Seq[LiteralV2]) =>
      val predicateInfo = PredicateInfoV2(
        predicateIri = predicateIri,
        objects = predObjs.map {
          case IriLiteralV2(iriStr) =>
            // We use xsd:dateTime in the triplestore (because it is supported in SPARQL), but we return
            // the more restrictive xsd:dateTimeStamp in the API.
            if (iriStr == OntologyConstants.Xsd.DateTime) {
              SmartIriLiteralV2(OntologyConstants.Xsd.DateTimeStamp.toSmartIri)
            } else {
              SmartIriLiteralV2(iriStr.toSmartIri)
            }

          case ontoLiteral: OntologyLiteralV2 => ontoLiteral

          case other =>
            throw InconsistentRepositoryDataException(s"Predicate $predicateIri has an invalid object: $other")
        }
      )

      predicateIri -> predicateInfo
    }

  /**
   * Extracts property definitions from a SPARQL CONSTRUCT response.
   *
   * @param propertyIris      the IRIs of the properties to be read.
   * @param constructResponse the SPARQL construct response to be read.
   * @return a map of property IRIs to property definitions.
   */
  def constructResponseToPropertyDefinitions(
    propertyIris: Set[SmartIri],
    constructResponse: SparqlExtendedConstructResponse
  )(implicit stringFormatter: StringFormatter): Map[SmartIri, PropertyInfoContentV2] =
    propertyIris.map { propertyIri =>
      propertyIri -> constructResponseToPropertyDefinition(
        propertyIri = propertyIri,
        constructResponse = constructResponse
      )
    }.toMap

  /**
   * Converts a SPARQL CONSTRUCT response to a [[PropertyInfoContentV2]].
   *
   * @param propertyIri       the IRI of the property to be read.
   * @param constructResponse the SPARQL CONSTRUCT response to be read.
   * @return a [[PropertyInfoContentV2]] representing a property definition.
   */
  private def constructResponseToPropertyDefinition(
    propertyIri: SmartIri,
    constructResponse: SparqlExtendedConstructResponse
  )(implicit stringFormatter: StringFormatter): PropertyInfoContentV2 = {
    // All properties defined in the triplestore must be in Knora ontologies.

    val ontologyIri = propertyIri.getOntologyFromEntity

    if (!ontologyIri.isKnoraOntologyIri) {
      throw InconsistentRepositoryDataException(s"Property $propertyIri is not in a Knora ontology")
    }

    val statements = constructResponse.statements

    // Get the statements whose subject is the property.
    val propertyDefMap: Map[SmartIri, Seq[LiteralV2]] = statements(IriSubjectV2(propertyIri.toString))

    val subPropertyOf: Set[SmartIri] = propertyDefMap.get(OntologyConstants.Rdfs.SubPropertyOf.toSmartIri) match {
      case Some(baseProperties) =>
        baseProperties.map {
          case iriLiteral: IriLiteralV2 => iriLiteral.value.toSmartIri
          case other                    => throw InconsistentRepositoryDataException(s"Unexpected object for rdfs:subPropertyOf: $other")
        }.toSet

      case None => Set.empty[SmartIri]
    }

    val otherPreds: Map[SmartIri, PredicateInfoV2] = getEntityPredicatesFromConstructResponse(
      propertyDefMap - OntologyConstants.Rdfs.SubPropertyOf.toSmartIri
    )

    // salsah-gui:guiOrder isn't allowed here.
    if (otherPreds.contains(OntologyConstants.SalsahGui.GuiOrder.toSmartIri)) {
      throw InconsistentRepositoryDataException(s"Property $propertyIri contains salsah-gui:guiOrder")
    }

    val propertyDef = PropertyInfoContentV2(
      propertyIri = propertyIri,
      subPropertyOf = subPropertyOf,
      predicates = otherPreds,
      ontologySchema = propertyIri.getOntologySchema.get
    )

    if (
      !propertyIri.isKnoraBuiltInDefinitionIri && propertyDef.getRdfTypes.contains(
        OntologyConstants.Owl.TransitiveProperty.toSmartIri
      )
    ) {
      throw InconsistentRepositoryDataException(
        s"Project-specific property $propertyIri cannot be an owl:TransitiveProperty"
      )
    }

    propertyDef
  }

  /**
   * Reads OWL named individuals from a SPARQL CONSTRUCT response.
   *
   * @param individualIris    the IRIs of the named individuals to be read.
   * @param constructResponse the SPARQL CONSTRUCT response.
   * @return a map of individual IRIs to named individuals.
   */
  def constructResponseToIndividuals(individualIris: Set[SmartIri], constructResponse: SparqlExtendedConstructResponse)(
    implicit stringFormatter: StringFormatter
  ): Map[SmartIri, IndividualInfoContentV2] =
    individualIris.map { individualIri =>
      individualIri -> constructResponseToIndividual(
        individualIri = individualIri,
        constructResponse = constructResponse
      )
    }.toMap

  /**
   * Reads an OWL named individual from a SPARQL CONSTRUCT response.
   *
   * @param individualIri     the IRI of the individual to be read.
   * @param constructResponse the SPARQL CONSTRUCT response.
   * @return an [[IndividualInfoContentV2]] representing the named individual.
   */
  private def constructResponseToIndividual(
    individualIri: SmartIri,
    constructResponse: SparqlExtendedConstructResponse
  )(implicit stringFormatter: StringFormatter): IndividualInfoContentV2 = {
    val statements = constructResponse.statements

    // Get the statements whose subject is the individual.
    val individualMap: Map[SmartIri, Seq[LiteralV2]] = statements(IriSubjectV2(individualIri.toString))

    val predicates: Map[SmartIri, PredicateInfoV2] = getEntityPredicatesFromConstructResponse(individualMap)

    IndividualInfoContentV2(
      individualIri = individualIri,
      predicates = predicates,
      ontologySchema = individualIri.getOntologySchema.get
    )
  }

  /**
   * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param classIris      the IRIs of the resource entities to be queried.
   * @param propertyIris   the IRIs of the property entities to be queried.
   * @param requestingUser the user making the request.
   * @return an [[EntityInfoGetResponseV2]].
   */
  def getEntityInfoResponseV2(
    classIris: Set[SmartIri] = Set.empty[SmartIri],
    propertyIris: Set[SmartIri] = Set.empty[SmartIri],
    requestingUser: UserADM
  )(implicit ec: ExecutionContext): Future[EntityInfoGetResponseV2] = {
    for {
      cacheData <- Cache.getCacheData

      // See if any of the requested entities are not Knora entities.

      nonKnoraEntities = (classIris ++ propertyIris).filter(!_.isKnoraEntityIri)

      _ = if (nonKnoraEntities.nonEmpty) {
            throw BadRequestException(
              s"Some requested entities are not Knora entities: ${nonKnoraEntities.mkString(", ")}"
            )
          }

      // See if any of the requested entities are unavailable in the requested schema.

      classesUnavailableInSchema: Set[SmartIri] = classIris.foldLeft(Set.empty[SmartIri]) { case (acc, classIri) =>
                                                    // Is this class IRI hard-coded in the requested schema?
                                                    if (
                                                      KnoraBaseToApiV2SimpleTransformationRules.externalClassesToAdd
                                                        .contains(classIri) ||
                                                      KnoraBaseToApiV2ComplexTransformationRules.externalClassesToAdd
                                                        .contains(classIri)
                                                    ) {
                                                      // Yes, so it's available.
                                                      acc
                                                    } else {
                                                      // No. Is it among the classes removed from the internal ontology in the requested schema?
                                                      classIri.getOntologySchema.get match {
                                                        case apiV2Schema: ApiV2Schema =>
                                                          val internalClassIri =
                                                            classIri.toOntologySchema(InternalSchema)
                                                          val knoraBaseClassesToRemove = OntologyTransformationRules
                                                            .getTransformationRules(
                                                              classIri.getOntologyFromEntity,
                                                              apiV2Schema
                                                            )
                                                            .internalClassesToRemove

                                                          if (knoraBaseClassesToRemove.contains(internalClassIri)) {
                                                            // Yes. Include it in the set of unavailable classes.
                                                            acc + classIri
                                                          } else {
                                                            // No. It's available.
                                                            acc
                                                          }

                                                        case InternalSchema => acc
                                                      }
                                                    }
                                                  }

      propertiesUnavailableInSchema: Set[SmartIri] = propertyIris.foldLeft(Set.empty[SmartIri]) {
                                                       case (acc, propertyIri) =>
                                                         // Is this property IRI hard-coded in the requested schema?
                                                         if (
                                                           KnoraBaseToApiV2SimpleTransformationRules.externalPropertiesToAdd
                                                             .contains(propertyIri) ||
                                                           KnoraBaseToApiV2ComplexTransformationRules.externalPropertiesToAdd
                                                             .contains(propertyIri)
                                                         ) {
                                                           // Yes, so it's available.
                                                           acc
                                                         } else {
                                                           // No. See if it's available in the requested schema.
                                                           propertyIri.getOntologySchema.get match {
                                                             case apiV2Schema: ApiV2Schema =>
                                                               val internalPropertyIri =
                                                                 propertyIri.toOntologySchema(InternalSchema)

                                                               // If it's a link value property and it's requested in the simple schema, it's unavailable.
                                                               if (
                                                                 apiV2Schema == ApiV2Simple && OntologyHelpers
                                                                   .isLinkValueProp(internalPropertyIri, cacheData)
                                                               ) {
                                                                 acc + propertyIri
                                                               } else {
                                                                 // Is it among the properties removed from the internal ontology in the requested schema?

                                                                 val knoraBasePropertiesToRemove =
                                                                   OntologyTransformationRules
                                                                     .getTransformationRules(
                                                                       propertyIri.getOntologyFromEntity,
                                                                       apiV2Schema
                                                                     )
                                                                     .internalPropertiesToRemove

                                                                 if (
                                                                   knoraBasePropertiesToRemove.contains(
                                                                     internalPropertyIri
                                                                   )
                                                                 ) {
                                                                   // Yes. Include it in the set of unavailable properties.
                                                                   acc + propertyIri
                                                                 } else {
                                                                   // No. It's available.
                                                                   acc
                                                                 }
                                                               }

                                                             case InternalSchema => acc
                                                           }
                                                         }
                                                     }

      entitiesUnavailableInSchema = classesUnavailableInSchema ++ propertiesUnavailableInSchema

      _ = if (entitiesUnavailableInSchema.nonEmpty) {
            throw NotFoundException(
              s"Some requested entities were not found: ${entitiesUnavailableInSchema.mkString(", ")}"
            )
          }

      // See if any of the requested entities are hard-coded for knora-api.

      hardCodedExternalClassesAvailable: Map[SmartIri, ReadClassInfoV2] =
        KnoraBaseToApiV2SimpleTransformationRules.externalClassesToAdd.view
          .filterKeys(classIris)
          .toMap ++
          KnoraBaseToApiV2ComplexTransformationRules.externalClassesToAdd.view.filterKeys(classIris).toMap

      hardCodedExternalPropertiesAvailable: Map[SmartIri, ReadPropertyInfoV2] =
        KnoraBaseToApiV2SimpleTransformationRules.externalPropertiesToAdd.view
          .filterKeys(propertyIris)
          .toMap ++
          KnoraBaseToApiV2ComplexTransformationRules.externalPropertiesToAdd.view.filterKeys(propertyIris)

      // Convert the remaining external entity IRIs to internal ones.

      internalToExternalClassIris: Map[SmartIri, SmartIri] =
        (classIris -- hardCodedExternalClassesAvailable.keySet)
          .map(externalIri => externalIri.toOntologySchema(InternalSchema) -> externalIri)
          .toMap
      internalToExternalPropertyIris: Map[SmartIri, SmartIri] =
        (propertyIris -- hardCodedExternalPropertiesAvailable.keySet)
          .map(externalIri => externalIri.toOntologySchema(InternalSchema) -> externalIri)
          .toMap

      classIrisForCache    = internalToExternalClassIris.keySet
      propertyIrisForCache = internalToExternalPropertyIris.keySet

      // Get the entities that are available in the ontology cache.

      classOntologiesForCache: Iterable[ReadOntologyV2] = cacheData.ontologies.view
                                                            .filterKeys(classIrisForCache.map(_.getOntologyFromEntity))
                                                            .toMap
                                                            .values
      propertyOntologiesForCache: Iterable[ReadOntologyV2] =
        cacheData.ontologies.view
          .filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity))
          .toMap
          .values

      classesAvailableFromCache: Map[SmartIri, ReadClassInfoV2] = classOntologiesForCache.flatMap { ontology =>
                                                                    ontology.classes.view
                                                                      .filterKeys(classIrisForCache)
                                                                      .toMap
                                                                  }.toMap

      propertiesAvailableFromCache: Map[SmartIri, ReadPropertyInfoV2] = propertyOntologiesForCache.flatMap { ontology =>
                                                                          ontology.properties.view
                                                                            .filterKeys(propertyIrisForCache)
                                                                            .toMap
                                                                        }.toMap

      allClassesAvailable: Map[SmartIri, ReadClassInfoV2] =
        classesAvailableFromCache ++ hardCodedExternalClassesAvailable
      allPropertiesAvailable: Map[SmartIri, ReadPropertyInfoV2] =
        propertiesAvailableFromCache ++ hardCodedExternalPropertiesAvailable

      // See if any entities are missing.

      allExternalClassIrisAvailable: Set[SmartIri] = allClassesAvailable.keySet.map { classIri =>
                                                       if (classIri.getOntologySchema.contains(InternalSchema)) {
                                                         internalToExternalClassIris(classIri)
                                                       } else {
                                                         classIri
                                                       }
                                                     }

      allExternalPropertyIrisAvailable = allPropertiesAvailable.keySet.map { propertyIri =>
                                           if (propertyIri.getOntologySchema.contains(InternalSchema)) {
                                             internalToExternalPropertyIris(propertyIri)
                                           } else {
                                             propertyIri
                                           }
                                         }

      missingClasses    = classIris -- allExternalClassIrisAvailable
      missingProperties = propertyIris -- allExternalPropertyIrisAvailable

      missingEntities = missingClasses ++ missingProperties

      _ = if (missingEntities.nonEmpty) {
            throw NotFoundException(s"Some requested entities were not found: ${missingEntities.mkString(", ")}")
          }

      response = EntityInfoGetResponseV2(
                   classInfoMap = new ErrorHandlingMap(allClassesAvailable, key => s"Resource class $key not found"),
                   propertyInfoMap = new ErrorHandlingMap(allPropertiesAvailable, key => s"Property $key not found")
                 )
    } yield response
  }

  /**
   * Requests information about OWL classes in a single ontology.
   *
   * @param classIris      the IRIs (internal or external) of the classes to query for.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  def getClassDefinitionsFromOntologyV2(
    classIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: UserADM
  )(implicit ec: ExecutionContext): Future[ReadOntologyV2] =
    for {
      cacheData <- Cache.getCacheData

      ontologyIris = classIris.map(_.getOntologyFromEntity)

      _ = if (ontologyIris.size != 1) {
            throw BadRequestException(s"Only one ontology may be queried per request")
          }

      classInfoResponse: EntityInfoGetResponseV2 <-
        getEntityInfoResponseV2(classIris = classIris, requestingUser = requestingUser)
      internalOntologyIri = ontologyIris.head.toOntologySchema(InternalSchema)

      // Are we returning data in the user's preferred language, or in all available languages?
      userLang = if (!allLanguages) {
                   // Just the user's preferred language.
                   Some(requestingUser.lang)
                 } else {
                   // All available languages.
                   None
                 }
    } yield ReadOntologyV2(
      ontologyMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata,
      classes = classInfoResponse.classInfoMap,
      userLang = userLang
    )

  /**
   * Loads a class definition from the triplestore and converts it to a [[ClassInfoContentV2]].
   *
   * @param classIri the IRI of the class to be loaded.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a [[ClassInfoContentV2]] representing the class definition.
   */
  def loadClassDefinition(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
    classIri: SmartIri,
    featureFactoryConfig: FeatureFactoryConfig
  )(implicit ex: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[ClassInfoContentV2] =
    for {
      sparql <- Future(
                  org.knora.webapi.messages.twirl.queries.sparql.v2.txt
                    .getClassDefinition(
                      triplestore = settings.triplestoreType,
                      classIri = classIri
                    )
                    .toString()
                )

      constructResponse <- (storeManager ? SparqlExtendedConstructRequest(
                             sparql = sparql,
                             featureFactoryConfig = featureFactoryConfig
                           )).mapTo[SparqlExtendedConstructResponse]
    } yield constructResponseToClassDefinition(
      classIri = classIri,
      constructResponse = constructResponse
    )

  /**
   * Extracts class definitions from a SPARQL CONSTRUCT response.
   *
   * @param classIris         the IRIs of the classes to be read.
   * @param constructResponse the SPARQL CONSTRUCT response to be read.
   * @return a map of class IRIs to class definitions.
   */
  def constructResponseToClassDefinitions(classIris: Set[SmartIri], constructResponse: SparqlExtendedConstructResponse)(
    implicit stringFormatter: StringFormatter
  ): Map[SmartIri, ClassInfoContentV2] =
    classIris.map { classIri =>
      classIri -> constructResponseToClassDefinition(
        classIri = classIri,
        constructResponse = constructResponse
      )
    }.toMap

  /**
   * Converts a SPARQL CONSTRUCT response to a [[ClassInfoContentV2]].
   *
   * @param classIri          the IRI of the class to be read.
   * @param constructResponse the SPARQL CONSTRUCT response to be read.
   * @return a [[ClassInfoContentV2]] representing a class definition.
   */
  private def constructResponseToClassDefinition(
    classIri: SmartIri,
    constructResponse: SparqlExtendedConstructResponse
  )(implicit stringFormatter: StringFormatter): ClassInfoContentV2 = {
    // All classes defined in the triplestore must be in Knora ontologies.

    val ontologyIri = classIri.getOntologyFromEntity

    if (!ontologyIri.isKnoraOntologyIri) {
      throw InconsistentRepositoryDataException(s"Class $classIri is not in a Knora ontology")
    }

    val statements = constructResponse.statements

    // Get the statements whose subject is the class.
    val classDefMap: Map[SmartIri, Seq[LiteralV2]] = statements(IriSubjectV2(classIri.toString))

    // Get the IRIs of the class's base classes.

    val subClassOfObjects: Seq[LiteralV2] =
      classDefMap.getOrElse(OntologyConstants.Rdfs.SubClassOf.toSmartIri, Seq.empty[LiteralV2])

    val subClassOf: Set[SmartIri] = subClassOfObjects.collect { case iriLiteral: IriLiteralV2 =>
      iriLiteral.value.toSmartIri
    }.toSet

    // Get the blank nodes representing cardinalities.

    val restrictionBlankNodeIDs: Set[BlankNodeLiteralV2] = subClassOfObjects.collect {
      case blankNodeLiteral: BlankNodeLiteralV2 => blankNodeLiteral
    }.toSet

    val directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = restrictionBlankNodeIDs.map { blankNodeID =>
      val blankNode: Map[SmartIri, Seq[LiteralV2]] = statements.getOrElse(
        BlankNodeSubjectV2(blankNodeID.value),
        throw InconsistentRepositoryDataException(
          s"Blank node '${blankNodeID.value}' not found in construct query result"
        )
      )

      val blankNodeTypeObjs: Seq[LiteralV2] = blankNode.getOrElse(
        OntologyConstants.Rdf.Type.toSmartIri,
        throw InconsistentRepositoryDataException(s"Blank node '${blankNodeID.value}' has no rdf:type")
      )

      blankNodeTypeObjs match {
        case Seq(IriLiteralV2(OntologyConstants.Owl.Restriction)) => ()
        case _ =>
          throw InconsistentRepositoryDataException(s"Blank node '${blankNodeID.value}' is not an owl:Restriction")
      }

      val onPropertyObjs: Seq[LiteralV2] = blankNode.getOrElse(
        OntologyConstants.Owl.OnProperty.toSmartIri,
        throw InconsistentRepositoryDataException(s"Blank node '${blankNodeID.value}' has no owl:onProperty")
      )

      val propertyIri: SmartIri = onPropertyObjs match {
        case Seq(propertyIri: IriLiteralV2) => propertyIri.value.toSmartIri
        case other                          => throw InconsistentRepositoryDataException(s"Invalid object for owl:onProperty: $other")
      }

      val owlCardinalityPreds: Set[SmartIri] = blankNode.keySet.filter { predicate =>
        OntologyConstants.Owl.cardinalityOWLRestrictions(predicate.toString)
      }

      if (owlCardinalityPreds.size != 1) {
        throw InconsistentRepositoryDataException(
          s"Expected one cardinality predicate in blank node '${blankNodeID.value}', got ${owlCardinalityPreds.size}"
        )
      }

      val owlCardinalityIri = owlCardinalityPreds.head

      val owlCardinalityValue: Int = blankNode(owlCardinalityIri) match {
        case Seq(IntLiteralV2(intVal)) => intVal
        case other =>
          throw InconsistentRepositoryDataException(
            s"Expected one integer object for predicate $owlCardinalityIri in blank node '${blankNodeID.value}', got $other"
          )
      }

      val guiOrder: Option[Int] = blankNode.get(OntologyConstants.SalsahGui.GuiOrder.toSmartIri) match {
        case Some(Seq(IntLiteralV2(intVal))) => Some(intVal)
        case None                            => None
        case other =>
          throw InconsistentRepositoryDataException(
            s"Expected one integer object for predicate ${OntologyConstants.SalsahGui.GuiOrder} in blank node '${blankNodeID.value}', got $other"
          )
      }

      // salsah-gui:guiElement and salsah-gui:guiAttribute aren't allowed here.

      if (blankNode.contains(OntologyConstants.SalsahGui.GuiElementProp.toSmartIri)) {
        throw InconsistentRepositoryDataException(
          s"Class $classIri contains salsah-gui:guiElement in an owl:Restriction"
        )
      }

      if (blankNode.contains(OntologyConstants.SalsahGui.GuiAttribute.toSmartIri)) {
        throw InconsistentRepositoryDataException(
          s"Class $classIri contains salsah-gui:guiAttribute in an owl:Restriction"
        )
      }

      propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
        propertyIri = propertyIri.toString,
        owlCardinality = Cardinality.OwlCardinalityInfo(
          owlCardinalityIri = owlCardinalityIri.toString,
          owlCardinalityValue = owlCardinalityValue,
          guiOrder = guiOrder
        )
      )
    }.toMap

    // Get any other predicates of the class.

    val otherPreds: Map[SmartIri, PredicateInfoV2] = getEntityPredicatesFromConstructResponse(
      classDefMap - OntologyConstants.Rdfs.SubClassOf.toSmartIri
    )

    ClassInfoContentV2(
      classIri = classIri,
      subClassOf = subClassOf,
      predicates = otherPreds,
      directCardinalities = directCardinalities,
      ontologySchema = classIri.getOntologySchema.get
    )
  }

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be. If not, return
   * an error message fitting for the "before update" case.
   *
   * @param settings the application settings.
   * @param storeManager the store manager actor ref.
   * @param internalOntologyIri          the internal IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a failed Future if the expected last modification date is not found.
   */
  def checkOntologyLastModificationDateBeforeUpdate(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
    featureFactoryConfig: FeatureFactoryConfig
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[Unit] =
    checkOntologyLastModificationDate(
      settings,
      storeManager,
      internalOntologyIri = internalOntologyIri,
      expectedLastModificationDate = expectedLastModificationDate,
      featureFactoryConfig = featureFactoryConfig,
      errorFun = throw EditConflictException(
        s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} has been modified by another user, please reload it and try again."
      )
    )

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be. If not, return
   * an error message fitting for the "after update" case.
   *
   * @param settings the application settings.
   * @param storeManager the store manager actor ref.
   * @param internalOntologyIri          the internal IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a failed Future if the expected last modification date is not found.
   */
  def checkOntologyLastModificationDateAfterUpdate(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
    featureFactoryConfig: FeatureFactoryConfig
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[Unit] =
    checkOntologyLastModificationDate(
      settings,
      storeManager,
      internalOntologyIri = internalOntologyIri,
      expectedLastModificationDate = expectedLastModificationDate,
      featureFactoryConfig = featureFactoryConfig,
      errorFun = throw UpdateNotPerformedException(
        s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} was not updated. Please report this as a possible bug."
      )
    )

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be.
   *
   * @param settings the application settings.
   * @param storeManager the store manager actor ref.
   * @param internalOntologyIri          the internal IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that the ontology is expected to have.
   * @param featureFactoryConfig the feature factory configuration.
   * @param errorFun                     a function that throws an exception. It will be called if the expected last modification date is not found.
   * @return a failed Future if the expected last modification date is not found.
   */
  private def checkOntologyLastModificationDate(
    settings: KnoraSettingsImpl,
    storeManager: ActorRef,
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
    featureFactoryConfig: FeatureFactoryConfig,
    errorFun: => Nothing
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[Unit] =
    for {
      existingOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(
                                                                settings,
                                                                storeManager,
                                                                internalOntologyIri = internalOntologyIri,
                                                                featureFactoryConfig = featureFactoryConfig
                                                              )

      _ = existingOntologyMetadata match {
            case Some(metadata) =>
              metadata.lastModificationDate match {
                case Some(lastModificationDate) =>
                  if (lastModificationDate != expectedLastModificationDate) {
                    errorFun
                  }

                case None =>
                  throw InconsistentRepositoryDataException(
                    s"Ontology $internalOntologyIri has no ${OntologyConstants.KnoraBase.LastModificationDate}"
                  )
              }

            case None =>
              throw NotFoundException(
                s"Ontology $internalOntologyIri (corresponding to ${internalOntologyIri.toOntologySchema(ApiV2Complex)}) not found"
              )
          }
    } yield ()

  /**
   * Checks whether the requesting user has permission to update an ontology.
   *
   * @param internalOntologyIri the internal IRI of the ontology.
   * @param requestingUser      the user making the request.
   * @return `true` if the user has permission to update the ontology
   */
  def canUserUpdateOntology(internalOntologyIri: SmartIri, requestingUser: UserADM)(implicit
    ec: ExecutionContext
  ): Future[Boolean] =
    for {
      cacheData <- Cache.getCacheData

      projectIri =
        cacheData.ontologies
          .getOrElse(
            internalOntologyIri,
            throw NotFoundException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} not found")
          )
          .ontologyMetadata
          .projectIri
          .get
    } yield requestingUser.permissions.isProjectAdmin(projectIri.toString) || requestingUser.permissions.isSystemAdmin

  /**
   * Throws an exception if the requesting user does not have permission to update an ontology.
   *
   * @param internalOntologyIri the internal IRI of the ontology.
   * @param requestingUser      the user making the request.
   * @return the project IRI.
   */
  def checkPermissionsForOntologyUpdate(internalOntologyIri: SmartIri, requestingUser: UserADM)(implicit
    ec: ExecutionContext
  ): Future[SmartIri] =
    for {
      cacheData <- Cache.getCacheData

      projectIri =
        cacheData.ontologies
          .getOrElse(
            internalOntologyIri,
            throw NotFoundException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} not found")
          )
          .ontologyMetadata
          .projectIri
          .get

      _ = if (
            !requestingUser.permissions.isProjectAdmin(projectIri.toString) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not a project or system admin
            throw ForbiddenException("Ontologies can be modified only by a project or system admin.")
          }

    } yield projectIri

  /**
   * Checks whether an ontology IRI is valid for an update.
   *
   * @param externalOntologyIri the external IRI of the ontology.
   * @return a failed Future if the IRI is not valid for an update.
   */
  def checkExternalOntologyIriForUpdate(externalOntologyIri: SmartIri): Future[Unit] =
    if (!externalOntologyIri.isKnoraOntologyIri) {
      FastFuture.failed(throw BadRequestException(s"Invalid ontology IRI for request: $externalOntologyIri}"))
    } else if (!externalOntologyIri.getOntologySchema.contains(ApiV2Complex)) {
      FastFuture.failed(throw BadRequestException(s"Invalid ontology schema for request: $externalOntologyIri"))
    } else if (externalOntologyIri.isKnoraBuiltInDefinitionIri) {
      FastFuture.failed(
        throw BadRequestException(s"Ontology $externalOntologyIri cannot be modified via the Knora API")
      )
    } else {
      FastFuture.successful(())
    }

  /**
   * Checks whether an entity IRI is valid for an update.
   *
   * @param externalEntityIri the external IRI of the entity.
   * @return a failed Future if the entity IRI is not valid for an update, or is not from the specified ontology.
   */
  private def checkExternalEntityIriForUpdate(externalEntityIri: SmartIri): Future[Unit] =
    if (!externalEntityIri.isKnoraApiV2EntityIri) {
      FastFuture.failed(throw BadRequestException(s"Invalid entity IRI for request: $externalEntityIri"))
    } else if (!externalEntityIri.getOntologySchema.contains(ApiV2Complex)) {
      FastFuture.failed(throw BadRequestException(s"Invalid ontology schema for request: $externalEntityIri"))
    } else {
      FastFuture.successful(())
    }

  /**
   * Given the definition of a link property, returns the definition of the corresponding link value property.
   *
   * @param internalPropertyDef the definition of the the link property, in the internal schema.
   * @return the definition of the corresponding link value property.
   */
  def linkPropertyDefToLinkValuePropertyDef(
    internalPropertyDef: PropertyInfoContentV2
  )(implicit stringFormatter: StringFormatter): PropertyInfoContentV2 = {
    val linkValuePropIri = internalPropertyDef.propertyIri.fromLinkPropToLinkValueProp

    val newPredicates: Map[SmartIri, PredicateInfoV2] =
      (internalPropertyDef.predicates - OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) +
        (OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri -> PredicateInfoV2(
          predicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
          objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraBase.LinkValue.toSmartIri))
        ))

    internalPropertyDef.copy(
      propertyIri = linkValuePropIri,
      predicates = newPredicates,
      subPropertyOf = Set(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
    )
  }

  /**
   * Given the cardinalities directly defined on a given class, and the cardinalities that it could inherit (directly
   * or indirectly) from its base classes, combines the two, filtering out the base class cardinalities ones that are overridden
   * by cardinalities defined directly on the given class. Checks that if a directly defined cardinality overrides an inheritable one,
   * the directly defined one is at least as restrictive as the inheritable one.
   *
   * @param classIri                  the class IRI.
   * @param thisClassCardinalities    the cardinalities directly defined on a given resource class.
   * @param inheritableCardinalities  the cardinalities that the given resource class could inherit from its base classes.
   * @param allSubPropertyOfRelations a map in which each property IRI points to the full set of its base properties.
   * @param errorSchema               the ontology schema to be used in error messages.
   * @param errorFun                  a function that throws an exception. It will be called with an error message argument if the cardinalities are invalid.
   * @return a map in which each key is the IRI of a property that has a cardinality in the resource class (or that it inherits
   *         from its base classes), and each value is the cardinality on the property.
   */
  def overrideCardinalities(
    classIri: SmartIri,
    thisClassCardinalities: Map[SmartIri, KnoraCardinalityInfo],
    inheritableCardinalities: Map[SmartIri, KnoraCardinalityInfo],
    allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
    errorSchema: OntologySchema,
    errorFun: String => Nothing
  ): Map[SmartIri, KnoraCardinalityInfo] = {
    // A map of directly defined properties to the base class properties they can override.
    val overrides: Map[SmartIri, Set[SmartIri]] = thisClassCardinalities.map {
      case (thisClassProp, thisClassCardinality) =>
        // For each directly defined cardinality, get its base properties, if available.
        // If the class has a cardinality for a non-Knora property like rdfs:label (which can happen only
        // if it's a built-in class), we won't have any information about the base properties of that property.
        val basePropsOfThisClassProp: Set[SmartIri] =
          allSubPropertyOfRelations.getOrElse(thisClassProp, Set.empty[SmartIri])

        val overriddenBaseProps: Set[SmartIri] = inheritableCardinalities.foldLeft(Set.empty[SmartIri]) {
          case (acc, (baseClassProp, baseClassCardinality)) =>
            // Can the directly defined cardinality override the inheritable one?
            if (thisClassProp == baseClassProp || basePropsOfThisClassProp.contains(baseClassProp)) {
              // Yes. Is the directly defined one at least as restrictive as the inheritable one?

              if (
                !Cardinality.isCompatible(
                  directCardinality = thisClassCardinality.cardinality,
                  inheritableCardinality = baseClassCardinality.cardinality
                )
              ) {
                // No. Throw an exception.
                errorFun(
                  s"In class <${classIri.toOntologySchema(errorSchema)}>, the directly defined cardinality ${thisClassCardinality.cardinality} on ${thisClassProp
                    .toOntologySchema(errorSchema)} is not compatible with the inherited cardinality ${baseClassCardinality.cardinality} on ${baseClassProp
                    .toOntologySchema(errorSchema)}, because it is less restrictive"
                )
              } else {
                // Yes. Filter out the inheritable one, because the directly defined one overrides it.
                acc + baseClassProp
              }
            } else {
              // No. Let the class inherit the inheritable cardinality.
              acc
            }
        }

        thisClassProp -> overriddenBaseProps
    }

    // A map of base class properties to the directly defined properties that can override them.
    val reverseOverrides: Map[SmartIri, Set[SmartIri]] = overrides.toVector.flatMap {
      // Unpack the sets to make an association list.
      case (thisClassProp: SmartIri, baseClassProps: Set[SmartIri]) =>
        baseClassProps.map { baseClassProp: SmartIri =>
          thisClassProp -> baseClassProp
        }
    }.map {
      // Reverse the direction of the association list.
      case (thisClassProp: SmartIri, baseClassProp: SmartIri) =>
        baseClassProp -> thisClassProp
    }.groupBy {
      // Group by base class prop to make a map.
      case (baseClassProp: SmartIri, _) => baseClassProp
    }.map {
      // Make sets of directly defined props.
      case (baseClassProp: SmartIri, thisClassProps: immutable.Iterable[(SmartIri, SmartIri)]) =>
        baseClassProp -> thisClassProps.map { case (_, thisClassProp) =>
          thisClassProp
        }.toSet
    }

    // Are there any base class properties that are overridden by more than one directly defined property,
    // and do any of those base properties have cardinalities that could cause conflicts between the cardinalities
    // on the directly defined cardinalities?
    reverseOverrides.foreach { case (baseClassProp, thisClassProps) =>
      if (thisClassProps.size > 1) {
        val overriddenCardinality: KnoraCardinalityInfo = inheritableCardinalities(baseClassProp)

        if (
          overriddenCardinality.cardinality == Cardinality.MustHaveOne || overriddenCardinality.cardinality == Cardinality.MayHaveOne
        ) {
          errorFun(
            s"In class <${classIri.toOntologySchema(errorSchema)}>, there is more than one cardinality that would override the inherited cardinality $overriddenCardinality on <${baseClassProp
              .toOntologySchema(errorSchema)}>"
          )
        }
      }
    }

    thisClassCardinalities ++ inheritableCardinalities.filterNot { case (basePropIri, _) =>
      reverseOverrides.contains(basePropIri)
    }
  }

  /**
   * Given all the `rdfs:subClassOf` relations between classes, calculates all the inverse relations.
   *
   * @param allSubClassOfRelations all the `rdfs:subClassOf` relations between classes.
   * @return a map of IRIs of resource classes to sets of the IRIs of their subclasses.
   */
  def calculateSuperClassOfRelations(
    allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]]
  ): Map[SmartIri, Set[SmartIri]] =
    allSubClassOfRelations.toVector.flatMap { case (subClass: SmartIri, baseClasses: Seq[SmartIri]) =>
      baseClasses.map { baseClass =>
        baseClass -> subClass
      }
    }
      .groupBy(_._1)
      .map { case (baseClass: SmartIri, baseClassAndSubClasses: Vector[(SmartIri, SmartIri)]) =>
        baseClass -> baseClassAndSubClasses.map(_._2).toSet
      }

  /**
   * Given a class loaded from the triplestore, recursively adds its inherited cardinalities to the cardinalities it defines
   * directly. A cardinality for a subproperty in a subclass overrides a cardinality for a base property in
   * a base class.
   *
   * @param classIri                  the IRI of the class whose properties are to be computed.
   * @param directSubClassOfRelations a map of the direct `rdfs:subClassOf` relations defined on each class.
   * @param allSubPropertyOfRelations a map in which each property IRI points to the full set of its base properties.
   * @param directClassCardinalities  a map of the cardinalities defined directly on each class.
   * @return a map in which each key is the IRI of a property that has a cardinality in the class (or that it inherits
   *         from its base classes), and each value is the cardinality on the property.
   */
  def inheritCardinalitiesInLoadedClass(
    classIri: SmartIri,
    directSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
    allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
    directClassCardinalities: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]]
  ): Map[SmartIri, KnoraCardinalityInfo] = {
    // Recursively get properties that are available to inherit from base classes. If we have no information about
    // a class, that could mean that it isn't a subclass of knora-base:Resource (e.g. it's something like
    // foaf:Person), in which case we assume that it has no base classes.
    val cardinalitiesAvailableToInherit: Map[SmartIri, KnoraCardinalityInfo] = directSubClassOfRelations
      .getOrElse(classIri, Set.empty[SmartIri])
      .foldLeft(Map.empty[SmartIri, KnoraCardinalityInfo]) {
        case (acc: Map[SmartIri, KnoraCardinalityInfo], baseClass: SmartIri) =>
          acc ++ inheritCardinalitiesInLoadedClass(
            classIri = baseClass,
            directSubClassOfRelations = directSubClassOfRelations,
            allSubPropertyOfRelations = allSubPropertyOfRelations,
            directClassCardinalities = directClassCardinalities
          )
      }

    // Get the properties that have cardinalities defined directly on this class. Again, if we have no information
    // about a class, we assume that it has no cardinalities.
    val thisClassCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
      directClassCardinalities.getOrElse(classIri, Map.empty[SmartIri, KnoraCardinalityInfo])

    // Combine the cardinalities defined directly on this class with the ones that are available to inherit.
    overrideCardinalities(
      classIri = classIri,
      thisClassCardinalities = thisClassCardinalities,
      inheritableCardinalities = cardinalitiesAvailableToInherit,
      allSubPropertyOfRelations = allSubPropertyOfRelations,
      errorSchema = InternalSchema,
      { msg: String =>
        throw InconsistentRepositoryDataException(msg)
      }
    )
  }

  /**
   * Checks whether a class IRI refers to a Knora internal resource class.
   *
   * @param classIri the class IRI.
   * @return `true` if the class IRI refers to a Knora resource class, or `false` if the class
   *         does not exist or is not a Knora internal resource class.
   */
  def isKnoraInternalResourceClass(classIri: SmartIri, cacheData: OntologyCacheData): Boolean =
    classIri.isKnoraInternalEntityIri &&
      cacheData.ontologies(classIri.getOntologyFromEntity).classes.get(classIri).exists(_.isResourceClass)

  /**
   * Checks whether a property is a subproperty of `knora-base:resourceProperty`.
   *
   * @param propertyIri the property IRI.
   * @param cacheData   the ontology cache.
   * @return `true` if the property is a subproperty of `knora-base:resourceProperty`.
   */
  def isKnoraResourceProperty(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean =
    propertyIri.isKnoraEntityIri &&
      cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isResourceProp)

  /**
   * Checks whether a property is a subproperty of `knora-base:hasLinkTo`.
   *
   * @param propertyIri the property IRI.
   * @param cacheData   the ontology cache.
   * @return `true` if the property is a subproperty of `knora-base:hasLinkTo`.
   */
  def isLinkProp(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean =
    propertyIri.isKnoraEntityIri &&
      cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isLinkProp)

  /**
   * Checks whether a property is a subproperty of `knora-base:hasLinkToValue`.
   *
   * @param propertyIri the property IRI.
   * @param cacheData   the ontology cache.
   * @return `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
   */
  def isLinkValueProp(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean =
    propertyIri.isKnoraEntityIri &&
      cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isLinkValueProp)

  /**
   * Checks whether a property is a subproperty of `knora-base:hasFileValue`.
   *
   * @param propertyIri the property IRI.
   * @param cacheData   the ontology cache.
   * @return `true` if the property is a subproperty of `knora-base:hasFileValue`.
   */
  def isFileValueProp(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean =
    propertyIri.isKnoraEntityIri &&
      cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isFileValueProp)

}
