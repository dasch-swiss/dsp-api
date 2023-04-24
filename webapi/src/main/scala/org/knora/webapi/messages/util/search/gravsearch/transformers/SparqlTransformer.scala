/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.ZLayer
import zio._

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

/**
 * Transforms a non-triplestore-specific SELECT for a triplestore that does not have inference enabled (e.g., Fuseki).
 *
 * @param simulateInference `true` if RDFS inference should be simulated using property path syntax.
 */

/**
 * Transforms a non-triplestore-specific CONSTRUCT query for a triplestore that does not have inference enabled (e.g., Fuseki).
 */

/**
 * Functions for transforming generated SPARQL.
 */
object SparqlTransformer {

  /**
   * Creates a syntactically valid variable base name, based on the given entity.
   *
   * @param entity the entity to be used to create a base name for a variable.
   * @return a base name for a variable.
   */
  def escapeEntityForVariable(entity: Entity): String = {
    val entityStr = entity match {
      case QueryVariable(varName)       => varName
      case IriRef(iriLiteral, _)        => iriLiteral.toOntologySchema(InternalSchema).toString
      case XsdLiteral(stringLiteral, _) => stringLiteral
      case _                            => throw GravsearchException(s"A unique variable name could not be made for ${entity.toSparql}")
    }

    entityStr
      .replaceAll("[:/.#-]", "")
      .replaceAll("\\s", "") // TODO: check if this is complete and if it could lead to collision of variable names
  }

  /**
   * Creates a unique variable name from the given entity and the local part of a property IRI.
   *
   * @param base        the entity to use to create the variable base name.
   * @param propertyIri the IRI of the property whose local part will be used to form the unique name.
   * @return a unique variable.
   */
  def createUniqueVariableNameFromEntityAndProperty(base: Entity, propertyIri: IRI): QueryVariable = {
    val propertyHashIndex = propertyIri.lastIndexOf('#')

    if (propertyHashIndex > 0) {
      val propertyName = propertyIri.substring(propertyHashIndex + 1)
      QueryVariable(escapeEntityForVariable(base) + "__" + escapeEntityForVariable(QueryVariable(propertyName)))
    } else {
      throw AssertionException(s"Invalid property IRI: $propertyIri")
    }
  }

  /**
   * Creates a unique variable name representing the `rdf:type` of an entity with a given base class.
   *
   * @param base         the entity to use to create the variable base name.
   * @param baseClassIri a base class of the entity's type.
   * @return a unique variable.
   */
  def createUniqueVariableNameForEntityAndBaseClass(base: Entity, baseClassIri: IriRef): QueryVariable =
    QueryVariable(escapeEntityForVariable(base) + "__subClassOf__" + escapeEntityForVariable(baseClassIri))

  /**
   * Create a unique variable from a whole statement.
   *
   * @param baseStatement the statement to be used to create the variable base name.
   * @param suffix        the suffix to be appended to the base name.
   * @return a unique variable.
   */
  def createUniqueVariableFromStatement(baseStatement: StatementPattern, suffix: String): QueryVariable =
    QueryVariable(
      escapeEntityForVariable(baseStatement.subj) + "__" + escapeEntityForVariable(
        baseStatement.pred
      ) + "__" + escapeEntityForVariable(baseStatement.obj) + "__" + suffix
    )

  /**
   * Create a unique variable name from a whole statement for a link value.
   *
   * @param baseStatement the statement to be used to create the variable base name.
   * @return a unique variable for a link value.
   */
  def createUniqueVariableFromStatementForLinkValue(baseStatement: StatementPattern): QueryVariable =
    createUniqueVariableFromStatement(baseStatement, "LinkValue")

