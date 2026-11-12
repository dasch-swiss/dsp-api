/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.nio.file.Paths

import dsp.errors.InconsistentRepositoryDataException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.ConstructResponseRdfData.MappingAndXSLTransformation
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.JsonLDValue
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.valuemessages.ReadTextValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType
import org.knora.webapi.responders.v2.ResourcesResponderV2SpecFullData
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.model.Project
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.util.FileUtil

@RunWith(classOf[DspZTestJUnitRunner])
class ConstructResponseUtilV2Spec extends E2EZSpec {

  private val constructResponseUtilV2ZIO = ZIO.serviceWithZIO[ConstructResponseUtilV2]

  private val thingIri     = "http://rdfh.ch/0001/a-thing-with-text-values"
  private val textValueIri = s"$thingIri/values/1"
  private val storedXml    = "<text>Ich <strong>liebe</strong> die Dinge.</text>"

  private def textValueTurtle(
    hasStoredXml: Boolean,
    hasMapping: Boolean,
    standoffNodeIris: Seq[String],
    withRootNodeData: Boolean,
  ): String = {
    val optional = Seq(
      Option.when(hasStoredXml)(s"""knora-base:valueHasXml "$storedXml";"""),
      Option.when(hasMapping)("knora-base:valueHasMapping <http://rdfh.ch/standoff/mappings/StandardMapping>;"),
      Option.when(standoffNodeIris.nonEmpty)(
        s"knora-base:valueHasStandoff ${standoffNodeIris.map(i => s"<$i>").mkString(", ")};",
      ),
    ).flatten.mkString("\n  ")
    val rootNode =
      if (withRootNodeData)
        s"""<$textValueIri/standoff/0> a <http://www.knora.org/ontology/standoff#StandoffRootTag>;
           |  knora-base:standoffTagHasEnd "45"^^xsd:int;
           |  knora-base:standoffTagHasStart "0"^^xsd:int;
           |  knora-base:standoffTagHasStartIndex 0;
           |  knora-base:standoffTagHasUUID "2e136103-2a4b-4e59-ac8f-79a53f54b496" .""".stripMargin
      else ""
    s"""@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
       |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |
       |<$thingIri> a <http://www.knora.org/ontology/0001/anything#Thing>, knora-base:Resource;
       |  <http://www.knora.org/ontology/0001/anything#hasText> <$textValueIri>;
       |  knora-base:attachedToProject <http://rdfh.ch/projects/0001>;
       |  knora-base:attachedToUser <http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q>;
       |  knora-base:creationDate "2016-03-02T15:05:10Z"^^xsd:dateTime;
       |  knora-base:hasPermissions "CR knora-admin:Creator|V knora-admin:ProjectMember";
       |  knora-base:hasValue <$textValueIri>;
       |  knora-base:isDeleted false;
       |  knora-base:isMainResource true;
       |  rdfs:label "A thing" .
       |
       |<$textValueIri> a knora-base:TextValue;
       |  knora-base:attachedToUser <http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q>;
       |  knora-base:hasPermissions "CR knora-admin:Creator";
       |  knora-base:isDeleted false;
       |  knora-base:valueCreationDate "2016-03-02T15:05:54Z"^^xsd:dateTime;
       |  knora-base:valueHasOrder 0;
       |  $optional
       |  knora-base:valueHasString "Ich liebe die Dinge.";
       |  knora-base:valueHasUUID "1" .
       |
       |$rootNode
       |""".stripMargin
  }

