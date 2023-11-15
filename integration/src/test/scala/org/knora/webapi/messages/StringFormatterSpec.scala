/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import java.time.Instant

import dsp.errors.AssertionException
import dsp.valueobjects.Iri
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._

/**
 * Tests [[StringFormatter]].
 */
class StringFormatterSpec extends CoreSpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The StringFormatter class" should {
    "recognize the url of the dhlab site as a valid IRI" in {
      val testUrl: String = "http://dhlab.unibas.ch/"
      val validIri =
        Iri.validateAndEscapeIri(testUrl).getOrElse(throw AssertionException(s"Invalid IRI $testUrl"))
      validIri should be(testUrl)
    }

    "recognize the url of the DaSCH site as a valid IRI" in {
      val testUrl = "http://dasch.swiss"
      val validIri =
        Iri.validateAndEscapeIri(testUrl).getOrElse(throw AssertionException(s"Invalid IRI $testUrl"))
      validIri should be(testUrl)
    }

    /////////////////////////////////////
    // Built-in ontologies

    "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/simple/v2#Resource" in {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2#Resource")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )
    }

    "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/v2#Resource" in {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/v2#Resource")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/simple/v2 to http://www.knora.org/ontology/knora-base" in {
      val externalOntologyIri = "http://api.knora.org/ontology/knora-api/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/simple/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
      val externalEntityIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/v2 to http://www.knora.org/ontology/knora-base" in {
      val externalOntologyIri = "http://api.knora.org/ontology/knora-api/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.isEmpty
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.isEmpty
      )
    }

    "convert http://api.knora.org/ontology/knora-api/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
      val externalEntityIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.isEmpty
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )
    }

    //////////////////////////////////////////
    // Non-shared, project-specific ontologies

    "convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild" in {
      val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/v2#bild" in {
      val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/v2#bild")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2 to http://www.knora.org/ontology/00FF/images" in {
      val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/00FF/images")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild to http://www.knora.org/ontology/00FF/images#bild" in {
      val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/00FF/images#bild")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/v2 to http://www.knora.org/ontology/00FF/images" in {
      val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("00FF")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/00FF/images")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://0.0.0.0:3333/ontology/00FF/images/v2#bild to http://www.knora.org/ontology/00FF/images#bild" in {
      val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bild".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.getProjectCode.contains("00FF")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/00FF/images#bild")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.contains("00FF")
      )
    }

    "convert http://www.knora.org/ontology/knora-base#TextValue to http://www.w3.org/2001/XMLSchema#string" in {
      val internalEntityIri = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          internalEntityIri.isKnoraInternalEntityIri &&
          internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.getProjectCode.isEmpty
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      assert(externalEntityIri.toString == "http://www.w3.org/2001/XMLSchema#string" && !externalEntityIri.isKnoraIri)
    }

    /////////////////////////////////////////////////////////////
    // Shared ontologies in the default shared ontologies project

    "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/example/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/simple/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/example/simple/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )
    }

    "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/example/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/example/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/simple/v2 to http://www.knora.org/ontology/shared/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/example/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/simple/v2#Person to http://www.knora.org/ontology/shared/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/example/simple/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/example#Person")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/v2 to http://www.knora.org/ontology/shared/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/example/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0000")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0000")
      )
    }

    "convert http://api.knora.org/ontology/shared/example/v2#Person to http://www.knora.org/ontology/shared/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/example/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0000")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/example#Person")
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0000")
      )
    }

    ///////////////////////////////////////////////////////////////
    // Shared ontologies in a non-default shared ontologies project

    "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/simple/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/simple/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/simple/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/simple/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )
    }

    "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/v2" in {
      val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )

      val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Complex)
      externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/v2")
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/v2#Person" in {
      val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )

      val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Complex)
      externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/v2#Person")
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          externalEntityIri.isKnoraApiV2EntityIri &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/simple/v2 to http://www.knora.org/ontology/shared/0111/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/0111/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/simple/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/0111/example#Person")
      assert(
        internalEntityIri.getOntologySchema.contains(InternalSchema) &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/v2 to http://www.knora.org/ontology/shared/0111/example" in {
      val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/v2".toSmartIri
      assert(
        externalOntologyIri.getOntologySchema.contains(ApiV2Complex) &&
          externalOntologyIri.isKnoraOntologyIri &&
          !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
          externalOntologyIri.isKnoraSharedDefinitionIri &&
          externalOntologyIri.getProjectCode.contains("0111")
      )

      val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
      internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/0111/example")
      assert(
        internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
          internalOntologyIri.isKnoraOntologyIri &&
          !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
          internalOntologyIri.isKnoraSharedDefinitionIri &&
          internalOntologyIri.getProjectCode.contains("0111")
      )
    }

    "convert http://api.knora.org/ontology/shared/0111/example/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person" in {
      val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/v2#Person".toSmartIri
      assert(
        externalEntityIri.getOntologySchema.contains(ApiV2Complex) &&
          !externalEntityIri.isKnoraBuiltInDefinitionIri &&
          externalEntityIri.isKnoraSharedDefinitionIri &&
          externalEntityIri.getProjectCode.contains("0111")
      )

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/0111/example#Person")
      assert(
        internalEntityIri.isKnoraInternalEntityIri &&
          !internalEntityIri.isKnoraBuiltInDefinitionIri &&
          internalEntityIri.isKnoraSharedDefinitionIri &&
          internalEntityIri.getProjectCode.contains("0111")
      )
    }

    /////////////////////

    "not change http://www.w3.org/2001/XMLSchema#string when converting to InternalSchema" in {
      val externalEntityIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
      assert(!externalEntityIri.isKnoraIri)

      val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
      assert(internalEntityIri.toString == externalEntityIri.toString && !externalEntityIri.isKnoraIri)
    }

    "parse http://rdfh.ch/0000/Ef9heHjPWDS7dMR_gGax2Q" in {
      val dataIri = "http://rdfh.ch/0000/Ef9heHjPWDS7dMR_gGax2Q".toSmartIri
      assert(dataIri.isKnoraDataIri)
    }

    "parse http://rdfh.ch/Ef9heHjPWDS7dMR_gGax2Q" in {
      val dataIri = "http://rdfh.ch/Ef9heHjPWDS7dMR_gGax2Q".toSmartIri
      assert(dataIri.isKnoraDataIri)
    }

    "parse http://www.w3.org/2001/XMLSchema#integer" in {
      val xsdIri = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
      assert(
        !xsdIri.isKnoraOntologyIri &&
          !xsdIri.isKnoraDataIri &&
          xsdIri.getOntologySchema.isEmpty &&
          xsdIri.getProjectCode.isEmpty
      )
    }

    "validate internal ontology path" in {
      val urlPath = "/ontology/knora-api/simple/v2"
      stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath) should be(true)
    }

    "reject an empty IRI string" in {
      assertThrows[AssertionException] {
        "".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject the IRI 'foo'" in {
      assertThrows[AssertionException] {
        "foo".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://" in {
      assertThrows[AssertionException] {
        "http://".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject ftp://www.knora.org/ontology/00FF/images (wrong URL scheme)" in {
      assertThrows[AssertionException] {
        "ftp://www.knora.org/ontology/00FF/images".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject https://www.knora.org/ontology/00FF/images (wrong URL scheme)" in {
      assertThrows[AssertionException] {
        "https://www.knora.org/ontology/00FF/images".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/ontology" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/ontology" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/ontology/00FF/images/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://www.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://www.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/ontology/00FF/images/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://api.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)" in {
      assertThrows[AssertionException] {
        "http://api.knora.org/ontology/00FF/images/simple/v2".toSmartIriWithErr(
          throw AssertionException(s"Invalid IRI")
        )
      }
    }

    "reject http://0.0.0.0:3333/ontology/v2 (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/0000/v2 (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/ontology (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/0000/ontology (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/0000/simple/simple/v2 (invalid ontology name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/00FF/images/v2#1234 (invalid entity name)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/00FF/images/v2#1234".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/v2 (missing project shortcode in ontology IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/simple/v2 (missing project shortcode in ontology IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/images/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/v2#bild (missing project shortcode in entity IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/images/v2#bild".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/images/simple/v2#bild (missing project shortcode in entity IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/images/simple/v2#bild".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "reject http://0.0.0.0:3333/ontology/shared/example/v2 (shared project code with local hostname in ontology IRI)" in {
      assertThrows[AssertionException] {
        "http://0.0.0.0:3333/ontology/shared/example/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
      }
    }

    "enable pattern matching with SmartIri" in {
      val input: SmartIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri

      val isResource = input match {
        case SmartIri(OntologyConstants.KnoraBase.Resource) => true
        case _                                              => false
      }

      assert(isResource)
    }

    "convert 100,000 IRIs" ignore {
      val totalIris = 100000

      val parseStart = System.currentTimeMillis

      for (i <- 1 to totalIris) {
        val iriStr = s"http://0.0.0.0:3333/ontology/00FF/images/v2#class$i"
        iriStr.toSmartIri.toOntologySchema(InternalSchema)
      }

      val parseEnd            = System.currentTimeMillis
      val parseDuration       = (parseEnd - parseStart).toDouble
      val parseDurationPerIri = parseDuration / totalIris.toDouble
      println(f"Parse and store $totalIris IRIs, $parseDuration ms, time per IRI $parseDurationPerIri%1.5f ms")

      val retrieveStart = System.currentTimeMillis

      for (i <- 1 to totalIris) {
        val iriStr = s"http://0.0.0.0:3333/ontology/00FF/images/v2#class$i"
        iriStr.toSmartIri.toOntologySchema(InternalSchema)
      }

      val retrieveEnd            = System.currentTimeMillis
      val retrieveDuration       = (retrieveEnd - retrieveStart).toDouble
      val retrieveDurationPerIri = retrieveDuration / totalIris.toDouble

      println(f"Retrieve time $retrieveDuration ms, time per IRI $retrieveDurationPerIri%1.5f ms")
    }

    "generate an ARK URL for a resource IRI without a timestamp" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl()
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn")
    }

    "generate an ARK URL for a resource IRI with a timestamp with a fractional part" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val timestamp        = Instant.parse("2018-06-04T08:56:22.9876543Z")
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(timestamp))
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T0856229876543Z")
    }

    "generate an ARK URL for a resource IRI with a timestamp with a leading zero" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val timestamp        = Instant.parse("2018-06-04T08:56:22.098Z")
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(timestamp))
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T085622098Z")
    }

    "generate an ARK URL for a resource IRI with a timestamp without a fractional part" in {
      val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
      val timestamp        = Instant.parse("2018-06-04T08:56:22Z")
      val arkUrl           = resourceIri.toSmartIri.fromResourceIriToArkUrl(maybeTimestamp = Some(timestamp))
      assert(arkUrl == "http://0.0.0.0:3336/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAn.20180604T085622Z")
    }

  }

  "The StringFormatter class for User" should {

    "validate username" in {
      // 4 - 50 characters long
      assert(stringFormatter.validateUsername("abc").isEmpty)
      assert(stringFormatter.validateUsername("123456789012345678901234567890123456789012345678901").isEmpty)

      // only contain alphanumeric, underscore, and dot
      stringFormatter.validateUsername("a_2.3") should be(Some("a_2.3"))
      assert(stringFormatter.validateUsername("a_2.3-4").isEmpty)

      // not allow @
      assert(stringFormatter.validateUsername("donald.duck@example.com").isEmpty)

      // Underscore and dot can't be at the end or start of a username
      assert(stringFormatter.validateUsername("_username").isEmpty)
      assert(stringFormatter.validateUsername("username_").isEmpty)
      assert(stringFormatter.validateUsername(".username").isEmpty)
      assert(stringFormatter.validateUsername("username.").isEmpty)

      // Underscore or dot can't be used multiple times in a row
      assert(stringFormatter.validateUsername("user__name").isEmpty)
      assert(stringFormatter.validateUsername("user..name").isEmpty)
    }

    "validate email" in {
      stringFormatter.validateEmail("donald.duck@example.com") should be(Some("donald.duck@example.com"))
      assert(stringFormatter.validateEmail("donald.duck").isEmpty)
    }
  }
}
