/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.ZIO

import java.util.UUID

import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM2.anythingProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.CreateMappingRequestV2
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

class StandoffResponderV2Spec extends CoreSpec {

  private def getMapping(iri: String): SparqlConstructResponse =
    UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TriplestoreService](_.query(Construct(sparql.v2.txt.getMapping(iri)))))

  "The standoff responder" should {
    "create a standoff mapping" in {
      val mappingName = "customMapping"
      val xmlContent =
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<mapping xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           |         xsi:noNamespaceSchemaLocation="../../../webapi/src/main/resources/mappingXMLToStandoff.xsd">
           |
           |    <mappingElement>
           |        <tag>
           |            <name>text</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffRootTag</classIri>
           |            <attributes>
           |                <attribute>
           |                    <attributeName>documentType</attributeName>
           |                    <namespace>noNamespace</namespace>
           |                    <propertyIri>http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType</propertyIri>
           |                </attribute>
           |            </attributes>
           |        </standoffClass>
           |    </mappingElement>
           |
           |    <mappingElement>
           |        <tag>
           |            <name>section</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffParagraphTag</classIri>
           |        </standoffClass>
           |    </mappingElement>
           |
           |    <mappingElement>
           |        <tag>
           |            <name>italic</name>
           |            <class>noClass</class>
           |            <namespace>noNamespace</namespace>
           |            <separatesWords>false</separatesWords>
           |        </tag>
           |        <standoffClass>
           |            <classIri>http://www.knora.org/ontology/standoff#StandoffItalicTag</classIri>
           |        </standoffClass>
           |    </mappingElement>
           |
           |</mapping>
           |""".stripMargin

      val projectIri = ProjectIri.unsafeFrom(anythingProjectIri)
      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[StandoffResponderV2](
          _.createMappingV2(
            CreateMappingRequestV2(
              "custom mapping",
              projectIri,
              mappingName,
              xmlContent,
            ),
            SharedTestDataADM.rootUser,
            UUID.randomUUID(),
          ),
        ),
      )

      val expectedMappingIRI = f"${projectIri.value}/mappings/$mappingName"
      response.mappingIri should equal(expectedMappingIRI)
      val mappingFromDB: SparqlConstructResponse = getMapping(response.mappingIri)
      mappingFromDB.statements should not be Map.empty
      mappingFromDB.statements.get(expectedMappingIRI) should not be Map.empty
    }
  }
}