  private val identityXslt =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
      |  <xsl:template match="/text"><div class="served"><xsl:value-of select="."/></div></xsl:template>
      |</xsl:stylesheet>""".stripMargin

  private val standardMappingWithXslt: Map[StandoffMappingIri, MappingAndXSLTransformation] =
    Map(
      StandoffMappingIri.StandardMapping -> MappingAndXSLTransformation(
        mapping = MappingXMLtoStandoff(namespace = Map.empty, defaultXSLTransformation = None),
        standoffEntities = StandoffEntityInfoGetResponseV2(Map.empty, Map.empty),
        XSLTransformation = Some(identityXslt),
      ),
    )

  private val servedProject = Project(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("shortname"),
    Shortcode.unsafeFrom("0001"),
    None,
    Seq(StringLiteralV2.from("Some description")),
    List.empty,
    None,
    Seq.empty,
    SelfJoin.CannotJoin,
    Set.empty,
    Set.empty,
  )

  private def readTextValue(
    turtle: String,
    standoff: StandoffSelection,
    mappings: Map[StandoffMappingIri, MappingAndXSLTransformation] = Map.empty,
  ) =
    for {
      response <- SparqlExtendedConstructResponse.make(turtle)
      rdfData  <- constructResponseUtilV2ZIO(_.splitMainResourcesAndValueRdfData(response, anythingAdminUser))
      sequence <- constructResponseUtilV2ZIO(
                    _.createApiResponse(
                      mainResourcesAndValueRdfData = rdfData,
                      orderByResourceIri = Seq(thingIri),
                      pageSizeBeforeFiltering = 1,
                      mappings = mappings,
                      standoffSelection = standoff,
                      versionDate = None,
                      calculateMayHaveMoreResults = false,
                      targetSchema = ApiV2Complex,
                      requestingUser = anythingAdminUser,
                    ),
                  )
    } yield sequence.resources.head.values.values.flatten.collectFirst { case v: ReadTextValueV2 => v.valueContent }.get

  override val e2eSpec = suite("ConstructResponseUtilV2")(
    test("convert a resource Turtle response into a resource") {
      val resourceIri: IRI  = "http://rdfh.ch/0803/c5058f3a"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/Zeitglocklein.ttl"),
        )
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(
            _.splitMainResourcesAndValueRdfData(resourceRequestResponse, incunabulaProjectAdminUser),
          )
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )

        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
                   resourceSequence,
                 )
      } yield check
    },
    test("convert a resource Turtle response with hidden values into a resource with the anything admin user") {
      val resourceIri: IRI  = "http://rdfh.ch/0001/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"),
        )
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anythingAdminUser))
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = anythingAdminUser,
                              ),
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            ConstructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesAnythingAdmin,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with hidden values into a resource with the incunabula user") {
      val resourceIri: IRI  = "http://rdfh.ch/0001/F8L7zPp7TI-4MGJQlCO4Zg"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/visibleThingWithHiddenIntValues.ttl"),
        )
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(
            _.splitMainResourcesAndValueRdfData(resourceRequestResponse, incunabulaProjectAdminUser),
          )
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            ConstructResponseUtilV2SpecFullData.expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesIncunabulaUser,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with a hidden thing into a resource with the anything admin user") {
      val resourceIri: IRI  = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"),
        )
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anythingAdminUser))
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = anythingAdminUser,
                              ),
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            ConstructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnythingAdmin,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with a hidden thing into a resource with an unknown user") {
      val resourceIri: IRI  = "http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw"
      val turtleStr: String = FileUtil.readTextFile(
        Paths.get("test_data/generated_test_data/constructResponseUtilV2/thingWithOneHiddenThing.ttl"),
      )
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anonymousUser))
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = anonymousUser,
                              ),
                            )
        check <-
          ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
            ConstructResponseUtilV2SpecFullData.expectedReadResourceForAnythingThingWithOneHiddenThingAnonymousUser,
            resourceSequence,
          )
      } yield check
    },
    test("convert a resource Turtle response with standoff into a resource with anything admin user") {
      val resourceIri: IRI  = "http://rdfh.ch/0001/a-thing-with-text-values"
      val turtleStr: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/constructResponseUtilV2/thingWithStandoff.ttl"),
        )
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(_.splitMainResourcesAndValueRdfData(resourceRequestResponse, anythingAdminUser))
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = Seq(resourceIri),
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = anythingAdminUser,
                              ),
                            )
        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   ConstructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2WithStandoffAnythingAdminUser,
                   resourceSequence,
                 )
      } yield check
    },
    test("convert a Gravsearch Turtle response into a resource sequence") {

      /*

            PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

            CONSTRUCT {
                ?page knora-api:isMainResource true .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                ?book incunabula:title ?title .
            } WHERE {

                ?page a incunabula:page .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                FILTER(?seqnum = 10)

                ?book incunabula:title ?title .

                FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')

            }

       */

      val resourceIris: Seq[IRI] = Seq("http://rdfh.ch/0803/76570a749901", "http://rdfh.ch/0803/773f258402")
      val turtleStr: String      =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/constructResponseUtilV2/mainQuery1.ttl"))
      for {
        resourceRequestResponse      <- SparqlExtendedConstructResponse.make(turtleStr)
        mainResourcesAndValueRdfData <-
          constructResponseUtilV2ZIO(
            _.splitMainResourcesAndValueRdfData(resourceRequestResponse, incunabulaProjectAdminUser),
          )
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = resourceIris,
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )
        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   ConstructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery1,
                   resourceSequence,
                 )
      } yield check
    },
    test("convert a Gravsearch Turtle response with virtual incoming links into a resource sequence") {

      // the same query as above, but with a different main resource.
      /*

            PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

            CONSTRUCT {
                ?book knora-api:isMainResource true .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                ?book incunabula:title ?title .
            } WHERE {

                ?page a incunabula:page .

                ?page knora-api:isPartOf ?book .

                ?page incunabula:seqnum ?seqnum .

                FILTER(?seqnum = 10)

                ?book incunabula:title ?title .

                FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')

            }

       */

      val resourceIris: Seq[IRI] = Seq("http://rdfh.ch/0803/c5058f3a", "http://rdfh.ch/0803/ff17e5ef9601")
      val turtleStr: String      =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/constructResponseUtilV2/mainQuery2.ttl"))
      for {
        mainResourcesAndValueRdfData <-
          SparqlExtendedConstructResponse.make(turtleStr).flatMap { r =>
            constructResponseUtilV2ZIO(_.splitMainResourcesAndValueRdfData(r, incunabulaProjectAdminUser))
          }
        resourceSequence <- constructResponseUtilV2ZIO(
                              _.createApiResponse(
                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
                                orderByResourceIri = resourceIris,
                                pageSizeBeforeFiltering = 1,
                                mappings = Map.empty,
                                standoffSelection = StandoffSelection.NoStandoff,
                                versionDate = None,
                                calculateMayHaveMoreResults = false,
                                targetSchema = ApiV2Complex,
                                requestingUser = incunabulaProjectAdminUser,
                              ),
                            )
        check <- ResourcesResponseCheckerV2.compareReadResourcesSequenceV2ResponseZIO(
                   ConstructResponseUtilV2SpecFullData.expectedReadResourceSequenceV2ForMainQuery2,
                   resourceSequence,
                 )
      } yield check
    },
    test("serve mode with stored XML and standoff node data sets storedXml and reads no standoff") {
      val link = s"$textValueIri/standoff/0"
      readTextValue(
        textValueTurtle(hasStoredXml = true, hasMapping = true, Seq(link), withRootNodeData = true),
        StandoffSelection.OnlyWithoutStoredXml,
      ).map { content =>
        assertTrue(
          content.storedXml.contains(storedXml),
          content.standoff.isEmpty,
          content.textValueType == TextValueType.FormattedText,
          content.mappingIri.contains(StandoffMappingIri.StandardMapping),
        )
      }
    },
    test("a served value gets the mapping and XSLT of its valueHasMapping and renders textValueAsHtml") {
      val link = s"$textValueIri/standoff/0"
      readTextValue(
        textValueTurtle(hasStoredXml = true, hasMapping = true, Seq(link), withRootNodeData = true),
        StandoffSelection.OnlyWithoutStoredXml,
        standardMappingWithXslt,
      ).flatMap { content =>
        ZIO.serviceWith[AppConfig] { appConfig =>
          val html = content.toJsonLDValue(ApiV2Complex, servedProject, appConfig, Set.empty) match {
            case JsonLDObject(value) => value.get(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml)
            case _                   => None
          }
          assertTrue(
            content.mapping.isDefined,
            content.xslt.contains(identityXslt),
            html.exists {
              case JsonLDString(s) => s.contains("class=\"served\"") && s.contains("Ich liebe die Dinge.")
              case _               => false
            },
          )
        }
      }
    },
    test("a markup=standoff read (NoStandoff) of a value with stored XML gives unformatted text without storedXml") {
      readTextValue(
        textValueTurtle(hasStoredXml = true, hasMapping = true, Seq.empty, withRootNodeData = false),
        StandoffSelection.NoStandoff,
      ).flatMap { content =>
        ZIO.serviceWith[AppConfig] { appConfig =>
          val out = content.toJsonLDValue(ApiV2Complex, servedProject, appConfig, Set.empty) match {
            case JsonLDObject(value) => value
            case _                   => Map.empty[String, JsonLDValue]
          }
          assertTrue(
            content.textValueType == TextValueType.UnformattedText,
            content.storedXml.isEmpty,
            content.standoff.isEmpty,
            out.get(OntologyConstants.KnoraApiV2Complex.ValueAsString).contains(JsonLDString("Ich liebe die Dinge.")),
            !out.contains(OntologyConstants.KnoraApiV2Complex.TextValueAsXml),
          )
        }
      }
    },
    test("the same value with AllStandoff has no storedXml and its standoff is rebuilt") {
      val link = s"$textValueIri/standoff/0"
      readTextValue(
        textValueTurtle(hasStoredXml = true, hasMapping = true, Seq(link), withRootNodeData = true),
        StandoffSelection.AllStandoff,
      ).map { content =>
        assertTrue(
          content.storedXml.isEmpty,
          content.standoff.size == 1,
          content.textValueType == TextValueType.FormattedText,
        )
      }
    },
    test("stored XML without valueHasMapping fails with InconsistentRepositoryDataException") {
      readTextValue(
        textValueTurtle(hasStoredXml = true, hasMapping = false, Seq.empty, withRootNodeData = false),
        StandoffSelection.OnlyWithoutStoredXml,
      ).exit.map(exit =>
        assertTrue(exit.causeOption.flatMap(_.failureOrCause.left.toOption).exists {
          case e: InconsistentRepositoryDataException => e.getMessage.contains(textValueIri)
          case _                                      => false
        }),
      )
    },
    test("standoff links without node data and no stored XML give unformatted text without standoff") {
      val links = Seq(s"$textValueIri/standoff/0", s"$textValueIri/standoff/1")
      readTextValue(
        textValueTurtle(hasStoredXml = false, hasMapping = true, links, withRootNodeData = false),
        StandoffSelection.OnlyWithoutStoredXml,
      ).map(content =>
        assertTrue(
          content.storedXml.isEmpty,
          content.standoff.isEmpty,
          content.textValueType == TextValueType.UnformattedText,
        ),
      )
    },
  )
}
