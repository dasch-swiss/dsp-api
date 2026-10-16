/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow

/** Unit spec for [[ViewRestrictionsRowCache]]; counting loaders stand in for the repo, so no triplestore is needed. */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsRowCacheSpec extends ZIOSpecDefault {

  private val ttl = 10.minutes

  private val projectA = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val projectB = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002")
  private val classC   = "http://www.knora.org/ontology/0001/onto#C"
  private val classD   = "http://www.knora.org/ontology/0001/onto#D"

  private def resourceRows(classIris: String*): Seq[ResourceCountRow] =
    classIris.map(ResourceCountRow(_, "V knora-admin:UnknownUser", 1))

  private val valueRow = ValueCountRow(classC, "http://www.knora.org/ontology/0001/onto#p", "V x", false, 1, 0)

  /** `rowsOfLoad` gives the resource rows returned by the n-th resource load, counting from 1. */
  private def rowCache(
    resourceLoads: Ref[Int],
    valueLoads: Ref[Int],
    rowsOfLoad: Int => Seq[ResourceCountRow] = _ => resourceRows(classC),
  ): UIO[ViewRestrictionsRowCache] =
    for {
      resources <- ViewRestrictionsRowCache.makeCache[ProjectIri, Seq[ResourceCountRow]](100, ttl)(_ =>
                     resourceLoads.updateAndGet(_ + 1).map(rowsOfLoad),
                   )
      values <- ViewRestrictionsRowCache.makeCache[ViewRestrictionsRowCache.ValueKey, Seq[ValueCountRow]](100, ttl)(_ =>
                  valueLoads.update(_ + 1).as(Seq(valueRow)),
                )
    } yield ViewRestrictionsRowCache(resources, values)

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
    test("refreshProject invalidates only the value keys of its own project") {
      for {
        resourceLoads <- Ref.make(0)
        valueLoads    <- Ref.make(0)
        cache         <- rowCache(resourceLoads, valueLoads)
        _             <- cache.valueCounts(projectA, classC)
        _             <- cache.valueCounts(projectB, classC)
        _             <- cache.refreshProject(projectA)
        _             <- cache.valueCounts(projectB, classC)
        afterB        <- valueLoads.get
        _             <- cache.valueCounts(projectA, classC)
        afterA        <- valueLoads.get
      } yield assertTrue(afterB == 2, afterA == 3)
    },
    test("refreshProject drops the value entry of a class that is only in the old rows") {
      for {
        resourceLoads <- Ref.make(0)
        valueLoads    <- Ref.make(0)
        cache         <-
          rowCache(resourceLoads, valueLoads, n => if (n == 1) resourceRows(classC, classD) else resourceRows(classC))
        _      <- cache.resourceCounts(projectA)
        _      <- cache.valueCounts(projectA, classD)
        _      <- cache.refreshProject(projectA)
        before <- valueLoads.get
        _      <- cache.valueCounts(projectA, classD)
        after  <- valueLoads.get
      } yield assertTrue(before == 1, after == 2)
    },
    test("refreshProject reloads the resource rows exactly once, whether or not they were cached") {
      for {
        resourceLoads <- Ref.make(0)
        valueLoads    <- Ref.make(0)
        cache         <- rowCache(resourceLoads, valueLoads)
        _             <- cache.refreshProject(projectA)
        afterCold     <- resourceLoads.get
        _             <- cache.refreshProject(projectA)
        afterWarm     <- resourceLoads.get
      } yield assertTrue(afterCold == 1, afterWarm == 2)
    },
    test("serves the value counts from cache after a refresh and a resource-count read of the same project") {
      for {
        resourceLoads <- Ref.make(0)
        valueLoads    <- Ref.make(0)
        cache         <- rowCache(resourceLoads, valueLoads)
        _             <- cache.refreshProject(projectA)
        _             <- cache.valueCounts(projectA, classC)
        _             <- cache.resourceCounts(projectA)
        _             <- cache.valueCounts(projectA, classC)
        values        <- valueLoads.get
        resources     <- resourceLoads.get
      } yield assertTrue(values == 1, resources == 1)
    },
  )
}
