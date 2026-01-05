/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.ZIO
import zio.test.assertTrue

import java.util.UUID

import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.CreateMappingRequestV2
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object StandoffResponderV2Spec extends E2EZSpec {

  override val e2eSpec = suite("The standoff responder")(
    test("create a standoff mapping") {
      val mappingName = "customMapping"
      val xmlContent  =
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
      val createRequest = CreateMappingRequestV2("custom mapping", anythingProjectIri, mappingName, xmlContent)

      for {
        response <- ZIO.serviceWithZIO[StandoffResponderV2](
                      _.createMappingV2(createRequest, rootUser, UUID.randomUUID()),
                    )
        expectedMappingIRI = f"${anythingProjectIri.value}/mappings/$mappingName"
        mappingFromDB     <- ZIO.serviceWithZIO[TriplestoreService](
                           _.query(Construct(sparql.v2.txt.getMapping(response.mappingIri))),
                         )
      } yield assertTrue(
        mappingFromDB.statements.nonEmpty,
        mappingFromDB.statements.get(expectedMappingIRI).nonEmpty,
        response.mappingIri == expectedMappingIRI,
      )
    },
  )
}