  /**
   * Optimises a query by replacing `knora-base:isDeleted false` with a `FILTER NOT EXISTS` pattern
   * placed at the end of the block.
   *
   * @param patterns the block of patterns to be optimised.
   * @return the result of the optimisation.
   */
  def optimiseIsDeletedWithFilter(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Separate the knora-base:isDeleted statements from the rest of the block.
    val (isDeletedPatterns: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
      case StatementPattern(
            _,
            IriRef(SmartIri(OntologyConstants.KnoraBase.IsDeleted), _),
            XsdLiteral("false", SmartIri(OntologyConstants.Xsd.Boolean)),
            _
          ) =>
        true
      case _ => false
    }

    // Replace the knora-base:isDeleted statements with FILTER NOT EXISTS patterns.
    val filterPatterns: Seq[FilterNotExistsPattern] = isDeletedPatterns.collect {
      case statementPattern: StatementPattern =>
        FilterNotExistsPattern(
          Seq(
            StatementPattern.makeExplicit(
              subj = statementPattern.subj,
              pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
              obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
            )
          )
        )
    }

    otherPatterns ++ filterPatterns
  }

  /**
   * Optimises a query by moving BIND patterns to the beginning of a block.
   *
   * @param patterns the block of patterns to be optimised.
   * @return the result of the optimisation.
   */
  def moveBindToBeginning(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    val (bindQueryPatterns: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
      case _: BindPattern => true
      case _              => false
    }

    bindQueryPatterns ++ otherPatterns
  }

  /**
   * Optimises a query by moving Lucene query patterns to the beginning of a block.
   *
   * @param patterns the block of patterns to be optimised.
   * @return the result of the optimisation.
   */
  def moveLuceneToBeginning(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    val (luceneQueryPatterns: Seq[QueryPattern], otherPatterns: Seq[QueryPattern]) = patterns.partition {
      case _: LuceneQueryPattern => true
      case _                     => false
    }

    luceneQueryPatterns ++ otherPatterns
  }
}

