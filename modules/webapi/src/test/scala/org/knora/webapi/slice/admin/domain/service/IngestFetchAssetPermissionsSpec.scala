/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import eu.timepit.refined.types.string.NonEmptyString
import org.junit.runner.RunWith
import swiss.dasch.domain.AssetId
import swiss.dasch.domain.AssetInfo
import swiss.dasch.domain.AssetRef
import swiss.dasch.domain.FileAndChecksum
import swiss.dasch.domain.OtherMetadata
import swiss.dasch.domain.ProjectShortcode
import swiss.dasch.domain.Sha256Hash
import zio.*
import zio.nio.file.Path
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.model.AssetAccess
import org.knora.webapi.slice.admin.domain.model.InternalFilename
import org.knora.webapi.slice.admin.domain.model.MediaKind
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv

@RunWith(classOf[DspZTestJUnitRunner])
class IngestFetchAssetPermissionsSpec extends ZIOSpecDefault {

  private val token = "system-user-token"

  private def assetInfo(derivative: String): AssetInfo = {
    val checksum = Sha256Hash.unsafeFrom("0" * 64)
    AssetInfo(
      AssetRef(AssetId.unsafeFrom("asset"), ProjectShortcode.unsafeFrom("0001")),
      FileAndChecksum(Path("asset.jpg.orig"), checksum),
      NonEmptyString.unsafeFrom("asset.jpg"),
      FileAndChecksum(Path(derivative), checksum),
      OtherMetadata(None, None),
    )
  }

  /** Grants the original of `granted.jp2` to the system user only, and records each lookup's key. */
  private def permissions(lookups: Ref[List[AssetPermissionsCache.CacheKey]]) =
    AssetPermissionsCache
      .makeCache(10, 1.minute) { key =>
        val grant = key.userIri == UserIri.unsafeFrom(SystemUser.id) &&
          key.filename == InternalFilename.unsafeFrom("granted.jp2")
        val perm = Option.when(grant)(Permission.ObjectAccess.View)
        lookups.update(key :: _).as(AssetAccess.from(perm, MediaKind.RasterStillImage, None))
      }
      .map(cache =>
        IngestFetchAssetPermissions(
          SparqlPassthroughTestEnv.tokenAuthenticator(token, SystemUser),
          AssetPermissionsCache(cache),
        ),
      )

  val spec = suite("IngestFetchAssetPermissions")(
    test("grants the original when dsp-api grants it to the token's user") {
      for {
        lookups <- Ref.make(List.empty[AssetPermissionsCache.CacheKey])
        granted <- permissions(lookups).flatMap(_.isOriginalGranted(Some(token), assetInfo("granted.jp2")))
        keys    <- lookups.get
      } yield assertTrue(
        granted,
        keys == List(
          AssetPermissionsCache.CacheKey(UserIri.unsafeFrom(SystemUser.id), InternalFilename.unsafeFrom("granted.jp2")),
        ),
      )
    },
    test("denies the original when dsp-api withholds it") {
      for {
        lookups <- Ref.make(List.empty[AssetPermissionsCache.CacheKey])
        granted <- permissions(lookups).flatMap(_.isOriginalGranted(Some(token), assetInfo("other.jp2")))
      } yield assertTrue(!granted)
    },
    test("decides for the anonymous user without a token") {
      for {
        lookups <- Ref.make(List.empty[AssetPermissionsCache.CacheKey])
        granted <- permissions(lookups).flatMap(_.isOriginalGranted(None, assetInfo("granted.jp2")))
        keys    <- lookups.get
      } yield assertTrue(!granted, keys.map(_.userIri) == List(UserIri.unsafeFrom(AnonymousUser.id)))
    },
    test("fails for a token that does not authenticate") {
      for {
        lookups <- Ref.make(List.empty[AssetPermissionsCache.CacheKey])
        result  <- permissions(lookups).flatMap(_.isOriginalGranted(Some("invalid"), assetInfo("granted.jp2"))).exit
        keys    <- lookups.get
      } yield assertTrue(result.isFailure, keys.isEmpty)
    },
  )
}
