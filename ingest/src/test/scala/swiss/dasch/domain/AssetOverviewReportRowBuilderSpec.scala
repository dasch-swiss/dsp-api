/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SupportedFileType.{Audio, MovingImage, OtherFiles, StillImage}
import zio.*
import zio.test.*

object AssetOverviewReportRowBuilderSpec extends ZIOSpecDefault {

  private val headerRow = AssetOverviewReportRowBuilder.headerRow

  val spec = suite("AssetOverviewReportRowBuilder")(
    test("should return the rows in correct order") {
      val report = AssetOverviewReport(
        ProjectShortcode.unsafeFrom("0007"),
        100,
        Map(Audio -> 3, MovingImage -> 2, OtherFiles -> 4, StillImage -> 1),
        SizeInBytesReport(Map.empty),
      )
        .add(SizeInBytesPerType(StillImage, FileSize(10), FileSize(20), FileSize.zero))
        .add(SizeInBytesPerType(MovingImage, FileSize(30), FileSize(40), FileSize(100)))
        .add(SizeInBytesPerType(Audio, FileSize(50), FileSize(60), FileSize.zero))
        .add(SizeInBytesPerType(OtherFiles, FileSize(70), FileSize(80), FileSize.zero));

      val headerAndValue = headerRow.zip(AssetOverviewReportRowBuilder.valueRow(report))

      assertTrue(
        headerAndValue == List(
          ("Project Shortcode", "0007"),
          ("No. Of All Assets Sum", 100),
          ("No. Of Still Image Assets", 1),
          ("No. Of Moving Image Assets", 2),
          ("No. Of Audio Assets", 3),
          ("No. Of Other Assets", 4),
          ("Size Of All Assets Sum", 460),
          ("Size Of Still Image Sum", 30),
          ("Size Of Still Image Originals", 10),
          ("Size Of Still Image Derivatives", 20),
          ("Size Of Moving Image Sum", 170),
          ("Size Of Moving Image Originals", 30),
          ("Size Of Moving Image Derivatives", 40),
          ("Size Of Moving Image Keyframes", 100),
          ("Size Of Audio Sum", 110),
          ("Size Of Audio Originals", 50),
          ("Size Of Audio Derivatives", 60),
          ("Size Of Other Sum", 150),
          ("Size Of Other Originals", 70),
          ("Size Of Other Derivatives", 80),
        ),
      );
    },
  )
}
