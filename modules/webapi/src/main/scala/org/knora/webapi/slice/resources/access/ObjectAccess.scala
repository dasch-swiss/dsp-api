/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import zio.*

import java.util.UUID

import dsp.errors.ForbiddenException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadLinkValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadTextValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.api.ProjectAdminGrant
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.access.AccessRequest.*

/**
 * The one gate of the v2 resource and value writes (ADR-0013 Addendum 1). It refuses anonymous callers; for a
 * request on an existing resource it then takes the resource's write lock and loads as `SystemUser`; it decides with
 * [[AccessDecision.decide]] and only then calls `write` with a [[Grant]]. The order is fixed here; no caller can
 * reorder it, and a write must not use its grant outside the `write` closure, where the lock no longer holds.
 */
final class ObjectAccess(loader: ObjectLoader, ontologyRepo: OntologyRepo, projects: KnoraProjectService) {

  def within[T, R](caller: User, apiRequestId: UUID, request: AccessRequest[T])(write: Grant[T] => Task[R]): Task[R] =
    refuseAnonymous(caller) *> (request match {
      case create: CreateResource =>
        decided(caller, create, None, isLinkProperty = false) *>
          write(
            new Grant(caller, create.project, NewResource(create.project, create.resourceClass), new Unfiltered(None)),
          )
      case onResource: ObjectRequest[T] =>
        ObjectAccess.locked(apiRequestId, onResource) {
          for {
            (resource, value) <- loadAndDecide(caller, onResource)
            grant             <- grantFor(caller, onResource, resource, value)
            result            <- write(grant)
          } yield result
        }
    })

  /**
   * Erases a resource for a project admin. The role decision is the RestService's; the gate locks, loads, answers 404
   * for a missing resource and 403 for one outside the grant's project. A project admin sees every object of the
   * project, so there is no visibility step.
   */
  def withinAsProjectAdmin[R](grant: ProjectAdminGrant, apiRequestId: UUID, resource: ResourceIri)(
    write: Grant[ResourceToErase] => Task[R],
  ): Task[R] =
    IriLocker.runWithIriLock(apiRequestId, resource) {
      for {
        loaded <- loader.preview(resource).someOrFail(NotFoundException(AccessDecision.missingResource(resource.value)))
        _      <- ZIO
               .fail(ForbiddenException("Only a system admin or project admin can erase a resource"))
               .when(loaded.projectADM.id != grant.project.id)
        result <- write(new Grant(grant.user, grant.project, ResourceToErase(loaded), new Unfiltered(Some(loaded))))
      } yield result
    }

  /**
   * The slots a reorder writes, see [[ReorderSlots.assign]]; 400 unless `requested` lists each of the grant's visible
   * values once.
   */
  def reorderSlots(grant: Grant[ValuesToReorder], requested: Seq[ValueIri]): Task[List[(ValueIri, Int)]] =
    for {
      _      <- ZIO.foreachDiscard(ReorderSlots.mismatch(grant.target, requested))(ZIO.fail(_))
      orders <- loader.valueOrders(grant.target.resource.resourceIri, grant.target.property)
    } yield ReorderSlots.assign(requested, orders)

  /**
   * The decision of [[within]] without lock or grant, for the read-only "can delete" check. `check` receives the
   * caller's view of the target, not a [[Grant]], and must not write: writes go through [[within]].
   */
  def decideOnly[T, R](caller: User, request: AccessRequest[T])(check: T => Task[R]): Task[R] =
    refuseAnonymous(caller) *> (request match {
      case create: CreateResource =>
        decided(caller, create, None, isLinkProperty = false) *> check(
          NewResource(create.project, create.resourceClass),
        )
      case onResource: ObjectRequest[T] =>
        for {
          (resource, value) <- loadAndDecide(caller, onResource)
          unfiltered        <- permitted(resource)
          target            <- targetOf(onResource, ObjectAccess.callerView(unfiltered, caller), value, caller)
          result            <- check(target)
        } yield result
    })

