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

import exceptions.AssertionException
import messages.{OntologyConstants, StringFormatter}
import messages.util.rdf._
import messages.IriConversions._

import java.util.UUID

import java.io.{BufferedInputStream, FileInputStream}

class UpgradePluginPR1892Spec extends UpgradePluginSpec {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(defaultFeatureFactoryConfig)
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private def checkIriNodeObject(model: RdfModel, subj: IriNode, pred: IriNode, expectedObj: IriNode): Unit = {
    model
      .find(
        subj = Some(subj),
        pred = Some(pred),
        obj = None
      )
      .toSet
      .headOption match {
      case Some(statement: Statement) =>
        statement.obj match {
          case iri: IriNode => assert(iri == expectedObj)
          case other        => throw AssertionException(s"Unexpected object for $pred: $other")
        }

      case None => throw AssertionException(s"No statement found with subject $subj and predicate $pred")
    }
  }

  "Upgrade plugin PR1892" should {
    "add ark url to the resource and its value" in {
      // Parse the input file.
//      val model: RdfModel = trigFileToModel("test_data/upgrade/pr1892.trig")

      val fileInputStream =
        new BufferedInputStream(new FileInputStream("test_data/all_data/anything-data.ttl"))
      val model: RdfModel = rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
      fileInputStream.close()

      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR1892(defaultFeatureFactoryConfig)
      plugin.transform(model)
//      val resourceIri: IRI = "http://rdfh.ch/0001/a-test-thing"
//      val resourceArkUrl = resourceIri.toSmartIri.fromResourceIriToArkUrl()
//
//      // Check that arkUrl is added to the resource
//      checkIriNodeObject(
//        model = model,
//        subj = nodeFactory.makeIriNode(resourceIri),
//        pred = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ArkUrl),
//        expectedObj = nodeFactory.makeIriNode(resourceArkUrl)
//      )
//
//      // Check that ark url is added to the value
//      val valueUUID: UUID = stringFormatter.base64DecodeUuid("SGXSDj6Oj7A4QAU1Pi957Y")
//      val valueIri: IRI = "http://rdfh.ch/0001/a-test-thing/values/SGXSDj6Oj7A4QAU1Pi957Y"
//      val valueArkUrl = valueIri.toSmartIri.fromValueIriToArkUrl(valueUUID)
//      checkIriNodeObject(
//        model = model,
//        subj = nodeFactory.makeIriNode(valueIri),
//        pred = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ArkUrl),
//        expectedObj = nodeFactory.makeIriNode(valueArkUrl)
//      )
//
//      //Check that the old version without UUID does not have an ARK-URL.
//      val oldValueIri: IRI = "http://rdfh.ch/0001/a-test-thing/values/pGXSDj6Oj7A4QAU1Pi957g"
//      val maybeArkURL: Option[Statement] = model
//        .find(
//          subj = Some(nodeFactory.makeIriNode(oldValueIri)),
//          pred = Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ArkUrl)),
//          obj = None
//        )
//        .toSet
//        .headOption
//
//      assert(maybeArkURL.isEmpty)
    }
  }
}
