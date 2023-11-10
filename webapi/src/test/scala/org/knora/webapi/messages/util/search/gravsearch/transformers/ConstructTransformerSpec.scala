/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object ConstructTransformerSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def constructTransformerTransform(q: ConstructQuery, limit: Option[Set[SmartIri]] = None) =
    ZIO.serviceWithZIO[ConstructTransformer](_.transform(q, limit))

  val spec: Spec[Any, Any] = suite("ConstructTransformerLive")(
    test(
      "Given an optional pattern in the Where clause it should transform the inner but not split the pattern"
    ) {
      val query = ConstructQuery(
        constructClause = ConstructClause(
          statements = Vector(
            StatementPattern(
              subj = QueryVariable("letter"),
              pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
              obj = XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri)
            )
          )
        ),
        whereClause = WhereClause(
          patterns = Vector(
            OptionalPattern(
              patterns = Vector(
                StatementPattern(
                  subj = QueryVariable("person1"),
                  pred = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri),
                  obj = QueryVariable("name")
                ),
                StatementPattern(
                  subj = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri),
                  pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                  obj = IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri)
                )
              )
            )
          )
        )
      )
      for {
        transformed <- constructTransformerTransform(query)
      } yield assertTrue(transformed.whereClause.patterns.count(_.isInstanceOf[OptionalPattern]) == 1)
    }
  ).provide(
    ConstructTransformer.layer,
    IriConverter.layer,
    StringFormatter.test,
    OntologyInferencer.layer,
    OntologyCacheFake.emptyCache
  )
}