  private def loadAndDecide(caller: User, request: ObjectRequest[?]) =
    for {
      (resource, isLinkProperty) <- load(request)
      value                       = resource.flatMap(valueOf(request, _))
      _                          <- decided(caller, request, resource.map(loaded(_, value)), isLinkProperty)
    } yield (resource, value)

  private def refuseAnonymous(caller: User): Task[Unit] =
    ZIO.fail(ForbiddenException("Anonymous users cannot write resources or values")).when(caller.isAnonymousUser).unit

  private def decided(
    caller: User,
    request: AccessRequest[?],
    loaded: Option[Loaded],
    isLinkProperty: Boolean,
  ): Task[Unit] =
    ZIO
      .attempt(AccessDecision.decide(caller, request, loaded, isLinkProperty))
      .flatMap(decision => ZIO.foreachDiscard(AccessDecision.failure(request, decision))(ZIO.fail(_)))

  /** Loads with the loader today's write path of the request uses; the flag says whether its property is a link. */
  private def load(request: ObjectRequest[?]): Task[(Option[ReadResourceV2], Boolean)] = request match {
    case r: CreateValue       => withPropertyValues(r.resource, r.property)
    case r: UpdateValue       => withPropertyValues(r.resource, r.property)
    case r: ChangeValueRights => withPropertyValues(r.resource, r.property)
    // A deleted text value's standoff links decrement the resource's knora-base:hasStandoffLinkToValue values.
    case r: DeleteValue    => loader.withAllValues(r.resource).map(_ -> false)
    case r: EraseValue     => loader.withProperty(r.resource, r.property).map(_ -> false)
    case r: ReorderValues  => loader.withProperty(r.resource, r.property).map(_ -> false)
    case r: UpdateResource => loader.previewWithDeleted(r.resource).map(_ -> false)
    case r: DeleteResource => loader.previewWithDeleted(r.resource).map(_ -> false)
  }

  private def withPropertyValues(resource: ResourceIri, property: PropertyIri) =
    for {
      info     <- findProperty(property)
      adjusted <- if (info.isLinkValueProp) findProperty(property.fromLinkValuePropToLinkProp) else ZIO.succeed(info)
      loaded   <- loader.withPropertyValues(resource, adjusted)
    } yield (loaded, info.isLinkValueProp)

  private def findProperty(property: PropertyIri): Task[ReadPropertyInfoV2] =
    ontologyRepo.findProperty(property).someOrFail(NotFoundException(s"Property not found: <$property>"))

  private def valueOf(request: ObjectRequest[?], resource: ReadResourceV2): Option[ReadValueV2] = {
    def find(property: PropertyIri, value: ValueIri) =
      resource.values.get(property.toInternalSchema).flatMap(_.find(_.valueIri == value))
    request match {
      case r: UpdateValue       => find(r.property, r.value)
      case r: ChangeValueRights => find(r.property, r.value)
      case r: DeleteValue       => find(r.property, r.value)
      case r: EraseValue        => find(r.property, r.value)
      case _                    => None
    }
  }

  private def loaded(resource: ReadResourceV2, value: Option[ReadValueV2]) =
    Loaded(
      resource.projectADM.id,
      Protection(resource.attachedToUser, resource.permissions),
      value.map(v => Protection(v.attachedToUser, v.permissions)),
    )

  private def grantFor[T](
    caller: User,
    request: ObjectRequest[T],
    resource: Option[ReadResourceV2],
    value: Option[ReadValueV2],
  ): Task[Grant[T]] =
    for {
      unfiltered <- permitted(resource)
      project    <- projects
                   .findById(unfiltered.projectADM.id)
                   .someOrFail(InconsistentRepositoryDataException("The project of the resource was not found"))
      view    = ObjectAccess.callerView(unfiltered, caller)
      target <- targetOf(request, view, value, caller)
    } yield new Grant(caller, project, target, new Unfiltered(Some(unfiltered)))

