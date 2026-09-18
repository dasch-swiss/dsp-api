/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.prequery.RemoveEntitiesInferredFromProperty.removeEntitiesInferredFromProperty
import org.knora.webapi.messages.util.search.gravsearch.prequery.RemoveRedundantKnoraApiResource.removeRedundantKnoraApiResource
import org.knora.webapi.messages.util.search.gravsearch.prequery.StatementsFirst.statementsFirst
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.messages.util.search.gravsearch.types.TypeableEntity

/**
 * A feature factory that constructs Gravsearch query optimisation algorithms.
 */
object GravsearchQueryOptimisation {

  def optimiseQueryPatterns(
    patterns: Seq[QueryPattern],
    typeInspectionResult: GravsearchTypeInspectionResult,
  ): Seq[QueryPattern] = {
    val removedRedundant = removeRedundantKnoraApiResource(patterns)
    val removedEntities  = removeEntitiesInferredFromProperty(removedRedundant, typeInspectionResult)
    val result           = statementsFirst(removedEntities)
    result
  }
}

/**
 * Removes a statement with rdf:type knora-api:Resource if there is another rdf:type statement with the same subject
 * and a different type.
 */
private object RemoveRedundantKnoraApiResource {

  /**
   * If the specified statement has rdf:type with an IRI as object, returns that IRI, otherwise None.
   */
  private def getObjOfRdfType(statementPattern: StatementPattern): Option[SmartIri] =
    statementPattern.pred match {
      case predicateIriRef: IriRef =>
        if (predicateIriRef.iri.toString == OntologyConstants.Rdf.Type) {
          statementPattern.obj match {
            case iriRef: IriRef => Some(iriRef.iri)
            case _              => None
          }
        } else {
          None
        }

      case _ => None
    }

  def removeRedundantKnoraApiResource(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    // Make a Set of subjects that have rdf:type statements whose objects are not knora-api:Resource.
    val rdfTypesBySubj: Set[Entity] = patterns
      .foldLeft(Set.empty[Entity]) { case (acc, queryPattern: QueryPattern) =>
        queryPattern match {
          case statementPattern: StatementPattern =>
            getObjOfRdfType(statementPattern) match {
              case Some(typeIri) =>
                if (!OntologyConstants.KnoraApi.KnoraApiV2ResourceIris.contains(typeIri.toString)) {
                  acc + statementPattern.subj
                } else {
                  acc
                }

              case None => acc
            }

          case _ => acc
        }
      }

    patterns.filterNot {
      case statementPattern: StatementPattern =>
        // If this statement has rdf:type knora-api:Resource, and we also have another rdf:type statement
        // with the same subject and a different type, remove this statement.
        getObjOfRdfType(statementPattern) match {
          case Some(typeIri) =>
            OntologyConstants.KnoraApi.KnoraApiV2ResourceIris
              .contains(typeIri.toString) && rdfTypesBySubj.contains(statementPattern.subj)

          case None => false
        }

      case _ => false
    }
  }
}

/**
 * Optimises a query by removing `rdf:type` statements that are known to be redundant. A redundant
 * `rdf:type` statement gives the type of a variable whose type is already restricted by its
 * use with a property that can only be used with that type (unless the property
 * statement is in an `OPTIONAL` block).
 */
private object RemoveEntitiesInferredFromProperty {

  def removeEntitiesInferredFromProperty(
    patterns: Seq[QueryPattern],
    typeInspectionResult: GravsearchTypeInspectionResult,
  ): Seq[QueryPattern] = {

    // Collect all entities which are used as subject or object of an OptionalPattern.
    val optionalEntities: Seq[TypeableEntity] = patterns.collect { case optionalPattern: OptionalPattern =>
      optionalPattern
    }.flatMap {
      _.patterns.flatMap {
        case pattern: StatementPattern =>
          GravsearchTypeInspectionUtil.maybeTypeableEntity(pattern.subj) ++ GravsearchTypeInspectionUtil
            .maybeTypeableEntity(pattern.obj)

        case _ => None
      }
    }

    // Remove statements whose predicate is rdf:type, type of subject is inferred from a property,
    // and the subject is not in optionalEntities.
    patterns.filterNot {
      case statementPattern: StatementPattern =>
        // Is the predicate an IRI?
        statementPattern.pred match {
          case predicateIriRef: IriRef =>
            // Yes. Is this an rdf:type statement?
            if (predicateIriRef.iri.toString == OntologyConstants.Rdf.Type) {
              // Yes. Is the subject a typeable entity?
              val subjectAsTypeableEntity: Option[TypeableEntity] =
                GravsearchTypeInspectionUtil.maybeTypeableEntity(statementPattern.subj)

              subjectAsTypeableEntity match {
                case Some(typeableEntity) =>
                  // Yes. Was the type of the subject inferred from another predicate?
                  if (typeInspectionResult.entitiesInferredFromProperties.keySet.contains(typeableEntity)) {
                    // Yes. Is the subject in optional entities?
                    if (optionalEntities.contains(typeableEntity)) {
                      // Yes. Keep the statement.
                      false
                    } else {
                      // Remove the statement.
                      true
                    }
                  } else {
                    // The type of the subject was not inferred from another predicate. Keep the statement.
                    false
                  }

                case _ =>
                  // The subject isn't a typeable entity. Keep the statement.
                  false
              }
            } else {
              // This isn't an rdf:type statement. Keep it.
              false
            }

          case _ =>
            // The predicate isn't an IRI. Keep the statement.
            false
        }

      case _ =>
        // This isn't a statement pattern. Keep it.
        false
    }
  }
}

/**
 * Moves statement patterns ahead of other patterns at each nesting level. The real ordering (subject/object
 * connectivity, anchor tiers) is done afterwards by `PrequeryPatternOrdering` in
 * `QueryTraverser.transformSelectToSelect`; this partition only guards two shapes that pass has no way to
 * fix because they are not statement-to-statement reordering problems: (a) a FILTER inside a nested braced
 * group written before the statements it binds against, and (b) a BIND pattern or block written before the
 * statements its expression depends on. The trivial `{ FILTER ... stmts }` shape still works without this
 * partition, so testing only that shape wrongly suggests it is dead; the regression goldens
 * `filterBeforeStatementsInUnion` and `dateFilterInUnionAndTopLevel` are the cases that actually fail if it
 * is dropped.
 */
private object StatementsFirst {

  def statementsFirst(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    val (statementPatterns, otherPatterns) = patterns.partition {
      case _: StatementPattern => true
      case _                   => false
    }

    val sortedOtherPatterns = otherPatterns.map {
      case unionPattern: UnionPattern =>
        UnionPattern(unionPattern.blocks.map(statementsFirst))
      case optionalPattern: OptionalPattern =>
        OptionalPattern(statementsFirst(optionalPattern.patterns))
      case minusPattern: MinusPattern =>
        MinusPattern(statementsFirst(minusPattern.patterns))
      case filterNotExistsPattern: FilterNotExistsPattern =>
        FilterNotExistsPattern(statementsFirst(filterNotExistsPattern.patterns))
      case pattern: QueryPattern => pattern
    }

    statementPatterns ++ sortedOtherPatterns
  }
}
