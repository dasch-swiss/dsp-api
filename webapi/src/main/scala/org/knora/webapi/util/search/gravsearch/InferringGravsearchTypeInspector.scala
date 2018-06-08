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
import org.knora.webapi.messages.v2.responder.ontologymessages.{EntityInfoGetRequestV2, EntityInfoGetResponseV2, ReadClassInfoV2, ReadPropertyInfoV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.GravsearchTypeInspectionUtil.IntermediateTypeInspectionResult
import org.knora.webapi.util.{SmartIri, StringFormatter}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * A Gravsearch type inspector that infers types, relying on information from the relevant ontologies.
  */
class InferringGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                       system: ActorSystem)
                                      (implicit executionContext: ExecutionContext) extends GravsearchTypeInspector(nextInspector = nextInspector, system = system) {

    import InferringGravsearchTypeInspector._

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val log: LoggingAdapter = akka.event.Logging(system, this)

    // The maximum number of type inference iterations.
    private val MAX_ITERATIONS = 100

    /**
      * Represents an inference rule in a pipeline. Each rule in the pipeline tries to determine type information
      * about a typeable entity. If it cannot do so, it calls the next rule in the pipeline.
      *
      * @param nextRule the next rule in the pipeline.
      */
    private abstract class InferenceRule(protected val nextRule: Option[InferenceRule]) {
        /**
          * Attempts to determine the type of a single entity. Each implementation must end by calling
          * `runNextRule`.
          *
          * @param untypedEntity           the entity whose type needs to be determined.
          * @param previousIterationResult the result of the previous iteration of type inference.
          * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex              an index of entity usage in the query.
          * @return type information about the entity, or `None` if the type could not be determined.
          */
        def infer(untypedEntity: TypeableEntity,
                  previousIterationResult: IntermediateTypeInspectionResult,
                  entityInfo: EntityInfoGetResponseV2,
                  usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo]

        /**
          * Runs the next rule in the pipeline.
          *
          * @param untypedEntity           the entity whose type needs to be determined.
          * @param maybeInferredType       the type inferred by the previous rule, if any.
          * @param previousIterationResult the result of the previous iteration of type inference.
          * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex              an index of entity usage in the query.
          * @return type information about the entity, or `None` if the type could not be determined.
          */
        protected def runNextRule(untypedEntity: TypeableEntity,
                                  maybeInferredType: Option[GravsearchEntityTypeInfo],
                                  previousIterationResult: IntermediateTypeInspectionResult,
                                  entityInfo: EntityInfoGetResponseV2,
                                  usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Did this rule determine the type?
            maybeInferredType match {
                case Some(inferred) =>
                    // Yes. Return it as the result of the pipeline.
                    Some(inferred)

                case None =>
                    // No. Is there another rule in the pipeline?
                    nextRule match {
                        case Some(rule) =>
                            // Yes. Run that rule.
                            rule.infer(
                                untypedEntity = untypedEntity,
                                previousIterationResult = previousIterationResult,
                                entityInfo = entityInfo,
                                usageIndex = usageIndex
                            )

                        case None =>
                            // No. The type could not be determined.
                            None
                    }
            }
        }
    }

    /**
      * Infers that an entity is a `knora-api:Resource` if there is an `rdf:type` statement about it and and the
      * specified type is a Knora resource class.
      */
    private class RdfTypeRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Has this entity been used as a subject?
            val maybeInferredType: Option[NonPropertyTypeInfo] = usageIndex.subjects.get(untypedEntity) match {
                case Some(statements) =>
                    // Yes. If it's been used with the predicate rdf:type with an IRI object, collect those objects.
                    val rdfTypes: Set[SmartIri] = statements.collect {
                        case StatementPattern(_, IriRef(predIri, _), IriRef(objIri, _), _) if predIri.toString == OntologyConstants.Rdf.Type => objIri
                    }

                    // Get any information the ontology responder provided about the classes identified by those IRIs.
                    val knoraClasses: Set[ReadClassInfoV2] = entityInfo.classInfoMap.filterKeys(rdfTypes).values.toSet

                    // Is any of them a resource class?
                    if (knoraClasses.exists(_.isResourceClass)) {
                        // Yes. Return the entity's type as knora-api:Resource.
                        val inferredType = NonPropertyTypeInfo(OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri)
                        log.debug("RdfTypeRule: {} {} .", untypedEntity, inferredType)
                        Some(inferredType)
                    } else {
                        // This entity isn't a resource, so this rule isn't relevant.
                        None
                    }

                case None =>
                    // This entity hasn't been used as a subject, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers a property's `knora-api:objectType` if the property's IRI is used as a predicate.
      */
    private class PropertyIriObjectTypeRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Is this entity an IRI?
            val maybeInferredType = untypedEntity match {
                case TypeableIri(iri) =>
                    // Yes. Has it been used as a predicate?
                    usageIndex.predicates.get(untypedEntity) match {
                        case Some(_) =>
                            // Yes. Has the ontology responder provided information about it?
                            entityInfo.propertyInfoMap.get(iri) match {
                                case Some(readPropertyInfo) =>
                                    // Yes. Is this a link property?
                                    if (readPropertyInfo.isLinkProp) {
                                        // Yes. Infer knora-api:objectType knora-api:Resource.
                                        val inferredType = PropertyTypeInfo(objectTypeIri = OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri)
                                        log.debug("PropertyIriObjectTypeRule: {} {} .", untypedEntity, inferredType)
                                        Some(inferredType)
                                    } else {
                                        // It's not a link property. Has the ontology responder provided its knora-api:objectType?
                                        val maybeObjectType: Option[SmartIri] = readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri).
                                            orElse(readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri))

                                        maybeObjectType match {
                                            case Some(objectType) =>
                                                // Yes. Use that type.
                                                val inferredType = PropertyTypeInfo(objectType)
                                                log.debug("PropertyIriObjectTypeRule: {} {} .", untypedEntity, inferredType)
                                                Some(inferredType)

                                            case None =>
                                                // The ontology responder hasn't provided its knora-api:objectType.
                                                None
                                        }
                                    }

                                case None =>
                                    // The ontology responder hasn't provided information about this property. This should have caused
                                    // an error earlier from the ontology responder.
                                    throw AssertionException(s"No information found about property $iri")
                            }

                        case None =>
                            // The IRI hasn't been used as a predicate, so this rule isn't relevant.
                            None
                    }

                case _ =>
                    // This entity isn't an IRI, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    /**
      * Infers an entity's type if the entity is used as the object of a statement and the predicate's
      * knora-api:objectType is known.
      */
    private class TypeOfObjectFromPropertyRule(nextRule: Option[InferenceRule]) extends InferenceRule(nextRule = nextRule) {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Has this entity been used as the object of one or more statements?
            val maybeInferredType: Option[NonPropertyTypeInfo] = usageIndex.objects.get(untypedEntity) match {
                case Some(statements) =>
                    // Yes. Try to infer type information from the predicate of each of those statements.
                    val typesFromPredicates: Set[NonPropertyTypeInfo] = statements.flatMap {
                        statement =>
                            // Is the predicate typeable?
                            GravsearchTypeInspectionUtil.maybeTypeableEntity(statement.pred) match {
                                case Some(typeablePred) =>
                                    // Yes. Do we have its type?
                                    previousIterationResult.typedEntities.get(typeablePred) match {
                                        case Some(propertyTypeInfo: PropertyTypeInfo) =>
                                            // Yes. Use its knora-api:objectType.
                                            Some(NonPropertyTypeInfo(propertyTypeInfo.objectTypeIri))

                                        case _ =>
                                            // We don't have the predicate's type.
                                            None
                                    }

                                case None =>
                                    // The predicate isn't typeable.
                                    None
                            }
                    }

                    if (typesFromPredicates.isEmpty) {
                        None
                    } else if (typesFromPredicates.size == 1) {
                        val inferredType = typesFromPredicates.head
                        log.debug("TypeOfObjectFromPropertyRule: {} {} .", untypedEntity, inferredType)
                        Some(inferredType)
                    } else {
                        throw GravsearchException(s"Incompatible types were inferred for $untypedEntity: ${typesFromPredicates.map(typeFromPred => IriRef(typeFromPred.typeIri)).mkString(", ")}")
                    }

                case None =>
                    // This entity hasn't been used as a statement object, so this rule isn't relevant.
                    None
            }

            runNextRule(
                untypedEntity = untypedEntity,
                maybeInferredType = maybeInferredType,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )
        }
    }

    // The inference rule pipeline.
    private val rulePipeline = new RdfTypeRule(Some(new PropertyIriObjectTypeRule(Some(new TypeOfObjectFromPropertyRule(None)))))

    /**
      * An index of entity usage in a Gravsearch query.
      *
      * @param knoraClasses               the Knora class IRIs that are used in the query.
      * @param knoraProperties            the Knora property IRIs that are used in the query.
      * @param subjects                   a map of all statement subjects to the statements they occur in.
      * @param predicates                 map of all statement predicates to the statements they occur in.
      * @param objects                    a map of all statement objects to the statements they occur in.
      * @param compareExpressionVariables a map of query variables to IRIs that they are compared to in FILTER expressions.
      */
    private case class UsageIndex(knoraClasses: Set[SmartIri],
                                  knoraProperties: Set[SmartIri],
                                  subjects: Map[TypeableEntity, Set[StatementPattern]],
                                  predicates: Map[TypeableEntity, Set[StatementPattern]],
                                  objects: Map[TypeableEntity, Set[StatementPattern]],
                                  compareExpressionVariables: Map[QueryVariable, Set[IriRef]])

    /**
      * A [[WhereTransformer]] that returns statements and filters unchanged.
      */
    private class NoOpWhereTransformer extends WhereTransformer {
        override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = Seq(statementPattern)

        override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
    }

    override def inspectTypes(previousResult: IntermediateTypeInspectionResult,
                              whereClause: WhereClause,
                              requestingUser: UserADM): Future[IntermediateTypeInspectionResult] = {
        log.debug("========== Starting type inference ==========")

        for {
            // Make an index of entity usage in the query.
            usageIndex <- Future(makeUsageIndex(whereClause))

            // Ask the ontology responder about all the Knora class and property IRIs mentioned in the query.

            entityInfoRequest = EntityInfoGetRequestV2(
                classIris = usageIndex.knoraClasses,
                propertyIris = usageIndex.knoraProperties,
                requestingUser = requestingUser)

            entityInfo: EntityInfoGetResponseV2 <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV2]

            // The ontology responder may return the requested information in the internal schema. Convert each entity
            // definition back to the input schema.

            entityInfoInInputSchemas = EntityInfoGetResponseV2(
                classInfoMap = usageIndex.knoraClasses.flatMap {
                    inputClassIri =>
                        val inputSchema = inputClassIri.getOntologySchema.get match {
                            case apiV2Schema: ApiV2Schema => apiV2Schema
                            case _ => throw GravsearchException(s"Invalid schema in IRI $inputClassIri")
                        }

                        val maybeReadClassInfo: Option[ReadClassInfoV2] = entityInfo.classInfoMap.get(inputClassIri).orElse {
                            entityInfo.classInfoMap.get(inputClassIri.toOntologySchema(InternalSchema))
                        }

                        maybeReadClassInfo.map {
                            readClassInfo => inputClassIri -> readClassInfo.toOntologySchema(inputSchema)
                        }
                }.toMap,
                propertyInfoMap = usageIndex.knoraProperties.flatMap {
                    inputPropertyIri =>
                        val inputSchema = inputPropertyIri.getOntologySchema.get match {
                            case apiV2Schema: ApiV2Schema => apiV2Schema
                            case _ => throw GravsearchException(s"Invalid schema in IRI $inputPropertyIri")
                        }


                        val maybeReadPropertyInfo: Option[ReadPropertyInfoV2] = entityInfo.propertyInfoMap.get(inputPropertyIri).orElse {
                            entityInfo.propertyInfoMap.get(inputPropertyIri.toOntologySchema(InternalSchema))
                        }

                        maybeReadPropertyInfo.map {
                            readPropertyInfo => inputPropertyIri -> readPropertyInfo.toOntologySchema(inputSchema)
                        }
                }.toMap
            )

            // Iterate over the inference rules until no new type information can be inferred.
            intermediateResult = doIterations(
                previousResult = previousResult,
                entityInfo = entityInfoInInputSchemas,
                usageIndex = usageIndex
            )

            // _ = println(s"Intermediate result from inferring inspector: $intermediateResult")

            // Pass the intermediate result to the next type inspector in the pipeline.
            lastResult <- runNextInspector(
                intermediateResult = intermediateResult,
                whereClause = whereClause,
                requestingUser = requestingUser
            )
        } yield lastResult
    }

    /**
      * Runs all the inference rules repeatedly until no new type information can be found.
      *
      * @param previousResult the result of the previous type inspection.
      * @param entityInfo     information about Knora ontology entities mentioned in the Gravsearch query.
      * @param usageIndex     an index of entity usage in the query.
      * @return a new intermediate result.
      */
    private def doIterations(previousResult: IntermediateTypeInspectionResult,
                             entityInfo: EntityInfoGetResponseV2,
                             usageIndex: UsageIndex): IntermediateTypeInspectionResult = {
        var iterationResult = previousResult
        var iterate = true
        var iterationNumber = 1

        while (iterate) {
            if (iterationNumber > MAX_ITERATIONS) {
                throw GravsearchException(s"Too many type inference iterations")
            }

            // Run an iteration of type inference and get its result.

            log.debug(s"****** Inference iteration $iterationNumber")

            val newTypesFound: Map[TypeableEntity, GravsearchEntityTypeInfo] = doIteration(
                previousIterationResult = iterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )

            // If no new type information was found, stop.
            if (newTypesFound.isEmpty) {
                log.debug("No new information, stopping iterations")
                iterate = false
            } else {
                // Otherwise, do another iteration based on the results of the previous one.
                iterationResult = IntermediateTypeInspectionResult(
                    typedEntities = iterationResult.typedEntities ++ newTypesFound,
                    untypedEntities = iterationResult.untypedEntities -- newTypesFound.keySet
                )

                iterationNumber += 1
            }
        }

        iterationResult
    }

    /**
      * Runs all the type inference rules for each untyped entity.
      *
      * @param previousIterationResult the result of the previous iteration of type inference.
      * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
      * @param usageIndex              an index of entity usage in the query.
      * @return the new type information that was found in this iteration, or empty map if none was found.
      */
    private def doIteration(previousIterationResult: IntermediateTypeInspectionResult,
                            entityInfo: EntityInfoGetResponseV2,
                            usageIndex: UsageIndex): Map[TypeableEntity, GravsearchEntityTypeInfo] = {
        // Start the iteration with an empty map of new type information.
        val newTypesFound: mutable.Map[TypeableEntity, GravsearchEntityTypeInfo] = mutable.Map.empty

        // Run the inference rule pipeline for each entity that is still untyped.
        for (untypedEntity <- previousIterationResult.untypedEntities) {
            val inferenceRuleResult: Option[GravsearchEntityTypeInfo] = rulePipeline.infer(
                untypedEntity = untypedEntity,
                previousIterationResult = previousIterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )

            // If the rule pipeline found type information about the entity, add it to the results of the iteration.
            inferenceRuleResult match {
                case Some(result) => newTypesFound.put(untypedEntity, result)
                case None => ()
            }
        }

        // Return the results of the iteration.
        newTypesFound.toMap
    }

    /**
      * Makes an index of entity usage in the query.
      *
      * @param whereClause the WHERE clause in the query.
      * @return an index of entity usage in the query.
      */
    private def makeUsageIndex(whereClause: WhereClause): UsageIndex = {
        // Flatten the statements and filters in the WHERE clause into a sequence.
        val flattenedPatterns: Seq[QueryPattern] = QueryTraverser.transformWherePatterns(
            patterns = whereClause.patterns,
            inputOrderBy = Seq.empty[OrderCriterion],
            whereTransformer = new NoOpWhereTransformer,
            rebuildStructure = false
        )

        // Make mutable association lists to collect the index in.
        val knoraClasses: mutable.Set[SmartIri] = mutable.Set.empty
        val knoraProperties: mutable.Set[SmartIri] = mutable.Set.empty
        val subjectEntitiesBuffer: mutable.ArrayBuffer[(TypeableEntity, StatementPattern)] = mutable.ArrayBuffer.empty[(TypeableEntity, StatementPattern)]
        val predicateEntitiesBuffer: mutable.ArrayBuffer[(TypeableEntity, StatementPattern)] = mutable.ArrayBuffer.empty[(TypeableEntity, StatementPattern)]
        val objectEntitiesBuffer: mutable.ArrayBuffer[(TypeableEntity, StatementPattern)] = mutable.ArrayBuffer.empty[(TypeableEntity, StatementPattern)]
        val compareExpressionVariablesBuffer: mutable.ArrayBuffer[(QueryVariable, IriRef)] = mutable.ArrayBuffer.empty[(QueryVariable, IriRef)]

        // Iterate over the sequence of patterns, indexing their contents.
        for (pattern <- flattenedPatterns) {
            pattern match {
                case statement: StatementPattern =>
                    // Make index entries for the statement's subject, predicate, and object, and add them to the buffers.
                    subjectEntitiesBuffer.appendAll(maybeIndexEntry(statement.subj, statement))
                    predicateEntitiesBuffer.appendAll(maybeIndexEntry(statement.pred, statement))
                    objectEntitiesBuffer.appendAll(maybeIndexEntry(statement.obj, statement))

                    statement.pred match {
                        case IriRef(predIri, _) =>
                            // The statement's predicate is an IRI. Is it rdf:type with an IRI as an object?
                            if (predIri.toString == OntologyConstants.Rdf.Type) {
                                statement.obj match {
                                    case IriRef(objIri, _) if objIri.isKnoraEntityIri =>
                                        // Yes. Add it to the set of Knora class IRIs that we'll ask the ontology responder about.
                                        knoraClasses.add(objIri)

                                    case _ => ()
                                }
                            } else {
                                // The predicate is not rdf:type. If it's a Knora property, add it to the set of Knora property IRIs
                                // that we'll ask the ontology responder about.
                                if (predIri.isKnoraEntityIri) {
                                    knoraProperties.add(predIri)
                                }
                            }

                        case _ => ()
                    }

                case filter: FilterPattern => () // TODO

                case _ => ()
            }
        }

        // Construct the index from the contents of the association lists.
        UsageIndex(
            knoraClasses = Set(knoraClasses.toSeq: _*),
            knoraProperties = Set(knoraProperties.toSeq: _*),
            subjects = associationListToMap(subjectEntitiesBuffer),
            predicates = associationListToMap(predicateEntitiesBuffer),
            objects = associationListToMap(objectEntitiesBuffer),
            compareExpressionVariables = associationListToMap(compareExpressionVariablesBuffer)
        )
    }

    /**
      * Given a statement and an entity in the statement, checks whether the entity is typeable, and makes an index
      * entry if so.
      *
      * @param statementEntity the entity (subject, predicate, or object).
      * @param statement       the statement.
      * @return an index entry for the statement, or `None` if the entity isn't typeable.
      */
    private def maybeIndexEntry(statementEntity: Entity, statement: StatementPattern): Option[(TypeableEntity, StatementPattern)] = {
        GravsearchTypeInspectionUtil.maybeTypeableEntity(statementEntity).map {
            typeableEntity => typeableEntity -> statement
        }
    }

    /**
      * Converts an association list to a map of keys to sets of values.
      *
      * @param seq the association list.
      * @tparam K the type of the keys.
      * @tparam V the type of the values.
      * @return a map of keys to sets of values.
      */
    private def associationListToMap[K, V](seq: Seq[(K, V)]): Map[K, Set[V]] = {
        seq.groupBy {
            case (key, _) => key
        }.map {
            case (key, values) =>
                key -> values.map {
                    case (_, value) => value
                }.toSet
        }
    }
}

object InferringGravsearchTypeInspector {
    /**
      * Provides the string representation of the [[InferringGravsearchTypeInspector]] class in log messages.
      *
      * See [[https://doc.akka.io/docs/akka/current/logging.html#translating-log-source-to-string-and-class]].
      */
    implicit val logSource: LogSource[AnyRef] = (o: AnyRef) => o.getClass.getName
}
