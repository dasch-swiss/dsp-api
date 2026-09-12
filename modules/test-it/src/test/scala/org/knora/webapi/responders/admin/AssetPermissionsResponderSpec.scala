/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.junit.runner.RunWith
import zio.ZIO
import zio.metrics.Metric
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.DerivativeAccess
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.OriginalAccess
import org.knora.webapi.slice.admin.domain.model.RestrictedView

@RunWith(classOf[DspZTestJUnitRunner])
class AssetPermissionsResponderSpec extends E2EZSpec {

  private val assetPermissionResponder = ZIO.serviceWithZIO[AssetPermissionsResponder]

  private val stillImage = InternalFilename.unsafeFrom("incunabula_0000003328.jp2")
  private val archive    = InternalFilename.unsafeFrom("asset-access-restricted.7z")
  private val audio      = InternalFilename.unsafeFrom("asset-access-restricted.mp3")
  private val document   = InternalFilename.unsafeFrom("asset-access-viewable.pdf")
  private val unmapped   = InternalFilename.unsafeFrom("asset-access-unmapped.holo")
  private val ambiguous  = InternalFilename.unsafeFrom("asset-access-ambiguous.jp2")

  // Named rather than imported: the counter's name is what an operator alerts on, so the test pins it.
  private val unresolvedFileValueClasses = Metric.counter("asset_access_unresolved_file_value_class")

  private def failsClosedAndCounts(filename: InternalFilename) =
    for {
      before <- unresolvedFileValueClasses.value
      access <- assetPermissionResponder(_.getAssetAccess(anonymousUser)(filename))
      after  <- unresolvedFileValueClasses.value
    } yield assertTrue(
      access.original == OriginalAccess.Withhold,
      access.derivative == DerivativeAccess.Denied,
      after.count == before.count + 1,
    )

  override val rdfDataObjects: List[RdfDataObject] = List(
    incunabulaRdfData,
    RdfDataObject(
      path = "test_data/project_data/asset-access-data.ttl",
      name = "http://www.knora.org/data/0803/asset-access",
    ),
  )

  override val e2eSpec = suite("The AssetPermissionsResponder")(
    test("grant the original and the full derivative to a project member") {
      assetPermissionResponder(_.getAssetAccess(incunabulaMemberUser)(stillImage)).map { access =>
        assertTrue(
          access.original == OriginalAccess.Grant,
          access.derivative == DerivativeAccess.Full,
        )
      }
    },
    test("clamp a still image and withhold its original under restricted view") {
      assetPermissionResponder(_.getAssetAccess(anonymousUser)(stillImage)).map { access =>
        assertTrue(
          access.original == OriginalAccess.Withhold,
          access.derivative == DerivativeAccess.Clamped(RestrictedView.Size.unsafeFrom("!512,512")),
        )
      }
    },
    test("stream audio and withhold its original under restricted view") {
      assetPermissionResponder(_.getAssetAccess(anonymousUser)(audio)).map { access =>
        assertTrue(
          access.original == OriginalAccess.Withhold,
          access.derivative == DerivativeAccess.Stream,
        )
      }
    },
    test("deny an archive and withhold its original under restricted view") {
      assetPermissionResponder(_.getAssetAccess(anonymousUser)(archive)).map { access =>
        assertTrue(
          access.original == OriginalAccess.Withhold,
          access.derivative == DerivativeAccess.Denied,
        )
      }
    },
    test("grant the original and the full derivative for a document with view permission") {
      assetPermissionResponder(_.getAssetAccess(anonymousUser)(document)).map { access =>
        assertTrue(
          access.original == OriginalAccess.Grant,
          access.derivative == DerivativeAccess.Full,
        )
      }
    },
    test("fail with NotFound for a filename no file value carries") {
      assetPermissionResponder(_.getAssetAccess(anonymousUser)(InternalFilename.unsafeFrom("nothing.jp2"))).exit
        .map(exit => assert(exit)(failsWithA[NotFoundException]))
    },
    test("withhold everything and count a file value class with no media kind") {
      failsClosedAndCounts(unmapped)
    },
    test("withhold everything and count a file value carrying two concrete classes") {
      failsClosedAndCounts(ambiguous)
    },
  )
}
