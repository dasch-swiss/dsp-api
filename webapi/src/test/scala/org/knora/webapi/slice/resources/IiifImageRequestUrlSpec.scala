/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

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
          // V1 https://iiif.io/api/image/1.0/#21-image-request-url-syntax
          "http://www.example.org/prefix1/abcd1234/80,15,60,75/full/0/native",
          "http://www.example.org/prefix1/prefix2/prefix3/abcd1234/80,15,60,75/full/0/native",
          "https://www.example.org/prefix1/abcd1234/80,15,60,75/full/0/native",
          "http://www.example.org/prefix1/abcd1234/80,15,60,75/full/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/pct:10,10,80,70/full/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/full/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/100,/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/,100/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/pct:50/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/150,75/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/!150,75/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/90/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/180/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/270/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/22.5/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/600,/0/native.jpg",
          "http://www.example.org/prefix1/abcd1234/full/600,/0/color.jpg",
          "http://www.example.org/prefix1/abcd1234/full/600,/0/grey.jpg",
          "http://www.example.org/prefix1/abcd1234/full/600,/0/bitonal.jpg",
          "http://www.example.org/prefix1/dasch.swiss/full/600,/0/bitonal.jpg",
          // V2 https://iiif.io/api/image/2.0/#image-request-uri-syntax
          "http://www.example.org/prefix1/prefix2/prefix3/prefix4/abcd1234/full/full/0/default.jpg",
          "https://www.example.org/prefix1/prefix2/prefix3/prefix4/abcd1234/full/full/!90/gray.webp",
          // V3 https://iiif.io/api/image/3.0/#21-image-request-uri-syntax
          "http://www.example.org/prefix1/prefix2/prefix3/prefix4/abcd1234/full/max/0/default.jpg",
          "https://www.example.org/prefix1/prefix2/prefix3/prefix4/abcd1234/square/%5Emax/0/gray.webp",
        )
      check(Gen.fromIterable(validUrls)) { url =>
        val actual = IiifImageRequestUrl.from(url)
        assertTrue(actual.isRight, actual == Right(IiifImageRequestUrl(URI.create(url).toURL)))
      }
    },
    suite("should reject invalid IIIF image request url")(
      test("should reject a IIIF image information request url") {
        val invalidUrls =
          Seq(
            "https://iiif.ub.unibe.ch/image/v2.1/632664f2-20cb-43e4-8584-2fa3988c63a2/info.json",
            "https://iiif.dasch.swiss/0811/5Jd909CLmCJ-BUUL1DDOXGJ.jp2/info.json",
            "ftp://www.example.org/prefix1/prefix2/prefix3/prefix4/abcd1234/square/%5Emax/0/gray.webp",
          )
        check(Gen.fromIterable(invalidUrls)) { url =>
          val actual = IiifImageRequestUrl.from(url)
          assertTrue(actual.isLeft)
        }
      },
      test("should reject dasch.swiss domain IIIF image request url") {
        val invalidUrls =
          Seq(
            "https://iiif.dasch.swiss/0811/1Oi7mdiLsG7-FmFgp0xz2xU.jp2/full/max/0/default.jpg",
            "https://iiif.dasch.swiss/0811/1Oi7mdiLsG7-FmFgp0xz2xU.jp2/full/max/0/default.jpg",
            "https://iiif.dasch.swiss/0811/1Oi7mdiLsG7-FmFgp0xz2xU.jp2/full/max/0/default.jpg",
          )
        check(Gen.fromIterable(invalidUrls)) { url =>
          val actual = IiifImageRequestUrl.from(url)
          assertTrue(actual.isLeft)
        }
      },
    ),
  )
}
