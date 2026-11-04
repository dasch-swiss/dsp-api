/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch

import org.junit.runner.RunWith
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.GravsearchException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchQueryCheckerSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def check(predicate: String, inConstructClause: Boolean) =
    ZIO
      .attempt(
        GravsearchQueryChecker.checkStatement(
          StatementPattern(QueryVariable("v"), IriRef(predicate.toSmartIri), QueryVariable("o")),
          ApiV2Complex,
          GravsearchTypeInspectionResult(Map.empty),
          inConstructClause,
        ),
      )
      .exit

  private val forbidden = Seq(
    OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFImageService,
    OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFBaseUrl,
  )

  override def spec: Spec[Any, Any] =
    suite("GravsearchQueryChecker.checkStatement")(
      suite("forbidden predicates")(
        for {
          predicate         <- forbidden
          inConstructClause <- Seq(false, true)
        } yield test(s"$predicate is rejected (inConstructClause = $inConstructClause)") {
          for {
            exit <- check(predicate, inConstructClause)
            err   = exit.causeOption.flatMap(_.failureOption)
          } yield assertTrue(
            err.exists(_.isInstanceOf[GravsearchException]),
            err.exists(_.getMessage.contains("cannot be used in a Gravsearch query")),
          )
        },
      ),
    )
}
