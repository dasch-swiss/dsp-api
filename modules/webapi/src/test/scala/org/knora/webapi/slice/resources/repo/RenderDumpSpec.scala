/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class RenderDumpSpec extends ZIOSpecDefault {
  private val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history")
  private val startDate   = Instant.parse("2018-06-04T00:00:00Z")
  private val endDate     = Instant.parse("2018-06-05T00:00:00Z")

  private def algebra(q: String): String = Algebra.compile(QueryFactory.create(q)).toString

  override def spec: Spec[TestEnvironment, Any] = test("parity + dump") {
    val args = for {
      withDeleted <- Seq(false, true)
      dates       <- Seq((None, None), (Some(startDate), Some(endDate)), (Some(startDate), None), (None, Some(endDate)))
    } yield (withDeleted, dates._1, dates._2)

    val results = args.map { case (withDeleted, s, e) =>
      val oldQ = OldVersionHistoryQuery.build(resourceIri, withDeleted, s, e).getQueryString
      val newQ = GetResourceValueVersionHistoryQuery.build(resourceIri, withDeleted, s, e)
      val same = algebra(oldQ) == algebra(newQ)
      if (!same) System.out.println(s"MISMATCH ($withDeleted,$s,$e)\nOLD:\n${algebra(oldQ)}\nNEW:\n${algebra(newQ)}")
      same
    }

    Seq(
      "A" -> GetResourceValueVersionHistoryQuery.build(resourceIri, false, None, None),
      "B" -> GetResourceValueVersionHistoryQuery.build(resourceIri, true, None, None),
      "C" -> GetResourceValueVersionHistoryQuery.build(resourceIri, false, Some(startDate), Some(endDate)),
    ).foreach { case (n, q) => System.out.println(s"<<<<$n\n$q\n>>>>$n") }

    assertTrue(results.forall(identity))
  }
}
