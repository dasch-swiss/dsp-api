/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.search.gravsearch

import akka.actor.ActorSystem
import akka.event.{LogSource, LoggingAdapter}
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.KnoraReadV2
import org.knora.webapi.messages.v2.responder.ontologymessages.{EntityInfoGetRequestV2, EntityInfoGetResponseV2, ReadClassInfoV2, ReadPropertyInfoV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search._
import org.knora.webapi.util.{SmartIri, StringFormatter}

import scala.annotation.tailrec
import scala.concurrent.Future

/**
  * A Gravsearch type inspector that infers types, relying on information from the relevant ontologies.
  */
class InferringGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                       system: ActorSystem) extends GravsearchTypeInspector(nextInspector = nextInspector, system = system) {

    import InferringGravsearchTypeInspector._

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val log: LoggingAdapter = akka.event.Logging(system, this)

    // The maximum number of type inference iterations.
    private val MAX_ITERATIONS = 50

    /**
      * Represents an inference rule in a pipeline. Each rule in the pipeline tries to determine type information
      * about a typeable entity, then calls the next rule in the pipeline.
      *
      * @param nextRule the next rule in the pipeline.
      */
    private abstract class InferenceRule(protected val nextRule: Option[InferenceRule]) {
        /**
          * Attempts to determine the type of a single entity. Each implementation must end by calling
          * `runNextRule`.
          *
          * @param entityToType       the entity whose type needs to be determined.
          * @param intermediateResult the current intermediate type inference result.
          * @param entityInfo         information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex         an index of entity usage in the query.
          * @return the types that the rule inferred for the entity, or an empty set if no type could be determined.
          */
        def infer(entityToType: TypeableEntity,
                  intermediateResult: IntermediateTypeInspectionResult,
                  entityInfo: EntityInfoGetResponseV2,
                  usageIndex: UsageIndex): IntermediateTypeInspectionResult

        /**
          * Runs the next rule in the pipeline.
          *
          * @param entityToType       the entity whose type needs to be determined.
          * @param intermediateResult this rule's intermediate result.
          * @param entityInfo         information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex         an index of entity usage in the query.
          * @return the types that the rule inferred for the entity, or an empty set if no type could be determined.
          */
        protected def runNextRule(entityToType: TypeableEntity,
                                  intermediateResult: IntermediateTypeInspectionResult,
                                  entityInfo: EntityInfoGetResponseV2,
                                  usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Is there another rule in the pipeline?
            nextRule match {
                case Some(rule) =>
                    // Yes. Run that rule.
                    rule.infer(
                        entityToType = entityToType,
                        intermediateResult = intermediateResult,
                        entityInfo = entityInfo,
                        usageIndex = usageIndex
                    )

                case None =>
                    // No. Return the result we have.
                    intermediateResult
            }
        }
    }

