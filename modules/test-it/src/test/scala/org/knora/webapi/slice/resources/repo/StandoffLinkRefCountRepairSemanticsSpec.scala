/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountMismatch
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountRepoLive
import org.knora.webapi.slice.resources.service.StandoffLinkRefCountRepair
import org.knora.webapi.slice.resources.service.StandoffLinkRefCountRepairPlan
import org.knora.webapi.slice.resources.service.StandoffLinkRefCountService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Applies the repairs of the standoff link reference count invariant to a live triplestore and checks that the
 * check query then reports nothing. The project's data graph is dedicated to this spec and re-seeded by every test.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountRepairSemanticsSpec extends E2EZSpec {

  private val triplestore = ZIO.serviceWithZIO[TriplestoreService]

  private val project = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("refcount"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Reference count repair"))),
    List.empty,
    None,
    SelfJoin.CannotJoin,
    None,
    Set.empty,
    Set.empty,
  )

  private val graph = "http://www.knora.org/data/0001/refcount"

  private val kb     = "http://www.knora.org/ontology/knora-base#"
  private val rdf    = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  private val hasTxt = "http://www.knora.org/ontology/0001/anything#hasText"
  private val xsdInt = "http://www.w3.org/2001/XMLSchema#integer"

  private val source = "http://rdfh.ch/0001/refcountSource"
  private val target = "http://rdfh.ch/0001/refcountTarget"

  private def named(name: String): String = s"http://rdfh.ch/0001/refcount/$name"

  private val linkValueIri = named("link-value")
  private val uuid         = "AAAAAAAAAAAAAAAAAAAAAQ"

  private def newLinkValueIri(i: Int) = ValueIri.unsafeFrom(named(s"values/new-$i"))

  private def textValue(name: String) =
    s"""<${named(name)}> a <${kb}TextValue> ; <${kb}isDeleted> false .
       |<$source> <$hasTxt> <${named(name)}> .
       |<${named(name)}/standoff/0> a <http://www.knora.org/ontology/standoff#StandoffLinkTag> ;
       |  <${kb}standoffTagHasLink> <$target> .
       |<${named(name)}> <${kb}valueHasStandoff> <${named(name)}/standoff/0> .""".stripMargin

  private def linkValue(count: Int) =
    s"""<$source> <${kb}hasStandoffLinkToValue> <$linkValueIri> .
       |<$linkValueIri> a <${kb}LinkValue> ;
       |  <${kb}isDeleted> false ;
       |  <${rdf}object> <$target> ;
       |  <${kb}valueHasUUID> "$uuid" ;
       |  <${kb}valueHasRefCount> "$count"^^<$xsdInt> .""".stripMargin

  private val directLink = s"<$source> <${kb}hasStandoffLinkTo> <$target> ."

  private def sourceResource(isDeleted: Boolean) =
    s"<$source> a <${kb}Resource> ; <${kb}isDeleted> $isDeleted ."

  private def seedRaw(triples: String*): RIO[TriplestoreService, Unit] =
    triplestore(_.query(Update(s"DROP SILENT GRAPH <$graph>"))) *>
      triplestore(_.query(Update(s"INSERT DATA { GRAPH <$graph> { ${triples.mkString("\n")} } }")))

  /** Seeds the graph with a live source resource and the given triples. */
  private def seed(triples: String*): RIO[TriplestoreService, Unit] =
    seedRaw(sourceResource(isDeleted = false) +: triples*)

  private def ask(pattern: String): RIO[TriplestoreService, Boolean] =
    triplestore(_.query(Ask(s"ASK { GRAPH <$graph> { $pattern } }")))

  private val repo = triplestore(ts => ZIO.succeed(StandoffLinkRefCountRepoLive(ts)))

  /** Plans every mismatch of the graph, applies the repairs and returns, per repair, whether it took effect. */
  private def repairAll: RIO[TriplestoreService, Seq[Boolean]] =
    for {
      r          <- repo
      mismatches <- r.selectMismatches(project)
      results    <- ZIO.foreach(mismatches.zipWithIndex) { case (m, i) => applyPlan(m, i) }
    } yield results

  private def applyPlan(m: StandoffLinkRefCountMismatch, i: Int): RIO[TriplestoreService, Boolean] =
    StandoffLinkRefCountRepair.plan(m, targetExists = true, newLinkValueIri(i)) match {
      case StandoffLinkRefCountRepairPlan.Repair(mismatch, update) =>
        repo.flatMap(_.applyRepair(project, mismatch.source, update))
      case skip => ZIO.fail(new IllegalStateException(s"unexpected skip: $skip"))
    }

  /** Every triple of the graph, as sorted lines. */
  private val graphContent: RIO[TriplestoreService, Seq[String]] =
    triplestore(
      _.query(Select(s"SELECT ?s ?p ?o WHERE { GRAPH <$graph> { ?s ?p ?o } }")),
    ).map(_.results.bindings.map(r => s"${r.rowMap("s")} ${r.rowMap("p")} ${r.rowMap("o")}").sorted)

  private val noMismatches = repo.flatMap(_.selectMismatches(project)).map(_.isEmpty)

  override val e2eSpec: Spec[env, Any] = suite("StandoffLinkRefCountRepair against a live triplestore")(
    test("a changed count is repaired and the previous version stays reachable with its old count") {
      for {
        _        <- seed(textValue("tv1"), textValue("tv2"), linkValue(3), directLink)
        applied  <- repairAll
        clean    <- noMismatches
        newCount <- ask(
                      s"<$source> <${kb}hasStandoffLinkToValue> <${named("values/new-0")}> . " +
                        s"<${named("values/new-0")}> <${kb}valueHasRefCount> 2 ; <${kb}valueHasUUID> \"$uuid\" ; " +
                        s"<${kb}previousValue> <$linkValueIri> .",
                    )
        previous <- ask(s"<$linkValueIri> <${kb}valueHasRefCount> 3 .")
        detached <- ask(s"<$source> <${kb}hasStandoffLinkToValue> <$linkValueIri> .").map(!_)
      } yield assertTrue(applied == Seq(true), clean, newCount, previous, detached)
    },
    test("a missing direct link is inserted") {
      for {
        _       <- seed(textValue("tv1"), linkValue(1))
        applied <- repairAll
        clean   <- noMismatches
        direct  <- ask(directLink)
      } yield assertTrue(applied == Seq(true), clean, direct)
    },
    test("an extra direct link is deleted") {
      for {
        _       <- seed(directLink)
        applied <- repairAll
        clean   <- noMismatches
        direct  <- ask(directLink).map(!_)
      } yield assertTrue(applied == Seq(true), clean, direct)
    },
    test("a link tag without a link value gets a link value") {
      for {
        _       <- seed(textValue("tv1"), directLink)
        applied <- repairAll
        clean   <- noMismatches
        created <- ask(
                     s"<$source> <${kb}hasStandoffLinkToValue> <${named("values/new-0")}> . " +
                       s"<${named("values/new-0")}> <${kb}valueHasRefCount> 1 .",
                   )
      } yield assertTrue(applied == Seq(true), clean, created)
    },
    test("a count to zero deletes the link value and the direct link, keeping the previous version") {
      for {
        _       <- seed(linkValue(1), directLink)
        applied <- repairAll
        clean   <- noMismatches
        deleted <- ask(
                     s"<${named("values/new-0")}> <${kb}isDeleted> true ; <${kb}valueHasRefCount> 0 ; " +
                       s"<${kb}previousValue> <$linkValueIri> .",
                   )
        direct <- ask(directLink).map(!_)
      } yield assertTrue(applied == Seq(true), clean, deleted, direct)
    },
    test("a repair whose stored count changed after the select is not applied and writes nothing") {
      for {
        _    <- seed(textValue("tv1"), textValue("tv2"), linkValue(3), directLink)
        r    <- repo
        rows <- r.selectMismatches(project)
        _    <- triplestore(
               _.query(
                 Update(
                   s"""DELETE { GRAPH <$graph> { <$linkValueIri> <${kb}valueHasRefCount> ?c } }
                      |INSERT { GRAPH <$graph> { <$linkValueIri> <${kb}valueHasRefCount> 5 } }
                      |WHERE { GRAPH <$graph> { <$linkValueIri> <${kb}valueHasRefCount> ?c } }""".stripMargin,
                 ),
               ),
             )
        applied   <- ZIO.foreach(rows.zipWithIndex) { case (m, i) => applyPlan(m, i) }
        unchanged <- ask(
                       s"<$source> <${kb}hasStandoffLinkToValue> <$linkValueIri> . " +
                         s"<$linkValueIri> <${kb}valueHasRefCount> 5 .",
                     )
        noNew <- ask(s"<${named("values/new-0")}> a <${kb}LinkValue> .").map(!_)
      } yield assertTrue(applied == Seq(false), unchanged, noNew)
    },
    test("a repair of a deleted source is not applied and writes nothing") {
      for {
        _ <- seedRaw(
               sourceResource(isDeleted = true),
               textValue("tv1"),
               textValue("tv2"),
               linkValue(3),
               directLink,
             )
        before  <- graphContent
        applied <- repairAll
        after   <- graphContent
      } yield assertTrue(applied == Seq(false), before == after)
    },
    test("a dry run of the service plans the repairs and changes no triple") {
      for {
        _       <- seed(textValue("tv1"), textValue("tv2"), linkValue(3), directLink, s"<$target> a <${kb}Resource> .")
        service <- ZIO.service[StandoffLinkRefCountService]
        before  <- graphContent
        summary <- service.repair(project, dryRun = true)
        after   <- graphContent
        rows    <- repo.flatMap(_.selectMismatches(project))
      } yield assertTrue(summary.planned == 1, summary.applied == 0, before == after, rows.size == 1)
    },
  )
}
