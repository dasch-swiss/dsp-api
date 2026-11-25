/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.permissionsmessages.ResourceCreateOperation
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess as Level
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.resources.access.AccessRequest.*

/** Who created a loaded resource or value, and its permission literal. */
private[access] final case class Protection(creator: IRI, literal: String)

/** What the gate loaded for a request on an existing resource; `value` is the requested value, if loaded. */
private[access] final case class Loaded(project: ProjectIri, resource: Protection, value: Option[Protection])

/**
 * The outcome of the gate's decision, computed without IO. Hidden means the caller has no permission at all, as in
 * the read filter; a caller with restricted view sees the object and is refused on the level.
 */
enum AccessDecision {
  case Permit
  case MissingResource
  case HiddenResource
  case MissingValue
  case WrongProject
  case LevelDenied(level: Level, on: AccessDecision.On)
  case CreateDenied
}

object AccessDecision {

  enum On {
    case Resource, Value
  }

  /**
   * Decides a request for a caller who is not anonymous; the gate refuses anonymous callers before. `loaded` is
   * `None` for a missing resource and is ignored for [[CreateResource]]. A hidden value and a missing one get the
   * same outcome.
   */
  private[access] def decide(
    caller: User,
    request: AccessRequest[?],
    loaded: Option[Loaded],
    isLinkProperty: Boolean,
  ): AccessDecision = request match {
    case CreateResource(project, resourceClass) =>
      val operation = ResourceCreateOperation(resourceClass.toInternalSchema.toString)
      if (caller.permissions.hasPermissionFor(operation, project.id.value)) Permit else CreateDenied
    case r: ObjectRequest[?] => loaded.fold(MissingResource)(onExisting(caller, r, _, isLinkProperty))
  }

  private def onExisting(caller: User, request: ObjectRequest[?], loaded: Loaded, isLinkProperty: Boolean) = {
    def levelOf(p: Protection) =
      PermissionUtilADM.getUserPermissionADM(p.creator, loaded.project.value, p.literal, caller)
    val onResource = levelOf(loaded.resource)
    val onValue    = loaded.value.flatMap(levelOf)

    def firstUnmet(required: (Level, On)*): AccessDecision =
      required.collectFirst {
        case (level, on) if !(if (on == On.Resource) onResource else onValue).exists(_ >= level) =>
          LevelDenied(level, on)
      }.getOrElse(Permit)

    def onVisibleValue(required: (Level, On)*): AccessDecision =
      if (onValue.isEmpty) MissingValue else firstUnmet((Level.View -> On.Resource) +: required*)

    def changes(permissions: PermissionsChange, current: String) = permissions match {
      case PermissionsChange.Unchanged   => false
      case PermissionsChange.To(literal) =>
        PermissionUtilADM.parsePermissions(literal) != PermissionUtilADM.parsePermissions(current)
    }

    if (onResource.isEmpty) HiddenResource
    else
      request match {
        case _: CreateValue | _: ReorderValues => firstUnmet(Level.Modify -> On.Resource)
        case UpdateValue(_, _, _, permissions) =>
          val level =
            if (loaded.value.exists(v => changes(permissions, v.literal))) Level.ChangeRights
            else Level.Modify
          onVisibleValue(Seq(level -> On.Value) ++ Option.when(isLinkProperty)(Level.Modify -> On.Resource)*)
        case _: ChangeValueRights => onVisibleValue(Level.ChangeRights -> On.Value)
        case _: DeleteValue       => onVisibleValue(Level.Delete -> On.Value)
        case erase: EraseValue    =>
          if (onValue.nonEmpty && loaded.project != erase.project.id) WrongProject
          else onVisibleValue(Level.Delete -> On.Value)
        case UpdateResource(_, permissions) =>
          firstUnmet(
            Seq(Level.Modify -> On.Resource) ++
              Option.when(changes(permissions, loaded.resource.literal))(Level.ChangeRights -> On.Resource)*,
          )
        case _: DeleteResource => firstUnmet(Level.Delete -> On.Resource)
      }
  }

  /** The failure the gate answers with: fixed text plus IRIs of the request, never loaded data. */
  private[access] def failure(request: AccessRequest[?], decision: AccessDecision): Option[RequestRejectedException] =
    decision match {
      case Permit          => None
      case MissingResource => Some(NotFoundException(missingResource(resourceOf(request))))
      case HiddenResource  =>
        Some(ForbiddenException(s"You do not have permission to see resource <${resourceOf(request)}>"))
      case MissingValue =>
        Some(valueOf(request).fold(NotFoundException(missingResource(resourceOf(request)))) { case (property, value) =>
          NotFoundException(
            s"Resource <${resourceOf(request)}> does not have value <$value> as an object of property <$property>",
          )
        })
      case WrongProject =>
        val project = request match {
          case erase: EraseValue => s" <${erase.project.id.value}>"
          case _                 => ""
        }
        Some(BadRequestException(s"Resource <${resourceOf(request)}> is not in the project$project of the request"))
      case LevelDenied(level, On.Resource) =>
        Some(ForbiddenException(s"You do not have the $level permission on resource <${resourceOf(request)}>"))
      case LevelDenied(level, On.Value) =>
        val value = valueOf(request).fold(s"resource <${resourceOf(request)}>")((_, v) => s"value <$v>")
        Some(ForbiddenException(s"You do not have the $level permission on $value"))
      case CreateDenied =>
        Some(request match {
          case CreateResource(project, resourceClass) =>
            ForbiddenException(
              s"You do not have permission to create a resource of class <$resourceClass> in project <${project.id.value}>",
            )
          case _ => ForbiddenException(s"You do not have permission to create resource <${resourceOf(request)}>")
        })
    }

  private[access] def missingResource(resource: String): String = s"Resource <$resource> was not found"

  private def resourceOf(request: AccessRequest[?]): String = request match {
    case r: ObjectRequest[?] => r.resource.value
    case r: CreateResource   => r.resourceClass.toString
  }

  private def valueOf(request: AccessRequest[?]): Option[(String, String)] = request match {
    case r: UpdateValue       => Some(r.property.toString -> r.value.value)
    case r: ChangeValueRights => Some(r.property.toString -> r.value.value)
    case r: DeleteValue       => Some(r.property.toString -> r.value.value)
    case r: EraseValue        => Some(r.property.toString -> r.value.value)
    case _                    => None
  }
}
