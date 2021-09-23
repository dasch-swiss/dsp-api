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
    s"""@base <http://ns.dasch.swiss/repository#> .
       |@prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
       |@prefix prov: <http://www.w3.org/ns/prov#> .
       |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix schema: <https://schema.org/> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |
       |<082D-dataset-001> a <Dataset> ;
       |    <hasAbstract> "Smart technologies transform farming today. Think of autonomous tractors and weeding robots, animals and underground infrastructures with inbuilt sensors, and drones or satellites offering image analysis from the air. There are many opportunities arising from this evolution in terms of increased productivity, profitability, and sustainability. Critical issues, in turn, include techno-dependency, vulnerability and privacy. This project contributes empirical evidence to the discussion of the opportunities and risks associated with smart farming. It starts from the assumption that smart farming – i.e. the management of agricultural practices and processes through techniques of data gathering, transfer and analysis – is never neutral, but shaped by all kinds of decisions and judgements built into the systems from their very elaboration. The project thus studies ‘what lies behind’ novel smart-farming solutions, looking at where, by whom and how they are produced and subsequently diffused as exemplars to follow. It does so through the investigation of two high-profile initiatives in Switzerland: Swiss Future Farm: An experimental and demonstration farm, set up on Agroscope’s test site in Tänikon (TG) as a joint public–private initiative, to be inaugurated on 21 September 2018. AgroFly’s (now with Aero41) sprayer-drone project (Sierre, VS): The first authorized drone system in Europe for the automated application of pesticides. Test sites are distributed in Switzerland and beyond. This project claims that specific initiatives, anchored in specific sites of experimentation, play a fundamental role in assembling the actor networks that carry forward the evolutions around smart farming. Grounded in Actor Network Theory, the project unpacks the chain of mediations through which relevant actors, ideas and things connect and interact in the co-production of the studied initiatives, and investigates the relationships and mechanisms that tie them to each other and to other sites and initiatives. There are four interrelated research objectives that run through this investigation, relating to the collaborative, expertise-related, discursive, and spatial dimensions of learning and exemplification: to analyse the practices of collaboration through which novel smart-farming solutions are developed and stabilized for more normalized use through the studied sites of experimentation. to examine what and how specific forms of expertise become authorized to act in the different stages and chains of mediation associated with the studied sites. to investigate what and how specific discourses, expectations and beliefs surrounding smart technologies crystallize in and emerge from the studied initiatives. to explore the forces and mechanisms through which the studied sites are tied to other places and projects in the circuits of learning and exemplification that shape the field of smart farming. To pursue these objectives, the project follows three methodological pathways: textual analysis of literature and reports, non-participant observation, and semi-structured interviews. This approach is exploratory in ambition and scope and leads to further, follow-up research proposals on Big Data in agriculture. On these grounds, the project studies, informs and debates innovation in the agricultural sector. It highlights the factors that favour or hinder technology innovation, diffusion and adaptation, and fosters greater understanding about the desirability or non-desirability of this. User beneficiaries include farmers and farming associations, technology  "^^xsd:string ;
       |    <hasConditionsOfAccess> "Restricted"^^xsd:string ;
       |    <hasHowToCite> "Klauser FR and Pauschinger D (2020) Big Data in Agriculture: The Making of Smart Farms, SNF Project Data Set."^^xsd:string ;
       |    <hasLanguage> "English"^^xsd:string,
       |        "French"^^xsd:string,
       |        "German"^^xsd:string ;
       |    <hasLicense> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "creativecommons.org" ] ;
       |            schema:url "https://creativecommons.org/licenses/" ] ;
       |    <hasQualifiedAttribution> [ a prov:Attribution ;
       |            <hasRole> "Senior Research Fellow"^^xsd:string ;
       |            prov:agent <082D-person-002> ],
       |        [ a prov:Attribution ;
       |            <hasRole> "PI"^^xsd:string ;
       |            prov:agent <082D-person-001> ] ;
       |    <hasStatus> "Finished"^^xsd:string ;
       |    <hasTitle> "Dataset of Big Data in Agriculture: The Making of Smart Farms"^^xsd:string ;
       |    <hasTypeOfData> "Text"^^xsd:string ;
       |    <isPartOf> <082D-project> .
       |
       |<DMP> a <DataManagementPlan> ;
       |    <isAvailable> true .
       |
       |<082D-grant-001> a <Grant> ;
       |    <hasFunder> <082D-organization-001> ;
       |    <hasName> "Digital Lives"^^xsd:string ;
       |    <hasNumber> "FN 10DL1A_183037"^^xsd:string ;
       |    <hasURL> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "snf.ch" ] ;
       |            schema:url "http://www.snf.ch/de/foerderung/projekte/digital-lives/Seiten/default.aspx" ] .
       |
       |<082D-person-002> a <Person> ;
       |    <hasAddress> [ a schema:PostalAddress ;
       |            schema:addressLocality "Neuchâtel"^^xsd:string ;
       |            schema:postalCode "2000"^^xsd:string ;
       |            schema:streetAddress "Espace Tilo-Frey 1"^^xsd:string ] ;
       |    <hasEmail> "dennis.pauschinger@unine.ch" ;
       |    <hasFamilyName> "Pauschinger"^^xsd:string ;
       |    <hasGivenName> ( "Dennis"^^xsd:string ) ;
       |    <hasJobTitle> "Postdoc "^^xsd:string ;
       |    <isMemberOf> <082D-organization-002> .
       |
       |<082D-project> a <Project> ;
       |    <hasContactPoint> <082D-person-001> ;
       |    <hasDataManagementPlan> <DMP> ;
       |    <hasDescription> \"\"\"Smart technologies transform farming today. Think of autonomous tractors and weeding robots, animals and underground infrastructures with inbuilt sensors, and drones or satellites offering image analysis from the air. There are many opportunities arising from this evolution in terms of increased productivity, profitability, and sustainability. Critical issues, in turn, include techno-dependency, vulnerability and privacy.
       |This project contributes empirical evidence to the discussion of the opportunities and risks associated with smart farming. It starts from the assumption that smart farming – i.e. the management of agricultural practices and processes through techniques of data gathering, transfer and analysis – is never neutral, but shaped by all kinds of decisions and judgements built into the systems from their very elaboration. The project thus studies ‘what lies behind’ novel smart-farming solutions, looking at where, by whom and how they are produced and subsequently diffused as exemplars to follow. It does so through the investigation of two high-profile initiatives in Switzerland:
       |Swiss Future Farm: An experimental and demonstration farm, set up on Agroscope’s test site in Tänikon (TG) as a joint public–private initiative, to be inaugurated on 21 September 2018.
       |AgroFly’s (now with Aero41) sprayer-drone project (Sierre, VS): The first authorized drone system in Europe for the automated application of pesticides. Test sites are distributed in Switzerland and beyond.
       |This project claims that specific initiatives, anchored in specific sites of experimentation, play a fundamental role in assembling the actor networks that carry forward the evolutions around smart farming. Grounded in Actor Network Theory, the project unpacks the chain of mediations through which relevant actors, ideas and things connect and interact in the co-production of the studied initiatives, and investigates the relationships and mechanisms that tie them to each other and to other sites and initiatives. There are four interrelated research objectives that run through this investigation, relating to the collaborative, expertise-related, discursive, and spatial dimensions of learning and exemplification:
       |to analyse the practices of collaboration through which novel smart-farming solutions are developed and stabilized for more normalized use through the studied sites of experimentation.
       |to examine what and how specific forms of expertise become authorized to act in the different stages and chains of mediation associated with the studied sites.
       |to investigate what and how specific discourses, expectations and beliefs surrounding smart technologies crystallize in and emerge from the studied initiatives.
       |to explore the forces and mechanisms through which the studied sites are tied to other places and projects in the circuits of learning and exemplification that shape the field of smart farming.
       |To pursue these objectives, the project follows three methodological pathways: textual analysis of literature and reports, non-participant observation, and semi-structured interviews. This approach is exploratory in ambition and scope and leads to further, follow-up research proposals on Big Data in agriculture. On these grounds, the project studies, informs and debates innovation in the agricultural sector. It highlights the factors that favour or hinder technology innovation, diffusion and adaptation, and fosters greater understanding about the desirability or non-desirability of this. User beneficiaries include farmers and farming associations, technology \"\"\"^^xsd:string ;
       |    <hasDiscipline> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "SKOS UNESCO Nomenclature" ] ;
       |            schema:url "http://skos.um.es/unesco6/540305" ] ;
       |    <hasEndDate> "2020-06-30"^^xsd:date ;
       |    <hasFunder> <082D-organization-001> ;
       |    <hasGrant> <082D-grant-001> ;
       |    <hasKeywords> "Agriculture"^^xsd:string ;
       |    <hasName> "Big Data in Agriculture: The Making of Smart Famrs"^^xsd:string ;
       |    <hasPublication> "Klauser, FR.: Surveillance Farms: Towards a Research Agenda on Big Data in Agriculture, Surveillance & Society, 16(3), 370-378, https://doi.org/10.24908/ss.v16i3.12594, 2020. "^^xsd:string,
       |        "Pauschinger, D. and Klauser, F.: Räume des Experimentierens: Die Einführung von Sprühdrohnen in der digitalen Landwirtschaft, Geogr. Helv., 75, 325–336, https://doi.org/10.5194/gh-75-325-2020, 2020. "^^xsd:string ;
       |    <hasShortcode> "082D"^^xsd:string ;
       |    <hasSpatialCoverage> [ a schema:Place ;
       |            schema:url [ a schema:URL ;
       |                    schema:propertyID [ a schema:PropertyValue ;
       |                            schema:propertyID "Geonames" ;
       |                            schema:url "https://www.geonames.org/2658434/switzerland.html" ] ] ] ;
       |    <hasStartDate> "2018-12-01"^^xsd:date ;
       |    <hasTemporalCoverage> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "dainst.org" ] ;
       |            schema:url "https://chronontology.dainst.org/period/8bGPuf5syqCD" ] ;
       |    <hasURL> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "dasch.swiss" ] ;
       |            schema:url "https://test.dasch.swiss" ] .
       |
       |<082D-organization-001> a <Organization> ;
       |    <hasName> "Swiss National Science Foundation"^^xsd:string .
       |
       |<082D-organization-002> a <Organization> ;
       |    <hasAddress> [ a schema:PostalAddress ;
       |            schema:addressLocality "Neuchâtel"^^xsd:string ;
       |            schema:postalCode "2000"^^xsd:string ;
       |            schema:streetAddress "Espace Tilo-Frey 1"^^xsd:string ] ;
       |    <hasEmail> "Secretariat.Geographie@unine.ch " ;
       |    <hasName> "Université de Neuchâtel, Faculté des lettres et sciences humaines"^^xsd:string ;
       |    <hasURL> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "unine.ch" ] ;
       |            schema:url "http://www.unine.ch/geographie/home.html" ] .
       |
       |<082D-person-001> a <Person> ;
       |    <hasAddress> [ a schema:PostalAddress ;
       |            schema:addressLocality "Neuchâtel"^^xsd:string ;
       |            schema:postalCode "2000"^^xsd:string ;
       |            schema:streetAddress "Espace Tilo-Frey 1"^^xsd:string ] ;
       |    <hasEmail> "francisco.klauser@unine.ch" ;
       |    <hasFamilyName> "Klauser"^^xsd:string ;
       |    <hasGivenName> ( "Francisco R."^^xsd:string ) ;
       |    <hasJobTitle> "Full professor of political geography"^^xsd:string ;
       |    <isMemberOf> <082D-organization-002> ;
       |    <sameAs> [ a schema:URL ;
       |            schema:propertyID [ a schema:PropertyValue ;
       |                    schema:propertyID "orcid.org" ] ;
       |            schema:url "https://orcid.org/0000-0003-3383-3570." ] .
       |
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