  private def permitted(resource: Option[ReadResourceV2]): UIO[ReadResourceV2] =
    ZIO.fromOption(resource).orDieWith(_ => IllegalStateException("permitted without a resource"))

  private def targetOf[T](
    request: ObjectRequest[T],
    view: ReadResourceV2,
    value: Option[ReadValueV2],
    caller: User,
  ): Task[T] = {
    def withValue[A](make: ReadValueV2 => A): Task[A] =
      ZIO
        .fromOption(value.flatMap(ObjectAccess.valueInView(_, view, caller)).map(make))
        .orDieWith(_ => IllegalStateException("permitted without a value"))
    request match {
      case _: CreateValue       => ZIO.succeed(ValueToCreate(view))
      case r: UpdateValue       => withValue(ValueToUpdate(view, _, r.permissions))
      case _: ChangeValueRights => withValue(ValueToRescope(view, _))
      case _: DeleteValue       => withValue(ValueToDelete(view, _))
      case _: EraseValue        => withValue(ValueToErase(view, _))
      case r: ReorderValues     =>
        val visible = view.values.getOrElse(r.property.toInternalSchema, Seq.empty).filter(_.deletionInfo.isEmpty)
        ZIO.succeed(ValuesToReorder(view, r.property, visible))
      case r: UpdateResource => ZIO.succeed(ResourceToUpdate(view, r.permissions))
      case _: DeleteResource => ZIO.succeed(ResourceToDelete(view))
    }
  }
}

object ObjectAccess {
  val layer = ZLayer.derive[ObjectAccess]

  /** The write lock of a request: the resource IRI, the same key today's write paths lock. */
  private[access] def locked[A](apiRequestId: UUID, request: ObjectRequest[?])(task: Task[A]): Task[A] =
    IriLocker.runWithIriLock(apiRequestId, request.resource)(task)

  /**
   * The resource as the caller sees it: values the caller has no permission on, and links to resources hidden from
   * the caller, left out, and the caller's own permission on the resource, its values and their link targets.
   */
  private[access] def callerView(resource: ReadResourceV2, caller: User): ReadResourceV2 = {
    val project = resource.projectADM.id.value
    resource.copy(
      userPermission = levelOf(caller, resource.attachedToUser, resource.permissions, project)
        .getOrElse(Permission.ObjectAccess.RestrictedView),
      values = resource.values.map { case (property, values) =>
        property -> values.flatMap(visibleValue(_, caller, project))
      },
    )
  }

  private def levelOf(caller: User, creator: String, literal: String, project: String) =
    PermissionUtilADM.getUserPermissionADM(creator, project, literal, caller)

  private def visibleValue(value: ReadValueV2, caller: User, project: String): Option[ReadValueV2] =
    levelOf(caller, value.attachedToUser, value.permissions, project).flatMap { level =>
      value match {
        case link: ReadLinkValueV2 =>
          link.valueContent.nestedResource match {
            case None         => Some(link.copy(userPermission = level))
            case Some(target) =>
              levelOf(caller, target.attachedToUser, target.permissions, target.projectADM.id.value).map(onTarget =>
                link.copy(
                  userPermission = level,
                  valueContent = link.valueContent.copy(nestedResource = Some(target.copy(userPermission = onTarget))),
                ),
              )
          }
        case text: ReadTextValueV2   => Some(text.copy(userPermission = level))
        case other: ReadOtherValueV2 => Some(other.copy(userPermission = level))
      }
    }

  /**
   * The requested value as the caller may see it: as in the view, or, when the view left it out for a link to a
   * resource hidden from the caller, without that resource's data.
   */
  private def valueInView(value: ReadValueV2, view: ReadResourceV2, caller: User): Option[ReadValueV2] =
    view.values.values.flatten.find(_.valueIri == value.valueIri).orElse {
      val withoutTarget = value match {
        case link: ReadLinkValueV2 => link.copy(valueContent = link.valueContent.copy(nestedResource = None))
        case other                 => other
      }
      visibleValue(withoutTarget, caller, view.projectADM.id.value)
    }
}
