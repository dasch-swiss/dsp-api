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

package org.knora.webapi.responders.v2

import java.util.UUID

import akka.testkit.ImplicitSender
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.metadatamessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.duration._

/**
  * Tests [[MetadataResponderV2]].
  */
class MetadataResponderV2Spec extends CoreSpec() with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)

  // The default timeout for receiving reply messages from actors.
  private val timeout = 10.seconds

  private val metadataContent =
    s"""
           |@prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
           |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
           |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
           |@prefix owl: <http://www.w3.org/2002/07/owl#> .
           |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
           |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
           |@prefix xml: <http://www.w3.org/XML/1998/namespace> .
           |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
           |@prefix foaf:<http://xmlns.com/foaf/0.1/> .
           |@prefix vowl: <http://purl.org/vowl/spec/v2/> .
           |@prefix prov: <http://www.w3.org/ns/prov#> .
           |@prefix dc: <http://purl.org/dc/elements/1.1/> .
           |@prefix dct: <http://purl.org/dc/terms/> .
           |@prefix locn: <http://www.w3.org/ns/locn#> .
           |@prefix vcard: <http://www.w3.org/2006/vcard/ns#> .
           |@prefix schema: <https://schema.org/>.
           |@prefix skos: <http://www.w3.org/2004/02/skos/core#> .
           |@prefix unesco6: <http://skos.um.es/unesco6/> .
           |@base <http://ns.dasch.swiss/repository#> .
           |
           |<beol> rdf:type dsp-repo:Project .
           |<beol> dsp-repo:hasName "Bernoulli-Euler Online (BEOL)" .
           |<beol> dsp-repo:hasDescription "The project Bernoulli-Euler Online (BEOL) integrates the two edition projects Basler Edition der Bernoulli-Briefwechsel (BEBB) and Leonhardi Euleri Opera Omnia (LEOO) into one digital platform available on the web. In addition, Jacob Bernoulli's scientific notebook Meditationes - a document of outstanding significance for the history of mathematics at its turning point around 1700 - is published for the first time in its entirety on the BEOL platform as a region-based multilayer interactive digital edition providing access to facsimiles, transcriptions, translations, indices, and commentaries. Besides being an edition platform, BEOL is a virtual research environment for the study of early modern mathematics and science as a data graph using sophisticated analysis tools. Currently BEOL is connected to two third-party repositories: The Newton Project and the Briefportal Leibniz, initiating the formation of a network of digital editions of the early modern scientific correspondence data.The goal of BEOL is thus twofold: it focuses on the mathematics influenced by the Bernoulli dynasty and Leonhard Euler and undertakes a methodological effort to present these materials to the public and researchers in a highly functional way." .
           |<beol> dsp-repo:hasKeywords "mathematics" .
           |<beol> dsp-repo:hasKeywords "science" .
           |<beol> dsp-repo:hasKeywords "history of science" .
           |<beol> dsp-repo:hasKeywords "history of mathematics" .
           |<beol> dsp-repo:hasKeywords "Bernoulli" .
           |<beol> dsp-repo:hasKeywords "Euler" .
           |<beol> dsp-repo:hasKeywords "Newton" .
           |<beol> dsp-repo:hasKeywords "Leibniz" .
           |<beol> dsp-repo:hasCategories "mathematics" .
           |<beol> dsp-repo:hasStartDate "2016.07" .
           |<beol> dsp-repo:hasEndDate "2020.01" .
           |<beol> dsp-repo:hasTemporalCoverage "17th century and 18th century CE" .
           |<beol> dsp-repo:hasSpatialCoverage "Europe" .
           |<beol> dsp-repo:hasSpatialCoverage "Russia" .
           |<beol> dsp-repo:hasSpatialCoverage "France" .
           |<beol> dsp-repo:hasSpatialCoverage "Switzerland" .
           |<beol> dsp-repo:hasSpatialCoverage "Germany" .
           |<beol> dsp-repo:hasSpatialCoverage "Italy" .
           |<beol> dsp-repo:hasSpatialCoverage "England" .
           |<beol> dsp-repo:hasFunder "Schweizerischer Nationalfonds (SNSF)" .
           |<beol> dsp-repo:hasURL "https://beol.dasch.swiss/" .
           |<beol> dsp-repo:hasDateCreated "09.2017" .
           |<beol> dsp-repo:hasDateModified "04.2020" .
           |<beol> dsp-repo:hasShortcode "0801" .
           |<beol> dsp-repo:hasAlternateName "beol" .
           |""".stripMargin

  // Parse the request to an RdfModel.
  private val requestModel: RdfModel = rdfFormatUtil.parseToRdfModel(
    rdfStr = metadataContent,
    rdfFormat = Turtle
  )

  "The metadata responder v2" should {

    "save a metadata graph in the triplestore" in {
      responderManager ! MetadataPutRequestV2(
        rdfModel = requestModel,
        projectADM = SharedTestDataADM.beolProject,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.beolUser,
        apiRequestID = UUID.randomUUID
      )

      val response = expectMsgType[SuccessResponseV2](timeout)
      assert(response.message.contains(s"<${SharedTestDataADM.beolProject.id}>"))
    }

    "get the metadata graph of a project" in {
      responderManager ! MetadataGetRequestV2(
        projectADM = SharedTestDataADM.beolProject,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.beolUser
      )

      val response = expectMsgType[MetadataGetResponseV2](timeout)

      val receivedModel: RdfModel = rdfFormatUtil.parseToRdfModel(
        rdfStr = response.turtle,
        rdfFormat = Turtle
      )

      assert(receivedModel.isIsomorphicWith(requestModel))
    }
  }
}
