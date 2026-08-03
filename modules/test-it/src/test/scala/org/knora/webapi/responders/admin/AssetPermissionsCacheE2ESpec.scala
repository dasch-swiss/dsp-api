/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.junit.runner.RunWith
import zio.ZIO
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.api.admin.model.ProjectRestrictedViewSettingsADM

/**
 * Integration spec for the wired [[AssetPermissionsCache]] against a real triplestore. It mirrors
 * [[AssetPermissionsResponderSpec]] so the cache is shown to reproduce the responder's decisions end-to-end, and pins
 * the one genuine divergence: for an anonymous request the endpoint injects the hardcoded `AnonymousUser` constant,
 * whereas the cache re-derives the user from its IRI via `findUserByIri`. Those two are asserted decision-equivalent.
 *
 * No cache-state isolation is needed here: each test compares the cache's decision to the direct responder decision (or
 * to a fixed literal) for given inputs, which holds whether the entry is a hit or a miss; hit/miss/eviction accounting
 * is covered by the pure-JVM `AssetPermissionsCacheSpec`.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class AssetPermissionsCacheE2ESpec extends E2EZSpec {

  private val cache     = ZIO.serviceWithZIO[AssetPermissionsCache]
  private val responder = ZIO.serviceWithZIO[AssetPermissionsResponder]
  private val asset     = InternalFilename.unsafeFrom("incunabula_0000003328.jp2")

  override val rdfDataObjects: List[RdfDataObject] = List(incunabulaRdfData)

  override val e2eSpec = suite("The AssetPermissionsCache (wired, real triplestore)")(
    test("serve a full-quality decision for an authenticated project member (code 6, no settings)") {
      cache(
        _.getPermissionCodeAndProjectRestrictedViewSettings(incunabulaMemberUser)(incunabulaProject.shortcode, asset),
      ).map(actual => assertTrue(actual == PermissionCodeAndProjectRestrictedViewSettings(permissionCode = 6, None)))
    },
    test("serve a restricted-view decision for an anonymous request (code 1 + settings)") {
      cache(
        _.getPermissionCodeAndProjectRestrictedViewSettings(anonymousUser)(incunabulaProject.shortcode, asset),
      ).map(actual =>
        assertTrue(
          actual == PermissionCodeAndProjectRestrictedViewSettings(
            permissionCode = 1,
            Some(ProjectRestrictedViewSettingsADM(size = Some("!512,512"), watermark = false)),
          ),
        ),
      )
    },
    test("treats the shortcode as non-authoritative and not part of the cache key (DEV-6806 regression)") {
      // The `{shortcode}` path segment must never affect the decision, and must not be part of the cache key —
      // otherwise an anonymous caller varying only the shortcode over one filename produces unlimited distinct
      // keys that all resolve to the same value, evicting genuinely-hot entries.
      for {
        decisionA <-
          cache(
            _.getPermissionCodeAndProjectRestrictedViewSettings(anonymousUser)(incunabulaProject.shortcode, asset),
          )
        decisionB <-
          cache(
            _.getPermissionCodeAndProjectRestrictedViewSettings(anonymousUser)(Shortcode.unsafeFrom("0001"), asset),
          )
      } yield assertTrue(decisionA == decisionB)
    },
    test(
      "cached anonymous decision equals the direct responder decision with the constant AnonymousUser (REQ-1.5/1.3)",
    ) {
      for {
        cached <-
          cache(
            _.getPermissionCodeAndProjectRestrictedViewSettings(anonymousUser)(incunabulaProject.shortcode, asset),
          )
        direct <-
          responder(_.getPermissionCodeAndProjectRestrictedViewSettings(anonymousUser)(asset))
      } yield assertTrue(cached == direct)
    },
    test("cached authenticated decision equals the direct responder decision (REQ-1.3)") {
      for {
        cached <- cache(
                    _.getPermissionCodeAndProjectRestrictedViewSettings(incunabulaMemberUser)(
                      incunabulaProject.shortcode,
                      asset,
                    ),
                  )
        direct <- responder(_.getPermissionCodeAndProjectRestrictedViewSettings(incunabulaMemberUser)(asset))
      } yield assertTrue(cached == direct)
    },
  )
}
