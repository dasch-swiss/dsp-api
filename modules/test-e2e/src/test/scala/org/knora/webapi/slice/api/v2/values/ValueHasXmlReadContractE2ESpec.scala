/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import org.junit.runner.RunWith
import sttp.client4.*
import zio.*
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlE2E.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
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

  private def createResource: RIO[TestApiClient, (String, String)] = {
    val body = Json.Obj(
      "@type"                       -> Json.Str("anything:Thing"),
      "knora-api:attachedToProject" -> Json.Obj("@id" -> Json.Str(projectIri)),
      "rdfs:label"                  -> Json.Str("value-has-xml-read-contract-e2e"),
      "@context"                    -> context,
    )
    val value = Json.Obj(
      "@id"              -> Json.Str("placeholder"),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> textValueJson(xml, StandoffMappingIri.StandardMapping, None),
      "@context"         -> context,
    )
    for {
      resourceDoc <-
        TestApiClient.postJsonLdDocument(uri"/v2/resources", body.toString, anythingUser1).flatMap(_.assert200)
      resource <-
        ZIO.fromEither(resourceDoc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_))
      valueBody = value.merge(Json.Obj("@id" -> Json.Str(resource)))
      valueDoc <-
        TestApiClient.postJsonLdDocument(uri"/v2/values", valueBody.toString, anythingUser1).flatMap(_.assert200)
      valueIri <- ZIO.fromEither(valueDoc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_))
    } yield (resource, valueIri)
  }

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
    test("a stored valueHasXml changes no client-visible form of the text value") {
      for {
        created         <- createResource
        (resource, vIri) = created
        stored          <- storedXml(vIri)
        withXml         <- readAllForms(resource)
        _               <- ts(_.query(Update(s"DELETE WHERE { GRAPH <$dataGraph> { <$vIri> <${kb}valueHasXml> ?xml } }")))
        storedAfter     <- storedXml(vIri)
        withoutXml      <- readAllForms(resource)
        complexXml       = JsonLDUtil.parseJsonLD(withXml(0)).body
        xmlValue         = complexXml.getRequiredObject(hasTextIri).toOption.get
        standoffValue    = JsonLDUtil.parseJsonLD(withXml(1)).body.getRequiredObject(hasTextIri).toOption.get
        simpleBody       = JsonLDUtil.parseJsonLD(withXml(2)).body
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
