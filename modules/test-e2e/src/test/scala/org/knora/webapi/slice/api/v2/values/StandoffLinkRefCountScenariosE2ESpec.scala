/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * The `valueHasRefCount` of a standoff link value counts the current text values of the source resource that link to
 * the target, across properties and mappings. After every step the count of each link is asserted exactly, and the
 * reference-count invariant holds.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountScenariosE2ESpec extends E2EZSpec {

  private val log = new LogCapture

  override protected def additionalLoggerLayer: ULayer[Unit] = Runtime.addLogger(log.logger)

  /** The current link value version from source to target. */
  private def linkValueOf(source: String, target: String): RIO[TriplestoreService, String] =
    selectFirst(
      s"""PREFIX kb: <$kb>
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |SELECT ?lv WHERE { GRAPH <$dataGraph> {
         |  <$source> kb:hasStandoffLinkToValue ?lv .
         |  ?lv rdf:object <$target> ; kb:isDeleted false } }""".stripMargin,
      "lv",
    ).someOrFail(new RuntimeException(s"no link value from $source to $target"))

  /** The predicates of a resource version, without their objects. */
  private def predicatesOf(iri: String): RIO[TriplestoreService, Set[String]] =
    select(s"SELECT DISTINCT ?p WHERE { GRAPH <$dataGraph> { <$iri> ?p ?o } }").map(_.getCol("p").toSet)

  /** The predicate of every triple of a resource version, with duplicates. */
  private def predicateRowsOf(iri: String): RIO[TriplestoreService, Seq[String]] =
    select(s"SELECT ?p WHERE { GRAPH <$dataGraph> { <$iri> ?p ?o } }").map(_.getCol("p"))

  /** The objects of the given predicate of a resource version, one entry per triple. */
  private def objectsOf(iri: String, predicate: String): RIO[TriplestoreService, Seq[String]] =
    select(s"SELECT ?o WHERE { GRAPH <$dataGraph> { <$iri> <$predicate> ?o } }").map(_.getCol("o"))

  /** Erases the text value; returns the status code and the response body. */
  private def eraseV(resource: String, property: String, valueIri: String) = {
    val body = Json
      .Obj(
        "@id"      -> Json.Str(resource),
        "@type"    -> Json.Str("anything:Thing"),
        property   -> Json.Obj("@id" -> Json.Str(valueIri), "@type" -> Json.Str("knora-api:TextValue")),
        "@context" -> context,
      )
      .toString
    TestApiClient
      .postJsonLd(uri"/v2/values/erase", body, rootUser)
      .map(response => response.code -> response.body.merge)
  }

  private val rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns"

  private val eraseRefusal = "Erasing standoff text values with links is not supported"

  private val standard = StandoffMappingIri.StandardMapping
  private val text     = "anything:hasText"
  private val richtext = "anything:hasRichtext"

  private def linkTag(target: String): String = s"""<a class="salsah-link" href="$target">link</a>"""

  private def paragraph(label: String, targets: String*): String =
    xmlDoc(s"<p>$label ${targets.map(linkTag).mkString(" ")}</p>")

  /** The `valueHasRefCount` of the current link value from source to target; 0 if there is none. */
  private def refCount(source: String, target: String): RIO[TriplestoreService, Int] =
    selectFirst(
      s"""PREFIX kb: <$kb>
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |SELECT ?count WHERE { GRAPH <$dataGraph> {
         |  <$source> kb:hasStandoffLinkToValue ?lv .
         |  ?lv rdf:object <$target> ; kb:isDeleted false ; kb:valueHasRefCount ?count } }""".stripMargin,
      "count",
    ).map(_.fold(0)(_.toInt))

  private def directLinkExists(source: String, target: String): RIO[TriplestoreService, Boolean] =
    ask(s"ASK { GRAPH <$dataGraph> { <$source> <${kb}hasStandoffLinkTo> <$target> } }")

  /** Asserts the exact count of each link, that the direct link exists exactly when the count is above zero, and the invariant. */
  private def expectCounts(source: String, expected: (String, Int)*): RIO[TriplestoreService, TestResult] =
    for {
      actual    <- ZIO.foreach(expected)((target, _) => refCount(source, target))
      direct    <- ZIO.foreach(expected)((target, _) => directLinkExists(source, target))
      invariant <- refCountsConsistent(source)
    } yield assertTrue(
      actual == expected.map(_._2),
      direct == expected.map(_._2 > 0),
    ) && invariant

  private def removeStoredXml(valueIri: String): RIO[TriplestoreService, Unit] =
    update(
      s"DELETE WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueHasXml> ?xml } }",
    )

  private def freshName(prefix: String): UIO[String] =
    Random.nextUUID.map(id => s"$prefix${id.toString.take(8)}")

  private def eraseResource(resource: String): RIO[TestApiClient & TriplestoreService, Unit] =
    for {
      modified <- selectFirst(
                    s"SELECT ?d WHERE { GRAPH <$dataGraph> { <$resource> <${kb}lastModificationDate> ?d } }",
                    "d",
                  )
      lastModification = modified.fold("")(d => s""""knora-api:lastModificationDate": {
                                                   |  "@type": "xsd:dateTimeStamp", "@value": "$d" },""".stripMargin)
      erase = s"""{
                 |  "@id": "$resource",
                 |  "@type": "anything:Thing",
                 |  $lastModification
                 |  "@context": {
                 |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                 |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                 |    "anything": "$anythingOnto"
                 |  }
                 |}""".stripMargin
      _ <- TestApiClient.postJsonLd(uri"/v2/resources/erase", erase, anythingAdminUser).flatMap(_.assert200)
    } yield ()

  /** A source resource with value A (standard mapping, hasText) linking to T, and targets T and U. */
  private final case class Fixture(source: String, t: String, u: String, a: String)

  private val fixtureWithA =
    for {
      t      <- createThing()
      u      <- createThing()
      source <- createThing()
      a      <- createValue(source, textValueJson(paragraph("A", t), standard, None), property = text)
    } yield Fixture(source, t, u, a)

  /** Fixture plus B (standard mapping, hasRichtext) and C (custom mapping, hasRichtext), each linking to T. */
  private val fixtureWithABC =
    for {
      f      <- fixtureWithA
      b      <- createValue(f.source, textValueJson(paragraph("B", f.t), standard, None), property = richtext)
      name   <- freshName("RefCountCustom")
      custom <- createCustomMapping(name)
      c      <- createValue(f.source, textValueJson(paragraph("C", f.t), custom, None), property = richtext)
    } yield (f, b, c, custom)

  private def editScenario(f: Fixture) =
    for {
      r1 <- expectCounts(f.source, f.t -> 1, f.u -> 0)
      a2 <- updateText(f.source, f.a, paragraph("A without link"), standard, property = text)
      r2 <- expectCounts(f.source, f.t -> 0, f.u -> 0)
      a3 <- updateText(f.source, a2, paragraph("A with link", f.t), standard, property = text)
      r3 <- expectCounts(f.source, f.t -> 1, f.u -> 0)
      a4 <- updateText(f.source, a3, paragraph("A moved", f.u), standard, property = text)
      r4 <- expectCounts(f.source, f.t -> 0, f.u -> 1)
      _  <- updateText(f.source, a4, paragraph("A text changed only", f.u), standard, property = text)
      r5 <- expectCounts(f.source, f.t -> 0, f.u -> 1)
    } yield r1 && r2 && r3 && r4 && r5

  override val e2eSpec: Spec[env, Any] = suite("standoff link reference counts")(
    test("erase is refused while the current version links") {
      for {
        f               <- fixtureWithA
        (code, message) <- eraseV(f.source, text, f.a)
        r1              <- expectCounts(f.source, f.t -> 1, f.u -> 0)
        stored          <- storedXml(f.a)
      } yield assertTrue(code == StatusCode.BadRequest, message.contains(eraseRefusal), stored.isDefined) && r1
    },
    test("erase is refused while only the history links") {
      for {
        f               <- fixtureWithA
        a2              <- updateText(f.source, f.a, paragraph("A without link"), standard, property = text)
        r1              <- expectCounts(f.source, f.t -> 0, f.u -> 0)
        (code, message) <- eraseV(f.source, text, a2)
        r2              <- expectCounts(f.source, f.t -> 0, f.u -> 0)
        stored          <- storedXml(a2)
      } yield assertTrue(code == StatusCode.BadRequest, message.contains(eraseRefusal), stored.isDefined) && r1 && r2
    },
    test("erase succeeds for a value without a link in its current version or history") {
      for {
        f         <- fixtureWithA
        plain     <- createValue(f.source, textValueJson(paragraph("no link"), standard, None), property = richtext)
        r1        <- expectCounts(f.source, f.t -> 1, f.u -> 0)
        (code, _) <- eraseV(f.source, richtext, plain)
        r2        <- expectCounts(f.source, f.t -> 1, f.u -> 0)
        stored    <- storedXml(plain)
      } yield assertTrue(code == StatusCode.Ok, stored.isEmpty) && r1 && r2
    },
    test("version history serves the old XML and leaves the counts unchanged") {
      for {
        f   <- fixtureWithA
        v1  <- creationDateOf(f.a)
        _   <- updateText(f.source, f.a, paragraph("A moved", f.u), standard, property = text)
        r1  <- expectCounts(f.source, f.t -> 0, f.u -> 1)
        old <- servedXml(f.source, Some(v1))
        now <- servedXml(f.source, None)
        r2  <- expectCounts(f.source, f.t -> 0, f.u -> 1)
      } yield assertTrue(
        old.contains(f.t),
        !old.contains(f.u),
        now.contains(f.u),
        !now.contains(f.t),
      ) && r1 && r2
    },
    test("repair, then edit") {
      // The repair version and the value-path version are both new versions of an existing link value, so both carry
      // kb:previousValue; a first version created by the value path has none and is not comparable.
      for {
        f                  <- fixtureWithA
        _                  <- setStoredCount(f.source, 5)
        rBefore            <- refCount(f.source, f.t)
        previous           <- linkValueOf(f.source, f.t)
        previousUuid       <- objectsOf(previous, s"${kb}valueHasUUID")
        _                  <- repairToCompletion(log)
        r1                 <- expectCounts(f.source, f.t -> 1, f.u -> 0)
        repaired           <- linkValueOf(f.source, f.t)
        repairedPredicates <- predicatesOf(repaired)
        repairedRows       <- predicateRowsOf(repaired)
        repairedUser       <- objectsOf(repaired, s"${kb}attachedToUser")
        repairedPerms      <- objectsOf(repaired, s"${kb}hasPermissions")
        repairedSubject    <- objectsOf(repaired, s"$rdf#subject")
        repairedPredicate  <- objectsOf(repaired, s"$rdf#predicate")
        repairedObject     <- objectsOf(repaired, s"$rdf#object")
        repairedUuid       <- objectsOf(repaired, s"${kb}valueHasUUID")
        b                  <- createValue(f.source, textValueJson(paragraph("B", f.t), standard, None), property = richtext)
        r2                 <- expectCounts(f.source, f.t -> 2)
        written            <- linkValueOf(f.source, f.t)
        writtenPredicates  <- predicatesOf(written)
        writtenPerms       <- objectsOf(written, s"${kb}hasPermissions")
        _                  <- deleteText(f.source, b, richtext)
        r3                 <- expectCounts(f.source, f.t -> 1)
        r4                 <- editScenario(f)
      } yield assertTrue(
        rBefore == 5,
        repairedPredicates == writtenPredicates,
        repairedRows.groupBy(identity).values.forall(_.size == 1),
        repairedUser == Seq(KnoraUserRepo.builtIn.SystemUser.id.value),
        repairedPerms.size == 1,
        repairedPerms == writtenPerms,
        repairedSubject == Seq(f.source),
        repairedPredicate == Seq(s"${kb}hasStandoffLinkTo"),
        repairedObject == Seq(f.t),
        previousUuid.size == 1,
        repairedUuid == previousUuid,
      ) && r1 && r2 && r3 && r4
    },
    test("increase across values, standard mapping") {
      for {
        f  <- fixtureWithA
        r1 <- expectCounts(f.source, f.t -> 1)
        _  <- createValue(f.source, textValueJson(paragraph("B", f.t), standard, None), property = richtext)
        r2 <- expectCounts(f.source, f.t -> 2)
      } yield r1 && r2
    },
    test("increase across values, custom mapping") {
      for {
        f      <- fixtureWithA
        _      <- createValue(f.source, textValueJson(paragraph("B", f.t), standard, None), property = richtext)
        r2     <- expectCounts(f.source, f.t -> 2)
        name   <- freshName("RefCountCustom")
        custom <- createCustomMapping(name)
        _      <- createValue(f.source, textValueJson(paragraph("C", f.t), custom, None), property = richtext)
        r3     <- expectCounts(f.source, f.t -> 3)
      } yield r2 && r3
    },
    test("one value, many tags") {
      for {
        t      <- createThing()
        u      <- createThing()
        source <- createThing()
        _      <- createValue(source, textValueJson(paragraph("many", t, t, t, u), standard, None), property = text)
        result <- expectCounts(source, t -> 1, u -> 1)
      } yield result
    },
    test("edit") {
      fixtureWithA.flatMap(editScenario)
    },
    test("edit a custom mapping value") {
      for {
        f      <- fixtureWithA
        name   <- freshName("RefCountCustom")
        custom <- createCustomMapping(name)
        c      <- createValue(f.source, textValueJson(paragraph("C", f.t), custom, None), property = richtext)
        r1     <- expectCounts(f.source, f.t -> 2, f.u -> 0)
        c2     <- updateText(f.source, c, paragraph("C moved", f.u), custom, property = richtext)
        r2     <- expectCounts(f.source, f.t -> 1, f.u -> 1)
        _      <- updateText(f.source, c2, paragraph("C text changed only", f.u), custom, property = richtext)
        r3     <- expectCounts(f.source, f.t -> 1, f.u -> 1)
      } yield r1 && r2 && r3
    },
    test("edit a fallback value") {
      for {
        f      <- fixtureWithA
        _      <- removeStoredXml(f.a)
        stored <- storedXml(f.a)
        result <- editScenario(f)
      } yield assertTrue(stored.isEmpty) && result
    },
    test("decrease to zero") {
      for {
        (f, b, c, _) <- fixtureWithABC
        r3           <- expectCounts(f.source, f.t -> 3)
        _            <- deleteText(f.source, f.a, text)
        r2           <- expectCounts(f.source, f.t -> 2)
        _            <- deleteText(f.source, b, richtext)
        r1           <- expectCounts(f.source, f.t -> 1)
        _            <- deleteText(f.source, c, richtext)
        r0           <- expectCounts(f.source, f.t -> 0)
        _            <- eraseResource(f.t)
        erased       <- ask(s"ASK { GRAPH <$dataGraph> { <${f.t}> ?p ?o } }")
        rErased      <- expectCounts(f.source, f.t -> 0)
      } yield r3 && r2 && r1 && r0 && assertTrue(!erased) && rErased
    },
    test("link again after zero") {
      for {
        f      <- fixtureWithA
        _      <- deleteText(f.source, f.a, text)
        r0     <- expectCounts(f.source, f.t -> 0)
        _      <- createValue(f.source, textValueJson(paragraph("again", f.t), standard, None), property = text)
        r1     <- expectCounts(f.source, f.t -> 1)
        fresh  <- createThing()
        _      <- createValue(f.source, textValueJson(paragraph("fresh", fresh), standard, None), property = richtext)
        rFresh <- expectCounts(f.source, f.t -> 1, fresh -> 1)
      } yield r0 && r1 && rFresh
    },
  )
}
