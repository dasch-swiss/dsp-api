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
    test("should accept a IIIF image request url version 1") {
      // https://iiif.io/api/image/1.0/#21-image-request-url-syntax
      // http[s]://server/[prefix/]identifier/region/size/rotation/quality[.format]
      val validUrls =
        Seq(
          // region https://iiif.io/api/image/1.0/#41-region
          "http://www.example.org/image-service/abcd1234/80,15,60,75/full/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/pct:10,10,80,70/full/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/full/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/100,/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/,100/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/pct:50/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/150,75/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/!150,75/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/90/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/180/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/270/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/22.5/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/600,/0/native.jpg",
          "http://www.example.org/image-service/abcd1234/full/600,/0/color.jpg",
          "http://www.example.org/image-service/abcd1234/full/600,/0/grey.jpg",
          "http://www.example.org/image-service/abcd1234/full/600,/0/bitonal.jpg",
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
