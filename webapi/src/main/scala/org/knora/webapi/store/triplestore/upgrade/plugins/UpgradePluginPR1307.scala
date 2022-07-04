/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.IRI
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1307.
 */
class UpgradePluginPR1307() extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  // IRI objects representing the IRIs used in this transformation.
  private val rdfTypeIri: IriNode          = nodeFactory.makeIriNode(OntologyConstants.Rdf.Type)
  private val TextValueIri: IriNode        = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.TextValue)
  private val ValueHasStandoffIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasStandoff)
  private val StandoffTagHasStartIndexIri: IriNode =
    nodeFactory.makeIriNode(OntologyConstants.KnoraBase.StandoffTagHasStartIndex)
  private val StandoffTagHasStartParentIri: IriNode =
    nodeFactory.makeIriNode(OntologyConstants.KnoraBase.StandoffTagHasStartParent)
  private val StandoffTagHasEndParentIri: IriNode =
    nodeFactory.makeIriNode(OntologyConstants.KnoraBase.StandoffTagHasEndParent)
  private val ValueHasMaxStandoffStartIndexIri: IriNode =
    nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex)

  /**
   * Represents a standoff tag to be transformed.
   *
   * @param oldIri     the tag's old IRI.
   * @param statements the statements about the tag.
   */
  case class StandoffRdf(oldIri: IriNode, statements: Set[Statement]) {
    def notFound =
      throw InconsistentRepositoryDataException(
        s"$oldIri does not have knora-base:standoffTagHasStartIndex with an integer object"
      )

    /**
     * The value of knora-base:standoffTagHasStartIndex.
     */
    val startIndex: Int = statements.find { statement =>
      statement.subj == oldIri && statement.pred == StandoffTagHasStartIndexIri
    } match {
      case Some(statement: Statement) =>
        statement.obj match {
          case datatypeLiteral: DatatypeLiteral => datatypeLiteral.integerValue(notFound).toInt
          case _                                => notFound
        }

      case None => notFound
    }

    /**
     * The tag's new IRI.
     */
    lazy val newIri: IriNode = {
      val oldSubjStr: String = oldIri.stringValue
      val slashPos: Int      = oldSubjStr.lastIndexOf('/')
      nodeFactory.makeIriNode(oldSubjStr.substring(0, slashPos + 1) + startIndex.toString)
    }

    def transform(model: RdfModel, standoff: Map[IriNode, StandoffRdf]): Unit =
      for (statement: Statement <- statements) {
        // Change statements with knora-base:standoffTagHasStartParent and knora-base:standoffTagHasEndParent to point
        // to the new IRIs of those tags.
        val newStatementObj: RdfNode =
          if (statement.pred == StandoffTagHasStartParentIri || statement.pred == StandoffTagHasEndParentIri) {
            val targetTagOldIri: IriNode = nodeFactory.makeIriNode(statement.obj.stringValue)
            standoff(targetTagOldIri).newIri
          } else {
            statement.obj
          }

        // Remove each statement that uses this tag's old IRI.
        model.removeStatement(statement)

        // Replace it with a statement that uses this tag's new IRI.
        model.add(
          subj = newIri,
          pred = statement.pred,
          obj = newStatementObj,
          context = statement.context
        )
      }
  }

  /**
   * Represents a `knora-base:TextValue` to be transformed.
   *
   * @param iri                        the text value's IRI.
   * @param context                    the text value's context.
   * @param valueHasStandoffStatements the statements whose subject is the text value and whose predicate is
   *                                   `knora-base:valueHasStandoff`.
   * @param standoff                   the standoff tags attached to this text value, as a map of old standoff tag IRIs to
   *                                   [[StandoffRdf]] objects.
   */
  case class TextValueRdf(
    iri: IriNode,
    context: Option[IRI],
    valueHasStandoffStatements: Set[Statement],
    standoff: Map[IriNode, StandoffRdf]
  ) {
    def transform(model: RdfModel): Unit = {
      // Transform the text value's standoff tags.
      for (standoffTag <- standoff.values) {
        standoffTag.transform(model = model, standoff = standoff)
      }

      if (standoff.nonEmpty) {
        for (statement: Statement <- valueHasStandoffStatements) {
          // Replace each statement in valueHasStandoffStatements with one that points to the standoff
          // tag's new IRI.

          val targetTagOldIri: IriNode = nodeFactory.makeIriNode(statement.obj.stringValue)
          val targetTagNewIri: IriNode = standoff(targetTagOldIri).newIri

          model.removeStatement(statement)

          model.add(
            subj = iri,
            pred = statement.pred,
            obj = targetTagNewIri,
            context = statement.context
          )
        }

        // Add a statement to the text value with the predicate knora-base:valueHasMaxStandoffStartIndex.
        model.add(
          subj = iri,
          pred = ValueHasMaxStandoffStartIndexIri,
          obj = nodeFactory
            .makeDatatypeLiteral(standoff.values.map(_.startIndex).max.toString, OntologyConstants.Xsd.Integer),
          context = context
        )
      }
    }
  }

  override def transform(model: RdfModel): Unit =
    for (textValue <- collectTextValues(model)) {
      textValue.transform(model)
    }

  /**
   * Collects the text values and standoff tags in the repository.
   */
  private def collectTextValues(model: RdfModel): Vector[TextValueRdf] = {

    // A map of text value IRIs to their contexts.
    val textValueSubjectsAndContexts: Map[IriNode, Option[IRI]] = model
      .find(
        subj = None,
        pred = Some(rdfTypeIri),
        obj = Some(TextValueIri)
      )
      .map { statement =>
        (nodeFactory.makeIriNode(statement.subj.stringValue), statement.context)
      }
      .toMap

    textValueSubjectsAndContexts.map { case (textValueSubj: IriNode, textValueContext: Option[IRI]) =>
      // Get the statements about the text value.
      val textValueStatements: Set[Statement] = model
        .find(
          subj = Some(textValueSubj),
          pred = None,
          obj = None
        )
        .toSet

      // Get the statements whose subject is the text value and whose predicate is knora-base:valueHasStandoff.
      val valueHasStandoffStatements: Set[Statement] = textValueStatements.filter { statement =>
        statement.pred == ValueHasStandoffIri
      }

      // Get the IRIs of the text value's standoff tags.
      val standoffSubjects: Set[IriNode] = valueHasStandoffStatements.map { statement =>
        statement.obj match {
          case iriNode: IriNode => iriNode
          case other =>
            throw InconsistentRepositoryDataException(
              s"Unexpected object for $textValueSubj $ValueHasStandoffIri: $other"
            )
        }
      }

      // Make a map of standoff IRIs to StandoffRdf objects.
      val standoff: Map[IriNode, StandoffRdf] = standoffSubjects.map { standoffSubj: IriNode =>
        standoffSubj -> StandoffRdf(
          oldIri = standoffSubj,
          statements = model
            .find(
              subj = Some(standoffSubj),
              pred = None,
              obj = None
            )
            .toSet
        )
      }.toMap

      TextValueRdf(
        iri = textValueSubj,
        context = textValueContext,
        valueHasStandoffStatements = valueHasStandoffStatements,
        standoff = standoff
      )
    }.toVector
  }
}
