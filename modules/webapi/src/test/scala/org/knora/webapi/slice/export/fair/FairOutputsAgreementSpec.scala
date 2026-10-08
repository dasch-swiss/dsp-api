/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import FairGraphFixtures.*
import org.junit.runner.RunWith
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class FairOutputsAgreementSpec extends ZIOSpecDefault {

  private val graphs =
    List("openWithFile" -> openWithFile, "restricted" -> restricted, "versioned" -> versioned, "bare" -> bare)

  private def field(json: Json, key: String): Option[Json] = json match {
    case Json.Obj(fields) => fields.collectFirst { case (`key`, v) => v }
    case _                => None
  }
  private def arr(json: Option[Json]): List[Json] = json match {
    case Some(Json.Arr(items)) => items.toList
    case _                     => Nil
  }
  private def str(json: Option[Json]): Option[String] = json.collect { case Json.Str(s) => s }

  val spec: Spec[Any, Nothing] = suite("FAIR outputs agree")(
    suite("JSON-LD and DataCite state the same facts")(graphs.map { case (name, g) =>
      test(name) {
        val ld = SchemaOrgJsonLd.render(g)
        val dc = DataCiteJson.render(g)
        assertTrue(
          str(field(ld, "@id")) == Some(g.ark),
          arr(field(dc, "identifiers")).flatMap(i => str(field(i, "identifier"))) == List(g.ark),
          str(field(ld, "name")) == Some(g.title),
          arr(field(dc, "titles")).flatMap(t => str(field(t, "title"))) == List(g.title),
          field(ld, "license").flatMap(l => str(field(l, "@id"))) ==
            arr(field(dc, "rightsList")).flatMap(r => str(field(r, "rightsUri"))).headOption,
          arr(field(ld, "creator")).flatMap(c => str(field(c, "name"))).toSet ==
            g.creators.map(_.name).toSet,
          field(ld, "distribution").isDefined == arr(field(dc, "formats")).nonEmpty,
        )
      }
    }*),
    suite("no output carries a placeholder or the internal app URL")(graphs.map { case (name, g) =>
      test(name) {
        val outputs = List(SchemaOrgJsonLd.toJsonString(g), DataCiteJson.toJsonString(g)) ++
          SchemaOrgTurtle.render(g).toOption.toList
        val forbidden = List("MISSING", "CALCULATED", "urn:dasch:placeholder", "http://app/", "localhost")
        assertTrue(outputs.size == 3, outputs.forall(o => forbidden.forall(f => !o.contains(f))))
      }
    }*),
  )
}
