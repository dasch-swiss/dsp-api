/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.http.version.versioninfo

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class VersionInfoSpec extends AnyWordSpecLike with Matchers {

  "The version info" should {

    "contain all the necessary information" in {
      VersionInfo.name should be("webapi")

      // all regex match semver: https://gist.github.com/jhorsman/62eeea161a13b80e39f5249281e17c39
      VersionInfo.webapiVersion should fullyMatch regex """^(v[0-9]+)\.([0-9]+)\.([0-9]+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+)?$"""
      VersionInfo.scalaVersion should fullyMatch regex """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+)?$"""
      VersionInfo.akkaHttpVersion should fullyMatch regex """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+)?$"""
      VersionInfo.sipiVersion should fullyMatch regex """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+)?$"""
      VersionInfo.jenaFusekiVersion should fullyMatch regex """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+)?$"""
    }
  }
}
