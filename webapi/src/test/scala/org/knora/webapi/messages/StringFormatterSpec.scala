/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.test.Assertion.failsWithA
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assert
import zio.test.assertTrue

import java.time.Instant

import dsp.errors.AssertionException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.slice.common.ResourceIri

/**
 * Tests [[StringFormatter]].
 */
object StringFormatterSpec extends ZIOSpecDefault {
  private implicit val stringFormatter: StringFormatter = {
    val config = Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(AppConfig.parseConfig).getOrThrowFiberFailure())
    StringFormatter.init(config)
    StringFormatter.getGeneralInstance
  }

  private def assertRejected(iri: String) =
    ZIO
      .attempt(iri.toSmartIriWithErr(throw AssertionException(s"Invalid IRI")))
      .exit
      .map(actual => assert(actual)(failsWithA[AssertionException]))

  val spec: Spec[Any, Nothing] = suite("The StringFormatter class")(
    test("recognize the url of the dhlab site as a valid IRI") {
      val testUrl: String = "http://dhlab.unibas.ch/"
      val validIri        =
        Iri.validateAndEscapeIri(testUrl).getOrElse(throw AssertionException(s"Invalid IRI $testUrl"))
      assertTrue(validIri == testUrl)
    },
    test("recognize the url of the DaSCH site as a valid IRI") {
      val testUrl  = "http://dasch.swiss"
      val validIri =
        Iri.validateAndEscapeIri(testUrl).getOrElse(throw AssertionException(s"Invalid IRI $testUrl"))
      assertTrue(validIri == testUrl)
    },
    /////////////////////////////////////
    // Built-in ontologies
    test("convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/simple/v2") {
      val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.isEmpty,
        externalOntologyIri.toString == "http://api.knora.org/ontology/knora-api/simple/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.isEmpty,
      )
    },
    test(
      "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/simple/v2#Resource",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.isEmpty,
        externalEntityIri.toString == "http://api.knora.org/ontology/knora-api/simple/v2#Resource",
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        externalEntityIri.isKnoraApiV2EntityIri,
        externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.isEmpty,
      )
    },
    test("convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/v2") {
      val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.isEmpty,
        externalOntologyIri.toString == "http://api.knora.org/ontology/knora-api/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.isEmpty,
      )
    },
    test(
      "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/v2#Resource",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.isEmpty,
        externalEntityIri.toString == "http://api.knora.org/ontology/knora-api/v2#Resource",
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        externalEntityIri.isKnoraApiV2EntityIri,
        externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.isEmpty,
      )
    },
    test("convert http://api.knora.org/ontology/knora-api/simple/v2 to http://www.knora.org/ontology/knora-base") {
      val externalOntologyIri = "http://api.knora.org/ontology/knora-api/simple/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.isEmpty,
        internalOntologyIri.toString == "http://www.knora.org/ontology/knora-base",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.isEmpty,
      )
    },
    test(
      "convert http://api.knora.org/ontology/knora-api/simple/v2#Resource to http://www.knora.org/ontology/knora-base#Resource",
    ) {
      val externalEntityIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        externalEntityIri.isKnoraApiV2EntityIri,
        externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.isEmpty,
        internalEntityIri.toString == "http://www.knora.org/ontology/knora-base#Resource",
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.isEmpty,
      )
    },
    test("convert http://api.knora.org/ontology/knora-api/v2 to http://www.knora.org/ontology/knora-base") {
      val externalOntologyIri = "http://api.knora.org/ontology/knora-api/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.isEmpty,
        internalOntologyIri.toString == "http://www.knora.org/ontology/knora-base",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.isEmpty,
      )
    },
    test(
      "convert http://api.knora.org/ontology/knora-api/v2#Resource to http://www.knora.org/ontology/knora-base#Resource",
    ) {
      val externalEntityIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        externalEntityIri.isKnoraApiV2EntityIri,
        externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.isEmpty,
        internalEntityIri.toString == "http://www.knora.org/ontology/knora-base#Resource",
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.isEmpty,
      )
    },
    //////////////////////////////////////////
    // Non-shared, project-specific ontologies
    test("convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/simple/v2") {
      val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.contains("00FF"),
        externalOntologyIri.toString == "http://0.0.0.0:3333/ontology/00FF/images/simple/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.contains("00FF"),
      )
    },
    test(
      "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.contains("00FF"),
        externalEntityIri.toString == "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild",
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.contains("00FF"),
      )
    },
    test("convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/v2") {
      val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.contains("00FF"),
        externalOntologyIri.toString == "http://0.0.0.0:3333/ontology/00FF/images/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.contains("00FF"),
      )
    },
    test("convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/v2#bild") {
      val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.contains("00FF"),
        externalEntityIri.toString == "http://0.0.0.0:3333/ontology/00FF/images/v2#bild",
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.contains("00FF"),
      )
    },
    test("convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2 to http://www.knora.org/ontology/00FF/images") {
      val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.contains("00FF"),
        internalOntologyIri.toString == "http://www.knora.org/ontology/00FF/images",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.contains("00FF"),
      )
    },
    test(
      "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild to http://www.knora.org/ontology/00FF/images#bild",
    ) {
      val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.contains("00FF"),
        internalEntityIri.toString == "http://www.knora.org/ontology/00FF/images#bild",
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.contains("00FF"),
      )
    },
    test("convert http://0.0.0.0:3333/ontology/00FF/images/v2 to http://www.knora.org/ontology/00FF/images") {
      val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.getProjectCode.contains("00FF"),
        internalOntologyIri.toString == "http://www.knora.org/ontology/00FF/images",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.getProjectCode.contains("00FF"),
      )
    },
    test("convert http://0.0.0.0:3333/ontology/00FF/images/v2#bild to http://www.knora.org/ontology/00FF/images#bild") {
      val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bild".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.getProjectCode.contains("00FF"),
        internalEntityIri.toString == "http://www.knora.org/ontology/00FF/images#bild",
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.contains("00FF"),
      )
    },
    test("convert http://www.knora.org/ontology/knora-base#TextValue to http://www.w3.org/2001/XMLSchema#string") {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        internalEntityIri.isKnoraInternalEntityIri,
        internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.getProjectCode.isEmpty,
        externalEntityIri.toString == "http://www.w3.org/2001/XMLSchema#string",
        !externalEntityIri.isKnoraIri,
      )
    },
    /////////////////////////////////////////////////////////////
    // Shared ontologies in the default shared ontologies project
    test(
      "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/simple/v2",
    ) {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0000"),
        externalOntologyIri.toString == "http://api.knora.org/ontology/shared/example/simple/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0000"),
      )
    },
    test(
      "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/simple/v2#Person",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0000"),
        externalEntityIri.toString == "http://api.knora.org/ontology/shared/example/simple/v2#Person",
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0000"),
      )
    },
    test("convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/v2") {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0000"),
        externalOntologyIri.toString == "http://api.knora.org/ontology/shared/example/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0000"),
      )
    },
    test(
      "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/v2#Person",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0000"),
        externalEntityIri.toString == "http://api.knora.org/ontology/shared/example/v2#Person",
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0000"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/example/simple/v2 to http://www.knora.org/ontology/shared/example",
    ) {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/example/simple/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0000"),
        internalOntologyIri.toString == "http://www.knora.org/ontology/shared/example",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0000"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/example/simple/v2#Person to http://www.knora.org/ontology/shared/example#Person",
    ) {
      val externalEntityIri = "http://api.knora.org/ontology/shared/example/simple/v2#Person".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0000"),
        internalEntityIri.toString == "http://www.knora.org/ontology/shared/example#Person",
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0000"),
      )
    },
    test("convert http://api.knora.org/ontology/shared/example/v2 to http://www.knora.org/ontology/shared/example") {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/example/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0000"),
        internalOntologyIri.toString == "http://www.knora.org/ontology/shared/example",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0000"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/example/v2#Person to http://www.knora.org/ontology/shared/example#Person",
    ) {
      val externalEntityIri = "http://api.knora.org/ontology/shared/example/v2#Person".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0000"),
        internalEntityIri.toString == "http://www.knora.org/ontology/shared/example#Person",
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0000"),
      )
    },
    ///////////////////////////////////////////////////////////////
    // Shared ontologies in a non-default shared ontologies project
    test(
      "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/simple/v2",
    ) {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0111"),
        externalOntologyIri.toString == "http://api.knora.org/ontology/shared/0111/example/simple/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/simple/v2#Person",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assertTrue(
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0111"),
        externalEntityIri.toString == "http://api.knora.org/ontology/shared/0111/example/simple/v2#Person",
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/v2",
    ) {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0111"),
        externalOntologyIri.toString == "http://api.knora.org/ontology/shared/0111/example/v2",
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/v2#Person",
    ) {
      val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      assertTrue(
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0111"),
        externalEntityIri.toString == "http://api.knora.org/ontology/shared/0111/example/v2#Person",
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        externalEntityIri.isKnoraApiV2EntityIri,
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/0111/example/simple/v2 to http://www.knora.org/ontology/shared/0111/example",
    ) {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0111"),
        internalOntologyIri.toString == "http://www.knora.org/ontology/shared/0111/example",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/0111/example/simple/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person",
    ) {
      val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2#Person".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple),
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0111"),
        internalEntityIri.toString == "http://www.knora.org/ontology/shared/0111/example#Person",
        internalEntityIri.getOntologySchema.contains(InternalSchema),
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/0111/example/v2 to http://www.knora.org/ontology/shared/0111/example",
    ) {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/v2".toSmartIri
      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex),
        externalOntologyIri.isKnoraOntologyIri,
        !externalOntologyIri.isKnoraBuiltInDefinitionIri,
        externalOntologyIri.isKnoraSharedDefinitionIri,
        externalOntologyIri.getProjectCode.contains("0111"),
        internalOntologyIri.toString == "http://www.knora.org/ontology/shared/0111/example",
        internalOntologyIri.getOntologySchema.contains(InternalSchema),
        internalOntologyIri.isKnoraOntologyIri,
        !internalOntologyIri.isKnoraBuiltInDefinitionIri,
        internalOntologyIri.isKnoraSharedDefinitionIri,
        internalOntologyIri.getProjectCode.contains("0111"),
      )
    },
    test(
      "convert http://api.knora.org/ontology/shared/0111/example/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person",
    ) {
      val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/v2#Person".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex),
        !externalEntityIri.isKnoraBuiltInDefinitionIri,
        externalEntityIri.isKnoraSharedDefinitionIri,
        externalEntityIri.getProjectCode.contains("0111"),
        internalEntityIri.toString == "http://www.knora.org/ontology/shared/0111/example#Person",
        internalEntityIri.isKnoraInternalEntityIri,
        !internalEntityIri.isKnoraBuiltInDefinitionIri,
        internalEntityIri.isKnoraSharedDefinitionIri,
        internalEntityIri.getProjectCode.contains("0111"),
      )
    },
    /////////////////////
    test("not change http://www.w3.org/2001/XMLSchema#string when converting to InternalSchema") {
      val externalEntityIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assertTrue(
        !externalEntityIri.isKnoraIri,
        internalEntityIri.toString == externalEntityIri.toString,
      )
    },
    test("parse http://rdfh.ch/0000/Ef9heHjPWDS7dMR_gGax2Q") {
      val dataIri = "http://rdfh.ch/0000/Ef9heHjPWDS7dMR_gGax2Q".toSmartIri
      assertTrue(dataIri.isKnoraDataIri)
    },
    test("parse http://rdfh.ch/Ef9heHjPWDS7dMR_gGax2Q") {
      val dataIri = "http://rdfh.ch/Ef9heHjPWDS7dMR_gGax2Q".toSmartIri
      assertTrue(dataIri.isKnoraDataIri)
    },
    test("parse http://www.w3.org/2001/XMLSchema#integer") {
      val xsdIri = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
      assertTrue(
        !xsdIri.isKnoraOntologyIri,
        !xsdIri.isKnoraDataIri,
        xsdIri.getOntologySchema.isEmpty,
        xsdIri.getProjectCode.isEmpty,
      )
    },
    test("validate internal ontology path") {
      val urlPath = "/ontology/knora-api/simple/v2"
      assertTrue(stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath))
    },
    test("reject an empty IRI string")(assertRejected("")),
    test("reject the IRI 'foo'")(assertRejected("foo")),
    test("reject http://")(assertRejected("http://")),
    test("reject ftp://www.knora.org/ontology/00FF/images (wrong URL scheme)")(
      assertRejected("ftp://www.knora.org/ontology/00FF/images"),
    ),
    test("reject https://www.knora.org/ontology/00FF/images (wrong URL scheme)")(
      assertRejected("https://www.knora.org/ontology/00FF/images"),
    ),
    test("reject http://www.knora.org/")(assertRejected("http://www.knora.org/")),
    test("reject http://api.knora.org/")(assertRejected("http://api.knora.org/")),
    test("reject http://0.0.0.0:3333/")(assertRejected("http://0.0.0.0:3333/")),
    test("reject http://www.knora.org/ontology")(assertRejected("http://www.knora.org/ontology")),
    test("reject http://api.knora.org/ontology")(assertRejected("http://api.knora.org/ontology")),
    test("reject http://0.0.0.0:3333/ontology")(assertRejected("http://0.0.0.0:3333/ontology")),
    test("reject http://www.knora.org/ontology/00FF/images/v2 (wrong hostname)")(
      assertRejected("http://www.knora.org/ontology/00FF/images/v2"),
    ),
    test("reject http://www.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)")(
      assertRejected("http://www.knora.org/ontology/00FF/images/simple/v2"),
    ),
    test("reject http://api.knora.org/ontology/00FF/images/v2 (wrong hostname)")(
      assertRejected("http://api.knora.org/ontology/00FF/images/v2"),
    ),
    test("reject http://api.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)")(
      assertRejected("http://api.knora.org/ontology/00FF/images/simple/v2"),
    ),
    test("reject http://0.0.0.0:3333/ontology/v2 (invalid ontology name)")(
      assertRejected("http://0.0.0.0:3333/ontology/v2"),
    ),
    test("reject http://0.0.0.0:3333/ontology/0000/v2 (invalid ontology name)")(
      assertRejected("http://0.0.0.0:3333/ontology/0000/v2"),
    ),
    test("reject http://0.0.0.0:3333/ontology/ontology (invalid ontology name)")(
      assertRejected("http://0.0.0.0:3333/ontology/ontology"),
    ),
    test("reject http://0.0.0.0:3333/ontology/0000/ontology (invalid ontology name)")(
      assertRejected("http://0.0.0.0:3333/ontology/0000/ontology"),
    ),
    test("reject http://0.0.0.0:3333/ontology/0000/simple/simple/v2 (invalid ontology name)")(
      assertRejected("http://0.0.0.0:3333/ontology/0000/simple/simple/v2"),
    ),
    test("reject http://0.0.0.0:3333/ontology/00FF/images/v2#1234 (invalid entity name)")(
      assertRejected("http://0.0.0.0:3333/ontology/00FF/images/v2#1234"),
    ),
    test("reject http://0.0.0.0:3333/ontology/images/v2 (missing project shortcode in ontology IRI)")(
      assertRejected("http://0.0.0.0:3333/ontology/images/v2"),
    ),
    test("reject http://0.0.0.0:3333/ontology/images/simple/v2 (missing project shortcode in ontology IRI)")(
      assertRejected("http://0.0.0.0:3333/ontology/images/simple/v2"),
    ),
    test("reject http://0.0.0.0:3333/ontology/images/v2#bild (missing project shortcode in entity IRI)")(
      assertRejected("http://0.0.0.0:3333/ontology/images/v2#bild"),
    ),
    test("reject http://0.0.0.0:3333/ontology/images/simple/v2#bild (missing project shortcode in entity IRI)")(
      assertRejected("http://0.0.0.0:3333/ontology/images/simple/v2#bild"),
    ),
    test(
      "reject http://0.0.0.0:3333/ontology/shared/example/v2 (shared project code with local hostname in ontology IRI)",
    )(assertRejected("http://0.0.0.0:3333/ontology/shared/example/v2")),
    test("enable pattern matching with SmartIri") {
      val input: SmartIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri

      val isResource = input match {
        case SmartIri(OntologyConstants.KnoraBase.Resource) => true
        case _                                              => false
      }

      assertTrue(isResource)
    },
    test("should convert link value prop to link prop") {
      val resourceIri: IRI = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue"
      val asLinkProp       = resourceIri.toSmartIri.fromLinkValuePropToLinkProp
      assertTrue(asLinkProp == "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri)
    },
    test("generate an ARK URL for a resource IRI without a timestamp") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA")
      val arkUrl      = stringFormatter.resourceIriToArkUrl(resourceIri)
      assertTrue(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn")
    },
    test("generate an ARK URL for a resource IRI with a timestamp with a fractional part") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA")
      val timestamp   = Instant.parse("2018-06-04T08:56:22.9876543Z")
      val arkUrl      = stringFormatter.resourceIriToArkUrl(resourceIri, maybeTimestamp = Some(timestamp))
      assertTrue(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T0856229876543Z")
    },
    test("generate an ARK URL for a resource IRI with a timestamp with a leading zero") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA")
      val timestamp   = Instant.parse("2018-06-04T08:56:22.098Z")
      val arkUrl      = stringFormatter.resourceIriToArkUrl(resourceIri, maybeTimestamp = Some(timestamp))
      assertTrue(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T085622098Z")
    },
    test("generate an ARK URL for a resource IRI with a timestamp without a fractional part") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA")
      val timestamp   = Instant.parse("2018-06-04T08:56:22Z")
      val arkUrl      = stringFormatter.resourceIriToArkUrl(resourceIri, maybeTimestamp = Some(timestamp))
      assertTrue(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T085622Z")
    },
  )
}
