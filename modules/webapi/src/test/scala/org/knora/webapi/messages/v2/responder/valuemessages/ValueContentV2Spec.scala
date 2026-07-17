/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService

object ValueContentV2Spec extends ZIOSpecDefault {

  private val assetId = AssetId.unsafeFrom("4sAf4AmPeeg-ZjDn3Tot1Zt")

  private val jsonLdObj = JsonLDUtil
    .parseJsonLD(s"{\"http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename\" : \"$assetId.txt\"}")
    .body

  private val expected      = FileMetadataSipiResponse(Some("origName"), None, "text/plain", None, None, None, None, None)
  private val shortcode0001 = KnoraProject.Shortcode.unsafeFrom("0001")

  override def spec: Spec[Any, Option[Throwable]] =
    suite("ValueContentV2")(
      suite("getFileInfo")(
        suite("Given the asset is ingested")(
          test("When getting file metadata from dsp-ingest, then it should succeed") {
            for {
              ingested <- ValueContentV2.getFileInfo(shortcode0001, jsonLdObj).some
            } yield assertTrue(ingested.metadata == expected)
          },
        ).provide(mockSipi()),
      ),
      suite("GeomValueContentV2.parsePoints")(
        test("parses a rectangle geometry into its list of points") {
          val geom =
            """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.3979668674698795,"y":0.24423475150602414},{"x":0.4906285297439759,"y":0.42000423569277123}],"type":"rectangle"}"""
          val result = GeomValueContentV2.parsePoints(geom)
          assertTrue(
            result == Right(
              List(
                GeomValueContentV2.Point(0.3979668674698795, 0.24423475150602414),
                GeomValueContentV2.Point(0.4906285297439759, 0.42000423569277123),
              ),
            ),
          )
        },
        test("returns an empty list when the points array is empty") {
          val result = GeomValueContentV2.parsePoints("""{"points":[]}""")
          assertTrue(result == Right(List.empty))
        },
        test("returns a Left when the input is not valid JSON") {
          val result = GeomValueContentV2.parsePoints("not json")
          assertTrue(result.isLeft)
        },
        test("returns a Left when the points field is missing") {
          val result = GeomValueContentV2.parsePoints("""{"type":"rectangle"}""")
          assertTrue(result.isLeft)
        },
      ),
      suite("GeomValueContentV2.parseShape")(
        test("extracts geomType 'rectangle' (the region-preview crop/highlight gate)") {
          val geom =
            """{"status":"active","points":[{"x":0.1,"y":0.2},{"x":0.3,"y":0.4}],"type":"rectangle"}"""
          val result = GeomValueContentV2.parseShape(geom)
          assertTrue(result.map(_.geomType) == Right(Some("rectangle")), result.map(_.points.size) == Right(2))
        },
        test("extracts a non-rectangle geomType (crop/highlight are gated off for it)") {
          val geom   = """{"points":[{"x":0.1,"y":0.2},{"x":0.3,"y":0.4},{"x":0.5,"y":0.1}],"type":"polygon"}"""
          val result = GeomValueContentV2.parseShape(geom)
          assertTrue(result.map(_.geomType) == Right(Some("polygon")))
        },
        test("yields geomType None when the type field is absent") {
          val result = GeomValueContentV2.parseShape("""{"points":[]}""")
          assertTrue(result.map(_.geomType) == Right(None))
        },
      ),
    )

  private def mockSipi() = ZLayer.succeed(new SipiService {
    override def getFileMetadataFromDspIngest(
      shortcode: KnoraProject.Shortcode,
      assetId: AssetId,
    ): Task[FileMetadataSipiResponse] =
      ZIO.succeed(expected)

    // The following are unsupported operations because they are not used in the test
    def getTextFileRequest(fileUrl: String, senderName: String): Task[String] =
      ZIO.dieMessage("unsupported operation")
  })
}
