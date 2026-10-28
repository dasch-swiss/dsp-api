/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import org.junit.runner.RunWith
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.ApiV2Simple
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.PlaceholderIri

@RunWith(classOf[DspZTestJUnitRunner])
class StillImageFileValueContentV2Spec extends ZIOSpecDefault {

  StringFormatter.getInitializedTestInstance

  private val serviceIdProp = OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFImageService
  private val baseUrlProp   = OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFBaseUrl
  private val fileAsUrlProp = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl
  private val filename      = "4sAf4AmPeeg-ZjDn3Tot1Zt.jp2"

  private def content(internalFilename: String, schema: ApiV2Schema = ApiV2Complex) =
    StillImageFileValueContentV2(schema, FileValueV2(internalFilename, "image/jp2"), 100, 200)

  private def render(c: StillImageFileValueContentV2, schema: ApiV2Schema = ApiV2Complex) =
    ZIO.serviceWith[AppConfig] { appConfig =>
      val base = appConfig.sipi.externalBaseUrl
      val json = c.toJsonLDValue(schema, TestDataFactory.someProjectADM, appConfig, Set.empty)
      (base, json.asInstanceOf[JsonLDObject].value)
    }

  private val serviceIdCases = Seq(
    ("http://0.0.0.0:1024", "0001", "abc.jp2", "http://0.0.0.0:1024/0001/abc.jp2"),
    ("https://iiif.example.org/sipi", "080E", "abc.jp2", "https://iiif.example.org/sipi/080E/abc.jp2"),
  )

  override def spec: Spec[Any, Any] =
    suite("StillImageFileValueContentV2")(
      suite("serviceId")(
        serviceIdCases.map { case (base, code, file, expected) =>
          test(s"base '$base', shortcode '$code', file '$file' gives '$expected'") {
            assertTrue(StillImageFileValueContentV2.serviceId(base, Shortcode.unsafeFrom(code), file) == expected)
          }
        }*,
      ),
      suite("toJsonLDValue")(
        test("complex schema carries the IIIF image service id as an xsd:anyURI") {
          for {
            r        <- render(content(filename))
            (base, m) = r
            obj       = m(serviceIdProp).asInstanceOf[JsonLDObject].value
          } yield assertTrue(
            obj("@type") == JsonLDString("http://www.w3.org/2001/XMLSchema#anyURI"),
            obj("@value") == JsonLDString(base + "/0001/4sAf4AmPeeg-ZjDn3Tot1Zt.jp2"),
          )
        },
        test("a placeholder file value has no image service id but keeps the base url") {
          for {
            r     <- render(content(PlaceholderIri.instance.value))
            (_, m) = r
          } yield assertTrue(!m.contains(serviceIdProp), m.contains(baseUrlProp))
        },
        test("simple schema does not contain the image service id") {
          for {
            r     <- render(content(filename, ApiV2Simple), ApiV2Simple)
            (_, m) = r
          } yield assertTrue(!m.toString.contains("hasIIIFImageService"))
        },
        test("fileValueAsUrl points at the full-size default jpg") {
          for {
            r        <- render(content(filename))
            (base, m) = r
            obj       = m(fileAsUrlProp).asInstanceOf[JsonLDObject].value
          } yield assertTrue(
            obj("@value") == JsonLDString(base + "/0001/4sAf4AmPeeg-ZjDn3Tot1Zt.jp2/full/max/0/default.jpg"),
          )
        },
      ).provide(AppConfig.layer),
    )
}
