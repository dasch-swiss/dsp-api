/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.util.UUID

import dsp.errors.ForbiddenException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.valuemessages.ReadLinkValueV2
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.resources.access.AccessRequest.*

@RunWith(classOf[DspZTestJUnitRunner])
class ObjectAccessSpec extends ZIOSpecDefault {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private val resource      = ResourceIri.unsafeFrom("http://rdfh.ch/0001/locked-resource")
  private val otherResource = ResourceIri.unsafeFrom("http://rdfh.ch/0001/other-resource")
  private val createdIri    = ResourceIri.unsafeFrom("http://rdfh.ch/0001/created-resource")
  private val property      = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri)
  private val resourceClass =
    ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)
  private val anonymous = KnoraSystemInstances.Users.AnonymousUser

  // Any load or project lookup would fail on the missing dependencies, so a ForbiddenException proves none ran.
  private val gate = ObjectAccess(null, null, null)

  private def refused(effect: Task[Any]) =
    effect.exit.map(exit =>
      assertTrue(exit.causeOption.flatMap(_.failureOption).exists(_.isInstanceOf[ForbiddenException])),
    )

  /** Whether the gate can lock `request` while `hold`, as a write path does today, holds its lock. */
  private def gateLockIsFree(hold: UUID => Task[Unit] => Task[Unit], request: ObjectRequest[?]) =
    for {
      inside  <- Promise.make[Nothing, Unit]
      release <- Promise.make[Nothing, Unit]
      holder  <- hold(UUID.randomUUID())(inside.succeed(()) *> release.await).fork
      _       <- inside.await
      probe   <- ObjectAccess.locked(UUID.randomUUID(), request)(ZIO.unit).fork
      _       <- ZIO.sleep(500.millis)
      free    <- probe.poll.map(_.isDefined)
      _       <- release.succeed(()) *> holder.join *> probe.join
    } yield free

  val spec: Spec[Any, Any] = suite("ObjectAccess")(
    suite("refuses anonymous callers before it locks or loads")(
      test("within, for a request on an existing resource") {
        refused(gate.within(anonymous, UUID.randomUUID(), DeleteResource(resource))(_ => ZIO.unit))
      },
      test("within, for resource creation") {
        val request = CreateResource(TestDataFactory.someProject, resourceClass)
        refused(gate.within(anonymous, UUID.randomUUID(), request)(_ => ZIO.unit))
      },
      test("decideOnly") {
        refused(gate.decideOnly(anonymous, ReorderValues(resource, property))(_ => ZIO.unit))
      },
    ),
    suite("locks the key today's write paths lock for a resource")(
      test("the typed resource IRI, as the value and resource writes lock it") {
        for {
          same  <- gateLockIsFree(id => task => IriLocker.runWithIriLock(id, resource)(task), DeleteResource(resource))
          other <-
            gateLockIsFree(id => task => IriLocker.runWithIriLock(id, resource)(task), DeleteResource(otherResource))
        } yield assertTrue(!same, other)
      },
      test("the IRI string, as resource creation locks it") {
        for {
          same <- gateLockIsFree(
                    id => task => IriLocker.runWithIriLock(id, createdIri.value)(task),
                    UpdateResource(createdIri, PermissionsChange.Unchanged),
                  )
        } yield assertTrue(!same)
      },
    ) @@ TestAspect.withLiveClock @@ TestAspect.sequential,
    suite("the caller's view replaces the SystemUser maximum of the load with the caller's level")(
      test("on the resource and each value the caller sees") {
        val view = ObjectAccess.callerView(
          AccessFixtures.resource(
            Map(
              AccessFixtures.hasInteger -> Seq(
                AccessFixtures.intValue("viewable", "V knora-admin:ProjectMember"),
                AccessFixtures.intValue("hidden", "CR knora-admin:Creator"),
              ),
            ),
          ),
          AccessFixtures.member,
        )
        val values = view.values.values.flatten.toList
        assertTrue(
          view.userPermission == Permission.ObjectAccess.Modify,
          values.map(_.valueIri.value.split('/').last) == List("viewable"),
          values.map(_.userPermission) == List(Permission.ObjectAccess.View),
        )
      },
      test("on a link value and the resource it links to") {
        val link = AccessFixtures.linkValue(
          "link",
          "D knora-admin:ProjectMember",
          otherResource,
          targetLiteral = Some("RV knora-admin:ProjectMember"),
        )
        val view = ObjectAccess.callerView(
          AccessFixtures.resource(Map(AccessFixtures.hasOtherThingValue -> Seq(link))),
          AccessFixtures.member,
        )
        val links = view.values.values.flatten.collect { case l: ReadLinkValueV2 => l }.toList
        assertTrue(
          links.map(_.userPermission) == List(Permission.ObjectAccess.Delete),
          links.flatMap(_.valueContent.nestedResource).map(_.userPermission) ==
            List(Permission.ObjectAccess.RestrictedView),
        )
      },
    ),
  )
}
