/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import java.util.UUID

import dsp.errors.*
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.UpdateResourceMetadataRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.domain.BatchEdit
import org.knora.webapi.slice.resources.domain.BatchEditFailure
import org.knora.webapi.slice.resources.domain.BatchEditFailure.*
import org.knora.webapi.slice.resources.domain.BatchEditOutcome

/**
 * Applies the planned writes of a bulk edit in order, each on its own: a failed edit never stops the others. An edit
 * whose `expected` content no longer matches what is stored is a conflict and is not written. Only plain text values,
 * list values and resource labels are supported: formatted text is refused, since a plain string would drop its
 * markup.
 *
 * Supported value types appear in `comparable`, `newContent` and `newContentForProperty`; a new type goes in all three.
 */
final class BatchEditService(
  valuesResponder: ValuesResponderV2,
  resourcesResponder: ResourcesResponderV2,
  readResources: ReadResourcesService,
  resourceUtil: ResourceUtilV2,
  ontologyRepo: OntologyRepo,
)(implicit sf: StringFormatter) {

  /** A failure of one edit, carrying what to report. */
  private final case class Rejected(failure: BatchEditFailure, message: String) extends Exception(message)

  private val internalErrorMessage = "Internal error while applying this change."

  def apply(user: User, edits: List[BatchEdit]): UIO[List[BatchEditOutcome]] =
    Ref.make(Set.empty[ValueIri]).flatMap { created =>
      ZIO.foreach(edits.zipWithIndex) { case (edit, index) =>
        applyEdit(edit, user, created)
          .map(BatchEditOutcome.Applied(_))
          .catchAll(r => ZIO.succeed(BatchEditOutcome.Failed(r.failure, r.message)))
          .catchAllDefect(t =>
            ZIO.logErrorCause(s"Batch edit $index failed", Cause.die(t)) *>
              ZIO.succeed(BatchEditOutcome.Failed(Error, internalErrorMessage)),
          )
      }
    }

  private def applyEdit(edit: BatchEdit, user: User, created: Ref[Set[ValueIri]]): IO[Rejected, Option[ValueIri]] =
    (edit match {
      case e: BatchEdit.SetLabel => applyLabel(e, user).as(None)
      case e: BatchEdit.SetValue => applyValue(e, user, created).map(Some(_))
    }).catchAll {
      case r: Rejected                 => ZIO.fail(r)
      case e: ForbiddenException       => ZIO.fail(Rejected(Forbidden, e.getMessage))
      case e: NotFoundException        => ZIO.fail(Rejected(NotFound, e.getMessage))
      case e: EditConflictException    => ZIO.fail(Rejected(Conflict, e.getMessage))
      case e: RequestRejectedException => ZIO.fail(Rejected(Invalid, e.getMessage))
      // Internal errors may carry query text or store details: logged, never returned.
      case e: Throwable =>
        ZIO.logErrorCause("Batch edit failed", Cause.fail(e)) *> ZIO.fail(Rejected(Error, internalErrorMessage))
    }

  private val systemUser = KnoraSystemInstances.Users.SystemUser

  // The current state is read as the system user so that the permission checks below decide what the caller may do.
  // Every path checks the caller's permission on what was read before a conflict is reported, so a conflict never
  // reveals content or versions the caller may not see.
  // Deleted values are moved out of their property, so a property whose values were all deleted reads as empty and a
  // deleted value is not taken for the current version.
  private def readResource(edit: BatchEdit): Task[ReadResourceV2] =
    readResources
      .getResourcesWithDeletedResource(
        Seq(edit.resource),
        showDeletedValues = false,
        targetSchema = ApiV2Complex,
        schemaOptions = Set.empty,
        requestingUser = systemUser,
      )
      .flatMap(_.toResource(edit.resource))

  extension (edit: BatchEdit) {
    private def resource = edit match {
      case e: BatchEdit.SetLabel => e.resource
      case e: BatchEdit.SetValue => e.resource
    }
  }

  private def applyLabel(e: BatchEdit.SetLabel, user: User): Task[Unit] =
    for {
      resource <- readResource(e)
      _        <- resourceUtil.checkResourcePermission(resource, Permission.ObjectAccess.Modify, user)
      _        <- ZIO.foreachDiscard(e.expected)(exp =>
             ZIO.fail(Rejected(Conflict, s"The label is '${resource.label}', not '$exp'.")).when(exp != resource.label),
           )
      requestId <- Random.nextUUID
      _         <- resourcesResponder.updateResourceMetadataV2(
             UpdateResourceMetadataRequestV2(
               resourceIri = e.resource,
               resourceClassIri = resource.resourceClassIri,
               maybeLastModificationDate = resource.lastModificationDate,
               maybeLabel = Some(e.newLabel),
               requestingUser = user,
               apiRequestID = requestId,
             ),
           )
    } yield ()

  private def applyValue(e: BatchEdit.SetValue, user: User, created: Ref[Set[ValueIri]]): Task[ValueIri] =
    for {
      resource  <- readResource(e)
      requestId <- Random.nextUUID
      iri       <- e.value match {
               case None           => createValue(e, resource, user, requestId, created)
               case Some(valueIri) => updateValue(e, valueIri, resource, user, requestId)
             }
    } yield iri

  private def createValue(
    e: BatchEdit.SetValue,
    resource: ReadResourceV2,
    user: User,
    requestId: UUID,
    created: Ref[Set[ValueIri]],
  ) =
    for {
      _          <- resourceUtil.checkResourcePermission(resource, Permission.ObjectAccess.Modify, user)
      ownCreated <- created.get
      _          <- ZIO
             .fail(Rejected(Conflict, "The property is no longer empty."))
             .when(e.expectEmpty && resource.findValues(e.property).exists(v => !ownCreated.contains(v.valueIri)))
      content  <- newContentForProperty(e.property, e.newValue)
      response <- valuesResponder.createValueV2(
                    CreateValueV2(e.resource, resource.resourceClassIri, e.property.smartIri, content),
                    user,
                    requestId,
                  )
      _ <- created.update(_ + response.valueIri)
    } yield response.valueIri

  private def updateValue(
    e: BatchEdit.SetValue,
    valueIri: ValueIri,
    resource: ReadResourceV2,
    user: User,
    requestId: UUID,
  ) =
    for {
      // Before telling whether the version is current, so a caller who may not see the resource learns nothing.
      _       <- resourceUtil.checkResourcePermission(resource, Permission.ObjectAccess.View, user)
      current <- ZIO
                   .fromOption(resource.findValues(e.property).find(_.valueIri == valueIri))
                   .orElseFail(Rejected(Conflict, s"The value ${valueIri.value} is no longer the current version."))
      _      <- resourceUtil.checkValuePermission(resource, current, Permission.ObjectAccess.Modify, user)
      stored <- comparable(current.valueContent)
      _      <- ZIO.foreachDiscard(e.expected)(exp =>
             ZIO.fail(Rejected(Conflict, s"The value is '$stored', not '$exp'.")).when(exp != stored),
           )
      content <- newContent(current.valueContent, e.newValue)
      updated <- valuesResponder.updateValueV2(
                   UpdateValueContentV2(e.resource, resource.resourceClassIri, e.property.smartIri, valueIri, content),
                   user,
                   requestId,
                 )
    } yield updated.valueIri

  /** The form in which a stored value is compared to `expected`: the plain text, or the list node IRI. */
  private def comparable(content: ValueContentV2): IO[Rejected, String] = content match {
    case t: TextValueContentV2             => ZIO.succeed(t.maybeValueHasString.getOrElse(""))
    case l: HierarchicalListValueContentV2 => ZIO.succeed(l.valueHasListNode)
    case other                             => ZIO.fail(unsupported(other.valueType.toString))
  }

  /** The new version of a stored value. Its comment, and the language of a text, are carried over. */
  private def newContent(current: ValueContentV2, newValue: String): IO[Rejected, ValueContentV2] = current match {
    case t: TextValueContentV2 if isFormatted(t.textValueType) =>
      ZIO.fail(Rejected(Invalid, "Formatted text cannot be set by a batch edit: its markup would be lost."))
    case t: TextValueContentV2 =>
      ZIO.succeed(textContent(newValue, t.comment).copy(valueHasLanguage = t.valueHasLanguage))
    case l: HierarchicalListValueContentV2 => ZIO.succeed(listContent(newValue, l.comment))
    case other                             => ZIO.fail(unsupported(other.valueType.toString))
  }

  private def newContentForProperty(property: PropertyIri, newValue: String): Task[ValueContentV2] =
    for {
      info <- ontologyRepo
                .findProperty(property)
                .someOrFail(NotFoundException(s"The property ${property.smartIri} was not found."))
      objectType = info.entityInfoContent
                     .getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri)
                     .map(_.toOntologySchema(InternalSchema).toString)
      content <- objectType match {
                   case Some(OntologyConstants.KnoraBase.TextValue) => ZIO.succeed(textContent(newValue, None))
                   case Some(OntologyConstants.KnoraBase.ListValue) => ZIO.succeed(listContent(newValue, None))
                   case other                                       => ZIO.fail(unsupported(other.getOrElse("unknown")))
                 }
    } yield content

  private def isFormatted(textValueType: TextValueType): Boolean = textValueType match {
    case TextValueType.FormattedText | TextValueType.CustomFormattedText(_) => true
    case _                                                                  => false
  }

  private def textContent(text: String, comment: Option[String]) =
    TextValueContentV2(ApiV2Complex, Some(text), TextValueType.UnformattedText, comment = comment)

  private def listContent(nodeIri: String, comment: Option[String]) =
    HierarchicalListValueContentV2(ApiV2Complex, nodeIri, None, comment)

  private def unsupported(valueType: String) =
    Rejected(Invalid, s"Values of type $valueType are not supported; only text and list values are.")
}

object BatchEditService {
  val layer = ZLayer.derive[BatchEditService]
}
