/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import akka.actor.ActorRef
import akka.event.LogSource
import akka.pattern._
import com.typesafe.scalalogging.Logger

import scala.annotation.tailrec
import scala.concurrent.Future

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.v2.responder.KnoraReadV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.EntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2

/**
 * A Gravsearch type inspector that infers types, relying on information from the relevant ontologies.
 */
class InferringGravsearchTypeInspector(
  nextInspector: Option[GravsearchTypeInspector],
  appActor: ActorRef,
  responderData: ResponderData,
  appConfig: AppConfig
) extends GravsearchTypeInspector(nextInspector = nextInspector, responderData = responderData, appConfig = appConfig) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val log: Logger = Logger(this.getClass())

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
    def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult

    /**
     * Runs the next rule in the pipeline.
     *
     * @param entityToType       the entity whose type needs to be determined.
     * @param intermediateResult this rule's intermediate result.
     * @param entityInfo         information about Knora ontology entities mentioned in the Gravsearch query.
     * @param usageIndex         an index of entity usage in the query.
     * @return the types that the rule inferred for the entity, or an empty set if no type could be determined.
     */
    protected def runNextRule(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult =
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

  /**
   * Infers the type of an entity if there is an `rdf:type` statement about it.
   */
  private class InferTypeOfSubjectOfRdfTypePredicate(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {

      // Has this entity been used as a subject?
      val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.subjectIndex.get(entityToType) match {
        case Some(statements) =>
          // Yes. If it's been used with the predicate rdf:type with an IRI object, collect those objects.
          val rdfTypes: Set[SmartIri] = statements.collect {
            case StatementPattern(_, IriRef(predIri, _), IriRef(objIri, _), _)
                if predIri.toString == OntologyConstants.Rdf.Type =>
              objIri
          }

          rdfTypes.flatMap { rdfType =>
            // Is this type a Knora entity?
            if (rdfType.isKnoraEntityIri) {
              // Yes. Has the ontology responder provided a class definition for it?
              entityInfo.classInfoMap.get(rdfType) match {
                case Some(classDef) =>
                  // Yes. Is it a resource class?
                  if (classDef.isResourceClass) {
                    // Yes. Use that class as the inferred type.
                    val inferredType = NonPropertyTypeInfo(classDef.entityInfoContent.classIri, isResourceType = true)
                    log.debug("InferTypeOfSubjectOfRdfTypePredicate: {} {} .", entityToType, inferredType)
                    Some(inferredType)
                  } else if (classDef.isStandoffClass) {
                    val inferredType =
                      NonPropertyTypeInfo(classDef.entityInfoContent.classIri, isStandoffTagType = true)
                    log.debug("InferTypeOfSubjectOfRdfTypePredicate: {} {} .", entityToType, inferredType)
                    Some(inferredType)
                  } else {

                    // It's not a resource class or standoff class. Is it valid in a type inspection result?
                    if (GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(rdfType.toString)) {
                      // Yes. Return it.
                      val inferredType = NonPropertyTypeInfo(rdfType, isValueType = true)
                      log.debug("InferTypeOfSubjectOfRdfTypePredicate: {} {} .", entityToType, inferredType)
                      Some(inferredType)
                    } else {
                      // No. This must mean it's not allowed in Gravsearch queries.
                      throw GravsearchException(s"Type not allowed in Gravsearch queries: $entityToType")
                    }
                  }

                case None =>
                  // The ontology responder hasn't provided a definition of this class.
                  // This should have caused an error earlier from the ontology responder.
                  throw AssertionException(s"No information found about class ${IriRef(rdfType).toString}")
              }
            } else if (GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(rdfType.toString)) {
              // This isn't a Knora entity. If it's valid in a type inspection result, return it.

              val inferredType = NonPropertyTypeInfo(rdfType, isValueType = true)
              log.debug("InferTypeOfSubjectOfRdfTypePredicate: {} {} .", entityToType, inferredType)
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
   * Infers the `knora-api:objectType` of a property if the property's IRI is used as a predicate.
   */
  private class InferTypeOfPropertyFromItsIri(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {

      // Is this entity an IRI?
      val inferredTypes: Set[GravsearchEntityTypeInfo] = entityToType match {
        case TypeableIri(iri) =>
          // Yes. Is it a Knora property IRI?
          if (usageIndex.knoraPropertyIris.contains(iri)) {
            // Yes. Has the ontology responder provided information about it?
            entityInfo.propertyInfoMap.get(iri) match {
              case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                // Yes. Try to infer its knora-api:objectType from the provided information.
                val inferredObjectTypes: Set[GravsearchEntityTypeInfo] = InferenceRuleUtil
                  .readPropertyInfoToObjectType(readPropertyInfo, entityInfo, usageIndex.querySchema)
                  .toSet
                log.debug("InferTypeOfPropertyFromItsIri: {} {} .", entityToType, inferredObjectTypes.mkString(", "))
                inferredObjectTypes

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
  private class InferTypeOfObjectFromPredicate(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {
      // for standoff links it is necessary to refine the determined types first. TODO: why in this rule and not in all rules?
      val updatedIntermediateResult: IntermediateTypeInspectionResult = refineDeterminedTypes(
        intermediateResult = intermediateResult,
        entityInfo = entityInfo
      )

      /**
       * Performs the inference for this rule on a set of statements.
       */
      def inferFromStatements(statements: Set[StatementPattern]): Set[GravsearchEntityTypeInfo] =
        statements.flatMap { statement =>
          // Is the predicate typeable?
          GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.pred) match {
            case Some(typeablePred: TypeableEntity) =>
              // Yes. Do we have its types?
              updatedIntermediateResult.entities.get(typeablePred) match {
                case Some(entityTypes: Set[GravsearchEntityTypeInfo]) =>
                  // Yes. Use the knora-api:objectType of each PropertyTypeInfo.
                  entityTypes.flatMap {
                    case propertyTypeInfo: PropertyTypeInfo =>
                      val inferredType: GravsearchEntityTypeInfo = propertyTypeInfo.toNonPropertyTypeInfo
                      log.debug("InferTypeOfObjectFromPredicate: {} {} .", entityToType, inferredType)
                      Some(inferredType)
                    case _ =>
                      None
                  }

                case _ =>
                  // We don't have the predicate's type.
                  Set.empty[GravsearchEntityTypeInfo]
              }
            case None =>
              // The predicate isn't typeable.
              Set.empty[GravsearchEntityTypeInfo]
          }
        }

      // Has this entity been used as the object of one or more statements?
      usageIndex.objectIndex.get(entityToType) match {
        case Some(statements: Set[StatementPattern]) =>
          // Yes. Try to infer type information from the predicate of each of those statements.
          // To keep track of which types are inferred from IRIs representing properties, and which ones
          // are inferred from variables representing properties, partition the statements into ones whose
          // predicates are IRIs and ones whose predicates are variables.

          val (statementsWithPropertyIris, statementsWithVariablesAsPredicates) = statements.partition { statement =>
            statement.pred match {
              case _: IriRef => true
              case _         => false
            }
          }

          // Separately infer types from statements whose predicates are IRIs and statements whose
          // predicates are variables.

          val typesInferredFromPropertyIris: Set[GravsearchEntityTypeInfo] = inferFromStatements(
            statementsWithPropertyIris
          )
          val typesInferredFromVariablesAsPredicates: Set[GravsearchEntityTypeInfo] = inferFromStatements(
            statementsWithVariablesAsPredicates
          )

          // If any types were inferred from statements whose predicates are IRIs, update the
          // intermediate type inspection result with that information.

          val intermediateResultWithTypesInferredFromPropertyIris = if (typesInferredFromPropertyIris.nonEmpty) {
            updatedIntermediateResult.addTypes(
              entityToType,
              typesInferredFromPropertyIris,
              inferredFromPropertyIri = true
            )
          } else {
            updatedIntermediateResult
          }

          val intermediateResultWithTypesInferredFromVariablesAsPredicates =
            intermediateResultWithTypesInferredFromPropertyIris.addTypes(
              entityToType,
              typesInferredFromVariablesAsPredicates
            )

          runNextRule(
            entityToType = entityToType,
            intermediateResult = intermediateResultWithTypesInferredFromVariablesAsPredicates,
            entityInfo = entityInfo,
            usageIndex = usageIndex
          )

        case None =>
          // This entity hasn't been used as a statement object, so this rule isn't relevant.
          runNextRule(
            entityToType = entityToType,
            intermediateResult = updatedIntermediateResult,
            entityInfo = entityInfo,
            usageIndex = usageIndex
          )
      }
    }
  }

  /**
   * Infers an entity's type if the entity is used as the subject of a statement in which the predicate is a
   * property IRI whose `knora-api:subjectType` is known.
   */
  private class InferTypeOfSubjectFromPredicateIri(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {

      // Has this entity been used as the subject of one or more statements?
      val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.subjectIndex.get(entityToType) match {
        case Some(statements) =>
          // Yes. Try to infer type information from the predicate of each of those statements.
          statements.flatMap { statement =>
            // Is the predicate a Knora IRI, and not a type annotation predicate?
            statement.pred match {
              case IriRef(predIri, _)
                  if predIri.isKnoraEntityIri && !GravsearchTypeInspectionUtil.TypeAnnotationProperties.allTypeAnnotationIris
                    .contains(predIri.toString) =>
                // Yes. Has the ontology responder provided a property definition for it?
                entityInfo.propertyInfoMap.get(predIri) match {
                  case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                    // Yes. Try to get the property's knora-api:subjectType from that definition,
                    // and infer that type as the type of the entity.
                    InferenceRuleUtil
                      .readPropertyInfoToSubjectType(readPropertyInfo, entityInfo, usageIndex.querySchema)
                      .map(_.toNonPropertyTypeInfo)

                  case None =>
                    // The ontology responder hasn't provided a definition of this property. This should have caused
                    // an error earlier from the ontology responder.
                    throw AssertionException(s"No information found about property $predIri")
                }
              case _ =>
                // The predicate isn't a Knora IRI, or is a type annotation predicate, so this rule isn't relevant.
                None
            }
          }

        case None =>
          // This entity hasn't been used as a subject, so this rule isn't relevant.
          Set.empty[GravsearchEntityTypeInfo]
      }

      runNextRule(
        entityToType = entityToType,
        intermediateResult = intermediateResult.addTypes(entityToType, inferredTypes, inferredFromPropertyIri = true),
        entityInfo = entityInfo,
        usageIndex = usageIndex
      )
    }
  }

  /**
   * Infers the `knora-api:objectType` of a property variable or IRI if it's used with an object whose type is known.
   */
  private class InferTypeOfPredicateFromObject(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {
      // for standoff links it is necessary to refine the types first. TODO: why in this rule and not in all rules?
      val updatedIntermediateResult: IntermediateTypeInspectionResult = refineDeterminedTypes(
        intermediateResult = intermediateResult,
        entityInfo = entityInfo
      )

      // Has this entity been used as a predicate?
      val inferredTypes: Set[GravsearchEntityTypeInfo] = usageIndex.predicateIndex.get(entityToType) match {
        case Some(statements) =>
          // Yes. Try to infer its knora-api:objectType from the object of each of those statements.
          statements.flatMap { statement =>
            // Is the object typeable?
            GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.obj) match {
              case Some(typeableObj: TypeableEntity) =>
                // Yes. Do we have its types?
                updatedIntermediateResult.entities.get(typeableObj) match {
                  case Some(entityTypes: Set[GravsearchEntityTypeInfo]) =>
                    // Yes. Use those types.

                    val alreadyInferredPropertyTypes: Set[PropertyTypeInfo] =
                      updatedIntermediateResult.entities.getOrElse(entityToType, Set.empty).collect {
                        case propertyTypeInfo: PropertyTypeInfo => propertyTypeInfo
                      }

                    entityTypes.flatMap {
                      case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                        // Is this type a subclass of an object type that we already have for this property,
                        // which we may have got from the property's definition in an ontology?
                        val baseClassesOfInferredType: Set[SmartIri] =
                          entityInfo.classInfoMap.get(nonPropertyTypeInfo.typeIri) match {
                            case Some(classDef) => classDef.allBaseClasses.toSet
                            case None           => Set.empty
                          }

                        val isSubclassOfAlreadyInferredType: Boolean = alreadyInferredPropertyTypes.exists {
                          alreadyInferredType: PropertyTypeInfo =>
                            baseClassesOfInferredType.contains(alreadyInferredType.objectTypeIri)
                        }

                        if (!isSubclassOfAlreadyInferredType) {
                          // No. Use the inferred type.
                          val inferredType: GravsearchEntityTypeInfo = nonPropertyTypeInfo.toPropertyTypeInfo
                          log.debug("InferTypeOfPredicateFromObject: {} {} .", entityToType, inferredType)
                          Some(inferredType)
                        } else {
                          // Yes. Don't infer the more specific type for the property.
                          None
                        }

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
        intermediateResult = updatedIntermediateResult.addTypes(entityToType, inferredTypes),
        entityInfo = entityInfo,
        usageIndex = usageIndex
      )
    }
  }

  /**
   * Infers the types of entities if their type was already determined by examining a FILTER expression when
   * constructing the usage index.
   */
  private class InferTypeOfEntityFromKnownTypeInFilter(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {
      // Do we have one or more types for this entity from a FILTER?
      val typesFromFilters: Set[GravsearchEntityTypeInfo] = usageIndex.typedEntitiesInFilters.get(entityToType) match {
        case Some(typesFromFilters: Set[SmartIri]) =>
          // Yes. Return those types.
          typesFromFilters.map { typeFromFilter: SmartIri =>
            val isValueType       = GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(typeFromFilter.toString)
            val isStandoffTagType = typeFromFilter.toString == OntologyConstants.KnoraApiV2Complex.StandoffTag
            val isResourceType    = !(isValueType || isStandoffTagType)
            val inferredType = NonPropertyTypeInfo(
              typeFromFilter,
              isResourceType = isResourceType,
              isValueType = isValueType,
              isStandoffTagType = isStandoffTagType
            )
            log.debug("InferTypeOfEntityFromKnownTypeInFilter: {} {} .", entityToType, inferredType)
            inferredType
          }

        case None =>
          // We don't have a type for this entity from a FILTER.
          Set.empty[GravsearchEntityTypeInfo]
      }

      runNextRule(
        entityToType = entityToType,
        intermediateResult = intermediateResult.addTypes(entityToType, typesFromFilters),
        entityInfo = entityInfo,
        usageIndex = usageIndex
      )
    }
  }

  /**
   * Infers a variable's type if it has been compared with a property IRI in a FILTER expression.
   */
  private class InferTypeOfVariableFromComparisonWithPropertyIriInFilter(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {

      val typesFromComparisons: Set[GravsearchEntityTypeInfo] = entityToType match {
        // Is this entity a variable?
        case variableToType: TypeableVariable =>
          // Yes. Has it been used as a predicate?
          usageIndex.predicateIndex.get(entityToType) match {
            case Some(_) =>
              // Yes. Has it been compared with one or more Knora property IRIs in a FILTER?
              usageIndex.knoraPropertyVariablesInFilters.get(variableToType) match {
                case Some(propertyIris: Set[SmartIri]) =>
                  // Yes.
                  propertyIris.flatMap { propertyIri: SmartIri =>
                    // Has the ontology responder provided a definition of this property?
                    entityInfo.propertyInfoMap.get(propertyIri) match {
                      case Some(readPropertyInfo: ReadPropertyInfoV2) =>
                        // Yes. Try to determine the property's knora-api:objectType from that definition.
                        InferenceRuleUtil
                          .readPropertyInfoToObjectType(readPropertyInfo, entityInfo, usageIndex.querySchema)
                          .toSet

                      case None =>
                        // The ontology responder hasn't provided a definition of this property. This should have caused
                        // an error earlier from the ontology responder.
                        throw AssertionException(s"No information found about property $propertyIri")
                    }
                  }

                case None =>
                  // The variable hasn't been compared with an IRI in a FILTER.
                  Set.empty[GravsearchEntityTypeInfo]
              }

            case None =>
              // The variable hasn't been used as a predicate.
              Set.empty[GravsearchEntityTypeInfo]
          }

        case _ =>
          // The entity isn't a variable.
          Set.empty[GravsearchEntityTypeInfo]
      }

      runNextRule(
        entityToType = entityToType,
        intermediateResult = intermediateResult.addTypes(entityToType, typesFromComparisons),
        entityInfo = entityInfo,
        usageIndex = usageIndex
      )
    }
  }

  /**
   * Infers the type of a variable or IRI that has been compared with another variable or IRI in a FILTER expression.
   */
  private class InferTypeOfEntityFromComparisonWithOtherEntityInFilter(nextRule: Option[InferenceRule])
      extends InferenceRule(nextRule = nextRule) {
    override def infer(
      entityToType: TypeableEntity,
      intermediateResult: IntermediateTypeInspectionResult,
      entityInfo: EntityInfoGetResponseV2,
      usageIndex: UsageIndex
    ): IntermediateTypeInspectionResult = {
      // Has this entity been compared with one or more other entities in a FILTER?
      val typesFromComparisons: Set[GravsearchEntityTypeInfo] =
        usageIndex.entitiesComparedInFilters.get(entityToType) match {
          case Some(comparedEntities: Set[TypeableEntity]) =>
            // Yes. Get the types that have been inferred for those entities, if any.
            val inferredTypes = comparedEntities.flatMap(comparedEntity => intermediateResult.entities(comparedEntity))

            if (inferredTypes.nonEmpty) {
              log.debug("InferTypeOfEntityFromComparisonWithOtherEntityInFilter: {} {} .", entityToType, inferredTypes)
            }

            inferredTypes

          case None =>
            // This entity hasn't been compared with other entities in a FILTER.
            Set.empty[GravsearchEntityTypeInfo]
        }

      runNextRule(
        entityToType = entityToType,
        intermediateResult = intermediateResult.addTypes(entityToType, typesFromComparisons),
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
    def getResourceTypeIriForSchema(querySchema: ApiV2Schema): SmartIri =
      querySchema match {
        case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri
        case ApiV2Complex => OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri
      }

    /**
     * Returns knora-api:File (if the simple schema is given) or knora-api:FileValue (if the complex schema is given).
     *
     * @param querySchema the ontology schema that the query is written in.
     */
    def getFileTypeForSchema(querySchema: ApiV2Schema): SmartIri =
      querySchema match {
        case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.File.toSmartIri
        case ApiV2Complex => OntologyConstants.KnoraApiV2Complex.FileValue.toSmartIri
      }

    /**
     * Given a [[ReadPropertyInfoV2]], returns the IRI of the inferred `knora-api:subjectType` of the property, if any.
     *
     * @param readPropertyInfo the property definition.
     * @param querySchema      the query schema.
     * @return the IRI of the inferred `knora-api:subjectType` of the property, or `None` if it could not inferred.
     */
    def readPropertyInfoToSubjectType(
      readPropertyInfo: ReadPropertyInfoV2,
      entityInfo: EntityInfoGetResponseV2,
      querySchema: ApiV2Schema
    ): Option[PropertyTypeInfo] =
      // Get the knora-api:subjectType that the ontology responder provided.
      readPropertyInfo.entityInfoContent
        .getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri)
        .orElse(
          readPropertyInfo.entityInfoContent
            .getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri)
        ) match {
        case Some(subjectType: SmartIri) =>
          val subjectTypeStr = subjectType.toString

          // Is it a resource class?
          if (readPropertyInfo.isResourceProp) {
            // Yes. Use it.
            Some(PropertyTypeInfo(subjectType, objectIsResourceType = true))
          } else if (
            subjectTypeStr == OntologyConstants.KnoraApiV2Complex.Value || OntologyConstants.KnoraApiV2Complex.ValueBaseClasses
              .contains(subjectTypeStr)
          ) {
            // If it's knora-api:Value or one of the knora-api:ValueBase classes, don't use it.
            None
          } else if (OntologyConstants.KnoraApiV2Complex.FileValueClasses.contains(subjectTypeStr)) {
            // If it's a file value class, return the representation of file values in the specified schema.
            Some(PropertyTypeInfo(getFileTypeForSchema(querySchema), objectIsValueType = true))
          } else {
            // It's not any of those types. Is it a standoff class?
            val isStandoffClass: Boolean = entityInfo.classInfoMap.get(subjectType) match {
              case Some(classDef) => classDef.isStandoffClass
              case None           => false
            }

            if (isStandoffClass) {
              // Yes. Return it as a standoff tag type.
              Some(PropertyTypeInfo(subjectType, objectIsStandoffTagType = true))
            } else if (GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(subjectTypeStr)) {
              // It's not any of those. If it's a value type, return it.
              Some(PropertyTypeInfo(subjectType, objectIsValueType = true))
            } else {
              // It's not valid in a type inspection result. This must mean it's not allowed in Gravsearch queries.
              throw GravsearchException(
                s"Type not allowed in Gravsearch queries: ${readPropertyInfo.entityInfoContent.propertyIri} knora-api:subjectType $subjectType"
              )
            }
          }

        case None =>
          // Subject type of the predicate is not known but this is a resource property?
          if (readPropertyInfo.isResourceProp) {
            // Yes. Infer knora-api:subjectType knora-api:Resource.
            Some(PropertyTypeInfo(getResourceTypeIriForSchema(querySchema), objectIsResourceType = true))
          } else None
      }

    /**
     * Given a [[ReadPropertyInfoV2]], returns the IRI of the inferred `knora-api:objectType` of the property, if any.
     *
     * @param readPropertyInfo the property definition.
     * @param querySchema      the ontology schema that the query is written in.
     * @return the IRI of the inferred `knora-api:objectType` of the property, or `None` if it could not inferred.
     */
    def readPropertyInfoToObjectType(
      readPropertyInfo: ReadPropertyInfoV2,
      entityInfo: EntityInfoGetResponseV2,
      querySchema: ApiV2Schema
    ): Option[PropertyTypeInfo] =
      // Is this a file value property?
      if (readPropertyInfo.isFileValueProp) {
        // Yes, return the representation of file values in the specified schema.
        Some(PropertyTypeInfo(getFileTypeForSchema(querySchema), objectIsValueType = true))
      } else {
        // It's not a link property. Get the knora-api:objectType that the ontology responder provided.
        readPropertyInfo.entityInfoContent
          .getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri)
          .orElse(
            readPropertyInfo.entityInfoContent
              .getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri)
          ) match {
          case Some(objectType: SmartIri) =>
            val objectTypeStr = objectType.toString

            // Is it knora-api:Value?
            if (objectTypeStr == OntologyConstants.KnoraApiV2Complex.Value) {
              // Yes. Don't use it.
              None
            } else {
              // No. Is it a standoff class?
              val isStandoffClass: Boolean = entityInfo.classInfoMap.get(objectType) match {
                case Some(classDef) => classDef.isStandoffClass
                case None           => false
              }

              if (isStandoffClass) {
                // Yes. Return it as a standoff tag type.
                Some(PropertyTypeInfo(objectType, objectIsStandoffTagType = true))
              } else if (readPropertyInfo.isLinkProp) { // No. Is this a link property?
                // Yes. Return it as a resource type.
                Some(PropertyTypeInfo(objectType, objectIsResourceType = true))
              } else if (GravsearchTypeInspectionUtil.GravsearchValueTypeIris.contains(objectTypeStr)) {
                // It's not any of those. If it's a value type, return it.
                Some(PropertyTypeInfo(objectType, objectIsValueType = true))
              } else {
                // No. This must mean it's not allowed in Gravsearch queries.
                throw GravsearchException(
                  s"Type not allowed in Gravsearch queries: ${readPropertyInfo.entityInfoContent.propertyIri} knora-api:objectType $objectType"
                )
              }
            }

          case None => None
        }
      }
  }

  // The inference rule pipeline for the first iteration. Includes rules that cannot return additional
  // information if they are run more than once. It's important that InferTypeOfPropertyFromItsIri
  // is run before InferTypeOfPredicateFromObject, so that the latter doesn't add a subtype of a type
  // already added by the former.
  private val firstIterationRulePipeline = new InferTypeOfSubjectOfRdfTypePredicate(
    Some(
      new InferTypeOfPropertyFromItsIri(
        Some(
          new InferTypeOfSubjectFromPredicateIri(
            Some(
              new InferTypeOfEntityFromKnownTypeInFilter(
                Some(
                  new InferTypeOfVariableFromComparisonWithPropertyIriInFilter(
                    Some(new InferTypeOfObjectFromPredicate(Some(new InferTypeOfPredicateFromObject(None))))
                  )
                )
              )
            )
          )
        )
      )
    )
  )

  // The inference rule pipeline for subsequent iterations. Excludes rules that cannot return additional
  // information if they are run more than once.
  private val subsequentIterationRulePipeline = new InferTypeOfObjectFromPredicate(
    Some(new InferTypeOfPredicateFromObject(Some(new InferTypeOfEntityFromComparisonWithOtherEntityInFilter(None))))
  )

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
   * @param typedEntitiesInFilters          a map of entities to types found for them in FILTER expressions.
   * @param entitiesComparedInFilters       variables or IRIs that are compared to other variables or IRIs in FILTER expressions.
   */
  case class UsageIndex(
    knoraClassIris: Set[SmartIri] = Set.empty,
    knoraPropertyIris: Set[SmartIri] = Set.empty,
    subjectIndex: Map[TypeableEntity, Set[StatementPattern]] = Map.empty,
    predicateIndex: Map[TypeableEntity, Set[StatementPattern]] = Map.empty,
    objectIndex: Map[TypeableEntity, Set[StatementPattern]] = Map.empty,
    knoraPropertyVariablesInFilters: Map[TypeableVariable, Set[SmartIri]] = Map.empty,
    typedEntitiesInFilters: Map[TypeableEntity, Set[SmartIri]] = Map.empty,
    entitiesComparedInFilters: Map[TypeableEntity, Set[TypeableEntity]] = Map.empty,
    querySchema: ApiV2Schema
  )

  override def inspectTypes(
    previousResult: IntermediateTypeInspectionResult,
    whereClause: WhereClause,
    requestingUser: UserADM
  ): Future[IntermediateTypeInspectionResult] = {
    log.debug("========== Starting type inference ==========")

    for {
      // get index of entity usage in the query and ontology information about all the Knora class and property IRIs mentioned in the query
      (usageIndex, allEntityInfo) <- getUsageIndexAndEntityInfos(whereClause, requestingUser)

      // Iterate over the inference rules until no new type information can be inferred.
      intermediateResult: IntermediateTypeInspectionResult = doIterations(
                                                               iterationNumber = 1,
                                                               intermediateResult = previousResult,
                                                               entityInfo = allEntityInfo,
                                                               usageIndex = usageIndex
                                                             )

      // refine the determined types before sending to the next inspector
      refinedIntermediateResult: IntermediateTypeInspectionResult = refineDeterminedTypes(
                                                                      intermediateResult = intermediateResult,
                                                                      entityInfo = allEntityInfo
                                                                    )

      // sanitize the inconsistent resource types inferred for an entity
      sanitizedResults: IntermediateTypeInspectionResult = sanitizeInconsistentResourceTypes(
                                                             refinedIntermediateResult,
                                                             usageIndex.querySchema,
                                                             entityInfo = allEntityInfo
                                                           )

      // Pass the intermediate result to the next type inspector in the pipeline.
      lastResult: IntermediateTypeInspectionResult <- runNextInspector(
                                                        intermediateResult = sanitizedResults,
                                                        whereClause = whereClause,
                                                        requestingUser = requestingUser
                                                      )
    } yield lastResult
  }

  /**
   * Get index of entity usage in the where clause of query and all ontology information about all the Knora class and property IRIs mentioned in the query.
   *
   * @param whereClause    the query where clause.
   * @param requestingUser the user requesting the query.
   * @return a tuple containing the usage index and all entity information acquired from the ontology.
   */
  def getUsageIndexAndEntityInfos(
    whereClause: WhereClause,
    requestingUser: UserADM
  ): Future[(UsageIndex, EntityInfoGetResponseV2)] =
    for {
      // Make an index of entity usage in the query.
      usageIndex <- Future(makeUsageIndex(whereClause))

      // Ask the ontology responder about all the Knora class and property IRIs mentioned in the query.

      initialEntityInfoRequest = EntityInfoGetRequestV2(
                                   classIris = usageIndex.knoraClassIris,
                                   propertyIris = usageIndex.knoraPropertyIris,
                                   requestingUser = requestingUser
                                 )

      initialEntityInfo: EntityInfoGetResponseV2 <-
        appActor
          .ask(initialEntityInfoRequest)
          .mapTo[EntityInfoGetResponseV2]

      // The ontology responder may return the requested information in the internal schema. Convert each entity
      // definition back to the input schema.
      initialEntityInfoInInputSchemas: EntityInfoGetResponseV2 = convertEntityInfoResponseToInputSchemas(
                                                                   usageIndex = usageIndex,
                                                                   entityInfo = initialEntityInfo
                                                                 )

      // Ask the ontology responder about all the Knora classes mentioned as subject or object types of the
      // properties returned.

      subjectAndObjectTypes: Set[SmartIri] = initialEntityInfoInInputSchemas.propertyInfoMap.foldLeft(
                                               Set.empty[SmartIri]
                                             ) { case (acc, (propertyIri, propertyDef)) =>
                                               val propertyIriSchema = propertyIri.getOntologySchema match {
                                                 case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                                 case other =>
                                                   throw AssertionException(s"Expected an ApiV2Schema, got $other")
                                               }

                                               val maybeSubjectType: Option[SmartIri] =
                                                 propertyDef.entityInfoContent.getPredicateIriObject(
                                                   OntologyConstants.KnoraApi
                                                     .getSubjectTypePredicate(propertyIriSchema)
                                                     .toSmartIri
                                                 ) match {
                                                   case Some(subjectType) if subjectType.isKnoraEntityIri =>
                                                     Some(subjectType)
                                                   case _ => None
                                                 }

                                               val maybeObjectType: Option[SmartIri] =
                                                 propertyDef.entityInfoContent.getPredicateIriObject(
                                                   OntologyConstants.KnoraApi
                                                     .getObjectTypePredicate(propertyIriSchema)
                                                     .toSmartIri
                                                 ) match {
                                                   case Some(objectType) if objectType.isKnoraEntityIri =>
                                                     Some(objectType)
                                                   case _ => None
                                                 }

                                               acc ++ maybeSubjectType ++ maybeObjectType
                                             }

      additionalEntityInfoRequest = EntityInfoGetRequestV2(
                                      classIris = subjectAndObjectTypes,
                                      requestingUser = requestingUser
                                    )

      additionalEntityInfo: EntityInfoGetResponseV2 <-
        appActor
          .ask(additionalEntityInfoRequest)
          .mapTo[EntityInfoGetResponseV2]

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
      allEntityInfo: EntityInfoGetResponseV2 =
        initialEntityInfoInInputSchemas.copy(
          classInfoMap = initialEntityInfoInInputSchemas.classInfoMap ++ additionalEntityInfoInInputSchemas.classInfoMap
        )

    } yield (usageIndexWithAdditionalClasses, allEntityInfo)

  /**
   * Given an [[EntityInfoGetResponseV2]], converts each class and property back to the input schema
   * found in the usage index.
   *
   * @param usageIndex the usage index.
   * @param entityInfo the [[EntityInfoGetResponseV2]] that was returned by the ontology responder.
   * @return an [[EntityInfoGetResponseV2]] in which the classes and properties are represented in the schemas
   *         found in the usage index.
   */
  private def convertEntityInfoResponseToInputSchemas(
    usageIndex: UsageIndex,
    entityInfo: EntityInfoGetResponseV2
  ): EntityInfoGetResponseV2 = {

    /**
     * Given a map of Knora class or property definitions, converts each one to the schema in which it was requested.
     *
     * @param inputEntityIris the IRIs of the entities that were requested.
     * @param entityMap       a map of entity IRIs to entity definitions as returned by the ontology responder.
     * @tparam C the entity definition type, which can be [[ReadClassInfoV2]] or [[ReadPropertyInfoV2]].
     * @return a map of entity IRIs to entity definitions, with each IRI and definition represented in the
     *         schema in which it was requested.
     */
    def toInputSchema[C <: KnoraReadV2[C]](
      inputEntityIris: Set[SmartIri],
      entityMap: Map[SmartIri, C]
    ): Map[SmartIri, C] =
      inputEntityIris.flatMap { inputEntityIri =>
        val inputSchema = inputEntityIri.getOntologySchema.get match {
          case apiV2Schema: ApiV2Schema => apiV2Schema
          case _                        => throw GravsearchException(s"Invalid schema in IRI $inputEntityIri")
        }

        val maybeReadEntityInfo: Option[C] = entityMap.get(inputEntityIri).orElse {
          entityMap.get(inputEntityIri.toOntologySchema(InternalSchema))
        }

        maybeReadEntityInfo.map { readEntityInfo =>
          inputEntityIri -> readEntityInfo.toOntologySchema(inputSchema)
        }
      }.toMap

    EntityInfoGetResponseV2(
      classInfoMap = toInputSchema(usageIndex.knoraClassIris, entityInfo.classInfoMap),
      propertyInfoMap = toInputSchema(usageIndex.knoraPropertyIris, entityInfo.propertyInfoMap)
    )
  }

  /**
   * Gets the iri of the type information.
   *
   * @param typeInfo a GravsearchEntityTypeInfo.
   * @return the IRI of the typeInfo as a [[SmartIri]].
   */
  private def iriOfGravsearchTypeInfo(typeInfo: GravsearchEntityTypeInfo): SmartIri =
    typeInfo match {
      case propertyTypeInfo: PropertyTypeInfo       => propertyTypeInfo.objectTypeIri
      case nonPropertyTypeInfo: NonPropertyTypeInfo => nonPropertyTypeInfo.typeIri
      case _                                        => throw GravsearchException(s"There is an invalid type")
    }

  /**
   * Sanitize determined results. If there were multiple resource types, replace with common base class
   *
   * @param lastResults this type inspection results.
   * @param querySchema the ontology schema that the query is written in.
   */
  def sanitizeInconsistentResourceTypes(
    lastResults: IntermediateTypeInspectionResult,
    querySchema: ApiV2Schema,
    entityInfo: EntityInfoGetResponseV2
  ): IntermediateTypeInspectionResult = {

    /**
     * Given a set of classes, this method finds a common base class.
     *
     * @param typesToBeChecked a set of classes.
     * @param defaultBaseClassIri the default base class IRI if none is found.
     * @return the IRI of a common base class.
     */
    def findCommonBaseClass(
      typesToBeChecked: Set[GravsearchEntityTypeInfo],
      defaultBaseClassIri: SmartIri
    ): SmartIri = {
      val baseClassesOfFirstType: Seq[SmartIri] =
        entityInfo.classInfoMap.get(iriOfGravsearchTypeInfo(typesToBeChecked.head)) match {
          case Some(classDef: ReadClassInfoV2) => classDef.allBaseClasses
          case _                               => Seq.empty[SmartIri]
        }

      if (baseClassesOfFirstType.nonEmpty) {
        val commonBaseClasses: Seq[SmartIri] = typesToBeChecked.tail.foldLeft(baseClassesOfFirstType) { (acc, aType) =>
          // get class info of the type Iri
          val baseClassesOfType: Seq[SmartIri] = entityInfo.classInfoMap.get(iriOfGravsearchTypeInfo(aType)) match {
            case Some(classDef: ReadClassInfoV2) => classDef.allBaseClasses
            case _                               => Seq.empty[SmartIri]
          }

          // find the common base classes
          baseClassesOfType.intersect(acc)
        }

        if (commonBaseClasses.nonEmpty) {
          // returns the most specific common base class.
          commonBaseClasses.head
        } else {
          defaultBaseClassIri
        }
      } else {
        defaultBaseClassIri
      }
    }

    /**
     * Replaces inconsistent types with a common base class.
     */
    def replaceInconsistentTypes(
      acc: IntermediateTypeInspectionResult,
      typedEntity: TypeableEntity,
      typesToBeChecked: Set[GravsearchEntityTypeInfo],
      newType: GravsearchEntityTypeInfo
    ): IntermediateTypeInspectionResult = {
      val withoutInconsistentTypes: IntermediateTypeInspectionResult = typesToBeChecked.foldLeft(acc) {
        (sanitizeResults: IntermediateTypeInspectionResult, currType: GravsearchEntityTypeInfo) =>
          sanitizeResults.removeType(entity = typedEntity, typeToRemove = currType)
      }

      withoutInconsistentTypes.addTypes(entity = typedEntity, entityTypes = Set(newType))
    }

    // get inconsistent types
    val inconsistentEntities: Map[TypeableEntity, Set[GravsearchEntityTypeInfo]] =
      lastResults.entitiesWithInconsistentTypes

    // Try replacing the inconsistent types
    inconsistentEntities.keySet.foldLeft(lastResults) { (acc, typedEntity) =>
      // all inconsistent types
      val typesToBeChecked: Set[GravsearchEntityTypeInfo] = inconsistentEntities.getOrElse(typedEntity, Set.empty)

      // Are all inconsistent types NonPropertyTypeInfo representing resource classes?
      if (
        typesToBeChecked.forall {
          case nonPropertyTypeInfo: NonPropertyTypeInfo => nonPropertyTypeInfo.isResourceType
          case _                                        => false
        }
      ) {
        // Yes. Remove inconsistent types and replace with a common base class.
        val commonBaseClassIri: SmartIri =
          findCommonBaseClass(typesToBeChecked, InferenceRuleUtil.getResourceTypeIriForSchema(querySchema))
        val newResourceType = NonPropertyTypeInfo(commonBaseClassIri, isResourceType = true)
        replaceInconsistentTypes(
          acc = acc,
          typedEntity = typedEntity,
          typesToBeChecked = typesToBeChecked,
          newType = newResourceType
        )
      } else if (
        typesToBeChecked.forall {
          case nonPropertyTypeInfo: NonPropertyTypeInfo => nonPropertyTypeInfo.isStandoffTagType
          case _                                        => false
        }
      ) {
        // No, they're NonPropertyTypeInfo representing standoff tag classes.
        // Yes. Remove inconsistent types and replace with a common base class.
        val commonBaseClassIri: SmartIri =
          findCommonBaseClass(typesToBeChecked, OntologyConstants.KnoraApiV2Complex.StandoffTag.toSmartIri)
        val newStandoffTagType = NonPropertyTypeInfo(commonBaseClassIri, isStandoffTagType = true)
        replaceInconsistentTypes(
          acc = acc,
          typedEntity = typedEntity,
          typesToBeChecked = typesToBeChecked,
          newType = newStandoffTagType
        )
      } else if (
        typesToBeChecked.forall {
          case nonPropertyTypeInfo: PropertyTypeInfo => nonPropertyTypeInfo.objectIsResourceType
          case _                                     => false
        }
      ) {
        // No, they're PropertyTypeInfo types with object types representing resource classes.
        // Remove inconsistent types and replace with a common base class.
        val commonBaseClassIri: SmartIri =
          findCommonBaseClass(typesToBeChecked, InferenceRuleUtil.getResourceTypeIriForSchema(querySchema))
        val newObjectType = PropertyTypeInfo(commonBaseClassIri, objectIsResourceType = true)
        replaceInconsistentTypes(
          acc = acc,
          typedEntity = typedEntity,
          typesToBeChecked = typesToBeChecked,
          newType = newObjectType
        )

      } else if (
        typesToBeChecked.forall {
          case nonPropertyTypeInfo: PropertyTypeInfo => nonPropertyTypeInfo.objectIsStandoffTagType
          case _                                     => false
        }
      ) {
        // No, they're PropertyTypeInfo types with object types representing standoff tag classes.
        // Remove inconsistent types and replace with a common base class.
        val commonBaseClassIri: SmartIri =
          findCommonBaseClass(typesToBeChecked, OntologyConstants.KnoraApiV2Complex.StandoffTag.toSmartIri)
        val newObjectType = PropertyTypeInfo(commonBaseClassIri, objectIsStandoffTagType = true)
        replaceInconsistentTypes(
          acc = acc,
          typedEntity = typedEntity,
          typesToBeChecked = typesToBeChecked,
          newType = newObjectType
        )
      } else {
        // None of the above. Don't touch the determined inconsistent types, later an error is returned for this.
        acc
      }
    }
  }

  /**
   * Make sure that the most specific type is stored for each typeable entity.
   *
   * @param intermediateResult this rule's intermediate result.
   * @param entityInfo         information about Knora ontology entities mentioned in the Gravsearch query.
   */
  def refineDeterminedTypes(
    intermediateResult: IntermediateTypeInspectionResult,
    entityInfo: EntityInfoGetResponseV2
  ): IntermediateTypeInspectionResult = {

    /**
     * Returns `true` if the specified type is a base class of any of the other types in a set.
     */
    def typeInBaseClasses(currType: GravsearchEntityTypeInfo, allTypes: Set[GravsearchEntityTypeInfo]): Boolean = {
      val currTypeIri = iriOfGravsearchTypeInfo(currType)

      allTypes.exists(aType =>
        entityInfo.classInfoMap.get(iriOfGravsearchTypeInfo(aType)) match {
          case Some(classDef: ReadClassInfoV2) =>
            classDef.allBaseClasses.contains(currTypeIri)
          case _ => false
        }
      )
    }

    // iterate over all typeable entities, refine determined types for it by keeping only the specific types.
    intermediateResult.entities.keySet.foldLeft(intermediateResult) {
      (acc: IntermediateTypeInspectionResult, typedEntity: TypeableEntity) =>
        val types: Set[GravsearchEntityTypeInfo] = intermediateResult.entities.getOrElse(typedEntity, Set.empty)

        types.foldLeft(acc) { (refinedResults: IntermediateTypeInspectionResult, currType: GravsearchEntityTypeInfo) =>
          // Is the type a base class of any other type?
          if (typeInBaseClasses(currType = currType, allTypes = types - currType)) {
            // Yes. Remove it.
            refinedResults.removeType(entity = typedEntity, typeToRemove = currType)
          } else refinedResults
        }
    }
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
  private def doIterations(
    iterationNumber: Int,
    intermediateResult: IntermediateTypeInspectionResult,
    entityInfo: EntityInfoGetResponseV2,
    usageIndex: UsageIndex
  ): IntermediateTypeInspectionResult = {
    if (iterationNumber > MAX_ITERATIONS) {
      throw GravsearchException(s"Too many type inference iterations")
    }

    // Run an iteration of type inference and get its result.

    log.debug(
      s"****** Inference iteration $iterationNumber (untyped ${intermediateResult.untypedEntities.size}, inconsistent ${intermediateResult.entitiesWithInconsistentTypes.size})"
    )

    val iterationResult: IntermediateTypeInspectionResult =
      intermediateResult.entities.keySet.foldLeft(intermediateResult) {
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
        intermediateResult = refineDeterminedTypes(intermediateResult = iterationResult, entityInfo = entityInfo),
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
     * @param usageIndex       the usage index being constructed.
     * @return an updated usage index.
     */
    override def visitStatementInWhere(statementPattern: StatementPattern, usageIndex: UsageIndex): UsageIndex = {
      // Index the statement by subject.
      val subjectIndex: Map[TypeableEntity, Set[StatementPattern]] = addStatementIndexEntry(
        statementEntity = statementPattern.subj,
        statementPattern = statementPattern,
        statementIndex = usageIndex.subjectIndex
      )

      // Index the statement by predicate.
      val predicateIndex: Map[TypeableEntity, Set[StatementPattern]] = addStatementIndexEntry(
        statementEntity = statementPattern.pred,
        statementPattern = statementPattern,
        statementIndex = usageIndex.predicateIndex
      )

      // Index the statement by object.
      val objectIndex: Map[TypeableEntity, Set[StatementPattern]] = addStatementIndexEntry(
        statementEntity = statementPattern.obj,
        statementPattern = statementPattern,
        statementIndex = usageIndex.objectIndex
      )

      // If the statement's predicate is rdf:type, and its object is a Knora entity, add it to the
      // set of Knora class IRIs.
      val knoraClassIris: Set[SmartIri] = usageIndex.knoraClassIris ++ (statementPattern.pred match {
        case IriRef(predIri, _) if predIri.toString == OntologyConstants.Rdf.Type =>
          statementPattern.obj match {
            case IriRef(objIri, _) if objIri.isKnoraEntityIri =>
              Some(objIri)

            case _ => None
          }

        case _ => None
      })

      // If the statement's predicate is a Knora property, and isn't a type annotation predicate or a Gravsearch option predicate,
      // add it to the set of Knora property IRIs.
      val knoraPropertyIris: Set[SmartIri] = usageIndex.knoraPropertyIris ++ (statementPattern.pred match {
        case IriRef(predIri, _)
            if predIri.isKnoraEntityIri &&
              !(GravsearchTypeInspectionUtil.TypeAnnotationProperties.allTypeAnnotationIris.contains(
                predIri.toString
              ) ||
                GravsearchTypeInspectionUtil.GravsearchOptionIris.contains(predIri.toString)) =>
          Some(predIri)

        case _ => None
      })

      usageIndex.copy(
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
     * @return an updated index entry.
     */
    private def addStatementIndexEntry(
      statementEntity: Entity,
      statementPattern: StatementPattern,
      statementIndex: Map[TypeableEntity, Set[StatementPattern]]
    ): Map[TypeableEntity, Set[StatementPattern]] =
      GravsearchTypeInspectionUtil.maybeTypeableEntity(statementEntity) match {
        case Some(typeableEntity) =>
          val currentPatterns: Set[StatementPattern] = statementIndex.getOrElse(typeableEntity, Set.empty)
          statementIndex + (typeableEntity -> (currentPatterns + statementPattern))

        case None => statementIndex
      }

    /**
     * Collects information for the usage index from filters.
     *
     * @param filterPattern the pattern to be visited.
     * @param usageIndex    the usage index being constructed.
     * @return an updated usage index.
     */
    override def visitFilter(filterPattern: FilterPattern, usageIndex: UsageIndex): UsageIndex =
      visitFilterExpression(filterPattern.expression, usageIndex)

    /**
     * Indexes two entities that are compared in a FILTER expression.
     *
     * @param leftQueryVariable the query variable that is the left argument of the comparison.
     * @param rightEntity       the query variable or IRI that is the right argument of the comparison.
     * @param usageIndex        the usage index being constructed.
     * @return an an updated usage index.
     */
    private def addEntityComparisonIndexEntry(
      leftQueryVariable: QueryVariable,
      rightEntity: Entity,
      usageIndex: UsageIndex
    ): UsageIndex = {
      val leftTypeableVariable = TypeableVariable(leftQueryVariable.variableName)

      val rightTypeableEntity: TypeableEntity = GravsearchTypeInspectionUtil.maybeTypeableEntity(rightEntity) match {
        case Some(typeableEntity) => typeableEntity
        case None =>
          throw GravsearchException(s"Entity ${rightEntity.toSparql} is not valid in a comparison expression")
      }

      val currentComparisonsForLeftVariable: Set[TypeableEntity] =
        usageIndex.entitiesComparedInFilters.getOrElse(leftTypeableVariable, Set.empty)
      val currentComparisonsForRightEntity: Set[TypeableEntity] =
        usageIndex.entitiesComparedInFilters.getOrElse(rightTypeableEntity, Set.empty)

      usageIndex.copy(
        entitiesComparedInFilters = usageIndex.entitiesComparedInFilters +
          (leftTypeableVariable -> (currentComparisonsForLeftVariable + rightTypeableEntity)) +
          (rightTypeableEntity  -> (currentComparisonsForRightEntity + leftTypeableVariable))
      )
    }

    /**
     * Visits a [[CompareExpression]] in a [[FilterPattern]].
     *
     * @param compareExpression the comparison expression to be visited.
     * @param usageIndex        the usage index being constructed.
     * @return an updated usage index.
     */
    private def visitCompareExpression(compareExpression: CompareExpression, usageIndex: UsageIndex): UsageIndex =
      compareExpression match {
        case CompareExpression(
              leftQueryVariable: QueryVariable,
              operator: CompareExpressionOperator.Value,
              rightEntity: Entity
            ) =>
          rightEntity match {
            case xsdLiteral: XsdLiteral =>
              // A variable is compared to an XSD literal. Index the variable and the literal's type.
              val typeableVariable = TypeableVariable(leftQueryVariable.variableName)
              val currentVarTypesFromFilters: Set[SmartIri] =
                usageIndex.typedEntitiesInFilters.getOrElse(typeableVariable, Set.empty)

              usageIndex.copy(
                typedEntitiesInFilters =
                  usageIndex.typedEntitiesInFilters + (typeableVariable -> (currentVarTypesFromFilters + xsdLiteral.datatype))
              )

            case rightIriRef: IriRef if rightIriRef.iri.isKnoraEntityIri =>
              // A variable is compared to a Knora ontology entity IRI, which must be a property IRI.
              // Index the property IRI in usageIndex.knoraPropertyVariablesInFilters.

              if (operator != CompareExpressionOperator.EQUALS) {
                throw GravsearchException(s"A Knora property IRI can be compared only with the equals operator")
              }

              val typeableVariable = TypeableVariable(leftQueryVariable.variableName)
              val currentIris: Set[SmartIri] =
                usageIndex.knoraPropertyVariablesInFilters.getOrElse(typeableVariable, Set.empty)

              usageIndex.copy(
                knoraPropertyIris = usageIndex.knoraPropertyIris + rightIriRef.iri,
                knoraPropertyVariablesInFilters =
                  usageIndex.knoraPropertyVariablesInFilters + (typeableVariable -> (currentIris + rightIriRef.iri))
              )

            case rightQueryVariable: QueryVariable =>
              // Two variables are compared. Index them both in usageIndex.entitiesComparedInFilters.
              addEntityComparisonIndexEntry(
                leftQueryVariable = leftQueryVariable,
                rightEntity = rightQueryVariable,
                usageIndex = usageIndex
              )

            case rightIriRef: IriRef =>
              // A variable is compared with an IRI, which must be a resource IRI.
              // Index them both in usageIndex.entitiesComparedInFilters.

              if (!rightIriRef.iri.isKnoraResourceIri) {
                throw GravsearchException(
                  s"IRI ${rightIriRef.toSparql}, used in a comparison, is not a Knora resource IRI"
                )
              }

              addEntityComparisonIndexEntry(
                leftQueryVariable = leftQueryVariable,
                rightEntity = rightIriRef,
                usageIndex = usageIndex
              )

            case _ => throw GravsearchException(s"An invalid `rightEntity` with value: $rightEntity was used.")
          }

        case _ =>
          val usageIndexFromLeft = visitFilterExpression(compareExpression.leftArg, usageIndex)
          visitFilterExpression(compareExpression.rightArg, usageIndexFromLeft)
      }

    /**
     * Visits a [[FunctionCallExpression]] in a [[FilterPattern]].
     *
     * @param functionCallExpression the function call to be visited.
     * @param usageIndex             the usage index being constructed.
     * @return an updated usage index.
     */
    private def visitFunctionCallExpression(
      functionCallExpression: FunctionCallExpression,
      usageIndex: UsageIndex
    ): UsageIndex =
      functionCallExpression.functionIri.iri.toString match {
        case OntologyConstants.KnoraApiV2Simple.MatchTextFunction =>
          // The first argument is a variable representing a string.
          val textVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
          val currentTextVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(textVar, Set.empty)

          usageIndex.copy(
            typedEntitiesInFilters = usageIndex.typedEntitiesInFilters +
              (textVar -> (currentTextVarTypesFromFilters + OntologyConstants.Xsd.String.toSmartIri))
          )

        case OntologyConstants.KnoraApiV2Complex.MatchTextFunction =>
          // The first argument is a variable representing a text value.
          val textVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
          val currentTextVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(textVar, Set.empty)

          usageIndex.copy(
            typedEntitiesInFilters = usageIndex.typedEntitiesInFilters +
              (textVar -> (currentTextVarTypesFromFilters + OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          )

        case OntologyConstants.KnoraApiV2Simple.MatchLabelFunction =>
          // The first argument is a variable representing a resource.
          val resourceVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
          val currentResourceVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(resourceVar, Set.empty)

          usageIndex.copy(
            typedEntitiesInFilters = usageIndex.typedEntitiesInFilters +
              (resourceVar -> (currentResourceVarTypesFromFilters + OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri))
          )

        case OntologyConstants.KnoraApiV2Complex.MatchLabelFunction =>
          // The first argument is a variable representing a resource.
          val resourceVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
          val currentResourceVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(resourceVar, Set.empty)

          usageIndex.copy(
            typedEntitiesInFilters = usageIndex.typedEntitiesInFilters +
              (resourceVar -> (currentResourceVarTypesFromFilters + OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri))
          )

        case OntologyConstants.KnoraApiV2Complex.MatchTextInStandoffFunction =>
          // The first argument is a variable representing a text value.
          val textVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(0).variableName)
          val currentTextVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(textVar, Set.empty)

          // The second argument is a variable representing a standoff tag.
          val standoffTagVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(1).variableName)
          val currentStandoffVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(standoffTagVar, Set.empty)

          usageIndex.copy(
            typedEntitiesInFilters = usageIndex.typedEntitiesInFilters +
              (textVar        -> (currentTextVarTypesFromFilters + OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)) +
              (standoffTagVar -> (currentStandoffVarTypesFromFilters + OntologyConstants.KnoraApiV2Complex.StandoffTag.toSmartIri))
          )

        case OntologyConstants.KnoraApiV2Complex.StandoffLinkFunction =>
          if (functionCallExpression.args.size != 3)
            throw GravsearchException(
              s"Three arguments are expected for ${functionCallExpression.functionIri.toSparql}"
            )

          // The first and third arguments are variables or IRIs representing resources.
          val resourceEntitiesAndTypes: Seq[(TypeableEntity, Set[SmartIri])] =
            Seq(functionCallExpression.args.head, functionCallExpression.args(2)).flatMap { entity =>
              GravsearchTypeInspectionUtil.maybeTypeableEntity(entity)
            }.map { typeableEntity =>
              val currentVarTypesFromFilters: Set[SmartIri] =
                usageIndex.typedEntitiesInFilters.getOrElse(typeableEntity, Set.empty)
              typeableEntity -> (currentVarTypesFromFilters + OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri)
            }

          // The second argument is a variable representing a standoff tag.
          val standoffTagVar = TypeableVariable(functionCallExpression.getArgAsQueryVar(1).variableName)
          val currentStandoffVarTypesFromFilters: Set[SmartIri] =
            usageIndex.typedEntitiesInFilters.getOrElse(standoffTagVar, Set.empty)

          usageIndex.copy(
            typedEntitiesInFilters = usageIndex.typedEntitiesInFilters ++ resourceEntitiesAndTypes +
              (standoffTagVar -> (currentStandoffVarTypesFromFilters + OntologyConstants.KnoraApiV2Complex.StandoffTag.toSmartIri))
          )

        case OntologyConstants.KnoraApiV2Complex.ToSimpleDateFunction =>
          // The function knora-api:toSimpleDate can take either a knora-api:DateValue or a knora-api:StandoffTag,
          // so we don't infer the type of its argument.
          usageIndex

        case _ => throw GravsearchException(s"Unrecognised function: ${functionCallExpression.functionIri.toSparql}")
      }

    /**
     * Collects information for the usage index from filter expressions.
     *
     * @param filterExpression the filter expression to be visited.
     * @param usageIndex       the usage index being constructed.
     * @return an updated usage index.
     */
    private def visitFilterExpression(filterExpression: Expression, usageIndex: UsageIndex): UsageIndex =
      filterExpression match {
        case compareExpression: CompareExpression =>
          visitCompareExpression(compareExpression = compareExpression, usageIndex = usageIndex)

        case functionCallExpression: FunctionCallExpression =>
          visitFunctionCallExpression(functionCallExpression = functionCallExpression, usageIndex = usageIndex)

        case andExpression: AndExpression =>
          val usageIndexFromLeft = visitFilterExpression(andExpression.leftArg, usageIndex)
          visitFilterExpression(filterExpression = andExpression.rightArg, usageIndex = usageIndexFromLeft)

        case orExpression: OrExpression =>
          val usageIndexFromLeft = visitFilterExpression(orExpression.leftArg, usageIndex)
          visitFilterExpression(filterExpression = orExpression.rightArg, usageIndex = usageIndexFromLeft)

        case _ => usageIndex
      }
  }

  /**
   * Makes an index of entity usage in the query.
   *
   * @param whereClause the WHERE clause in the query.
   * @return an index of entity usage in the query.
   */
  private def makeUsageIndex(whereClause: WhereClause): UsageIndex =
    // Traverse the query, collecting information for the usage index.
    QueryTraverser.visitWherePatterns(
      patterns = whereClause.patterns,
      whereVisitor = new UsageIndexCollectingWhereVisitor,
      initialAcc = UsageIndex(
        querySchema = whereClause.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema"))
      )
    )
}

object InferringGravsearchTypeInspector {

  /**
   * Provides the string representation of the companion class in log messages.
   *
   * See [[https://doc.akka.io/docs/akka/current/logging.html#translating-log-source-to-string-and-class]].
   */
  implicit val logSource: LogSource[AnyRef] = (o: AnyRef) => o.getClass.getName
}
