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

package org.knora.webapi
package store.triplestore.upgrade.plugins
import messages.IriConversions._
import messages.{OntologyConstants, StringFormatter}
import messages.util.rdf._
import feature.FeatureFactoryConfig
import exceptions.{BadRequestException, InconsistentRepositoryDataException}
import store.triplestore.upgrade.UpgradePlugin

import java.util.UUID
import java.io.PrintWriter

/**
  * Transforms a repository for Knora PR 1892.
  */
class UpgradePluginPR1892(featureFactoryConfig: FeatureFactoryConfig) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val arkUrlIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ArkUrl)
  private val resourceCreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.CreationDate)
  private val valueCreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueCreationDate)
  private val valueHasUUIDIri = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasUUID)

  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)

  override def transform(model: RdfModel): Unit = {

    val resourceIris: Iterator[IriNode] = collectResourceIris(model)

    resourceIris.foreach { iriNode =>
      val resourceArkIri: IRI = iriNode.iri.toSmartIri.fromResourceIriToArkUrl()
      model.add(
        subj = iriNode,
        pred = arkUrlIri,
        obj = nodeFactory.makeIriNode(iri = resourceArkIri)
      )
    }

    val valueIris: Iterator[IriNode] = collectValueIris(model)
    valueIris.foreach { iriNode =>
      // find stored UUID of the value.
      val maybeValueUUID: Option[DatatypeLiteral] = getValueUUIDProperty(model, iriNode)

      // Does value have a UUID stored?
      maybeValueUUID match {
        // Yes. decode the stored string value to base64 UUID and return that.
        case Some(uuidLiteral: DatatypeLiteral) =>
          val foundUUID =
            stringFormatter.decodeUuidWithErr(uuidLiteral.value,
                                              throw BadRequestException(s"${uuidLiteral.value} is not a base64 uuid"))

          val valueArkUrl: IRI = iriNode.iri.toSmartIri.fromValueIriToArkUrl(foundUUID)
          model.add(
            subj = iriNode,
            pred = arkUrlIri,
            obj = nodeFactory.makeIriNode(iri = valueArkUrl)
          )
        case None => Nil
        // No. don't do anything
      }

    }

    val formatted = rdfFormatUtil.format(rdfModel = model, rdfFormat = Turtle)
    new PrintWriter("/tmp/anything-data.ttl") {
      write(formatted); close
    }
  }

  /**
    * Collects the IRIs of all resources.
    */
  private def collectResourceIris(model: RdfModel): Iterator[IriNode] = {
    model
      .find(None, Some(resourceCreationDateIri), None)
      .map(_.subj)
      .map {
        case iriNode: IriNode => iriNode
        case other =>
          throw InconsistentRepositoryDataException(s"Unexpected subject for $resourceCreationDateIri: $other")
      }
  }

  /**
    * Collects the IRIs of all values.
    */
  private def collectValueIris(model: RdfModel): Iterator[IriNode] = {
    model
      .find(None, Some(valueCreationDateIri), None)
      .map(_.subj)
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $valueCreationDateIri: $other")
      }
  }

  /**
    * Check if the value already has a UUID. If so, return it.
    */
  private def getValueUUIDProperty(model: RdfModel, valueIri: IriNode): Option[DatatypeLiteral] = {
    val valueUUIDStatements: Set[Statement] = model.find(Some(valueIri), Some(valueHasUUIDIri), None).toSet
    valueUUIDStatements.headOption match {
      case Some(statement: Statement) =>
        statement.obj match {
          case datatypeLiteral: DatatypeLiteral =>
            Some(datatypeLiteral)
          case other => throw InconsistentRepositoryDataException(s"Unexpected value for $valueHasUUIDIri: $other")
        }
      case None => None

    }
  }
}
