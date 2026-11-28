/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import java.time.Instant
import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.InternalSchema
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.DeletionInfo
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadLinkValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

/** Resources and values as the gate's loader returns them: every object carries the `SystemUser` maximum. */
object AccessFixtures {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  val project    = TestDataFactory.someProjectADM
  val projectIri = project.id.value

  val resourceIri: ResourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/loaded-resource")
  val hasInteger: SmartIri     = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri
  val hasOtherThing: SmartIri  = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  val hasOtherThingValue       = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri
  private val loadedLevel      = Permission.ObjectAccess.ChangeRights
  private val anyone: IRI      = "http://rdfh.ch/users/someone-else"
  private val created          = Instant.parse("2026-01-01T00:00:00Z")

  def member: User =
    TestDataFactory.User.rootUser.copy(
      id = "http://rdfh.ch/users/member",
      username = "member",
      email = "member@example.com",
      permissions = PermissionsDataADM(
        groupsPerProject = Map(projectIri -> Seq("http://www.knora.org/ontology/knora-admin#ProjectMember")),
        administrativePermissionsPerProject = Map.empty,
      ),
    )

  def resource(values: Map[SmartIri, Seq[ReadValueV2]], literal: String = "M knora-admin:ProjectMember") =
    ReadResourceV2(
      resourceIri = resourceIri,
      label = "loaded",
      resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
      attachedToUser = anyone,
      projectADM = project,
      permissions = literal,
      userPermission = loadedLevel,
      values = values,
      creationDate = created,
      lastModificationDate = None,
      versionDate = None,
      deletionInfo = None,
    )

  def intValue(name: String, literal: String) =
    ReadOtherValueV2(
      valueIri = ValueIri.unsafeFrom(s"${resourceIri.value}/values/$name"),
      attachedToUser = anyone,
      permissions = literal,
      userPermission = loadedLevel,
      valueCreationDate = created,
      valueHasUUID = UUID.randomUUID(),
      valueContent = IntegerValueContentV2(InternalSchema, 1),
      previousValueIri = None,
      deletionInfo = None,
    )

  def linkValue(
    name: String,
    literal: String,
    target: ResourceIri,
    refCount: Int = 1,
    targetLiteral: Option[String] = None,
    deleted: Boolean = false,
  ) =
    ReadLinkValueV2(
      valueIri = ValueIri.unsafeFrom(s"${resourceIri.value}/values/$name"),
      attachedToUser = anyone,
      permissions = literal,
      userPermission = loadedLevel,
      valueCreationDate = created,
      valueHasUUID = UUID.randomUUID(),
      valueContent = LinkValueContentV2(
        InternalSchema,
        target,
        nestedResource = targetLiteral.map(l => resource(Map.empty, l).copy(resourceIri = target)),
      ),
      valueHasRefCount = refCount,
      deletionInfo = Option.when(deleted)(DeletionInfo(created, None)),
    )
}
