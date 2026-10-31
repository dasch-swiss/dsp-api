/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import org.junit.runner.RunWith
import sttp.tapir.AnyEndpoint
import zio.*
import zio.test.*

import scala.io.Source
import scala.util.Using

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.slice.common.api.Policy

/**
 * The endpoint policy ratchet (ADR-0013): an endpoint either declares a [[Policy]] or is listed in the baseline
 * `endpoints-without-policy.txt`, and the baseline only shrinks.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class EndpointPolicyE2ESpec extends E2EZSpec {

  private val baselineResource = "endpoints-without-policy.txt"

  // Rendered like the span name in `DspApiServer`, so a baseline line reads like the route in a trace.
  private def key(ep: AnyEndpoint): String =
    s"${ep.method.map(_.method).getOrElse("HTTP")} ${ep.showPathTemplate(showQueryParam = None, showQueryParamsAs = None)}"

  private val endpoints = ZIO.serviceWith[Endpoints](_.serverEndpoints.map(_.endpoint))

  private val baseline: Task[Set[String]] = ZIO.attempt {
    Using.resource(Source.fromResource(baselineResource))(
      _.getLines().map(_.trim).filterNot(line => line.isEmpty || line.startsWith("#")).toSet,
    )
  }

  private def sortedLines(keys: Iterable[String]): String = keys.toList.sorted.mkString("\n")

  // Pinned so that an appended baseline line fails; lowered in the same commit that removes lines.
  private val baselineSize = 177

  override def e2eSpec = suite("Endpoint policy ratchet")(
    test("every endpoint has a unique METHOD /path key") {
      endpoints.map { all =>
        val duplicates = all.groupBy(key).collect { case (k, eps) if eps.size > 1 => k }
        assertTrue(all.nonEmpty) &&
        (assertTrue(duplicates.isEmpty) ?? s"these keys name more than one endpoint:\n${sortedLines(duplicates)}")
      }
    },
    test("every endpoint without a policy is in the baseline") {
      for {
        all    <- endpoints
        listed <- baseline
        missing = all.filter(_.attribute(Policy.key).isEmpty).map(key).toSet -- listed
      } yield assertTrue(missing.isEmpty) ??
        s"""These endpoints declare no Policy. Build them with a policy-declaring factory in BaseEndpoints
           |(publicPolicyEndpoint, authenticatedPolicyEndpoint, systemAdminPolicyEndpoint,
           |projectAdminPolicyEndpoint, objectAccessPolicyEndpoint); never add them to $baselineResource:
           |${sortedLines(missing)}""".stripMargin
    },
    test("the baseline names only existing endpoints without a policy") {
      for {
        all    <- endpoints
        listed <- baseline
        stale   = listed -- all.filter(_.attribute(Policy.key).isEmpty).map(key)
      } yield assertTrue(stale.isEmpty) ??
        s"remove these lines from $baselineResource (the endpoint declares a policy or no longer exists):\n${sortedLines(stale)}"
    },
    test("the baseline has the pinned size") {
      baseline.map(listed =>
        assertTrue(listed.size == baselineSize) ??
          s"""$baselineResource lists ${listed.size} endpoints, but baselineSize is $baselineSize. The baseline only
             |shrinks: when you remove lines, lower baselineSize in the same commit. Never add lines and never raise
             |baselineSize; build a new endpoint with a policy-declaring factory in BaseEndpoints instead.""".stripMargin,
      )
    },
    test("every Public and Authenticated policy gives a reason") {
      endpoints.map { all =>
        val withoutReason = all.filter { ep =>
          ep.attribute(Policy.key).exists {
            case Policy.Public(reason)        => reason.isBlank
            case Policy.Authenticated(reason) => reason.isBlank
            case _                            => false
          }
        }.map(key)
        assertTrue(withoutReason.isEmpty) ?? s"give these policies a non-blank reason:\n${sortedLines(withoutReason)}"
      }
    },
    test("every endpoint with a policy states its requirement in its description") {
      endpoints.map { all =>
        val undescribed = all.flatMap { ep =>
          ep.attribute(Policy.key)
            .map(_.requirement)
            .filterNot(requirement => ep.info.description.exists(_.contains(requirement)))
            .map(requirement => s"${key(ep)}: $requirement")
        }
        assertTrue(undescribed.isEmpty) ??
          s"add the policy's requirement sentence to these endpoint descriptions:\n${sortedLines(undescribed)}"
      }
    },
  )
}