    /**
      * Infers the type of an entity if there is an `rdf:type` statement about it.
      */
    private class RdfTypeRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(entityToType: TypeableEntity,
                           intermediateResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Has this entity been used as a subject?
            val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.subjectIndex.get(entityToType) match {
                case Some(statements) =>
                    // Yes. If it's been used with the predicate rdf:type with an IRI object, collect those objects.
                    val rdfTypes: Set[SmartIri] = statements.collect {
                        case StatementPattern(_, IriRef(predIri, _), IriRef(objIri, _), _) if predIri.toString == OntologyConstants.Rdf.Type => objIri
                    }

                    rdfTypes.flatMap {
                        rdfType =>
                            // Is this type a Knora entity?
                            if (rdfType.isKnoraEntityIri) {
                                // Yes. Has the ontology responder provided a class definition for it?
                                entityInfo.classInfoMap.get(rdfType) match {
                                    case Some(classDef) =>
                                        // Yes. Is it a resource class?
                                        if (classDef.isResourceClass) {
                                            // Yes. Infer rdf:type knora-api:Resource.
                                            val inferredType = NonPropertyTypeInfo(InferenceRuleUtil.getResourceTypeIriForSchema(usageIndex.querySchema))
                                            log.debug("RdfTypeRule: {} {} .", entityToType, inferredType)
                                            Some(inferredType)
                                        } else if (classDef.isStandoffClass) {
                                            // It's not a resource class, it's a standoff class. Infer rdf:type knora-api:StandoffTag.
                                            val inferredType = NonPropertyTypeInfo(OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag.toSmartIri)
                                            log.debug("RdfTypeRule: {} {} .", entityToType, inferredType)
                                            Some(inferredType)
                                        } else {
                                            val inferredType = NonPropertyTypeInfo(rdfType)

                                            // It's not a resource class or standoff class. Is it valid in a type inspection result?
                                            if (GravsearchTypeInspectionUtil.GravsearchTypeIris.contains(rdfType.toString)) {
                                                // Yes. Return it.
                                                val inferredType = NonPropertyTypeInfo(rdfType)
                                                log.debug("RdfTypeRule: {} {} .", entityToType, inferredType)
                                                Some(inferredType)
                                            } else {
                                                // No. This must mean it's not allowed in Gravsearch queries.
                                                throw GravsearchException(s"Type not allowed in Gravsearch queries: $entityToType $inferredType")
                                            }
                                        }

                                    case None =>
                                        // The ontology responder hasn't provided a definition of this class.
                                        // This should have caused an error earlier from the ontology responder.
                                        throw AssertionException(s"No information found about class ${IriRef(rdfType).toString}")
                                }
                            } else if (GravsearchTypeInspectionUtil.GravsearchTypeIris.contains(rdfType.toString)) {
                                // This isn't a Knora entity. If it's valid in a type inspection result, return it.
                                val inferredType = NonPropertyTypeInfo(rdfType)
                                log.debug("RdfTypeRule: {} {} .", entityToType, inferredType)
                                Some(inferredType)
                            } else {
                                None
                            }
                    }

                case None =>
                    // This entity hasn't been used as a subject, so this rule isn't relevant.
                    Set.empty[GravsearchEntityTypeInfo]
            }

            runNextRule(
                entityToType = entityToType,
                intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes),
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers a Knora property's `knora-api:objectType` if the property's IRI is used as a predicate.
      */
    private class PropertyIriObjectTypeRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(entityToType: TypeableEntity,
                           intermediateResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Is this entity an IRI?
            val inferredTypes: Set[GravsearchEntityTypeInfo] = entityToType match {
                case TypeableIri(iri) =>
                    // Yes. Is it a Knora property IRI?
                    if (usageIndex.knoraPropertyIris.contains(iri)) {
                        // Yes. Has the ontology responder provided information about it?
                        entityInfo.propertyInfoMap.get(iri) match {
                            case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                                // Yes. Try to infer its knora-api:objectType from the provided information.
                                InferenceRuleUtil.readPropertyInfoToObjectType(readPropertyInfo, entityInfo, usageIndex.querySchema) match {
                                    case Some(objectTypeIri: SmartIri) =>
                                        val inferredType = PropertyTypeInfo(objectTypeIri = objectTypeIri)
                                        log.debug("PropertyIriObjectTypeRule: {} {} .", entityToType, inferredType)
                                        Set(inferredType)

                                    case None =>
                                        // Its knora-api:objectType couldn't be inferred.
                                        Set.empty[GravsearchEntityTypeInfo]
                                }

                            case None =>
                                // The ontology responder hasn't provided a definition of this property. This should have caused
                                // an error earlier from the ontology responder.
                                throw AssertionException(s"No information found about property $iri")
                        }
                    } else {
                        // The IRI isn't a Knora property IRI, so this rule isn't relevant.
                        Set.empty[GravsearchEntityTypeInfo]
                    }

                case _ =>
                    // This entity isn't an IRI, so this rule isn't relevant.
                    Set.empty[GravsearchEntityTypeInfo]
            }

            runNextRule(
                entityToType = entityToType,
                intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes),
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers an entity's type if the entity is used as the object of a statement and the predicate's
      * `knora-api:objectType` is known.
      */
    private class TypeOfObjectFromPropertyRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(entityToType: TypeableEntity,
                           intermediateResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Has this entity been used as the object of one or more statements?
            val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.objectIndex.get(entityToType) match {
                case Some(statements) =>
                    // Yes. Try to infer type information from the predicate of each of those statements.
                    statements.flatMap {
                        statement =>
                            // Is the predicate typeable?
                            GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.pred) match {
                                case Some(typeablePred: TypeableEntity) =>
                                    // Yes. Do we have its types?
                                    intermediateResult.entities.get(typeablePred) match {
                                        case Some(entityTypes: Set[GravsearchEntityTypeInfo]) =>
                                            // Yes. Use the knora-api:objectType of each PropertyTypeInfo.
                                            entityTypes.flatMap {
                                                case propertyTypeInfo: PropertyTypeInfo =>
                                                    val inferredType: GravsearchEntityTypeInfo = NonPropertyTypeInfo(propertyTypeInfo.objectTypeIri)
                                                    log.debug("TypeOfObjectFromPropertyRule: {} {} .", entityToType, inferredType)
                                                    Some(inferredType)

                                                case _ =>
                                                    None
                                            }

                                        case other =>
                                            // We don't have the predicate's type.
                                            Set.empty[GravsearchEntityTypeInfo]
                                    }

                                case None =>
                                    // The predicate isn't typeable.
                                    Set.empty[GravsearchEntityTypeInfo]
                            }
                    }

                case None =>
                    // This entity hasn't been used as a statement object, so this rule isn't relevant.
                    Set.empty[GravsearchEntityTypeInfo]
            }

            runNextRule(
                entityToType = entityToType,
                intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes),
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }


    /**
      * Infers an entity's type if the entity is used as the subject of a statement, the predicate is an IRI, and
      * the predicate's `knora-api:subjectType` is known.
      */
    private class TypeOfSubjectFromPropertyRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(entityToType: TypeableEntity,
                           intermediateResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Has this entity been used as the subject of one or more statements?
            val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.subjectIndex.get(entityToType) match {
                case Some(statements) =>
                    // Yes. Try to infer type information from the predicate of each of those statements.
                    statements.flatMap {
                        statement =>
                            // Is the predicate a Knora IRI, and not a type annotation predicate?
                            statement.pred match {
                                case IriRef(predIri, _) if predIri.isKnoraEntityIri && !GravsearchTypeInspectionUtil.TypeAnnotationProperties.allTypeAnnotationIris.contains(predIri.toString) =>
                                    // Yes. Has the ontology responder provided a property definition for it?
                                    entityInfo.propertyInfoMap.get(predIri) match {
                                        case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                                            // Yes. Can we infer the property's knora-api:subjectType from that definition?
                                            InferenceRuleUtil.readPropertyInfoToSubjectType(readPropertyInfo, entityInfo, usageIndex.querySchema) match {
                                                case Some(subjectTypeIri: SmartIri) =>
                                                    // Yes. Use that type.
                                                    val inferredType = NonPropertyTypeInfo(subjectTypeIri)
                                                    log.debug("TypeOfSubjectFromPropertyRule: {} {} .", entityToType, inferredType)
                                                    Some(inferredType)

                                                case None =>
                                                    // No. This rule can't infer the entity's type.
                                                    None
                                            }


                                        case None =>
                                            // The ontology responder hasn't provided a definition of this property. This should have caused
                                            // an error earlier from the ontology responder.
                                            throw AssertionException(s"No information found about property $predIri")
                                    }

                                case _ =>
                                    // The predicate isn't a Knora IRI, or is a type inspection predicate, so this rule isn't relevant.
                                    None
                            }
                    }

                case None =>
                    // This entity hasn't been used as a subject, so this rule isn't relevant.
                    Set.empty[GravsearchEntityTypeInfo]
            }

            runNextRule(
                entityToType = entityToType,
                intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes),
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers the `knora-api:objectType` of a property variable or IRI if it's used with an object whose type is known.
      */
    private class PropertyTypeFromObjectRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(entityToType: TypeableEntity,
                           intermediateResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Has this entity been used as a predicate?
            val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.predicateIndex.get(entityToType) match {
                case Some(statements) =>
                    // Yes. Try to infer its knora-api:objectType from the object of each of those statements.
                    statements.flatMap {
                        statement =>
                            // Is the object typeable?
                            GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.obj) match {
                                case Some(typeableObj: TypeableEntity) =>
                                    // Yes. Do we have its types?
                                    intermediateResult.entities.get(typeableObj) match {
                                        case Some(entityTypes: Set[GravsearchEntityTypeInfo]) =>
                                            // Yes. Use those types.
                                            entityTypes.flatMap {
                                                case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                                                    val inferredType: GravsearchEntityTypeInfo = PropertyTypeInfo(nonPropertyTypeInfo.typeIri)
                                                    log.debug("PropertyTypeFromObjectRule: {} {} .", entityToType, inferredType)
                                                    Some(inferredType)

                                                case _ =>
                                                    None
                                            }

                                        case _ =>
                                            // We don't have the object's type.
                                            Set.empty[GravsearchEntityTypeInfo]
                                    }

                                case None =>
                                    // The object isn't typeable.
                                    Set.empty[GravsearchEntityTypeInfo]
                            }
                    }

                case None =>
                    // This entity hasn't been used as a predicate, so this rule isn't relevant.
                    Set.empty[GravsearchEntityTypeInfo]
            }

            runNextRule(
                entityToType = entityToType,
                intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes),
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers the types of variables from their use in FILTER expressions.
      */
    private class VarTypeFromFilterRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(entityToType: TypeableEntity,
                           intermediateResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
            // Is this entity a variable?
            val inferredTypes: Set[GravsearchEntityTypeInfo] = entityToType match {
                case variableToType: TypeableVariable =>
                    // Yes. Has it been used as a predicate?
                    usageIndex.predicateIndex.get(variableToType) match {
                        case Some(_) =>
                            // Yes. Has it been compared with one or more Knora property IRIs in a FILTER?
                            usageIndex.knoraPropertyVariablesInFilters.get(variableToType) match {
                                case Some(propertyIris: Set[SmartIri]) =>
                                    // Yes.
                                    propertyIris.flatMap {
                                        propertyIri: SmartIri =>
                                            // Has the ontology responder provided a definition of this property?
                                            entityInfo.propertyInfoMap.get(propertyIri) match {
                                                case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                                                    // Yes. Can we determine the property's knora-api:objectType from that definition?
                                                    InferenceRuleUtil.readPropertyInfoToObjectType(readPropertyInfo, entityInfo, usageIndex.querySchema) match {
                                                        case Some(objectTypeIri: SmartIri) =>
                                                            // Yes. Use that type.
                                                            val inferredType = PropertyTypeInfo(objectTypeIri = objectTypeIri)
                                                            log.debug("VarTypeFromFilterRule: {} {} .", variableToType, inferredType)
                                                            Some(inferredType)

                                                        case None =>
                                                            // No knora-api:objectType could be determined for the property IRI.
                                                            None
                                                    }

                                                case None =>
                                                    // The ontology responder hasn't provided a definition of this property. This should have caused
                                                    // an error earlier from the ontology responder.
                                                    throw AssertionException(s"No information found about property $propertyIri")
                                            }
                                    }

                                case None =>
                                    // The variable hasn't been compared with an IRI in a FILTER, so this rule isn't relevant.
                                    Set.empty[GravsearchEntityTypeInfo]
                            }

                        case None =>
                            // The variable hasn't been used as a predicate. Do we have one or more types for it from a FILTER?
                            usageIndex.typedVariablesInFilters.get(variableToType) match {
                                case Some(typesFromFilters: Set[SmartIri]) =>
                                    // Yes. If any of them are valid in type inspection results, return them.

                                    typesFromFilters.collect {
                                        case typeFromFilter if GravsearchTypeInspectionUtil.GravsearchTypeIris.contains(typeFromFilter.toString) =>
                                            val inferredType = NonPropertyTypeInfo(typeFromFilter)
                                            log.debug("VarTypeFromFilterRule: {} {} .", variableToType, inferredType)
                                            inferredType
                                    }

                                case None =>
                                    Set.empty[GravsearchEntityTypeInfo]
                            }
                    }

                case _ =>
                    // The entity isn't a variable, so this rule isn't relevant.
                    Set.empty[GravsearchEntityTypeInfo]
            }

            runNextRule(
                entityToType = entityToType,
                intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes),
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Utility functions for type inference rules.
      */
    private object InferenceRuleUtil {
        /**
          * Returns knora-api:Resource in the specified schema.
          *
          * @param querySchema the ontology schema that the query is written in.
          */
        def getResourceTypeIriForSchema(querySchema: ApiV2Schema): SmartIri = {
            querySchema match {
                case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri
                case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri
            }
        }

        /**
          * Returns knora-api:File (if the simple schema is given) or knora-api:FileValue (if the complex schema is given).
          *
          * @param querySchema the ontology schema that the query is written in.
          */
        def getFileTypeForSchema(querySchema: ApiV2Schema): SmartIri = {
            querySchema match {
                case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.File.toSmartIri
                case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.FileValue.toSmartIri
            }
        }

        /**
          * Given a [[ReadPropertyInfoV2]], returns the IRI of the inferred `knora-api:subjectType` of the property, if any.
          *
          * @param readPropertyInfo the property definition.
          * @param querySchema      the query schema.
          * @return the IRI of the inferred `knora-api:subjectType` of the property, or `None` if it could not inferred.
          */
        def readPropertyInfoToSubjectType(readPropertyInfo: ReadPropertyInfoV2, entityInfo: EntityInfoGetResponseV2, querySchema: ApiV2Schema): Option[SmartIri] = {
            // Is this a resource property?
            if (readPropertyInfo.isResourceProp) {
                // Yes. Infer knora-api:subjectType knora-api:Resource.
                Some(getResourceTypeIriForSchema(querySchema))
            } else {
                // It's not a resource property. Get the knora-api:subjectType that the ontology responder provided.
                readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri).
                    orElse(readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri)) match {
                    case Some(subjectType: SmartIri) =>
                        val subjectTypeStr = subjectType.toString

                        // Is it knora-api:Value?
                        if (subjectTypeStr == OntologyConstants.KnoraApiV2WithValueObjects.Value) {
                            // Yes. Don't use it.
                            None
                        } else if (OntologyConstants.KnoraApiV2WithValueObjects.FileValueClasses.contains(subjectTypeStr)) {
                            // No. If it's a file value class, return the representation of file values in the specified schema.
                            Some(getFileTypeForSchema(querySchema))
                        } else {
                            // It's not a file value class, either. Is it a standoff class?
                            val isStandoffClass: Boolean = entityInfo.classInfoMap.get(subjectType) match {
                                case Some(classDef) => classDef.isStandoffClass
                                case None => false
                            }

                            if (isStandoffClass) {
                                // Yes. Infer knora-api:subjectType knora-api:StandoffTag.
                                Some(OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag.toSmartIri)
                            } else if (GravsearchTypeInspectionUtil.GravsearchTypeIris.contains(subjectTypeStr)) {
                                // It's not any of those. If it's valid in a type inspection result, return it.
                                Some(subjectType)
                            } else {
                                // It's not valid in a type inspection result. This must mean it's not allowed in Gravsearch queries.
                                throw GravsearchException(s"Type not allowed in Gravsearch queries: ${readPropertyInfo.entityInfoContent.propertyIri} knora-api:subjectType $subjectType")
                            }
                        }

                    case None => None
                }
            }
        }

        /**
          * Given a [[ReadPropertyInfoV2]], returns the IRI of the inferred `knora-api:objectType` of the property, if any.
          *
          * @param readPropertyInfo the property definition.
          * @param querySchema      the ontology schema that the query is written in.
          * @return the IRI of the inferred `knora-api:objectType` of the property, or `None` if it could not inferred.
          */
        def readPropertyInfoToObjectType(readPropertyInfo: ReadPropertyInfoV2, entityInfo: EntityInfoGetResponseV2, querySchema: ApiV2Schema): Option[SmartIri] = {
            // Is this a link property?
            if (readPropertyInfo.isLinkProp) {
                // Yes. Infer knora-api:objectType knora-api:Resource.
                Some(getResourceTypeIriForSchema(querySchema))
            } else if (readPropertyInfo.isFileValueProp) {
                // No. If it's a file value property, return the representation of file values in the specified schema.
                Some(getFileTypeForSchema(querySchema))
            } else {
                // It's not a link property. Get the knora-api:objectType that the ontology responder provided.
                readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri).
                    orElse(readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri)) match {
                    case Some(objectType: SmartIri) =>
                        val objectTypeStr = objectType.toString

                        // Is it knora-api:Value?
                        if (objectTypeStr == OntologyConstants.KnoraApiV2WithValueObjects.Value) {
                            // Yes. Don't use it.
                            None
                        } else {
                            // No. Is it a standoff class?
                            val isStandoffClass: Boolean = entityInfo.classInfoMap.get(objectType) match {
                                case Some(classDef) => classDef.isStandoffClass
                                case None => false
                            }

                            if (isStandoffClass) {
                                // Yes. Infer knora-api:subjectType knora-api:StandoffTag.
                                Some(OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag.toSmartIri)
                            } else if (GravsearchTypeInspectionUtil.GravsearchTypeIris.contains(objectTypeStr)) {
                                // It's not any of those. If it's valid in a type inspection result, return it.
                                Some(objectType)
                            } else {
                                // No. This must mean it's not allowed in Gravsearch queries.
                                throw GravsearchException(s"Type not allowed in Gravsearch queries: ${readPropertyInfo.entityInfoContent.propertyIri} knora-api:objectType $objectType")
                            }
                        }

                    case None => None
                }
            }
        }
    }

    // The inference rule pipeline for the first iteration. Includes rules that cannot return additional
    // information if they are run more than once.
    private val firstIterationRulePipeline = new RdfTypeRule(
        Some(new PropertyIriObjectTypeRule(
            Some(new TypeOfSubjectFromPropertyRule(
                Some(new VarTypeFromFilterRule(
                    Some(new TypeOfObjectFromPropertyRule(
                        Some(new PropertyTypeFromObjectRule(None)))))))))))

    // The inference rule pipeline for subsequent iterations. Excludes rules that cannot return additional
    // if they are run more than once.
    private val subsequentIterationRulePipeline = new TypeOfObjectFromPropertyRule(
        Some(new PropertyTypeFromObjectRule(None)))

    /**
      * An index of entity usage in a Gravsearch query.
      *
      * @param knoraClassIris                  the Knora class IRIs that are used in the query.
      * @param knoraPropertyIris               the Knora property IRIs that are used in the query.
      * @param subjectIndex                    a map of all statement subjects to the statements they occur in.
      * @param predicateIndex                  map of all statement predicates to the statements they occur in.
      * @param objectIndex                     a map of all statement objects to the statements they occur in.
      * @param knoraPropertyVariablesInFilters a map of query variables to Knora property IRIs that they are compared to in
      *                                        FILTER expressions.
      * @param typedVariablesInFilters         a map of query variables to types found for them in FILTER expressions.
      */
    private case class UsageIndex(knoraClassIris: Set[SmartIri] = Set.empty[SmartIri],
                                  knoraPropertyIris: Set[SmartIri] = Set.empty[SmartIri],
                                  subjectIndex: Map[TypeableEntity, Set[StatementPattern]] = Map.empty[TypeableEntity, Set[StatementPattern]],
                                  predicateIndex: Map[TypeableEntity, Set[StatementPattern]] = Map.empty[TypeableEntity, Set[StatementPattern]],
                                  objectIndex: Map[TypeableEntity, Set[StatementPattern]] = Map.empty[TypeableEntity, Set[StatementPattern]],
                                  knoraPropertyVariablesInFilters: Map[TypeableVariable, Set[SmartIri]] = Map.empty[TypeableVariable, Set[SmartIri]],
                                  typedVariablesInFilters: Map[TypeableVariable, Set[SmartIri]] = Map.empty[TypeableVariable, Set[SmartIri]],
                                  querySchema: ApiV2Schema)

    override def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                              whereClause: WhereClause,
                              requestingUser: UserADM): Future[IntermediateTypeInspectionResult] = {
        log.debug("========== Starting type inference ==========")

        for {
            // Make an index of entity usage in the query.
            usageIndex <- Future(makeUsageIndex(whereClause))

            // Ask the ontology responder about all the Knora class and property IRIs mentioned in the query.

            initialEntityInfoRequest = EntityInfoGetRequestV2(
                classIris = usageIndex.knoraClassIris,
                propertyIris = usageIndex.knoraPropertyIris,
                requestingUser = requestingUser
            )

            initialEntityInfo: EntityInfoGetResponseV2 <- (responderManager ? initialEntityInfoRequest).mapTo[EntityInfoGetResponseV2]

            // The ontology responder may return the requested information in the internal schema. Convert each entity
            // definition back to the input schema.
            initialEntityInfoInInputSchemas: EntityInfoGetResponseV2 = convertEntityInfoResponseToInputSchemas(
                usageIndex = usageIndex,
                entityInfo = initialEntityInfo
            )

            // Ask the ontology responder about all the Knora classes mentioned as subject or object types of the
            // properties returned.

            subjectAndObjectTypes: Set[SmartIri] = initialEntityInfoInInputSchemas.propertyInfoMap.foldLeft(Set.empty[SmartIri]) {
                case (acc, (propertyIri, propertyDef)) =>
                    val propertyIriSchema = propertyIri.getOntologySchema match {
                        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                        case other => throw AssertionException(s"Expected an ApiV2Schema, got $other")
                    }

                    val maybeSubjectType: Option[SmartIri] = propertyDef.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApi.getSubjectTypePredicate(propertyIriSchema).toSmartIri) match {
                        case Some(subjectType) if subjectType.isKnoraEntityIri => Some(subjectType)
                        case _ => None
                    }

                    val maybeObjectType: Option[SmartIri] = propertyDef.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApi.getObjectTypePredicate(propertyIriSchema).toSmartIri) match {
                        case Some(objectType) if objectType.isKnoraEntityIri => Some(objectType)
                        case _ => None
                    }

                    acc ++ maybeSubjectType ++ maybeObjectType
            }

            additionalEntityInfoRequest = EntityInfoGetRequestV2(
                classIris = subjectAndObjectTypes,
                requestingUser = requestingUser
            )

            additionalEntityInfo: EntityInfoGetResponseV2 <- (responderManager ? additionalEntityInfoRequest).mapTo[EntityInfoGetResponseV2]

            // Add the additional classes to the usage index.
            usageIndexWithAdditionalClasses = usageIndex.copy(
                knoraClassIris = usageIndex.knoraClassIris ++ subjectAndObjectTypes
            )

            // The ontology responder may return the requested information in the internal schema. Convert each entity
            // definition back to the input schema.
            additionalEntityInfoInInputSchemas: EntityInfoGetResponseV2 = convertEntityInfoResponseToInputSchemas(
                usageIndex = usageIndexWithAdditionalClasses,
                entityInfo = additionalEntityInfo
            )

            // Combine all the entity info into one object.
            allEntityInfo: EntityInfoGetResponseV2 = initialEntityInfoInInputSchemas.copy(
                classInfoMap = initialEntityInfoInInputSchemas.classInfoMap ++ additionalEntityInfoInInputSchemas.classInfoMap
            )

            // Iterate over the inference rules until no new type information can be inferred.
            intermediateResult: IntermediateTypeInspectionResult = doIterations(
                iterationNumber = 1,
                intermediateResult = previousResult,
                entityInfo = allEntityInfo,
                usageIndex = usageIndexWithAdditionalClasses
            )

            // Pass the intermediate result to the next type inspector in the pipeline.
            lastResult: IntermediateTypeInspectionResult <- runNextInspector(
                intermediateResult = intermediateResult,
                whereClause = whereClause,
                requestingUser = requestingUser
            )
        } yield lastResult
    }

    /**
      * Given an [[EntityInfoGetResponseV2]], converts each class and property back to the input schema
      * found in the usage index.
      *
      * @param usageIndex the usage index.
      * @param entityInfo the [[EntityInfoGetResponseV2]] that was returned by the ontology responder.
      * @return an [[EntityInfoGetResponseV2]] in which the classes and properties are represented in the schemas
      *         found in the usage index.
      */
    private def convertEntityInfoResponseToInputSchemas(usageIndex: UsageIndex,
                                                        entityInfo: EntityInfoGetResponseV2): EntityInfoGetResponseV2 = {
        /**
          * Given a map of Knora class or property definitions, converts each one to the schema in which it was requested.
          *
          * @param inputEntityIris the IRIs of the entities that were requested.
          * @param entityMap       a map of entity IRIs to entity definitions as returned by the ontology responder.
          * @tparam C the entity definition type, which can be [[ReadClassInfoV2]] or [[ReadPropertyInfoV2]].
          * @return a map of entity IRIs to entity definitions, with each IRI and definition represented in the
          *         schema in which it was requested.
          */
        def toInputSchema[C <: KnoraReadV2[C]](inputEntityIris: Set[SmartIri], entityMap: Map[SmartIri, C]): Map[SmartIri, C] = {
            inputEntityIris.flatMap {
                inputEntityIri =>
                    val inputSchema = inputEntityIri.getOntologySchema.get match {
                        case apiV2Schema: ApiV2Schema => apiV2Schema
                        case _ => throw GravsearchException(s"Invalid schema in IRI $inputEntityIri")
                    }

                    val maybeReadEntityInfo: Option[C] = entityMap.get(inputEntityIri).orElse {
                        entityMap.get(inputEntityIri.toOntologySchema(InternalSchema))
                    }

                    maybeReadEntityInfo.map {
                        readEntityInfo => inputEntityIri -> readEntityInfo.toOntologySchema(inputSchema)
                    }
            }.toMap
        }

        EntityInfoGetResponseV2(
            classInfoMap = toInputSchema(usageIndex.knoraClassIris, entityInfo.classInfoMap),
            propertyInfoMap = toInputSchema(usageIndex.knoraPropertyIris, entityInfo.propertyInfoMap)
        )
    }

    /**
      * Runs all the inference rules repeatedly until no new type information can be found.
      *
      * @param iterationNumber    the current iteration number.
      * @param intermediateResult the current intermediate result.
      * @param entityInfo         information about Knora ontology entities mentioned in the Gravsearch query.
      * @param usageIndex         an index of entity usage in the query.
      * @return a new intermediate result.
      */
    @tailrec
    private def doIterations(iterationNumber: Int,
                             intermediateResult: IntermediateTypeInspectionResult,
                             entityInfo: EntityInfoGetResponseV2,
                             usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
        if (iterationNumber > MAX_ITERATIONS) {
            throw GravsearchException(s"Too many type inference iterations")
        }

        // Run an iteration of type inference and get its result.

        log.debug(s"****** Inference iteration $iterationNumber (untyped ${intermediateResult.untypedEntities.size}, inconsistent ${intermediateResult.entitiesWithInconsistentTypes.size})")

        val iterationResult: IntermediateTypeInspectionResult = intermediateResult.entities.keySet.foldLeft(intermediateResult) {
            case (acc: IntermediateTypeInspectionResult, entityToType: TypeableEntity) =>
                val pipeline: InferenceRule = if (iterationNumber == 1) {
                    firstIterationRulePipeline
                } else {
                    subsequentIterationRulePipeline
                }

                pipeline.infer(
                    entityToType = entityToType,
                    intermediateResult = acc,
                    entityInfo = entityInfo,
                    usageIndex = usageIndex
                )
        }

        // If no new type information was found, stop.
        if (iterationResult == intermediateResult) {
            log.debug("No new information, stopping iterations")
            intermediateResult
        } else {
            doIterations(
                iterationNumber = iterationNumber + 1,
                intermediateResult = iterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Creates a usage index from a Gravsearch WHERE clause.
      */
    private class UsageIndexCollectingWhereVisitor extends WhereVisitor[UsageIndex] {
        /**
          * Collects information for the usage index from statements.
          *
          * @param statementPattern the pattern to be visited.
          * @param acc              the accumulator.
          * @return the accumulator.
          */
        override def visitStatementInWhere(statementPattern: StatementPattern, acc: UsageIndex): UsageIndex = {
            // Index the statement by subject.
            val subjectIndex: Map[TypeableEntity, Set[StatementPattern]] = addIndexEntry(
                statementEntity = statementPattern.subj,
                statementPattern = statementPattern,
                statementIndex = acc.subjectIndex
            )

            // Index the statement by predicate.
            val predicateIndex: Map[TypeableEntity, Set[StatementPattern]] = addIndexEntry(
                statementEntity = statementPattern.pred,
                statementPattern = statementPattern,
                statementIndex = acc.predicateIndex
            )

            // Index the statement by object.
            val objectIndex: Map[TypeableEntity, Set[StatementPattern]] = addIndexEntry(
                statementEntity = statementPattern.obj,
                statementPattern = statementPattern,
                statementIndex = acc.objectIndex
            )

            // If the statement's predicate is rdf:type, and its object is a Knora entity, add it to the
            // set of Knora class IRIs.
            val knoraClassIris: Set[SmartIri] = acc.knoraClassIris ++ (statementPattern.pred match {
                case IriRef(predIri, _) if predIri.toString == OntologyConstants.Rdf.Type =>
                    statementPattern.obj match {
                        case IriRef(objIri, _) if objIri.isKnoraEntityIri =>
                            Some(objIri)

                        case _ => None
                    }

                case _ => None
            })

            // If the statement's predicate is a Knora property, and isn't a type annotation predicate, add it to the set of Knora property IRIs.
            val knoraPropertyIris: Set[SmartIri] = acc.knoraPropertyIris ++ (statementPattern.pred match {
                case IriRef(predIri, _) if predIri.isKnoraEntityIri && !GravsearchTypeInspectionUtil.TypeAnnotationProperties.allTypeAnnotationIris.contains(predIri.toString) =>
                    Some(predIri)

                case _ => None
            })

            acc.copy(
                knoraClassIris = knoraClassIris,
                knoraPropertyIris = knoraPropertyIris,
                subjectIndex = subjectIndex,
                predicateIndex = predicateIndex,
                objectIndex = objectIndex
            )
        }

        /**
          * Given a statement pattern and an entity contained in it, checks whether the entity is typeable, and makes an index
          * entry if so.
          *
          * @param statementEntity  the entity (subject, predicate, or object).
          * @param statementPattern the statement pattern.
          * @param statementIndex   an accumulator for a statement index.
          * @return an updated accumulator.
          */
        private def addIndexEntry(statementEntity: Entity,
                                  statementPattern: StatementPattern,
                                  statementIndex: Map[TypeableEntity, Set[StatementPattern]]): Map[TypeableEntity, Set[StatementPattern]] = {
            GravsearchTypeInspectionUtil.maybeTypeableEntity(statementEntity) match {
                case Some(typeableEntity) =>
                    val currentPatterns = statementIndex.getOrElse(typeableEntity, Set.empty[StatementPattern])
                    statementIndex + (typeableEntity -> (currentPatterns + statementPattern))

                case None => statementIndex
            }
        }

        /**
          * Collects information for the usage index from filters.
          *
          * @param filterPattern the pattern to be visited.
          * @param acc           the accumulator.
          * @return the accumulator.
          */
        override def visitFilter(filterPattern: FilterPattern, acc: UsageIndex): UsageIndex = {
            visitFilterExpression(filterPattern.expression, acc)
        }

        /**
          * Collects information for the usage index from filter expressions.
          *
          * @param filterExpression the filter expression to be visited.
          * @param acc              the accumulator.
          * @return the accumulator.
          */
        private def visitFilterExpression(filterExpression: Expression, acc: UsageIndex): UsageIndex = {
            filterExpression match {
                case compareExpression: CompareExpression =>
                    compareExpression match {
                        case CompareExpression(queryVariable: QueryVariable, operator: CompareExpressionOperator.Value, iriRef: IriRef)
                            if operator == CompareExpressionOperator.EQUALS && iriRef.iri.isKnoraEntityIri =>
                            // A variable is compared to a Knora entity IRI, which must be a property IRI.
                            // Index the property IRI.

                            val typeableVariable = TypeableVariable(queryVariable.variableName)
                            val currentIris: Set[SmartIri] = acc.knoraPropertyVariablesInFilters.getOrElse(typeableVariable, Set.empty[SmartIri])

                            acc.copy(
                                knoraPropertyIris = acc.knoraPropertyIris + iriRef.iri,
                                knoraPropertyVariablesInFilters = acc.knoraPropertyVariablesInFilters + (typeableVariable -> (currentIris + iriRef.iri))
                            )

                        case CompareExpression(queryVariable: QueryVariable, _, xsdLiteral: XsdLiteral) =>
                            // A variable is compared to an XSD literal. Index the variable and the literal's type.
                            val typeableVariable = TypeableVariable(queryVariable.variableName)
                            val currentVarTypesFromFilters: Set[SmartIri] = acc.typedVariablesInFilters.getOrElse(typeableVariable, Set.empty[SmartIri])

                            acc.copy(
                                typedVariablesInFilters = acc.typedVariablesInFilters + (typeableVariable -> (currentVarTypesFromFilters + xsdLiteral.datatype))
                            )

                        case _ =>
                            val accFromLeft = visitFilterExpression(compareExpression.leftArg, acc)
                            visitFilterExpression(compareExpression.rightArg, accFromLeft)
                    }

                case functionCallExpression: FunctionCallExpression =>
                    // One or more variables are used in functions. Index them and their types, if those can be determined from
                    // the function.

                    functionCallExpression.functionIri.iri.toString match {
                        case OntologyConstants.KnoraApiV2Simple.MatchFunction | OntologyConstants.KnoraApiV2WithValueObjects.MatchFunction =>
                            // The first argument is a variable representing a string.
                            val textVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
                            val currentTextVarTypesFromFilters: Set[SmartIri] = acc.typedVariablesInFilters.getOrElse(textVar, Set.empty[SmartIri])

                            acc.copy(
                                typedVariablesInFilters = acc.typedVariablesInFilters +
                                    (textVar -> (currentTextVarTypesFromFilters + OntologyConstants.Xsd.String.toSmartIri))
                            )

                        case OntologyConstants.KnoraApiV2WithValueObjects.MatchInStandoffFunction =>
                            // The first argument is a variable representing a string.
                            val textVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
                            val currentTextVarTypesFromFilters: Set[SmartIri] = acc.typedVariablesInFilters.getOrElse(textVar, Set.empty[SmartIri])

                            // The second argument is a variable representing a standoff tag.
                            val standoffTagVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(1).variableName)
                            val currentStandoffVarTypesFromFilters: Set[SmartIri] = acc.typedVariablesInFilters.getOrElse(standoffTagVar, Set.empty[SmartIri])

                            acc.copy(
                                typedVariablesInFilters = acc.typedVariablesInFilters +
                                    (textVar -> (currentTextVarTypesFromFilters + OntologyConstants.Xsd.String.toSmartIri)) +
                                    (standoffTagVar -> (currentStandoffVarTypesFromFilters + OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag.toSmartIri))
                            )

                        case OntologyConstants.KnoraApiV2WithValueObjects.StandoffLinkFunction =>
                            if (functionCallExpression.args.size != 3) throw GravsearchException(s"Three arguments are expected for ${functionCallExpression.functionIri.toSparql}")

                            // The first and third arguments are variables or IRIs representing resources.
                            val resourceVarsAndTypes: Seq[(TypeableVariable, Set[SmartIri])] = Seq(functionCallExpression.args.head, functionCallExpression.args(2)).collect {
                                case queryVar: QueryVariable =>
                                    val resourceVar = TypeableVariable(queryVar.variableName)
                                    val currentVarTypesFromFilters: Set[SmartIri] = acc.typedVariablesInFilters.getOrElse(resourceVar, Set.empty[SmartIri])
                                    resourceVar -> (currentVarTypesFromFilters + OntologyConstants.KnoraApiV2WithValueObjects.Resource.toSmartIri)
                            }

                            // The second argument is a variable representing a standoff tag.
                            val standoffTagVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(1).variableName)
                            val currentStandoffVarTypesFromFilters: Set[SmartIri] = acc.typedVariablesInFilters.getOrElse(standoffTagVar, Set.empty[SmartIri])

                            acc.copy(
                                typedVariablesInFilters = acc.typedVariablesInFilters ++ resourceVarsAndTypes +
                                    (standoffTagVar -> (currentStandoffVarTypesFromFilters + OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag.toSmartIri))
                            )

                        case _ =>
                            // The function knora-api:toSimpleDate can take either a knora-api:DateValue or a knora-api:StandoffTag,
                            // so we don't infer the type of its argument.
                            acc
                    }

                case andExpression: AndExpression =>
                    val accFromLeft = visitFilterExpression(andExpression.leftArg, acc)
                    visitFilterExpression(andExpression.rightArg, accFromLeft)

                case orExpression: OrExpression =>
                    val accFromLeft = visitFilterExpression(orExpression.leftArg, acc)
                    visitFilterExpression(orExpression.rightArg, accFromLeft)

                case _ => acc
            }
        }
    }

    /**
      * Makes an index of entity usage in the query.
      *
      * @param whereClause the WHERE clause in the query.
      * @return an index of entity usage in the query.
      */
    private def makeUsageIndex(whereClause: WhereClause): UsageIndex = {
        // Traverse the query, collecting information for the usage index.
        QueryTraverser.visitWherePatterns(
            patterns = whereClause.patterns,
            whereVisitor = new UsageIndexCollectingWhereVisitor,
            initialAcc = UsageIndex(querySchema = whereClause.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema")))
        )
    }
}

object InferringGravsearchTypeInspector {
    /**
      * Provides the string representation of the companion class in log messages.
      *
      * See [[https://doc.akka.io/docs/akka/current/logging.html#translating-log-source-to-string-and-class]].
      */
    implicit val logSource: LogSource[AnyRef] = (o: AnyRef) => o.getClass.getName
}