final case class SparqlTransformerLive(ontologyCache: OntologyCache, implicit val stringFormatter: StringFormatter) {

  /**
   * Transforms a statement in a WHERE clause for a triplestore that does not provide inference.
   *
   * @param statementPattern           the statement pattern.
   * @param simulateInference          `true` if RDFS inference should be simulated on basis of the ontology cache.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the statement pattern as expanded to work without inference.
   */
  def transformStatementInWhereForNoInference(
    statementPattern: StatementPattern,
    simulateInference: Boolean,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]] = {

    statementPattern.pred match {
      case iriRef: IriRef if iriRef.iri.toString == OntologyConstants.KnoraBase.StandoffTagHasStartAncestor =>
        // Simulate knora-api:standoffTagHasStartAncestor, using knora-api:standoffTagHasStartParent.
        ZIO.succeed(
          Seq(
            statementPattern.copy(
              pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasStartParent.toSmartIri, Some('*'))
            )
          )
        )

      case _ =>
        // Is the statement in KnoraExplicitNamedGraph?
        statementPattern.namedGraph match {
          case Some(graphIri: IriRef)
              if graphIri.iri.toString == OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph =>
            // Yes. No expansion needed. Just remove KnoraExplicitNamedGraph.
            ZIO.succeed(Seq(statementPattern.copy(namedGraph = None)))

          case _ =>
            // Is inference enabled?
            if (simulateInference) {
              // Yes. The statement might need to be expanded. Is the predicate a property IRI?
              statementPattern.pred match {
                case iriRef: IriRef =>
                  ontologyCache.getCacheData.flatMap { ontoCache =>
                    ZIO.attempt {

                      // Yes.
                      val predIri     = iriRef.iri
                      val propertyIri = predIri.toString

                      // Is the property rdf:type?
                      if (propertyIri == OntologyConstants.Rdf.Type) {
                        // Yes. Expand using rdfs:subClassOf*.

                        val baseClassIri: IriRef = statementPattern.obj match {
                          case iriRef: IriRef => iriRef
                          case other =>
                            throw GravsearchException(s"The object of rdf:type must be an IRI, but $other was used")
                        }

                        // look up subclasses from ontology cache
                        val superClasses = ontoCache.classToSubclassLookup
                        val knownSubClasses = superClasses
                          .get(baseClassIri.iri)
                          .getOrElse({
                            Set(baseClassIri.iri)
                          })
                          .toSeq

                        // if provided, limit the child classes to those that belong to relevant ontologies
                        val relevantSubClasses = limitInferenceToOntologies match {
                          case None                       => knownSubClasses
                          case Some(relevantOntologyIris) =>
                            // filter the known subclasses against the relevant ontologies
                            knownSubClasses.filter { subClass =>
                              ontoCache.classDefinedInOntology.get(subClass) match {
                                case Some(ontologyOfSubclass) =>
                                  // return true, if the ontology of the subclass is contained in the set of relevant ontologies; false otherwise
                                  relevantOntologyIris.contains(ontologyOfSubclass)
                                case None => false // should never happen
                              }
                            }
                        }

                        // if subclasses are available, create a union statement that searches for either the provided triple (`?v a <classIRI>`)
                        // or triples where the object is a subclass of the provided object (`?v a <subClassIRI>`)
                        // i.e. `{?v a <classIRI>} UNION {?v a <subClassIRI>}`
                        if (relevantSubClasses.length > 1) {
                          Seq(
                            UnionPattern(
                              relevantSubClasses.map(newObject => Seq(statementPattern.copy(obj = IriRef(newObject))))
                            )
                          )
                        } else {
                          // if no subclasses are available, the initial statement can be used.
                          Seq(statementPattern)
                        }
                      } else {
                        // No. Expand using rdfs:subPropertyOf*.

                        // look up subproperties from ontology cache
                        val superProps = ontoCache.superPropertyOfRelations
                        val knownSubProps = superProps
                          .get(predIri)
                          .getOrElse({
                            Set(predIri)
                          })
                          .toSeq

                        // if provided, limit the child properties to those that belong to relevant ontologies
                        val relevantSubProps = limitInferenceToOntologies match {
                          case None => knownSubProps
                          case Some(ontologyIris) =>
                            knownSubProps.filter { subProperty =>
                              // filter the known subproperties against the relevant ontologies
                              ontoCache.propertyDefinedInOntology.get(subProperty) match {
                                case Some(childOntologyIri) =>
                                  // return true, if the ontology of the subproperty is contained in the set of relevant ontologies; false otherwise
                                  ontologyIris.contains(childOntologyIri)
                                case None => false // should never happen
                              }
                            }
                        }

                        // if subproperties are available, create a union statement that searches for either the provided triple (`?a <propertyIRI> ?b`)
                        // or triples where the predicate is a subproperty of the provided object (`?a <subPropertyIRI> ?b`)
                        // i.e. `{?a <propertyIRI> ?b} UNION {?a <subPropertyIRI> ?b}`
                        if (relevantSubProps.length > 1) {
                          Seq(
                            UnionPattern(
                              relevantSubProps.map(newPredicate =>
                                Seq(statementPattern.copy(pred = IriRef(newPredicate)))
                              )
                            )
                          )
                        } else {
                          // if no subproperties are available, the initial statement can be used
                          Seq(statementPattern)
                        }
                      }
                    }
                  }

                case _ =>
                  // The predicate isn't a property IRI, so no expansion needed.
                  ZIO.succeed(Seq(statementPattern))
              }
            } else {
              // Inference is disabled. Just return the statement as is.
              ZIO.succeed(Seq(statementPattern))
            }
        }
    }
  }

  /**
   * Transforms a [[LuceneQueryPattern]] for Fuseki.
   *
   * @param luceneQueryPattern the query pattern.
   * @return Fuseki-specific statements implementing the query.
   */
  def transformLuceneQueryPatternForFuseki(luceneQueryPattern: LuceneQueryPattern): Task[Seq[StatementPattern]] =
    ZIO.attempt(
      Seq(
        StatementPattern(
          subj = luceneQueryPattern.subj, // In Fuseki, an index entry is associated with an entity that has a literal.
          pred = IriRef("http://jena.apache.org/text#query".toSmartIri),
          obj = XsdLiteral(
            value = luceneQueryPattern.queryString.getQueryString,
            datatype = OntologyConstants.Xsd.String.toSmartIri
          )
        )
      )
    )
}

object SparqlTransformerLive {
  val layer: ZLayer[OntologyCache with StringFormatter, Nothing, SparqlTransformerLive] =
    ZLayer.fromFunction(SparqlTransformerLive.apply _)
}
