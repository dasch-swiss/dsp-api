/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.test.SpecPaths.pathFromResource
import zio.Scope
import zio.json.*
import zio.nio.charset.Charset
import zio.nio.file.{ Files, Path }
import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue }

object AssetInfoFileContentSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AssetInfoFileContent")(test("parsing a file works") {
      for {
        actual <- Files
                    .readAllLines(pathFromResource("test-folder-structure/0001/fg/il/FGiLaT4zzuV-CqwbEDFAFeS.info"))
                    .map(lines => lines.mkString.fromJson[AssetInfoFileContent])
      } yield assertTrue(
        actual.contains(
          AssetInfoFileContent(
            internalFilename = "FGiLaT4zzuV-CqwbEDFAFeS.jp2",
            originalInternalFilename = "FGiLaT4zzuV-CqwbEDFAFeS.jp2.orig",
            originalFilename = "250x250.jp2",
            checksumOriginal = "fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c",
            checksumDerivative = "0ce405c9b183fb0d0a9998e9a49e39c93b699e0f8e2a9ac3496c349e5cea09cc",
          )
        )
      )
    })
}
