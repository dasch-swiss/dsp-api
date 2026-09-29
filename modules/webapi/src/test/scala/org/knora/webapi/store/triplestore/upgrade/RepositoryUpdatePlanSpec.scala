/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import scala.io.Source

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.knoraBaseVersionFrom
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan.makePluginsForVersions

@RunWith(classOf[DspZTestJUnitRunner])
class RepositoryUpdatePlanSpec extends ZIOSpecDefault {

  private val ontologyVersionPattern = """:ontologyVersion\s+"(knora-base v\d+)"""".r

  private def ontologyVersionFromTtl(): Option[Int] = {
    val is      = getClass.getClassLoader.getResourceAsStream("knora-ontologies/knora-base.ttl")
    val content =
      try Source.fromInputStream(is)(using scala.io.Codec.UTF8).mkString
      finally is.close()
    ontologyVersionPattern.findFirstMatchIn(content).flatMap(m => knoraBaseVersionFrom(m.group(1)))
  }

  def spec: Spec[Any, Nothing] = suite("RepositoryUpdatePlan")(
    test(
      "KnoraBaseVersion, the knora-base.ttl :ontologyVersion, and the highest upgrade plugin versionNumber stay in lockstep",
    ) {
      val ttlVersion       = ontologyVersionFromTtl()
      val maxPluginVersion = makePluginsForVersions.map(_.versionNumber).max
      assertTrue(
        ttlVersion.contains(KnoraBaseVersion),
        maxPluginVersion == KnoraBaseVersion,
      )
    },
  )
}
