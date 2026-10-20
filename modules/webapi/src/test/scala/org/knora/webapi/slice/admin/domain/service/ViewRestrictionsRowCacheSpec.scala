/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow

/** Unit spec for [[ViewRestrictionsRowCache]]; counting loaders stand in for the repo, so no triplestore is needed. */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsRowCacheSpec extends ZIOSpecDefault {

  private val ttl = 60.minutes

  private val projectA = ViewRestrictionsTestFixtures.anythingProject
  private val projectB = projectA.copy(id = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002"))
  private val classC   = "http://www.knora.org/ontology/0001/onto#C"

  private val resourceRows = Seq(ResourceCountRow(classC, "V knora-admin:UnknownUser", 1))

  private val valueRow = ValueCountRow(classC, "http://www.knora.org/ontology/0001/onto#p", "V x", false, 1, 0)

  /** Counts the loads of each cache. */
  final case class Loads(resources: Ref[Int], values: Ref[Int]) {
    def get: UIO[(Int, Int)] = resources.get.zip(values.get)
  }

  private def rowCache: UIO[(ViewRestrictionsRowCache, Loads)] =
    for {
      loads     <- Ref.make(0).zip(Ref.make(0)).map(Loads.apply)
      resources <- ViewRestrictionsRowCache.makeCache[KnoraProject, Seq[ResourceCountRow]](100, ttl)(_ =>
                     loads.resources.update(_ + 1).as(resourceRows),
                   )
      values <- ViewRestrictionsRowCache.makeCache[KnoraProject, Seq[ValueCountRow]](100, ttl)(_ =>
                  loads.values.update(_ + 1).as(Seq(valueRow)),
                )
    } yield (ViewRestrictionsRowCache(resources, values), loads)

  def spec: Spec[Any, Any] = suite("ViewRestrictionsRowCache")(
    test("runs the load once for concurrent gets of one missing key") {
      for {
        calls   <- Ref.make(0)
        entered <- Promise.make[Nothing, Unit]
        release <- Promise.make[Nothing, Unit]
        cache   <- ViewRestrictionsRowCache.makeCache[String, Int](100, ttl) { _ =>
                   calls.update(_ + 1) *> entered.succeed(()) *> release.await.as(1)
                 }
        arrived <- Ref.make(0)
        fibers  <- ZIO.foreach(1 to 5)(_ => (arrived.update(_ + 1) *> cache.get("k")).fork)
        _       <- entered.await
        _       <- arrived.get.repeatUntil(_ == 5)
        _       <- ZIO.yieldNow
        _       <- release.succeed(())
        _       <- ZIO.foreach(fibers)(_.join)
        n       <- calls.get
      } yield assertTrue(n == 1)
    },
    test("does not keep a failed load") {
      for {
        calls <- Ref.make(0)
        cache <- ViewRestrictionsRowCache.makeCache[String, Int](100, ttl) { _ =>
                   calls.updateAndGet(_ + 1).flatMap {
                     case 1 => ZIO.fail(new RuntimeException("transient"))
                     case n => ZIO.succeed(n)
                   }
                 }
        first <- cache.get("k").exit
        _     <- TestClock.adjust(1.milli)
        again <- cache.get("k")
        n     <- calls.get
      } yield assertTrue(first.isFailure, again == 2, n == 2)
    },
    test("refreshProject drops the value rows of its own project only") {
      for {
        (cache, loads) <- rowCache
        _              <- cache.valueCounts(projectA)
        _              <- cache.valueCounts(projectB)
        _              <- cache.refreshProject(projectA)
        _              <- cache.valueCounts(projectB)
        afterB         <- loads.get
        _              <- cache.valueCounts(projectA)
        afterA         <- loads.get
      } yield assertTrue(afterB == (1, 2), afterA == (1, 3))
    },
    test("refreshProject reloads the resource rows exactly once, whether or not they were cached") {
      for {
        (cache, loads) <- rowCache
        _              <- cache.refreshProject(projectA)
        afterCold      <- loads.resources.get
        _              <- cache.refreshProject(projectA)
        afterWarm      <- loads.resources.get
      } yield assertTrue(afterCold == 1, afterWarm == 2)
    },
    test("serves every row set from cache after the first read") {
      for {
        (cache, loads) <- rowCache
        _              <- cache.refreshProject(projectA)
        values         <- cache.valueCounts(projectA)
        _              <- cache.resourceCounts(projectA)
        _              <- cache.valueCounts(projectA)
        n              <- loads.get
      } yield assertTrue(values == Seq(valueRow), n == (1, 1))
    },
  )
}
