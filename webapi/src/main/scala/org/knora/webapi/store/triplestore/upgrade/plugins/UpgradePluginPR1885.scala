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
    val resourceIrisDict: collection.mutable.Map[IriNode, IriNode] = collection.mutable.Map.empty
    val valueIrisDict: collection.mutable.Map[IRI, IRI] = collection.mutable.Map.empty

    /**
      * Change the current IRI given as subject or object of the statement to the new format, if it is an old resource
      * or value IRI. Otherwise, return the current IRI.
      *
      * @param maybeResourceIri       IRI maybe be a resource IRI.
      * @param maybeValueIri          IRI maybe a value IRI.
      * @param currIri                current IRI.
      * @return updated IRI.
      */
    def updateIri(maybeResourceIri: Option[(IriNode, IriNode)],
                  maybeValueIri: Option[(IriNode, IriNode)],
                  currIri: IriNode): IriNode = {
      maybeResourceIri match {
        case Some((_: IriNode, newResourceIri: IriNode)) =>
          newResourceIri
        case None =>
          maybeValueIri match {
            case Some((oldResourceIri: IriNode, newResourceIri: IriNode)) =>
              val oldValueIri: IRI = currIri.iri
              val newValueIri: IRI = oldValueIri.replace(oldResourceIri.iri, newResourceIri.iri)
              valueIrisDict(oldValueIri) = newValueIri
              nodeFactory.makeIriNode(iri = newValueIri)
            case None => currIri
          }
      }
    }

    /**
      * Changes the IRI of resources to the form rdf.ch/resources/resourecUUID.
      * It also updated all statements in which object is a resource IRI.
      *
      * @param irisDict a map collection of old resource IRI to new ones.
      */
    def updateResourceValueIris(irisDict: Map[IriNode, IriNode]): Unit = {
      model.map { statement: Statement =>
        /* Check and update the subject of the statement if it is either an old resource IRI or a value IRI */
        // Is the subject of the statement an old resource IRI?
        val resourceAsSubject: Option[(IriNode, IriNode)] = irisDict.find {
          case (oldIri, _) => statement.subj == oldIri
        }

        // Is the subject of the statement a value IRI starting with an old resource Iri?
        val valueAsSubject: Option[(IriNode, IriNode)] = irisDict.find {
          case (oldIri, _) =>
            statement.subj.isInstanceOf[IriNode] &&
              statement.subj.asInstanceOf[IriNode].iri.startsWith(oldIri.iri + "/values/")
        }

        // Is subject of the statement an IRI?
        val updatedSubject: RdfResource = statement.subj.isInstanceOf[IriNode] match {
          // Yes. Update the subject of the statement if it is an old resource or value IRI
          case true => updateIri(resourceAsSubject, valueAsSubject, statement.subj.asInstanceOf[IriNode])
          // No. Don't do anything.
          case false => statement.subj
        }

        /* Check and update the object of the statement if it is either an old resource IRI or a value IRI */
        // Is object of the statement an old resource IRI?
        val resourceAsObject: Option[(IriNode, IriNode)] = irisDict.find { case (oldIri, _) => statement.obj == oldIri }

        // Is object of the statement a value IRI starting with an old resource Iri?
        val valueAsObject: Option[(IriNode, IriNode)] = irisDict.find {
          case (oldIri, _) =>
            statement.obj.isInstanceOf[IriNode] &&
              statement.obj.asInstanceOf[IriNode].iri.startsWith(oldIri.iri + "/values/")
        }

        // Is object of the statement an IRI?
        val updatedObject: RdfNode = statement.obj.isInstanceOf[IriNode] match {
          //Yes. Update the object of the statement if it is an old resource or value IRI
          case true => updateIri(resourceAsObject, valueAsObject, statement.obj.asInstanceOf[IriNode])
          // No. Don't do anything.
          case false => statement.obj
        }

        // update the model only if either of the subject or object of the statement are changed.
        if (updatedObject != statement.obj || updatedSubject != statement.subj) {
          statementsToAdd += nodeFactory.makeStatement(
            subj = updatedSubject,
            pred = statement.pred,
            obj = updatedObject,
            context = statement.context
          )
          statementsToRemove += statement
        }
      }
    }

    val resourcesWithOldIris: Iterator[IriNode] = collectResourceIris(model)
    resourcesWithOldIris.foreach { oldIri =>
      val newIri: IriNode = hasResourceUUIDProperty(model, oldIri) match {
        case Some(givenUUID: DatatypeLiteral) =>
          val resourceUUID: UUID = stringFormatter.base64DecodeUuid(givenUUID.value)
          nodeFactory.makeIriNode(stringFormatter.makeResourceIri(Some(resourceUUID)))
        case None =>
          val resourceUUID: UUID = stringFormatter.getUUIDFromIriOrMakeRandom(oldIri.iri)
          val newResourceIri = nodeFactory.makeIriNode(stringFormatter.makeResourceIri(Some(resourceUUID)))
          // Add UUID to each resource.
          model.add(
            subj = newResourceIri,
            pred = ResourceHasUUIDIri,
            obj = nodeFactory.makeStringLiteral(stringFormatter.base64EncodeUuid(resourceUUID))
          )
          newResourceIri
      }

      if (oldIri.iri != newIri.iri) {
        log.warn(s"Changed resource IRI from <${oldIri.iri}> to <${newIri.iri}>")
        resourceIrisDict(oldIri) = newIri
      }
    }

    updateResourceValueIris(resourceIrisDict.toMap)
    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)

    valueIrisDict.foreach {
      case (oldValueIRI, newValueIri) =>
        log.warn(s"Changed value IRI from <$oldValueIRI> to <$newValueIri>")
    }

    val formatted = rdfFormatUtil.format(rdfModel = model, rdfFormat = Turtle)
    new PrintWriter("/tmp/gravsearchtest1-data.ttl") {
      write(formatted); close
    }
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

  /**
    * Check if the resource already has a UUID. If so, return it.
    */
  private def hasResourceUUIDProperty(model: RdfModel, subj: IriNode): Option[DatatypeLiteral] = {
    val resourceUUIDStatements: Set[Statement] = model.find(Some(subj), Some(ResourceHasUUIDIri), None).toSet
    resourceUUIDStatements.headOption match {
      case Some(statement: Statement) =>
        statement.obj match {
          case datatypeLiteral: DatatypeLiteral =>
            Some(datatypeLiteral)
          case other => throw InconsistentRepositoryDataException(s"Unexpected value for $ResourceHasUUIDIri: $other")
        }
      case None => None
    }
  }

}
