package org.knora.webapi.slice.resources

import zio.Scope
import zio.test.Gen
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import java.net.URI

object IiifImageRequestUrlSpec extends ZIOSpecDefault {

  val spec: Spec[TestEnvironment with Scope, Nothing] = suite("IiifImageRequestUrl")(
    test("should accept a IIIF image request url") {
      val validUrls =
        Seq(
          "https://iiif.ub.unibe.ch/image/v2.1/632664f2-20cb-43e4-8584-2fa3988c63a2/full/max/0/default.jpg",
          "https://iiif.dasch.swiss/0811/5Jd909CLmCJ-BUUL1DDOXGJ.jp2/full/3360,2123/0/default.jpg",
        )
      check(Gen.fromIterable(validUrls)) { url =>
        val actual = IiifImageRequestUrl.from(url)
        assertTrue(actual.isRight, actual == Right(IiifImageRequestUrl(URI.create(url).toURL)))
      }
    },
    test("should reject a IIIF image information request url") {
      val invalidUrls =
        Seq(
          "https://iiif.ub.unibe.ch/image/v2.1/632664f2-20cb-43e4-8584-2fa3988c63a2/info.json",
          "https://iiif.dasch.swiss/0811/5Jd909CLmCJ-BUUL1DDOXGJ.jp2/info.json",
        )
      check(Gen.fromIterable(invalidUrls)) { url =>
        val actual = IiifImageRequestUrl.from(url)
        assertTrue(actual.isLeft)
      }
    },
  )
}
