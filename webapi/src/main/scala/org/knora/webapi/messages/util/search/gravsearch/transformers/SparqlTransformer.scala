/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._

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
            XsdLiteral("false", SmartIri(OntologyConstants.Xsd.Boolean))
          ) =>
        true
      case _ => false
    }

    // Replace the knora-base:isDeleted statements with FILTER NOT EXISTS patterns.
    val filterPatterns: Seq[FilterNotExistsPattern] = isDeletedPatterns.collect {
      case statementPattern: StatementPattern =>
        FilterNotExistsPattern(
          Seq(
            StatementPattern(
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
