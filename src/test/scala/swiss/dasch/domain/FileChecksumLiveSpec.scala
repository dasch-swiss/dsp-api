/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.test.SpecPaths.pathFromResource
import zio.nio.file.*
import zio.test.{ ZIOSpecDefault, assertTrue }

object FileChecksumLiveSpec extends ZIOSpecDefault {

  private val checksumOrig = "fb252a4fb3d90ce4ebc7e123d54a4112398a7994541b11aab5e4230eac01a61c"
  private val fileprefix   = "test-folder-structure/0001/fg/il/FGiLaT4zzuV-CqwbEDFAFeS"

  val spec = suite("FileChecksumLiveSpec")(
    test("checksum of .jp2.orig should be correct") {
      for {
        checksum <-
          FileChecksum.checksum(pathFromResource(fileprefix + ".jp2.orig"))
      } yield assertTrue(checksum == checksumOrig)
    },
    test("checksum of .jp2 should not match orig") {
      for {
        checksum <-
          FileChecksum.checksum(pathFromResource(fileprefix + ".jp2"))
      } yield assertTrue(checksum != checksumOrig)
    },
  ).provide(FileChecksumLive.layer)
}
