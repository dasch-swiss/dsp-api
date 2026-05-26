/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import sttp.client4.*
import zio.test.*

import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * End-to-end coverage for the `urn:dasch:placeholder` sentinel on file-value writes.
 *
 * The parser-level unit tests in `ApiComplexV2JsonLdRequestParserSpec` feed JSON-LD strings
 * directly into the parser and never traverse the actual HTTP edge. They therefore cannot
 * catch regressions where pre-parser validation (e.g. the `AssetId` regex on
 * `fileValueHasFilename`) rejects the sentinel before the placeholder short-circuit fires,
 * or where the request path still reaches out to dsp-ingest.
 *
 * Each test POSTs to `/v2/resources` without registering any asset in dsp-ingest. If the
 * placeholder support is broken and the request reaches the ingest lookup, the test fails
 * with a non-200 response.
 */
object PlaceholderFileValueE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  private val projectIri = s"http://rdfh.ch/projects/$anythingShortcode"

  // language=json
  private val placeholderFilenameJsonLd =
    s"""{
       |  "@type": "anything:ThingPicture",
       |  "knora-api:hasStillImageFileValue": {
       |    "@type": "knora-api:StillImageFileValue",
       |    "knora-api:fileValueHasFilename": "urn:dasch:placeholder"
       |  },
       |  "knora-api:attachedToProject": { "@id": "$projectIri" },
       |  "rdfs:label": "placeholder filename",
       |  "@context": {
       |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
       |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
       |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
       |  }
       |}""".stripMargin

  // language=json
  private val placeholderAllFieldsJsonLd =
    s"""{
       |  "@type": "anything:ThingPicture",
       |  "knora-api:hasStillImageFileValue": {
       |    "@type": "knora-api:StillImageFileValue",
       |    "knora-api:fileValueHasFilename": "urn:dasch:placeholder",
       |    "knora-api:hasCopyrightHolder": "urn:dasch:placeholder",
       |    "knora-api:hasAuthorship": ["urn:dasch:placeholder"],
       |    "knora-api:hasLicense": { "@id": "urn:dasch:placeholder" }
       |  },
       |  "knora-api:attachedToProject": { "@id": "$projectIri" },
       |  "rdfs:label": "placeholder all fields",
       |  "@context": {
       |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
       |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
       |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
       |  }
       |}""".stripMargin

  val e2eSpec = suite("Placeholder file value support on /v2/resources")(
    test("creating a StillImage with the filename as urn:dasch:placeholder succeeds without calling dsp-ingest") {
      for {
        // No asset is registered in dsp-ingest. If the placeholder short-circuit is broken
        // and the API attempts the ingest lookup, this request fails with a non-200.
        response <- TestApiClient.postJsonLd(uri"/v2/resources", placeholderFilenameJsonLd, rootUser)
        _        <- response.assert200
      } yield assertCompletes
    },
    test("creating a StillImage with all four FileValue fields as urn:dasch:placeholder succeeds") {
      for {
        // None of the four fields are registered anywhere (no asset on dsp-ingest, no
        // placeholder license enabled on the project, no placeholder copyright holder
        // allowed on the project). The placeholder short-circuits must let all four through.
        response <- TestApiClient.postJsonLd(uri"/v2/resources", placeholderAllFieldsJsonLd, rootUser)
        _        <- response.assert200
      } yield assertCompletes
    },
  )
}
