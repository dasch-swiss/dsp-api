/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import org.junit.runner.RunWith
import sttp.client4.*
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.createThing
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.createValue
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.dataGraph
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.hasTextIri
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.kb
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.storedXml
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.textValueJson
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.update
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaQueryParam
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * The stored `kb:valueHasXml` of a formatted text value never reaches a client. Reading the resource as complex
 * markup=xml, complex markup=standoff and the simple schema yields the same bodies with and
 * without the stored predicate.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlReadContractE2ESpec extends E2EZSpec {

  private val simpleHasText = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText"

  private val xml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>Q3 yields 41, then <strong>seven</strong> remain; X-9 stays <em>put</em>.</p></text>""".stripMargin

  private val plainText = "Q3 yields 41, then seven remain; X-9 stays put."

  private def createResource: RIO[TestApiClient, (String, String)] =
    for {
      resource <- createThing()
      value    <- createValue(resource, textValueJson(xml, StandoffMappingIri.StandardMapping, None))
    } yield (resource, value)

  private def parseBody(response: String): Task[JsonLDObject] =
    ZIO.attempt(JsonLDUtil.parseJsonLD(response).body)

  private def textValueOf(body: JsonLDObject): Task[JsonLDObject] =
    ZIO.fromEither(body.getRequiredObject(hasTextIri)).mapError(new RuntimeException(_))

  /** The raw response bodies of every client-visible form of the text value, in the order complex xml, complex standoff, simple. */
  private def readAllForms(resource: String): RIO[TestApiClient, Seq[String]] =
    for {
      complexXml <- TestApiClient
                      .getJsonLd(uri"/v2/resources/$resource", anythingUser1, _.header("x-knora-accept-markup", "xml"))
                      .flatMap(_.assert200)
      complexStandoff <- TestApiClient
                           .getJsonLd(
                             uri"/v2/resources/$resource",
                             anythingUser1,
                             _.header("x-knora-accept-markup", "standoff"),
                           )
                           .flatMap(_.assert200)
      simple <- TestApiClient
                  .getJsonLd(uri"/v2/resources/$resource", anythingUser1, addSimpleSchemaQueryParam)
                  .flatMap(_.assert200)
    } yield Seq(complexXml, complexStandoff, simple)

  override val e2eSpec: Spec[env, Any] = suite("valueHasXml is invisible to the read path")(
    test("REQ-4.1 a stored valueHasXml changes no client-visible form of the text value") {
      for {
        created         <- createResource
        (resource, vIri) = created
        stored          <- storedXml(vIri)
        withXml         <- readAllForms(resource)
        _               <- update(s"DELETE WHERE { GRAPH <$dataGraph> { <$vIri> <${kb}valueHasXml> ?xml } }")
        storedAfter     <- storedXml(vIri)
        withoutXml      <- readAllForms(resource)
        xmlValue        <- parseBody(withXml(0)).flatMap(textValueOf)
        standoffValue   <- parseBody(withXml(1)).flatMap(textValueOf)
        simpleBody      <- parseBody(withXml(2))
      } yield assertTrue(
        stored.isDefined,
        storedAfter.isEmpty,
        withXml == withoutXml,
        withXml.forall(body => !body.contains("valueHasXml") && !body.contains("knora-base:")),
        xmlValue.getString(KA.TextValueAsXml) == Right(Some(xml)),
        xmlValue.getRequiredObject(KA.TextValueHasMapping).isRight,
        standoffValue.getString(KA.TextValueAsXml) == Right(None),
        standoffValue.getString(KA.ValueAsString) == Right(Some(plainText)),
        simpleBody.getString(simpleHasText) == Right(Some(plainText)),
      )
    },
  )
}
