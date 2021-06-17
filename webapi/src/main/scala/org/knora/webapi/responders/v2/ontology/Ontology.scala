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

import org.knora.webapi.{IRI, InternalSchema, OntologySchema}
import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.messages.StringFormatter.{SalsahGuiAttribute, SalsahGuiAttributeDefinition}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.store.triplestoremessages.{
  BooleanLiteralV2,
  IriLiteralV2,
  IriSubjectV2,
  LiteralV2,
  SparqlExtendedConstructResponse,
  StringLiteralV2
}
import org.knora.webapi.messages.util.rdf.{SparqlSelectResult, VariableResultsRow}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  Cardinality,
  ClassInfoContentV2,
  IndividualInfoContentV2,
  OntologyMetadataV2,
  PredicateInfoV2,
  PropertyInfoContentV2,
  ReadClassInfoV2,
  ReadIndividualInfoV2,
  ReadPropertyInfoV2
}
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses

import java.time.Instant
import scala.Predef.->

object Ontology {

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
  def buildOntologyMetadata(allOntologyMetadataResponse: SparqlSelectResult)(
      implicit stringFormatter: StringFormatter): Map[SmartIri, OntologyMetadataV2] = {
    allOntologyMetadataResponse.results.bindings.groupBy(_.rowMap("ontologyGraph")).map {
      case (ontologyGraph: IRI, rows: Seq[VariableResultsRow]) =>
        val ontologyIri = rows.head.rowMap("ontologyIri")

        if (ontologyIri != ontologyGraph) {
          throw InconsistentRepositoryDataException(
            s"Ontology $ontologyIri must be stored in named graph $ontologyIri, but it is in $ontologyGraph")
        }

        val ontologySmartIri = ontologyIri.toSmartIri

        if (!ontologySmartIri.isKnoraOntologyIri) {
          throw InconsistentRepositoryDataException(s"Ontology $ontologySmartIri is not a Knora ontology")
        }

        val ontologyMetadataMap: Map[IRI, String] = rows.map { row =>
          val pred =
            row.rowMap.getOrElse("ontologyPred",
                                 throw InconsistentRepositoryDataException(s"Empty predicate in ontology $ontologyIri"))
          val obj = row.rowMap.getOrElse(
            "ontologyObj",
            throw InconsistentRepositoryDataException(s"Empty object for predicate $pred in ontology $ontologyIri"))
          pred -> obj
        }.toMap

        val projectIri: SmartIri = ontologyMetadataMap
          .getOrElse(
            OntologyConstants.KnoraBase.AttachedToProject,
            throw InconsistentRepositoryDataException(s"Ontology $ontologyIri has no knora-base:attachedToProject"))
          .toSmartIri
        val ontologyLabel: String =
          ontologyMetadataMap.getOrElse(OntologyConstants.Rdfs.Label, ontologySmartIri.getOntologyName)
        val lastModificationDate: Option[Instant] = ontologyMetadataMap
          .get(OntologyConstants.KnoraBase.LastModificationDate)
          .map(
            instant =>
              stringFormatter.xsdDateTimeStampToInstant(
                instant,
                throw InconsistentRepositoryDataException(s"Invalid UTC instant: $instant")))
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
      * Given a list of ontology graphs, finds the IRIs of all subjects whose `rdf:type` is contained in a given set of types.
      *
      * @param ontologyGraphs a list of ontology graphs.
      * @param entityTypes    the types of entities to be found.
      * @return a map of ontology IRIs to sets of the IRIs of entities with matching types in each ontology.
      */
    def getEntityIrisFromOntologyGraphs(ontologyGraphs: Iterable[OntologyGraph],
                                        entityTypes: Set[IRI]): Map[SmartIri, Set[SmartIri]] = {
      val entityTypesAsIriLiterals = entityTypes.map(entityType => IriLiteralV2(entityType))

      ontologyGraphs.map { ontologyGraph =>
        val entityIrisInGraph: Set[SmartIri] =
          ontologyGraph.constructResponse.statements.foldLeft(Set.empty[SmartIri]) {
            case (acc, (subjectIri: IriSubjectV2, subjectStatements: Map[SmartIri, Seq[LiteralV2]])) =>
              val subjectTypeLiterals: Seq[IriLiteralV2] = subjectStatements
                .getOrElse(OntologyConstants.Rdf.Type.toSmartIri,
                           throw InconsistentRepositoryDataException(s"Subject $subjectIri has no rdf:type"))
                .collect {
                  case iriLiteral: IriLiteralV2 => iriLiteral
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
    def makeReadClassInfos(classDefs: Map[SmartIri, ClassInfoContentV2],
                           directClassCardinalities: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]],
                           classCardinalitiesWithInheritance: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]],
                           directSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                           allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]],
                           allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                           allPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
                           allKnoraResourceProps: Set[SmartIri],
                           allLinkProps: Set[SmartIri],
                           allLinkValueProps: Set[SmartIri],
                           allFileValueProps: Set[SmartIri]): Map[SmartIri, ReadClassInfoV2] = {
      classDefs.map {
        case (classIri, classDef) =>
          val ontologyIri = classIri.getOntologyFromEntity

          // Get the OWL cardinalities for the class.
          val allOwlCardinalitiesForClass: Map[SmartIri, KnoraCardinalityInfo] =
            classCardinalitiesWithInheritance(classIri)
          val allPropertyIrisForCardinalitiesInClass: Set[SmartIri] = allOwlCardinalitiesForClass.keys.toSet

          // Identify the Knora resource properties, link properties, link value properties, and file value properties in the cardinalities.
          val knoraResourcePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allKnoraResourceProps)
          val linkPropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allLinkProps)
          val linkValuePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allLinkValueProps)
          val fileValuePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allFileValueProps)

          // Make sure there is a link value property for each link property.

          val missingLinkValueProps = linkPropsInClass.map(_.fromLinkPropToLinkValueProp) -- linkValuePropsInClass

          if (missingLinkValueProps.nonEmpty) {
            throw InconsistentRepositoryDataException(
              s"Resource class $classIri has cardinalities for one or more link properties without corresponding link value properties. The missing (or incorrectly defined) property or properties: ${missingLinkValueProps
                .mkString(", ")}")
          }

          // Make sure there is a link property for each link value property.

          val missingLinkProps = linkValuePropsInClass.map(_.fromLinkValuePropToLinkProp) -- linkPropsInClass

          if (missingLinkProps.nonEmpty) {
            throw InconsistentRepositoryDataException(
              s"Resource class $classIri has cardinalities for one or more link value properties without corresponding link properties. The missing (or incorrectly defined) property or properties: ${missingLinkProps
                .mkString(", ")}")
          }

          // Make sure that the cardinality for each link property is the same as the cardinality for the corresponding link value property.
          for (linkProp <- linkPropsInClass) {
            val linkValueProp: SmartIri = linkProp.fromLinkPropToLinkValueProp
            val linkPropCardinality: KnoraCardinalityInfo = allOwlCardinalitiesForClass(linkProp)
            val linkValuePropCardinality: KnoraCardinalityInfo = allOwlCardinalitiesForClass(linkValueProp)

            if (!linkPropCardinality.equalsWithoutGuiOrder(linkValuePropCardinality)) {
              throw InconsistentRepositoryDataException(
                s"In class $classIri, the cardinality for $linkProp is different from the cardinality for $linkValueProp")
            }
          }

          // The class's direct cardinalities.
          val directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = directClassCardinalities(classIri)

          val directCardinalityPropertyIris = directCardinalities.keySet
          val allBaseClasses: Seq[SmartIri] = allSubClassOfRelations(classIri)
          val isKnoraResourceClass = allBaseClasses.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)
          val isStandoffClass = allBaseClasses.contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri)
          val isValueClass = !(isKnoraResourceClass || isStandoffClass) && allBaseClasses.contains(
            OntologyConstants.KnoraBase.Value.toSmartIri)

          // If the class is defined in project-specific ontology, do the following checks.
          if (!ontologyIri.isKnoraBuiltInDefinitionIri) {
            // It must be either a resource class or a standoff class, but not both.
            if (!(isKnoraResourceClass ^ isStandoffClass)) {
              throw InconsistentRepositoryDataException(
                s"Class $classIri must be a subclass either of knora-base:Resource or of knora-base:StandoffTag (but not both)")
            }

            // All its cardinalities must be on properties that are defined.
            val cardinalitiesOnMissingProps = directCardinalityPropertyIris.filterNot(allPropertyDefs.keySet)

            if (cardinalitiesOnMissingProps.nonEmpty) {
              throw InconsistentRepositoryDataException(
                s"Class $classIri has one or more cardinalities on undefined properties: ${cardinalitiesOnMissingProps
                  .mkString(", ")}")
            }

            // It cannot have cardinalities both on property P and on a subproperty of P.

            val maybePropertyAndSubproperty: Option[(SmartIri, SmartIri)] = findPropertyAndSubproperty(
              propertyIris = allPropertyIrisForCardinalitiesInClass,
              subPropertyOfRelations = allSubPropertyOfRelations
            )

            maybePropertyAndSubproperty match {
              case Some((basePropertyIri, propertyIri)) =>
                throw InconsistentRepositoryDataException(
                  s"Class $classIri has a cardinality on property $basePropertyIri and on its subproperty $propertyIri")

              case None => ()
            }

            if (isKnoraResourceClass) {
              // If it's a resource class, all its directly defined cardinalities must be on Knora resource properties, not including knora-base:resourceProperty or knora-base:hasValue.

              val cardinalitiesOnInvalidProps = directCardinalityPropertyIris.filterNot(allKnoraResourceProps)

              if (cardinalitiesOnInvalidProps.nonEmpty) {
                throw InconsistentRepositoryDataException(
                  s"Resource class $classIri has one or more cardinalities on properties that are not Knora resource properties: ${cardinalitiesOnInvalidProps
                    .mkString(", ")}")
              }

              Set(OntologyConstants.KnoraBase.ResourceProperty, OntologyConstants.KnoraBase.HasValue).foreach {
                invalidProp =>
                  if (directCardinalityPropertyIris.contains(invalidProp.toSmartIri)) {
                    throw InconsistentRepositoryDataException(
                      s"Class $classIri has a cardinality on property $invalidProp, which is not allowed")
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
                if (baseClass.isKnoraDefinitionIri && !allSubClassOfRelations(baseClass).contains(
                      OntologyConstants.KnoraBase.Resource.toSmartIri)) {
                  throw InconsistentRepositoryDataException(
                    s"Class $classIri is a subclass of knora-base:Resource, but its base class $baseClass is not")
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
                    .mkString(", ")}")
              }

              // All its base classes with Knora IRIs must also be standoff classes.
              for (baseClass <- classDef.subClassOf) {
                if (baseClass.isKnoraDefinitionIri) {
                  if (isStandoffClass && !allSubClassOfRelations(baseClass).contains(
                        OntologyConstants.KnoraBase.StandoffTag.toSmartIri)) {
                    throw InconsistentRepositoryDataException(
                      s"Class $classIri is a subclass of knora-base:StandoffTag, but its base class $baseClass is not")
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

          val inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = allOwlCardinalitiesForClass
            .filterNot {
              case (propertyIri, _) => directCardinalityPropertyIris.contains(propertyIri)
            }

          // Get the class's standoff data type, if any. A standoff class that has a datatype is a subclass of one of the classes
          // in StandoffDataTypeClasses.

          val standoffDataType: Set[SmartIri] = allSubClassOfRelations(classIri).toSet
            .intersect(StandoffDataTypeClasses.getStandoffClassIris.map(_.toSmartIri))

          if (standoffDataType.size > 1) {
            throw InconsistentRepositoryDataException(
              s"Class $classIri is a subclass of more than one standoff datatype: ${standoffDataType.mkString(", ")}")
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
                    throw InconsistentRepositoryDataException(s"$dataType is not a valid standoff datatype")))

              case None => None
            }
          )

          classIri -> readClassInfo
      }
    }

  }

  /**
    * Checks for invalid cardinalities on boolean properties.
    *
    * @param classIri the class IRI.
    * @param directCardinalities the cardinalities directly defined on the class.
    * @param allPropertyDefs all property definitions.
    */
  def checkForInvalidBooleanCardinalities(classIri: SmartIri,
                                          directCardinalities: Map[SmartIri, KnoraCardinalityInfo],
                                          allPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
                                          schemaForErrors: OntologySchema,
                                          errorFun: String => Nothing): Unit = {
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
          .mkString(", ")}")
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
  private def makeReadPropertyInfos(propertyDefs: Map[SmartIri, PropertyInfoContentV2],
                                    directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                    allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                    allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]],
                                    allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
                                    allKnoraResourceProps: Set[SmartIri],
                                    allLinkProps: Set[SmartIri],
                                    allLinkValueProps: Set[SmartIri],
                                    allFileValueProps: Set[SmartIri]): Map[SmartIri, ReadPropertyInfoV2] = {
    propertyDefs.map {
      case (propertyIri, propertyDef) =>
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
        val isLinkProp = allLinkProps.contains(propertyIri)
        val isLinkValueProp = allLinkValueProps.contains(propertyIri)
        val isFileValueProp = allFileValueProps.contains(propertyIri)

        // If the property is defined in a project-specific ontology and is a Knora resource property (a subproperty of knora-base:hasValue or knora-base:hasLinkTo), do the following checks.
        if (!propertyIri.isKnoraBuiltInDefinitionIri && isResourceProp) {
          // The property must be a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.
          if (isValueProp && isLinkProp) {
            throw InconsistentRepositoryDataException(
              s"Property $propertyIri cannot be a subproperty of both knora-base:hasValue and knora-base:hasLinkTo")
          }

          // It can't be a subproperty of knora-base:hasFileValue.
          if (isFileValueProp) {
            throw InconsistentRepositoryDataException(
              s"Property $propertyIri cannot be a subproperty of knora-base:hasFileValue")
          }

          // Each of its base properties that has a Knora IRI must also be a Knora resource property.
          for (baseProperty <- propertyDef.subPropertyOf) {
            if (baseProperty.isKnoraDefinitionIri && !allKnoraResourceProps.contains(baseProperty)) {
              throw InconsistentRepositoryDataException(
                s"Property $propertyIri is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but its base property $baseProperty is not")
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
            OntologyConstants.KnoraBase.StandoffTagHasInternalReference.toSmartIri)
        )

        propertyIri -> propertyEntityInfo
    }
  }

  /**
    * Constructs a map of OWL named individual IRIs to [[ReadIndividualInfoV2]] instances.
    *
    * @param individualDefs a map of OWL named individual IRIs to named individuals.
    * @return a map of individual IRIs to [[ReadIndividualInfoV2]] instances.
    */
  private def makeReadIndividualInfos(
      individualDefs: Map[SmartIri, IndividualInfoContentV2]): Map[SmartIri, ReadIndividualInfoV2] = {
    individualDefs.map {
      case (individualIri, individual) =>
        individualIri -> ReadIndividualInfoV2(individual)
    }
  }

  /**
    * Given all the OWL named individuals available, constructs a map of `salsah-gui:Guielement` individuals to
    * their GUI attribute definitions.
    *
    * @param allIndividuals all the OWL named individuals available.
    * @return a map of `salsah-gui:Guielement` individuals to their GUI attribute definitions.
    */
  private def makeGuiAttributeDefinitions(
      allIndividuals: Map[SmartIri, IndividualInfoContentV2]): Map[SmartIri, Set[SalsahGuiAttributeDefinition]] = {
    val guiElementIndividuals: Map[SmartIri, IndividualInfoContentV2] = allIndividuals.filter {
      case (_, individual) => individual.getRdfType.toString == OntologyConstants.SalsahGui.GuiElementClass
    }

    guiElementIndividuals.map {
      case (guiElementIri, guiElementIndividual) =>
        val attributeDefs: Set[SalsahGuiAttributeDefinition] =
          guiElementIndividual.predicates.get(OntologyConstants.SalsahGui.GuiAttributeDefinition.toSmartIri) match {
            case Some(predicateInfo) =>
              predicateInfo.objects.map {
                case StringLiteralV2(attributeDefStr, None) =>
                  stringFormatter.toSalsahGuiAttributeDefinition(
                    attributeDefStr,
                    throw InconsistentRepositoryDataException(
                      s"Invalid salsah-gui:guiAttributeDefinition in $guiElementIri: $attributeDefStr")
                  )

                case other =>
                  throw InconsistentRepositoryDataException(
                    s"Invalid salsah-gui:guiAttributeDefinition in $guiElementIri: $other")
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
  private def validateGuiAttributes(propertyInfoContent: PropertyInfoContentV2,
                                    allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
                                    errorFun: String => Nothing): Unit = {
    val propertyIri = propertyInfoContent.propertyIri
    val predicates = propertyInfoContent.predicates

    // Find out which salsah-gui:Guielement the property uses, if any.
    val maybeGuiElementPred: Option[PredicateInfoV2] =
      predicates.get(OntologyConstants.SalsahGui.GuiElementProp.toSmartIri)
    val maybeGuiElementIri: Option[SmartIri] = maybeGuiElementPred.map(
      _.requireIriObject(throw InconsistentRepositoryDataException(
        s"Property $propertyIri has an invalid object for ${OntologyConstants.SalsahGui.GuiElementProp}")))

    // Get that Guielement's attribute definitions, if any.
    val guiAttributeDefs: Set[SalsahGuiAttributeDefinition] = maybeGuiElementIri match {
      case Some(guiElementIri) =>
        allGuiAttributeDefinitions.getOrElse(
          guiElementIri,
          errorFun(s"Property $propertyIri has salsah-gui:guiElement $guiElementIri, which doesn't exist"))

      case None => Set.empty[SalsahGuiAttributeDefinition]
    }

    // If the property has the predicate salsah-gui:guiAttribute, syntactically validate the objects of that predicate.
    val guiAttributes: Set[SalsahGuiAttribute] =
      predicates.get(OntologyConstants.SalsahGui.GuiAttribute.toSmartIri) match {
        case Some(guiAttributePred) =>
          val guiElementIri = maybeGuiElementIri.getOrElse(
            errorFun(s"Property $propertyIri has salsah-gui:guiAttribute, but no salsah-gui:guiElement"))

          if (guiAttributeDefs.isEmpty) {
            errorFun(
              s"Property $propertyIri has salsah-gui:guiAttribute, but $guiElementIri has no salsah-gui:guiAttributeDefinition")
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
    val requiredAttributeNames = guiAttributeDefs.filter(_.isRequired).map(_.attributeName)
    val providedAttributeNames = guiAttributes.map(_.attributeName)
    val missingAttributeNames: Set[String] = requiredAttributeNames -- providedAttributeNames

    if (missingAttributeNames.nonEmpty) {
      errorFun(
        s"Property $propertyIri has one or more missing objects of salsah-gui:guiAttribute: ${missingAttributeNames
          .mkString(", ")}")
    }
  }

}
