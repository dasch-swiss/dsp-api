/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.access.AccessFixtures.*

@RunWith(classOf[DspZTestJUnitRunner])
class LinkValueWritesSpec extends ZIOSpecDefault {

  private val target  = ResourceIri.unsafeFrom("http://rdfh.ch/0001/link-target")
  private val newIri  = ValueIri.unsafeFrom(s"${resourceIri.value}/values/new-version")
  private val creator = "http://rdfh.ch/users/creator"

  private def grantOn(linkValues: ReadValueV2*) = {
    val loaded = resource(Map(hasOtherThingValue -> linkValues))
    new Grant(member, TestDataFactory.someProject, (), new Unfiltered(Some(loaded)))
  }

  private def decrement(grant: Grant[?]) =
    LinkValueWrites.decrement(grant, hasOtherThing, target, creator, "V knora-admin:UnknownUser", ZIO.succeed(newIri))

  private def increment(grant: Grant[?]) =
    LinkValueWrites.increment(grant, hasOtherThing, target, creator, "V knora-admin:UnknownUser", ZIO.succeed(newIri))

  private def changeMetadata(grant: Grant[?]) =
    LinkValueWrites.changeMetadata(
      grant,
      hasOtherThing,
      target,
      creator,
      "V knora-admin:UnknownUser",
      ZIO.succeed(newIri),
    )

  val spec: Spec[Any, Any] = suite("LinkValueWrites")(decrementSuite, incrementSuite, changeMetadataSuite)

  private def incrementSuite = suite("increment")(
    test("counts up the current link value, read from the unfiltered resource") {
      val hiddenLink = linkValue("hidden", "CR knora-admin:Creator", target, refCount = 2)
      for {
        update <- increment(grantOn(hiddenLink))
      } yield assertTrue(
        update.currentReferenceCount == 2,
        update.newReferenceCount == 3,
        update.linkValueExists,
        !update.insertDirectLink,
        update.newLinkValueIri == newIri,
      )
    },
    test("creates the direct link and a link value counting 1 when only a deleted version exists") {
      val deleted = linkValue("old", "CR knora-admin:Creator", target, deleted = true)
      for {
        update <- increment(grantOn(deleted))
      } yield assertTrue(
        update.currentReferenceCount == 0,
        update.newReferenceCount == 1,
        !update.linkValueExists,
        update.insertDirectLink,
      )
    },
  )

  private def changeMetadataSuite = suite("changeMetadata")(
    test("keeps the reference count of the current link value") {
      val hiddenLink = linkValue("hidden", "CR knora-admin:Creator", target, refCount = 2)
      for {
        update <- changeMetadata(grantOn(hiddenLink))
      } yield assertTrue(
        update.currentReferenceCount == 2,
        update.newReferenceCount == 2,
        !update.insertDirectLink,
        !update.deleteDirectLink,
        update.newLinkValueCreator == creator,
      )
    },
    test("dies when the resource has no link value for the target") {
      for {
        exit <- changeMetadata(grantOn()).exit
      } yield assertTrue(exit.causeOption.exists(_.isDie))
    },
  )

  private def decrementSuite = suite("decrement")(
    test("counts down the current link value, read from the unfiltered resource") {
      val hiddenLink = linkValue("hidden", "CR knora-admin:Creator", target, refCount = 2)
      for {
        update <- decrement(grantOn(hiddenLink))
      } yield assertTrue(
        update.currentReferenceCount == 2,
        update.newReferenceCount == 1,
        !update.deleteDirectLink,
        update.newLinkValueIri == newIri,
        update.newLinkValueCreator == creator,
      )
    },
    test("removes the direct link with the last reference, ignoring deleted versions") {
      val deleted = linkValue("old", "CR knora-admin:Creator", target, refCount = 3, deleted = true)
      val current = linkValue("current", "CR knora-admin:Creator", target)
      for {
        update <- decrement(grantOn(deleted, current))
      } yield assertTrue(update.currentReferenceCount == 1, update.newReferenceCount == 0, update.deleteDirectLink)
    },
    test("dies when the resource has no link value for the target") {
      for {
        exit <- decrement(grantOn()).exit
      } yield assertTrue(exit.causeOption.exists(_.isDie))
    },
  )
}
