/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import org.junit.runner.RunWith
import sttp.model.QueryParams
import sttp.model.StatusCode
import zio.*
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughOverloadedException
import org.knora.webapi.store.triplestore.errors.SparqlRequestTooLargeException
import org.knora.webapi.store.triplestore.errors.SparqlStoreUnavailableException

@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughRestServiceSpec extends ZIOSpecDefault {

  private val normalUser =
    User(
      "http://rdfh.ch/users/sparql-passthrough-spec",
      "username",
      "email@example.com",
      "given name",
      "family name",
      status = true,
      "lang",
    )

  private val systemAdmin = normalUser.copy(permissions =
    PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
  )

  private val selectQuery = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 1"

  private def runQuery(
    user: User,
    sparql: String = selectQuery,
    accept: Option[String] = None,
    params: QueryParams = QueryParams.fromSeq(Seq.empty),
  ) = ZIO.serviceWithZIO[SparqlPassthroughRestService](_.query(user)(sparql, accept, params))

  val spec: Spec[Any, Any] = suite("SparqlPassthroughRestService")(
    suite("with the passthrough enabled")(
      test("a SystemAdmin gets the store's status, Content-Type and body") {
        for {
          result                     <- runQuery(systemAdmin)
          (contentType, body, status) = result
        } yield assertTrue(
          status == StatusCode.Ok,
          contentType.contains("application/sparql-results+json"),
          body.nonEmpty,
        )
      },
      test("a caller who is not a SystemAdmin is forbidden") {
        runQuery(normalUser).exit.map(exit => assert(exit)(failsWithA[ForbiddenException]))
      },
      test("the SPARQL statement sent as a query-string parameter is rejected, not silently ignored") {
        runQuery(systemAdmin, params = QueryParams.fromSeq(Seq("query" -> selectQuery))).exit
          .map(exit => assert(exit)(failsWithA[BadRequestException]))
      },
      test("an update sent as a query-string parameter is rejected too") {
        runQuery(systemAdmin, params = QueryParams.fromSeq(Seq("update" -> "DROP ALL"))).exit
          .map(exit => assert(exit)(failsWithA[BadRequestException]))
      },
      test("an unrelated query-string parameter is accepted and simply not relayed") {
        runQuery(systemAdmin, params = QueryParams.fromSeq(Seq("pretty" -> "true"))).map { case (_, _, status) =>
          assertTrue(status == StatusCode.Ok)
        }
      },
    ).provide(SparqlPassthroughTestEnv.layer()) @@ TestAspect.sequential,
    suite("with the passthrough disabled")(
      test("even a SystemAdmin is forbidden, as defence in depth behind the unregistered route") {
        runQuery(systemAdmin).exit.map(exit => assert(exit)(failsWithA[ForbiddenException]))
      },
      test("a caller who is not a SystemAdmin is forbidden without learning the flag state") {
        // Both cases fail identically, which is the point: the authorization check runs before the flag re-check.
        for {
          asAdmin  <- runQuery(systemAdmin).exit
          asNormal <- runQuery(normalUser).exit
        } yield assert(asAdmin)(failsWithA[ForbiddenException]) && assert(asNormal)(failsWithA[ForbiddenException])
      },
    ).provide(SparqlPassthroughTestEnv.layer("app.allow-sparql-passthrough" -> false)),
    suite("guardrails")(
      test("a request body over the cap is rejected before the store is reached") {
        runQuery(systemAdmin, sparql = "SELECT ?s WHERE { ?s ?p ?o }").exit
          .map(exit => assert(exit)(failsWithA[SparqlRequestTooLargeException]))
      },
      test("a request body within the cap is accepted") {
        runQuery(systemAdmin, sparql = "ASK{}").map { case (_, _, status) => assertTrue(status == StatusCode.Ok) }
      },
    ).provide(SparqlPassthroughTestEnv.layer("app.triplestore.sparql-passthrough.max-request-body-bytes" -> 10)),
    suite("concurrency backstop")(
      test("a call arriving while the only slot is held is rejected, not queued") {
        // A latch holds the slot for the duration of the second attempt, so the overlap is deterministic rather
        // than dependent on how fast the store answers.
        for {
          service  <- ZIO.service[SparqlPassthroughRestService]
          occupied <- Promise.make[Nothing, Unit]
          release  <- Promise.make[Nothing, Unit]
          holder   <- service.withBackstop(occupied.succeed(()) *> release.await).fork
          _        <- occupied.await
          rejected <- service.withBackstop(ZIO.unit).exit
          _        <- release.succeed(())
          _        <- holder.join
        } yield assert(rejected)(failsWithA[SparqlPassthroughOverloadedException])
      },
      test("the slot is released again, so a later call succeeds") {
        for {
          service <- ZIO.service[SparqlPassthroughRestService]
          _       <- service.withBackstop(ZIO.unit)
          second  <- service.withBackstop(ZIO.succeed(42))
        } yield assertTrue(second == 42)
      },
      test("the slot is released even when the call it guards fails") {
        for {
          service <- ZIO.service[SparqlPassthroughRestService]
          _       <- service.withBackstop(ZIO.fail(SparqlStoreUnavailableException.make)).exit
          after   <- service.withBackstop(ZIO.succeed(42))
        } yield assertTrue(after == 42)
      },
    ).provide(
      SparqlPassthroughTestEnv.layer("app.triplestore.sparql-passthrough.max-concurrent-calls" -> 1),
    ) @@ TestAspect.sequential,
  )

}
