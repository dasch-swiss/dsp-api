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
import akka.pattern._
import org.knora.webapi.{ApiV2Simple, OntologyConstants}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.ontologymessages.{EntityInfoGetRequestV2, EntityInfoGetResponseV2, ReadClassInfoV2}
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.gravsearch.GravsearchTypeInspectionUtil.IntermediateTypeInspectionResult
import org.knora.webapi.util.IriConversions._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
  * A Gravsearch type inspector that infers types, relying on information from the relevant ontologies.
  */
class InferringGravsearchTypeInspector(nextInspector: Option[GravsearchTypeInspector],
                                       system: ActorSystem)
                                      (implicit executionContext: ExecutionContext) extends GravsearchTypeInspector(nextInspector = nextInspector, system = system) {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Represents an inference rule.
      */
    private trait InferenceRule {
        /**
          * Attempts to determine the type of a single entity.
          *
          * @param untypedEntity           the entity whose type needs to be determined.
          * @param previousIterationResult the result of the previous iteration of type inference.
          * @param entityInfo              information about Knora ontology entities mentioned in the Gravsearch query.
          * @param usageIndex              an index of entity usage in the query.
          * @return type information about the entity, or `None` if this rule could not determine the entity's type.
          */
        def infer(untypedEntity: TypeableEntity,
                  previousIterationResult: IntermediateTypeInspectionResult,
                  entityInfo: EntityInfoGetResponseV2,
                  usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo]
    }

    /**
      * Infers that if there's a statement that gives an entity's `rdf:type`, and the specified type is a Knora
      * resource class, the entity's type is `knora-api:Resource`.
      */
    private class RdfTypeRule extends InferenceRule {
        override def infer(untypedEntity: TypeableEntity,
                           previousIterationResult: IntermediateTypeInspectionResult,
                           entityInfo: EntityInfoGetResponseV2,
                           usageIndex: UsageIndex): Option[GravsearchEntityTypeInfo] = {
            // Has this entity been used as a subject?
            usageIndex.subjects.get(untypedEntity) match {
                case Some(statements) =>
                    // Yes. If it's been used with the predicate rdf:type with an IRI object, collect those objects.
                    val rdfTypes: Set[SmartIri] = statements.collect {
                        case StatementPattern(_, IriRef(predIri, _), IriRef(objIri, _), _) if predIri.toString == OntologyConstants.Rdf.Type => objIri
                    }

                    // Get any information the ontology responder provided about the classes identified by those IRIs.
                    val knoraClasses: Set[ReadClassInfoV2] = entityInfo.classInfoMap.filterKeys(rdfTypes).values.toSet

                    // If any of them is a resource class, return the entity's type as knora-api:Resource.
                    if (knoraClasses.exists(_.isResourceClass)) {
                        println(s"Inferred that $untypedEntity is a knora-api:Resource")
                        Some(NonPropertyTypeInfo(OntologyConstants.KnoraApiV2Simple.Resource.toSmartIri))
                    } else {
                        None
                    }

                case None => None
            }
        }
    }

    // The sequence of all inference rules.
    private val inferenceRules = Seq(new RdfTypeRule)

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
        // println(s"Inferring inspector got previous result: $previousResult")

        for {
            // Make an index of entity usage in the query.
            usageIndex <- Future(makeUsageIndex(whereClause))

            // Ask the ontology responder about all the Knora class and property IRIs mentioned in the query.

            entityInfoRequest = EntityInfoGetRequestV2(
                classIris = usageIndex.knoraClasses,
                propertyIris = usageIndex.knoraProperties,
                requestingUser = requestingUser)

            entityInfo: EntityInfoGetResponseV2 <- (responderManager ? entityInfoRequest).mapTo[EntityInfoGetResponseV2]

            // The ontology responder may return the requested information in the internal schema. Convert it
            // to the API v2 simple schema.

            entityInfoSimple = EntityInfoGetResponseV2(
                classInfoMap = entityInfo.classInfoMap.map {
                    case (classIri, readClassInfo) => classIri.toOntologySchema(ApiV2Simple) -> readClassInfo.toOntologySchema(ApiV2Simple)
                },
                propertyInfoMap = entityInfo.propertyInfoMap.map {
                    case (propertyIri, readPropertyInfo) => propertyIri.toOntologySchema(ApiV2Simple) -> readPropertyInfo.toOntologySchema(ApiV2Simple)
                }
            )

            // Iterate over the inference rules until no new type information can be inferred.
            intermediateResult = doIterations(
                previousResult = previousResult,
                entityInfo = entityInfoSimple,
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
        var iterationIndex = 0

        while (iterate) {
            // Run an interation of type inference and get its result.

            println(s"******** Inference iteration $iterationIndex")

            val newTypesFound: Map[TypeableEntity, GravsearchEntityTypeInfo] = doIteration(
                previousIterationResult = iterationResult,
                entityInfo = entityInfo,
                usageIndex = usageIndex
            )

            // If no new type information was found, stop.
            if (newTypesFound.isEmpty) {
                println("No new information, stopping iterations")
                iterate = false
            } else {
                // Otherwise, do another iteration based on the results of the previous one.
                iterationResult = IntermediateTypeInspectionResult(
                    typedEntities = previousResult.typedEntities ++ newTypesFound,
                    untypedEntities = previousResult.untypedEntities -- newTypesFound.keySet
                )

                iterationIndex += 1
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

        // For each untyped entity, run all the inference rules to try to determine the entity's type.
        for (untypedEntity <- previousIterationResult.untypedEntities) {
            for (inferenceRule <- inferenceRules) {
                val inferenceRuleResult: Option[GravsearchEntityTypeInfo] = inferenceRule.infer(
                    untypedEntity = untypedEntity,
                    previousIterationResult = previousIterationResult,
                    entityInfo = entityInfo,
                    usageIndex = usageIndex
                )

                // If a rule found type information, add it to the results of the iteration.
                inferenceRuleResult match {
                    case Some(result) => newTypesFound.put(untypedEntity, result)
                    case None => ()
                }
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
