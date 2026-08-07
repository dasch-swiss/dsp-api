/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.tapir.EndpointOutput
import zio.*
import zio.test.*

import scala.compiletime.constValueTuple
import scala.deriving.Mirror

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughException

/**
 * Holds the endpoint's error-output variants against the sealed [[SparqlPassthroughException]] hierarchy.
 *
 * The variants are hand-enumerated -- tapir needs a codec per concrete type, so they cannot be derived -- and
 * `errorOutVariantsPrepend` appends a catch-all default variant after them. A seventh subtype added without a variant
 * would therefore compile cleanly and be answered as a generic `500` with `{"message":"Internal server error"}`,
 * which is the exact failure mode the per-endpoint error channel exists to prevent. Nothing in the compiler catches
 * that, so this does: the subtype names come from the hierarchy's own `Mirror`, so adding one and stopping there
 * fails here.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughErrorVariantsSpec extends ZIOSpecDefault {

  /** Every direct subtype of a sealed hierarchy, by simple name, taken from the compiler's own view of it. */
  private inline def subtypeNamesOf[A](using m: Mirror.SumOf[A]): Set[String] =
    constValueTuple[m.MirroredElemLabels].productIterator.map(_.toString).toSet

  private val errorOutput = ZIO
    .serviceWith[SparqlPassthroughEndpoints](_.postAdminSparqlQuery.endpoint.errorOutput)
    .provide(SparqlPassthroughTestEnv.layer())

  val spec: Spec[Any, Any] = suite("the SPARQL passthrough error variants")(
    test("cover every failure in the sealed hierarchy") {
      val covered = SparqlPassthroughEndpoints.errorVariants.map(_._1.getClass.getSimpleName).toSet
      assertTrue(covered == subtypeNamesOf[SparqlPassthroughException])
    },
    test("each one is matched by a variant of its own, not by the catch-all that answers a generic 500") {
      errorOutput.map {
        case oneOf: EndpointOutput.OneOf[?, ?] =>
          // `errorOutVariantsPrepend` puts the shared default variant last, and its `appliesTo` is always true.
          val specific = oneOf.variants.dropRight(1)
          assertTrue(
            SparqlPassthroughEndpoints.errorVariants.forall { case (failure, _) =>
              specific.exists(_.appliesTo(failure))
            },
          )
        case other => assertTrue(false).label(s"expected a oneOf error output, got ${other.show}")
      }
    },
  )
}
