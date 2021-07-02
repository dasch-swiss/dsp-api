/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.store.triplestore.upgrade.plugins

import java.util.UUID
import com.typesafe.scalalogging.Logger
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import java.io.PrintWriter
import org.knora.webapi.messages.util.rdf.RdfFormatUtil

/**
  * Transforms a repository for Knora PR 1885.
  */
class UpgradePluginPR1885(featureFactoryConfig: FeatureFactoryConfig, log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)


  private val ResourceHasUUIDIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ResourceHasUUID)
  private val CreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement] = collection.mutable.Set.empty

    /**
     * Changes the IRI of resources to the form rdf.ch/resources/resourecUUID.
     * It also updated all statements in which object is a resource IRI.
     *
     * @param irisDict a map collection of old resource IRI to new ones.
     */
    def changeResourceIri(irisDict: Map[IriNode, IriNode]): Unit = {
      model.map { statement: Statement =>
        val maybeSubject: Option[(IriNode, IriNode)] = irisDict.find { case (oldIri, _) => statement.subj == oldIri }
        val maybeObject: Option[(IriNode, IriNode)] = irisDict.find { case (oldIri, _) => statement.obj == oldIri }
        // Is the subject of the statement an old resource IRI?
        maybeSubject match {
          // Yes.
          case Some((_: IriNode, subjectNewIri: IriNode)) =>
            // Is the object of the statement is also as old resource IRI?
            maybeObject match {
              // Yes. Change both subject and object IRIs to their corresponding new form.
              case Some((_: IriNode, objectNewIri: IriNode)) =>
                statementsToAdd += nodeFactory.makeStatement(
                  subj = subjectNewIri,
                  pred = statement.pred,
                  obj = objectNewIri,
                  context = statement.context
                )
              // No. Change only the subject IRI to its corresponding new form.
              case _ =>
                statementsToAdd += nodeFactory.makeStatement(
                  subj = subjectNewIri,
                  pred = statement.pred,
                  obj = statement.obj,
                  context = statement.context
                )
            }
            // the statement containing old IRI can be removed.
            statementsToRemove += statement

          // No. The subject of statement does not contain an old IRI, check its object.
          case None =>
            // Is the object of the statement an old IRI?
            maybeObject match {
              // Yes. Change only the object IRI to its corresponding new form.
              case Some((_: IriNode, objectNewIri: IriNode)) =>
                statementsToAdd += nodeFactory.makeStatement(
                  subj = statement.subj,
                  pred = statement.pred,
                  obj = objectNewIri,
                  context = statement.context
                )
                // the statement containing old IRI can be removed.
                statementsToRemove += statement
              // No. Do nothing.
              case _ => Nil
            }
        }
      }
    }

    val resourcesWithOldIris = collectResourceIris(model)
    val irisDict: collection.mutable.Map[IriNode, IriNode] = collection.mutable.Map.empty
    resourcesWithOldIris.foreach { oldIri =>
      val resourceUUID: UUID = stringFormatter.getUUIDFromIriOrMakeRandom(oldIri.iri)
      val newResourceIri = stringFormatter.makeResourceIri(resourceUUID)

      // Add UUID to each resource.
      val newIri = nodeFactory.makeIriNode(newResourceIri)
      model.add(
        subj = newIri,
        pred = ResourceHasUUIDIri,
        obj = nodeFactory.makeStringLiteral(stringFormatter.base64EncodeUuid(resourceUUID))
      )
      if (oldIri.iri != newResourceIri) {
        log.warn(s"Changed resource IRI from <${oldIri.iri}> to <$newResourceIri>")
        irisDict(oldIri) =  newIri
      }
    }

    changeResourceIri(irisDict.toMap)
    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)

//    val formatted = rdfFormatUtil.format(rdfModel = model, rdfFormat = Turtle)
//    new PrintWriter("/tmp/incunabula-data.ttl") {
//      write(formatted); close
//    }
  }


  /**
    * Collects the IRIs of all resources.
    */
  private def collectResourceIris(model: RdfModel): Iterator[IriNode] = {
    model
      .find(None, Some(CreationDateIri), None)
      .map(_.subj)
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $CreationDateIri: $other")
      }
  }

}
