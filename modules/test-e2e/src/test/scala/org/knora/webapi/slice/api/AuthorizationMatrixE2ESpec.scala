/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import zio.*
import zio.json.JsonEncoder
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Paths

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.responders.IriService
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.ANYTHING_HasDate_PROPERTY_LocalHost
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.api.EndpointPolicyE2ESpec.key
import org.knora.webapi.slice.api.admin.CopyrightHolderAddRequest
import org.knora.webapi.slice.api.admin.CopyrightHolderReplaceRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.api.admin.PermissionEndpointsRequests.ChangeDoapRequest
import org.knora.webapi.slice.api.admin.Requests.*
import org.knora.webapi.slice.api.admin.ResourceSideLegalInfo
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.*
import org.knora.webapi.slice.api.admin.model.*
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.*
import org.knora.webapi.slice.api.admin.service.UserRestService.UserResponse
import org.knora.webapi.slice.api.v3.ontology.AddClassMappingsRequest
import org.knora.webapi.slice.api.v3.ontology.AddPropertyMappingsRequest
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.api.Policy
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.FileUtil

/**
 * The authorization matrix (ADR-0013): for every endpoint whose policy depends on the caller's role, a hand-written
 * outcome per principal. The table is an independent oracle: every cell is checked with a valid request, and the
 * principals a row allows must be the ones the endpoint's declared [[Policy]] allows, so a mislabelled policy fails too.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class AuthorizationMatrixE2ESpec extends E2EZSpec {
  import AuthorizationMatrixE2ESpec.*
  import AuthorizationMatrixE2ESpec.Expected.*
  import AuthorizationMatrixE2ESpec.Principal.*

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    anythingRdfOntology,
  )

  /**
   * One endpoint of the matrix. `prepare` creates a fresh target per cell through the API, so cells stay independent
   * and no shared dataset gains records. `readBack` renders what the request may change; it is `None` for an
   * endpoint that writes nothing. `selfOf` names the caller who is the row's target user; only a row with a `Self`
   * outcome has one.
   */
  private final case class Row[T](
    key: String,
    expected: Map[Principal, Expected],
    prepare: RIO[env, T],
    send: (T, Option[User]) => RIO[env, StatusCode],
    readBack: Option[T => RIO[env, String]],
    selfOf: Option[T => User] = None,
  )

  private def outcomes(
    anonymous: Expected,
    normalUser: Expected,
    anythingMember: Expected,
    incunabulaAdmin: Expected,
    anythingAdmin: Expected,
    root: Expected,
  ): Map[Principal, Expected] = Map(
    Anonymous       -> anonymous,
    NormalUser      -> normalUser,
    AnythingMember  -> anythingMember,
    IncunabulaAdmin -> incunabulaAdmin,
    AnythingAdmin   -> anythingAdmin,
    Root            -> root,
  )

  private def post[B: JsonEncoder](uri: Uri, body: B)(user: Option[User]) =
    user
      .fold(TestApiClient.postJson[Json, B](uri, body))(u => TestApiClient.postJson[Json, B](uri, body, u))
      .map(_.code)

  private def put[B: JsonEncoder](uri: Uri, body: B)(user: Option[User]) =
    user
      .fold(TestApiClient.putJson[Json, B](uri, body))(u => TestApiClient.putJson[Json, B](uri, body, u))
      .map(_.code)

  private def delete(uri: Uri)(user: Option[User]) =
    user.fold(TestApiClient.deleteJson[Json](uri))(u => TestApiClient.deleteJson[Json](uri, u)).map(_.code)

  private def labels(text: String)   = Labels.unsafeFrom(Seq(StringLiteralV2.from(text, EN)))
  private def comments(text: String) = Comments.unsafeFrom(Seq(StringLiteralV2.from(text, EN)))

  private val rootRequest =
    ListCreateRootNodeRequest(None, comments("matrix"), labels("matrix"), None, anythingProjectIri)

  private def childRequest(parent: ListIri, text: String) =
    ListCreateChildNodeRequest(None, Some(comments(text)), labels(text), None, parent, None, anythingProjectIri)

  /** A root list of the anything project with two children, the first of which has comments. */
  private final case class ListFixture(root: ListIri, first: ListIri)

  private val freshList: RIO[env, ListFixture] = {
    def createChild(parent: ListIri, text: String) =
      TestApiClient
        .postJson[ChildNodeInfoGetResponseADM, ListCreateChildNodeRequest](
          uri"/admin/lists/${parent.value}",
          childRequest(parent, text),
          anythingAdminUser,
        )
        .flatMap(_.assert200)
        .map(response => ListIri.unsafeFrom(response.nodeinfo.id))
    for {
      root <-
        TestApiClient
          .postJson[ListGetResponseADM, ListCreateRootNodeRequest](uri"/admin/lists", rootRequest, anythingAdminUser)
          .flatMap(_.assert200)
          .map(response => ListIri.unsafeFrom(response.list.listinfo.id))
      first <- createChild(root, "first")
      _     <- createChild(root, "second")
    } yield ListFixture(root, first)
  }

  // The project's root lists come back in no fixed order, so only their sorted IRIs are compared.
  private def listState(fixture: ListFixture): RIO[env, String] =
    for {
      rootLists <- TestApiClient
                     .getJson[ListsGetResponseADM](uri"/admin/lists?projectIri=${anythingProjectIri.value}")
                     .flatMap(_.assert200)
      tree <- TestApiClient.getAsString(uri"/admin/lists/${fixture.root.value}").flatMap(_.assert200)
    } yield rootLists.lists.map(_.id).sorted.mkString("\n") + tree

  private def listRow(key: String, expected: Map[Principal, Expected])(
    send: ListFixture => Option[User] => RIO[env, StatusCode],
  ) = Row[ListFixture](key, expected, freshList, (fixture, user) => send(fixture)(user), Some(listState))

  private def mappingBody(mappingName: String) = {
    val json = Json.Obj(
      "knora-api:mappingHasName"    -> Json.Str(mappingName),
      "knora-api:attachedToProject" -> Json.Obj("@id" -> Json.Str(anythingProjectIri.value)),
      "rdfs:label"                  -> Json.Str("matrix"),
      "@context"                    -> Json.Obj(
        "rdfs"      -> Json.Str(Rdfs.RdfsPrefixExpansion),
        "knora-api" -> Json.Str(KA.KnoraApiV2PrefixExpansion),
      ),
    )
    Seq(
      multipart("json", json.toString()).contentType("application/json"),
      multipartFile("xml", Paths.get("test_data/test_route/texts/mappingForLetter.xml")).contentType("text/xml(UTF-8)"),
    )
  }

  private def mappingExists(mappingName: String): RIO[env, String] =
    ZIO
      .fromEither(StandoffMappingIri.from(anythingProjectIri, mappingName))
      .mapError(Exception(_))
      .flatMap(iri => ZIO.serviceWithZIO[IriService](_.checkIriExists(iri.value)))
      .map(exists => s"mapping $mappingName exists: $exists")

  /** A project of a fresh shortname and shortcode; neither collides with the loaded data or another cell. */
  private final case class ProjectFixture(shortname: String, shortcode: String)

  private val freshProjectFixture: UIO[ProjectFixture] =
    Random.nextIntBetween(0xf000, 0x10000).zip(Random.nextUUID).map { case (code, uuid) =>
      ProjectFixture(s"mx-${uuid.toString.take(8)}", f"$code%04x")
    }

  private def projectRequest(fixture: ProjectFixture) =
    ProjectCreateRequest(
      None,
      Shortname.unsafeFrom(fixture.shortname),
      Shortcode.unsafeFrom(fixture.shortcode),
      None,
      List(Description.unsafeFrom(StringLiteralV2.from("matrix", EN))),
      List(Keyword.unsafeFrom("matrix")),
      None,
      SelfJoin.CannotJoin,
      None,
      Some(Set(LicenseIri.CC_BY_4_0)),
    )

  private val freshProject: RIO[env, ProjectFixture] =
    for {
      fixture <- freshProjectFixture
      _       <- TestApiClient
             .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
               uri"/admin/projects",
               projectRequest(fixture),
               rootUser,
             )
             .flatMap(_.assert200)
    } yield fixture

  private def projectExists(fixture: ProjectFixture): RIO[env, String] =
    TestApiClient
      .getJson[Json](uri"/admin/projects/shortcode/${fixture.shortcode}", rootUser)
      .map(response => s"project ${fixture.shortcode} exists: ${response.code == StatusCode.Ok}")

  private val freshUserWithEmail: RIO[env, (UserIri, Email)] =
    for {
      uuid <- Random.nextUUID
      name  = s"matrix-${uuid.toString.take(8)}"
      user <- TestApiClient
                .postJson[UserResponse, UserCreateRequest](
                  uri"/admin/users",
                  UserCreateRequest(
                    id = None,
                    username = Username.unsafeFrom(name),
                    email = Email.unsafeFrom(s"$name@example.org"),
                    givenName = GivenName.unsafeFrom("matrix"),
                    familyName = FamilyName.unsafeFrom("matrix"),
                    password = Password.unsafeFrom("test"),
                    status = UserStatus.Active,
                    lang = EN,
                    systemAdmin = SystemAdmin.IsNotSystemAdmin,
                  ),
                  rootUser,
                )
                .flatMap(_.assert200)
    } yield (UserIri.unsafeFrom(user.user.id), Email.unsafeFrom(s"$name@example.org"))

  private val freshUser: RIO[env, UserIri] = freshUserWithEmail.map(_._1)

  private def userCreateRequest(name: String) =
    UserCreateRequest(
      id = None,
      username = Username.unsafeFrom(name),
      email = Email.unsafeFrom(s"$name@example.org"),
      givenName = GivenName.unsafeFrom("matrix"),
      familyName = FamilyName.unsafeFrom("matrix"),
      password = Password.unsafeFrom("test"),
      status = UserStatus.Active,
      lang = EN,
      systemAdmin = SystemAdmin.IsNotSystemAdmin,
    )

  private def userExists(name: String): RIO[env, String] =
    TestApiClient
      .getJson[Json](uri"/admin/users/username/$name", rootUser)
      .map(response => s"user $name exists: ${response.code == StatusCode.Ok}")

  // The caller who is the target; the API reads the user from the token's subject, so only the IRI matters.
  private def selfUser(iri: UserIri) =
    User(iri.value, "self", "self@example.org", "Self", "Self", status = true, "en")

  private def userRow(key: String, send: UserIri => Option[User] => RIO[env, StatusCode]) =
    Row[UserIri](
      key,
      selfOrAdminOutcomes,
      freshUser,
      (iri, user) => send(iri)(user),
      Some(userState),
      Some(selfUser),
    )

  private def selfOrAdminRead(key: String, kind: String) =
    Row[UserIri](
      key,
      selfOrAdminOutcomes,
      freshUser,
      (iri, user) => get(uri"/admin/users/iri/${iri.value}/$kind")(user),
      readBack = None,
      selfOf = Some(selfUser),
    )

  private def canLogIn(fixture: (UserIri, Email)): RIO[env, String] =
    ZIO
      .foreach(List("test", matrixPassword))(password =>
        TestApiClient
          .getJson[Json](uri"/v2/authentication", _.auth.basic(fixture._2.value, password))
          .map(response => s"$password: ${response.code}"),
      )
      .map(_.mkString("\n"))

  private val matrixPassword = "matrix-password"

  private def userState(iri: UserIri): RIO[env, String] =
    TestApiClient.getJson[Json](uri"/admin/users/iri/${iri.value}", rootUser).map(_.body.toString)

  private def memberships(iri: UserIri): RIO[env, String] =
    ZIO
      .foreach(List("project-memberships", "project-admin-memberships"))(kind =>
        TestApiClient.getJson[Json](uri"/admin/users/iri/${iri.value}/$kind", rootUser).map(_.body.toString),
      )
      .map(_.mkString("\n"))

  private def membershipUri(iri: UserIri, kind: String) =
    uri"/admin/users/iri/${iri.value}/$kind/${anythingProjectIri.value}"

  private def joinAnything(kind: String)(iri: UserIri): RIO[env, UserIri] =
    TestApiClient.postJson[Json, Json](membershipUri(iri, kind), Json.Obj(), rootUser).flatMap(_.assert200).as(iri)

  private val freshMember = freshUser.flatMap(joinAnything("project-memberships"))
  private val freshAdmin  = freshMember.flatMap(joinAnything("project-admin-memberships"))

  private def membershipRow(key: String, prepare: RIO[env, UserIri])(
    send: UserIri => Option[User] => RIO[env, StatusCode],
  ) = Row[UserIri](
    key,
    outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
    prepare,
    (iri, user) => send(iri)(user),
    Some(memberships),
  )

  private def get(uri: Uri)(user: Option[User]) =
    user.fold(TestApiClient.getJson[Json](uri))(u => TestApiClient.getJson[Json](uri, u)).map(_.code)

  private val anythingByIri = uri"/admin/projects/iri/${anythingProjectIri.value}"
  private val anythingThing = "http://www.knora.org/ontology/0001/anything#Thing"
  private val anythingText  = "http://www.knora.org/ontology/0001/anything#hasText"

  /** A read of the anything project, which an admin of it may make; it writes nothing. */
  private def anythingRead(key: String, uri: Uri) =
    Row[Unit](
      key,
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = ZIO.unit,
      send = (_, user) => get(uri)(user),
      readBack = None,
    )

  private val anythingOntologyV2 = "http://0.0.0.0:3333/ontology/0001/anything/v2"
  private val anythingThingV2    = s"$anythingOntologyV2#Thing"
  private val anythingTextV2     = s"$anythingOntologyV2#hasText"
  private val externalIri        = "https://schema.org/Thing"

  private val classMappingUri    = uri"/v3/ontologies/$anythingOntologyV2/classes/$anythingThingV2/mapping"
  private val propertyMappingUri =
    uri"/v3/ontologies/$anythingOntologyV2/properties/$anythingTextV2/mapping"

  private val classDefinitionUri    = uri"/v2/ontologies/classes/$anythingThingV2"
  private val propertyDefinitionUri = uri"/v2/ontologies/properties/$anythingTextV2"

  private def mappingParam(uri: Uri) = uri.addParam("mapping", externalIri)

  private def addMapping[B: JsonEncoder](uri: Uri, body: B): RIO[env, Unit] =
    TestApiClient.putJson[Json, B](uri, body, rootUser).flatMap(_.assert200).unit

  private def removeMapping(uri: Uri): RIO[env, Unit] =
    TestApiClient.deleteJson[Json](mappingParam(uri), rootUser).flatMap(_.assert200).unit

  /** What the mapping endpoints change: the entity's definition as the ontology API shows it. */
  private def definition(uri: Uri): RIO[env, String] =
    TestApiClient.getJson[Json](uri, rootUser).map(_.body.toString)

  private def mappingRow(key: String, definitionUri: Uri, prepare: RIO[env, Unit])(
    send: Option[User] => RIO[env, StatusCode],
  ) =
    Row[Unit](key, projectAdminOutcomes, prepare, (_, user) => send(user), Some(_ => definition(definitionUri)))

  private val anythingPermissions          = uri"/admin/permissions"
  private def permissionsOf(kind: String*) =
    anythingPermissions.addPath(kind).addPath(anythingProjectIri.value)

  private def groupRequest(name: String) =
    GroupCreateRequest(
      name = GroupName.unsafeFrom(name),
      descriptions = GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("matrix", EN))),
      project = anythingProjectIri,
      status = GroupStatus.active,
      selfjoin = GroupSelfJoin.disabled,
    )

  private def groupExists(name: String): RIO[env, String] =
    TestApiClient
      .getJson[Json](uri"/admin/groups", rootUser)
      .map(response => s"group $name exists: ${response.body.toString.contains(s"\"$name\"")}")

  private val anythingByShortcode = uri"/admin/projects/shortcode/${anythingShortcode}"
  private val anythingByShortname = uri"/admin/projects/shortname/anything"
  private val legalInfo           = anythingByShortcode.addPath("legal-info")

  /** A read of the anything project that any of its members may make; it writes nothing. */
  private def anythingMemberRead(key: String, uri: Uri) =
    Row[Unit](
      key,
      outcomes(unauthorized, forbidden, allowed, forbidden, allowed, allowed),
      prepare = ZIO.unit,
      send = (_, user) => get(uri)(user),
      readBack = None,
    )

  private val freshName = Random.nextUUID.map(uuid => s"matrix-${uuid.toString.take(8)}")

  private val projectAdminOutcomes = outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed)

  private val systemAdminOutcomes = outcomes(unauthorized, forbidden, forbidden, forbidden, forbidden, allowed)

  private val selfOrAdminOutcomes =
    outcomes(unauthorized, forbidden, forbidden, forbidden, forbidden, allowed) + (Self -> allowed)

  private val anyProjectAdminOutcomes = outcomes(unauthorized, forbidden, forbidden, allowed, allowed, allowed)

  private def copyrightHolderPresent(name: String): RIO[env, String] =
    TestApiClient
      .getJson[Json](legalInfo.addPath("copyright-holders"), rootUser)
      .map(response => s"copyright holder $name present: ${response.body.toString.contains(name)}")

  private val matrixLicense = LicenseIri.CC_BY_NC_4_0

  private def licenseEnabled: RIO[env, String] =
    TestApiClient
      .getJson[Json](legalInfo.addPath("licenses").addParam("showOnlyEnabled", "true"), rootUser)
      .map(response => s"license enabled: ${response.body.toString.contains(matrixLicense.value)}")

  private def licenseRow(key: String, action: String, before: String) =
    Row[Unit](
      key,
      projectAdminOutcomes,
      prepare = put(legalInfo.addPath("licenses", matrixLicense.value, before), Json.Obj())(Some(rootUser))
        .filterOrFail(_ == StatusCode.Ok)(Exception(s"could not $before the matrix license"))
        .unit,
      send = (_, user) => put(legalInfo.addPath("licenses", matrixLicense.value, action), Json.Obj())(user),
      readBack = Some(_ => licenseEnabled),
    )

  private final case class PermissionFixture(iri: PermissionIri, group: GroupIri, other: GroupIri)

  private val memberPermissions = Set(
    PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
  )

  private def freshAp: RIO[env, PermissionFixture] =
    for {
      group   <- freshGroup
      other   <- freshGroup
      created <- TestApiClient
                   .postJson[AdministrativePermissionCreateResponseADM, CreateAdministrativePermissionAPIRequestADM](
                     anythingPermissions.addPath("ap"),
                     CreateAdministrativePermissionAPIRequestADM(
                       forProject = anythingProjectIri,
                       forGroup = group,
                       hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
                     ),
                     rootUser,
                   )
                   .flatMap(_.assert200)
    } yield PermissionFixture(created.administrativePermission.iri, group, other)

  private def freshDoap: RIO[env, PermissionFixture] =
    for {
      group   <- freshGroup
      other   <- freshGroup
      created <-
        TestApiClient
          .postJson[DefaultObjectAccessPermissionCreateResponseADM, CreateDefaultObjectAccessPermissionAPIRequestADM](
            anythingPermissions.addPath("doap"),
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = anythingProjectIri,
              forGroup = Some(group),
              hasPermissions = memberPermissions,
            ),
            rootUser,
          )
          .flatMap(_.assert200)
    } yield PermissionFixture(created.defaultObjectAccessPermission.iri, group, other)

  /** The administrative and default object access permissions of the anything project whose `field` is `value`. */
  private def permissionsWith(field: String, value: String): RIO[env, String] = {
    def find(json: Json): List[Json] = json match {
      case Json.Obj(fields) =>
        (if fields.contains(field -> Json.Str(value)) then List(json) else Nil) ++ fields.flatMap(f => find(f._2))
      case Json.Arr(elements) => elements.toList.flatMap(find)
      case _                  => Nil
    }
    ZIO
      .foreach(List("ap", "doap"))(kind =>
        TestApiClient
          .getJson[Json](permissionsOf(kind), rootUser)
          .map(_.body.fold(identity, json => find(json).mkString("\n"))),
      )
      .map(_.mkString("\n"))
  }

  private def permissionState(fixture: PermissionFixture) = permissionsWith("iri", fixture.iri.value)

  private def permissionRow(key: String, prepare: RIO[env, PermissionFixture])(
    send: PermissionFixture => Option[User] => RIO[env, StatusCode],
  ) = Row[PermissionFixture](
    key,
    systemAdminOutcomes,
    prepare,
    (fixture, user) => send(fixture)(user),
    Some(permissionState),
  )

  private def groupUri(iri: GroupIri) = uri"/admin/groups/${iri.value}"

  private val freshGroup: RIO[env, GroupIri] =
    for {
      name  <- freshName
      group <- TestApiClient
                 .postJson[GroupGetResponseADM, GroupCreateRequest](uri"/admin/groups", groupRequest(name), rootUser)
                 .flatMap(_.assert200)
    } yield GroupIri.unsafeFrom(group.group.id)

  private def groupState(iri: GroupIri): RIO[env, String] =
    TestApiClient.getJson[Json](groupUri(iri), rootUser).map(_.body.toString)

  private def groupRow(key: String, readBack: Option[GroupIri => RIO[env, String]])(
    send: GroupIri => Option[User] => RIO[env, StatusCode],
  ) = Row[GroupIri](key, projectAdminOutcomes, freshGroup, (iri, user) => send(iri)(user), readBack)

  private def groupMemberships(fixture: (UserIri, GroupIri)): RIO[env, String] =
    TestApiClient
      .getJson[Json](uri"/admin/users/iri/${fixture._1.value}/group-memberships", rootUser)
      .map(_.body.toString)

  private def groupMembershipUri(fixture: (UserIri, GroupIri)) =
    uri"/admin/users/iri/${fixture._1.value}/group-memberships/${fixture._2.value}"

  private def groupMembershipRow(key: String, joined: Boolean)(
    send: ((UserIri, GroupIri)) => Option[User] => RIO[env, StatusCode],
  ) = Row[(UserIri, GroupIri)](
    key,
    projectAdminOutcomes,
    prepare = for {
      user   <- freshUser
      group  <- freshGroup
      fixture = (user, group)
      _      <- TestApiClient
             .postJson[Json, Json](groupMembershipUri(fixture), Json.Obj(), rootUser)
             .flatMap(_.assert200)
             .when(joined)
    } yield fixture,
    send = (fixture, user) => send(fixture)(user),
    readBack = Some(groupMemberships),
  )

  private def ontologyRequest(name: String) =
    s"""{
       |  "knora-api:ontologyName": "$name",
       |  "knora-api:attachedToProject": { "@id": "${anythingProjectIri.value}" },
       |  "rdfs:label": "matrix",
       |  "@context": {
       |    "rdfs": "${Rdfs.RdfsPrefixExpansion}",
       |    "knora-api": "${KA.KnoraApiV2PrefixExpansion}"
       |  }
       |}""".stripMargin

  private def postOntology(name: String)(user: Option[User]) =
    TestApiClient.postJsonLdDocument(uri"/v2/ontologies", ontologyRequest(name), user, identity)

  private val freshOntologyName = Random.nextUUID.map(uuid => s"mx${uuid.toString.filter(_.isLetter).take(10)}")

  /** A fresh ontology in the anything project, as its IRI and last modification date. */
  private final case class OntologyFixture(name: String, iri: String, lastModificationDate: String)

  private val freshOntology: RIO[env, OntologyFixture] =
    for {
      name <- freshOntologyName
      doc  <- postOntology(name)(Some(rootUser)).flatMap(_.assert200)
      iri  <- ZIO.fromEither(doc.body.getRequiredString("@id")).mapError(Exception(_))
    } yield OntologyFixture(name, iri, lastModificationOf(doc))

  private def lastModificationOf(doc: JsonLDDocument): String =
    doc.body.requireDatatypeValueInObject(KA.LastModificationDate, Xsd.DateTimeStamp.toSmartIri, (value, _) => value)

  private def ontologyExists(name: String): RIO[env, String] =
    TestApiClient
      .getJsonLd(uri"/v2/ontologies/metadata/${anythingProjectIri.value}", rootUser)
      .map(response => s"ontology $name exists: ${response.body.exists(_.contains(s"/$name/v2"))}")

  private def ontologyBody(onto: OntologyFixture, topLevel: String = "", graph: Seq[String] = Seq.empty) = {
    val graphField = if graph.isEmpty then "" else s""""@graph": [ ${graph.mkString(", ")} ],"""
    s"""{
       |  "@id": "${onto.iri}",
       |  "@type": "owl:Ontology",
       |  $topLevel
       |  "knora-api:lastModificationDate": { "@type": "xsd:dateTimeStamp", "@value": "${onto.lastModificationDate}" },
       |  $graphField
       |  "@context": {
       |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
       |    "rdfs": "${Rdfs.RdfsPrefixExpansion}",
       |    "owl": "http://www.w3.org/2002/07/owl#",
       |    "xsd": "http://www.w3.org/2001/XMLSchema#",
       |    "knora-api": "${KA.KnoraApiV2PrefixExpansion}",
       |    "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
       |    "${onto.name}": "${onto.iri}#"
       |  }
       |}""".stripMargin
  }

  private def langString(text: String) = s"""{ "@language": "en", "@value": "$text" }"""

  private def classEntry(onto: OntologyFixture) =
    s"""{
       |  "@id": "${onto.name}:MatrixClass",
       |  "@type": "owl:Class",
       |  "rdfs:label": ${langString("matrix class")},
       |  "rdfs:comment": ${langString("a matrix class")},
       |  "rdfs:subClassOf": { "@id": "knora-api:Resource" }
       |}""".stripMargin

  private def propertyEntry(onto: OntologyFixture, withSubject: Boolean) = {
    val subject = if withSubject then s""""knora-api:subjectType": { "@id": "${onto.name}:MatrixClass" },""" else ""
    s"""{
       |  "@id": "${onto.name}:hasMatrix",
       |  "@type": "owl:ObjectProperty",
       |  $subject
       |  "knora-api:objectType": { "@id": "knora-api:TextValue" },
       |  "rdfs:label": ${langString("has matrix")},
       |  "rdfs:comment": ${langString("a matrix property")},
       |  "rdfs:subPropertyOf": { "@id": "knora-api:hasValue" }
       |}""".stripMargin
  }

  private def cardinality(onto: OntologyFixture, restriction: String) =
    s"""{
       |  "@id": "${onto.name}:MatrixClass",
       |  "@type": "owl:Class",
       |  "rdfs:subClassOf": {
       |    "@type": "owl:Restriction",
       |    $restriction
       |    "owl:onProperty": { "@id": "${onto.name}:hasMatrix" }
       |  }
       |}""".stripMargin

  private def classOf(onto: OntologyFixture)    = s"${onto.iri}#MatrixClass"
  private def propertyOf(onto: OntologyFixture) = s"${onto.iri}#hasMatrix"

  private def postJsonLd(uri: Uri, body: String)(user: Option[User]) =
    TestApiClient.postJsonLdDocument(uri, body, user, identity).map(_.code)

  private def putJsonLd(uri: Uri, body: String)(user: Option[User]) =
    ZIO.serviceWithZIO[TestApiClient](_.putJsonLdDocument(uri, body, user, identity)).map(_.code)

  private def patchJsonLd(uri: Uri, body: String)(user: Option[User]) =
    ZIO.serviceWithZIO[TestApiClient](_.patchJsonLdDocument(uri, body, user, identity)).map(_.code)

  private def createdBy(uri: Uri, onto: OntologyFixture, graph: Seq[String]): RIO[env, OntologyFixture] =
    TestApiClient
      .postJsonLdDocument(uri, ontologyBody(onto, graph = graph), rootUser)
      .flatMap(_.assert200)
      .map(doc => onto.copy(lastModificationDate = lastModificationOf(doc)))

  private val ontologyWithComment: RIO[env, OntologyFixture] =
    freshOntology.flatMap(onto =>
      TestApiClient
        .putJsonLdDocument(
          uri"/v2/ontologies/metadata",
          ontologyBody(onto, topLevel = """"rdfs:comment": "a matrix comment","""),
          rootUser,
        )
        .flatMap(_.assert200)
        .map(doc => onto.copy(lastModificationDate = lastModificationOf(doc))),
    )

  private val ontologyWithClass: RIO[env, OntologyFixture] =
    freshOntology.flatMap(onto => createdBy(uri"/v2/ontologies/classes", onto, Seq(classEntry(onto))))

  private val ontologyWithProperty: RIO[env, OntologyFixture] =
    ontologyWithClass.flatMap(onto =>
      createdBy(uri"/v2/ontologies/properties", onto, Seq(propertyEntry(onto, withSubject = true))),
    )

  private val ontologyWithCardinality: RIO[env, OntologyFixture] =
    ontologyWithProperty.flatMap(onto =>
      createdBy(uri"/v2/ontologies/cardinalities", onto, Seq(cardinality(onto, """"owl:maxCardinality": 1,"""))),
    )

  /** The whole ontology as the API shows it, including its last modification date: every write to it changes this. */
  private def ontologyState(onto: OntologyFixture): RIO[env, String] =
    TestApiClient.getJsonLd(uri"/v2/ontologies/allentities/${onto.iri}", rootUser).map(_.body.toString)

  private def ontologyRow(key: String, prepare: RIO[env, OntologyFixture])(
    send: OntologyFixture => Option[User] => RIO[env, StatusCode],
  ) = Row[OntologyFixture](key, projectAdminOutcomes, prepare, (onto, user) => send(onto)(user), Some(ontologyState))

  private val restrictedViewUri          = anythingByIri.addPath("RestrictedViewSettings")
  private val shortcodeRestrictedViewUri = uri"/admin/projects/shortcode/${anythingShortcode}/RestrictedViewSettings"
  private val restrictedViewSet          = SetRestrictedViewRequest(Some(RestrictedView.Size.unsafeFrom("!512,512")), None)

  private val anythingState: RIO[env, String] =
    TestApiClient.getJson[Json](anythingByIri, rootUser).map(_.body.toString)

  private val restrictedViewState: RIO[env, String] =
    TestApiClient.getJson[Json](restrictedViewUri, rootUser).map(_.body.toString)

  // The oracle. Columns: anonymous, normalUser, anythingUser1, incunabulaProjectAdminUser, anythingAdminUser, rootUser.
  private val rows: List[Row[?]] = List(
    listRow("POST /admin/lists", outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed))(_ =>
      post(uri"/admin/lists", rootRequest),
    ),
    listRow("POST /admin/lists/{param1}", outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed))(
      fixture => post(uri"/admin/lists/${fixture.root.value}", childRequest(fixture.root, "third")),
    ),
    listRow(
      "PUT /admin/lists/{param1}/name",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
    )(fixture =>
      // List names are unique within a project, so each cell names its fresh node after the node itself.
      put(
        uri"/admin/lists/${fixture.first.value}/name",
        ListChangeNameRequest(ListName.unsafeFrom(s"renamed-${fixture.first.value.split('/').last}")),
      ),
    ),
    listRow(
      "PUT /admin/lists/{param1}/labels",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
    )(fixture => put(uri"/admin/lists/${fixture.first.value}/labels", ListChangeLabelsRequest(labels("relabelled")))),
    listRow(
      "PUT /admin/lists/{param1}/comments",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
    )(fixture =>
      put(uri"/admin/lists/${fixture.first.value}/comments", ListChangeCommentsRequest(comments("recommented"))),
    ),
    listRow(
      "PUT /admin/lists/{param1}/position",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
    )(fixture =>
      put(
        uri"/admin/lists/${fixture.first.value}/position",
        ListChangePositionRequest(Position.unsafeFrom(1), fixture.root),
      ),
    ),
    listRow("PUT /admin/lists/{param1}", outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed))(
      fixture =>
        put(
          uri"/admin/lists/${fixture.first.value}",
          ListChangeRequest(fixture.first, anythingProjectIri, labels = Some(labels("changed"))),
        ),
    ),
    listRow("DELETE /admin/lists/{param1}", outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed))(
      fixture => delete(uri"/admin/lists/${fixture.first.value}"),
    ),
    listRow(
      "DELETE /admin/lists/comments/{param1}",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
    )(fixture => delete(uri"/admin/lists/comments/${fixture.first.value}")),
    Row[ProjectFixture](
      "POST /admin/projects",
      outcomes(unauthorized, forbidden, forbidden, forbidden, forbidden, allowed),
      prepare = freshProjectFixture,
      send = (fixture, user) => post(uri"/admin/projects", projectRequest(fixture))(user),
      readBack = Some(projectExists),
    ),
    Row[ProjectFixture](
      "DELETE /admin/projects/shortcode/{projectShortcode}/erase",
      outcomes(unauthorized, forbidden, forbidden, forbidden, forbidden, allowed),
      prepare = freshProject,
      send = (fixture, user) => delete(uri"/admin/projects/shortcode/${fixture.shortcode}/erase")(user),
      readBack = Some(projectExists),
    ),
    Row[UserIri](
      "PUT /admin/users/iri/{userIri}/SystemAdmin",
      outcomes(unauthorized, forbidden, forbidden, forbidden, forbidden, allowed),
      prepare = freshUser,
      send = (iri, user) =>
        put(uri"/admin/users/iri/${iri.value}/SystemAdmin", SystemAdminChangeRequest(SystemAdmin.IsSystemAdmin))(user),
      readBack = Some(userState),
    ),
    Row[String](
      "POST /v2/mapping",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = Random.nextUUID.map(uuid => s"Matrix-$uuid"),
      send = (mappingName, user) =>
        user
          .fold(TestApiClient.postMultiPart[Json](uri"/v2/mapping", mappingBody(mappingName)))(u =>
            TestApiClient.postMultiPart[Json](uri"/v2/mapping", mappingBody(mappingName), u),
          )
          .map(_.code),
      readBack = Some(mappingExists),
    ),
    userRow("DELETE /admin/users/iri/{userIri}", iri => delete(uri"/admin/users/iri/${iri.value}")),
    userRow(
      "PUT /admin/users/iri/{userIri}/BasicUserInformation",
      iri =>
        put(
          uri"/admin/users/iri/${iri.value}/BasicUserInformation",
          BasicUserInformationChangeRequest(givenName = Some(GivenName.unsafeFrom("changed"))),
        ),
    ),
    userRow(
      "PUT /admin/users/iri/{userIri}/Status",
      iri => put(uri"/admin/users/iri/${iri.value}/Status", StatusChangeRequest(UserStatus.Inactive)),
    ),
    Row[(UserIri, Email)](
      "PUT /admin/users/iri/{userIri}/Password",
      selfOrAdminOutcomes,
      prepare = freshUserWithEmail,
      send = (fixture, user) =>
        put(
          uri"/admin/users/iri/${fixture._1.value}/Password",
          PasswordChangeRequest(Password.unsafeFrom("test"), Password.unsafeFrom(matrixPassword)),
        )(user),
      readBack = Some(canLogIn),
      selfOf = Some(fixture => selfUser(fixture._1)),
    ),
    Row[Unit](
      "GET /admin/users",
      anyProjectAdminOutcomes,
      prepare = ZIO.unit,
      send = (_, user) => get(uri"/admin/users")(user),
      readBack = None,
    ),
    Row[String](
      "POST /admin/users",
      anyProjectAdminOutcomes,
      prepare = freshName,
      send = (name, user) => post(uri"/admin/users", userCreateRequest(name))(user),
      readBack = Some(userExists),
    ),
    selfOrAdminRead("GET /admin/users/iri/{userIri}/project-memberships", "project-memberships"),
    selfOrAdminRead("GET /admin/users/iri/{userIri}/project-admin-memberships", "project-admin-memberships"),
    selfOrAdminRead("GET /admin/users/iri/{userIri}/group-memberships", "group-memberships"),
    anythingRead("GET /admin/projects/iri/{projectIri}/AllData", anythingByIri.addPath("AllData")),
    anythingRead("GET /admin/projects/iri/{projectIri}/members", anythingByIri.addPath("members")),
    anythingRead("GET /admin/projects/iri/{projectIri}/admin-members", anythingByIri.addPath("admin-members")),
    anythingRead(
      "GET /admin/projects/iri/{projectIri}/view-restrictions/classes",
      anythingByIri.addPath("view-restrictions", "classes"),
    ),
    anythingRead(
      "GET /admin/projects/iri/{projectIri}/view-restrictions/values",
      anythingByIri.addPath("view-restrictions", "values").addParam("resourceClass", anythingThing),
    ),
    anythingRead(
      "GET /admin/projects/iri/{projectIri}/view-restrictions/items",
      anythingByIri.addPath("view-restrictions", "items").addParam("resourceClass", anythingThing),
    ),
    anythingRead(
      "GET /admin/projects/iri/{projectIri}/view-restrictions/properties",
      anythingByIri.addPath("view-restrictions", "properties"),
    ),
    anythingRead(
      "GET /admin/projects/iri/{projectIri}/view-restrictions/property-values",
      anythingByIri.addPath("view-restrictions", "property-values").addParam("property", anythingText),
    ),
    anythingRead(
      "GET /admin/projects/iri/{projectIri}/view-restrictions/property-items",
      anythingByIri.addPath("view-restrictions", "property-items").addParam("property", anythingText),
    ),
    Row[Longname](
      "PUT /admin/projects/iri/{projectIri}",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = Random.nextUUID.map(uuid => Longname.unsafeFrom(s"matrix $uuid")),
      send = (longname, user) => put(anythingByIri, ProjectUpdateRequest(longname = Some(longname)))(user),
      readBack = Some(_ => anythingState),
    ),
    // The two restricted-view writes start from the opposite state, so an allowed cell always changes it.
    Row[Unit](
      "POST /admin/projects/iri/{projectIri}/RestrictedViewSettings",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = TestApiClient.deleteJson[Json](restrictedViewUri, rootUser).flatMap(_.assert200).unit,
      send = (_, user) => post(restrictedViewUri, restrictedViewSet)(user),
      readBack = Some(_ => restrictedViewState),
    ),
    Row[Unit](
      "DELETE /admin/projects/iri/{projectIri}/RestrictedViewSettings",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = TestApiClient
        .postJson[Json, SetRestrictedViewRequest](restrictedViewUri, restrictedViewSet, rootUser)
        .flatMap(_.assert200)
        .unit,
      send = (_, user) => delete(restrictedViewUri)(user),
      readBack = Some(_ => restrictedViewState),
    ),
    membershipRow("POST /admin/users/iri/{userIri}/project-memberships/{projectIri}", freshUser)(iri =>
      post(membershipUri(iri, "project-memberships"), Json.Obj()),
    ),
    membershipRow("POST /admin/users/iri/{userIri}/project-admin-memberships/{projectIri}", freshMember)(iri =>
      post(membershipUri(iri, "project-admin-memberships"), Json.Obj()),
    ),
    membershipRow("DELETE /admin/users/iri/{userIri}/project-memberships/{projectIri}", freshMember)(iri =>
      delete(membershipUri(iri, "project-memberships")),
    ),
    membershipRow("DELETE /admin/users/iri/{userIri}/project-admin-memberships/{projectIri}", freshAdmin)(iri =>
      delete(membershipUri(iri, "project-admin-memberships")),
    ),
    anythingRead("GET /admin/projects/shortcode/{projectShortcode}/members", anythingByShortcode.addPath("members")),
    anythingRead(
      "GET /admin/projects/shortcode/{projectShortcode}/admin-members",
      anythingByShortcode.addPath("admin-members"),
    ),
    anythingRead("GET /admin/projects/shortname/{projectShortname}/members", anythingByShortname.addPath("members")),
    anythingRead(
      "GET /admin/projects/shortname/{projectShortname}/admin-members",
      anythingByShortname.addPath("admin-members"),
    ),
    anythingRead(
      "GET /v2/metadata/projects/{projectShortcode}/resources",
      uri"/v2/metadata/projects/$anythingShortcode/resources",
    ),
    Row[Unit](
      "POST /admin/projects/shortcode/{projectShortcode}/RestrictedViewSettings",
      projectAdminOutcomes,
      prepare = TestApiClient.deleteJson[Json](restrictedViewUri, rootUser).flatMap(_.assert200).unit,
      send = (_, user) => post(shortcodeRestrictedViewUri, restrictedViewSet)(user),
      readBack = Some(_ => restrictedViewState),
    ),
    Row[Unit](
      "DELETE /admin/projects/shortcode/{projectShortcode}/RestrictedViewSettings",
      projectAdminOutcomes,
      prepare = TestApiClient
        .postJson[Json, SetRestrictedViewRequest](restrictedViewUri, restrictedViewSet, rootUser)
        .flatMap(_.assert200)
        .unit,
      send = (_, user) => delete(shortcodeRestrictedViewUri)(user),
      readBack = Some(_ => restrictedViewState),
    ),
    groupRow("GET /admin/groups/{groupIri}/members", None)(iri => get(groupUri(iri).addPath("members"))),
    groupRow("PUT /admin/groups/{groupIri}", Some(groupState))(iri =>
      put(groupUri(iri), GroupUpdateRequest(name = Some(GroupName.unsafeFrom(s"renamed-${iri.value.split('/').last}")))),
    ),
    groupRow("PUT /admin/groups/{groupIri}/status", Some(groupState))(iri =>
      put(groupUri(iri).addPath("status"), GroupStatusUpdateRequest(GroupStatus.inactive)),
    ),
    groupRow("DELETE /admin/groups/{groupIri}", Some(groupState))(iri => delete(groupUri(iri))),
    groupMembershipRow("POST /admin/users/iri/{userIri}/group-memberships/{groupIri}", joined = false)(fixture =>
      post(groupMembershipUri(fixture), Json.Obj()),
    ),
    groupMembershipRow("DELETE /admin/users/iri/{userIri}/group-memberships/{groupIri}", joined = true)(fixture =>
      delete(groupMembershipUri(fixture)),
    ),
    anythingMemberRead(
      "GET /admin/projects/shortcode/{projectShortcode}/legal-info/authorships",
      legalInfo.addPath("authorships"),
    ),
    anythingMemberRead(
      "GET /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders",
      legalInfo.addPath("copyright-holders"),
    ),
    Row[String](
      "POST /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders",
      projectAdminOutcomes,
      prepare = freshName,
      send = (name, user) =>
        post(
          legalInfo.addPath("copyright-holders"),
          CopyrightHolderAddRequest(Set(CopyrightHolder.unsafeFrom(name))),
        )(user),
      readBack = Some(copyrightHolderPresent),
    ),
    licenseRow(
      "PUT /admin/projects/shortcode/{projectShortcode}/legal-info/licenses/{licenseIri}/enable",
      "enable",
      "disable",
    ),
    licenseRow(
      "PUT /admin/projects/shortcode/{projectShortcode}/legal-info/licenses/{licenseIri}/disable",
      "disable",
      "enable",
    ),
    Row[String](
      "PUT /admin/projects/shortcode/{projectShortcode}/legal-info/resource",
      projectAdminOutcomes,
      prepare = freshName,
      send = (name, user) =>
        put(
          legalInfo.addPath("resource"),
          ResourceSideLegalInfo(Some(LicenseIri.CC_BY_4_0), Some(CopyrightHolder.unsafeFrom(name)), List.empty),
        )(user),
      readBack = Some(_ => anythingState),
    ),
    anythingRead("GET /admin/permissions/{projectIri}", permissionsOf()),
    anythingRead("GET /admin/permissions/ap/{projectIri}", permissionsOf("ap")),
    anythingRead(
      "GET /admin/permissions/ap/{projectIri}/{groupIri}",
      permissionsOf("ap").addPath("http://www.knora.org/ontology/knora-admin#ProjectMember"),
    ),
    anythingRead("GET /admin/permissions/doap/{projectIri}", permissionsOf("doap")),
    Row[GroupIri](
      "POST /admin/permissions/ap",
      projectAdminOutcomes,
      prepare = freshGroup,
      send = (group, user) =>
        post(
          anythingPermissions.addPath("ap"),
          CreateAdministrativePermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = group,
            hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
          ),
        )(user),
      readBack = Some(group => permissionsWith("forGroup", group.value)),
    ),
    Row[GroupIri](
      "POST /admin/permissions/doap",
      projectAdminOutcomes,
      prepare = freshGroup,
      send = (group, user) =>
        post(
          anythingPermissions.addPath("doap"),
          CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = anythingProjectIri,
            forGroup = Some(group),
            hasPermissions = memberPermissions,
          ),
        )(user),
      readBack = Some(group => permissionsWith("forGroup", group.value)),
    ),
    permissionRow("DELETE /admin/permissions/{permissionIri}", freshAp)(fixture =>
      delete(anythingPermissions.addPath(fixture.iri.value)),
    ),
    permissionRow("PUT /admin/permissions/doap/{permissionIri}", freshDoap)(fixture =>
      put(
        anythingPermissions.addPath("doap", fixture.iri.value),
        ChangeDoapRequest(hasPermissions =
          Some(
            Set(PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value)),
          ),
        ),
      ),
    ),
    permissionRow("PUT /admin/permissions/{permissionIri}/group", freshDoap)(fixture =>
      put(
        anythingPermissions.addPath(fixture.iri.value, "group"),
        ChangePermissionGroupApiRequestADM(fixture.other),
      ),
    ),
    permissionRow("PUT /admin/permissions/{permissionIri}/hasPermissions", freshAp)(fixture =>
      put(
        anythingPermissions.addPath(fixture.iri.value, "hasPermissions"),
        ChangePermissionHasPermissionsApiRequestADM(Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll))),
      ),
    ),
    permissionRow("PUT /admin/permissions/{permissionIri}/property", freshDoap)(fixture =>
      put(
        anythingPermissions.addPath(fixture.iri.value, "property"),
        ChangePermissionPropertyApiRequestADM(ANYTHING_HasDate_PROPERTY_LocalHost),
      ),
    ),
    permissionRow("PUT /admin/permissions/{permissionIri}/resourceClass", freshDoap)(fixture =>
      put(
        anythingPermissions.addPath(fixture.iri.value, "resourceClass"),
        ChangePermissionResourceClassApiRequestADM(ANYTHING_THING_RESOURCE_CLASS_LocalHost),
      ),
    ),
    Row[String](
      "PUT /admin/projects/shortcode/{projectShortcode}/legal-info/copyright-holders",
      systemAdminOutcomes,
      prepare = freshName.tap(name =>
        post(legalInfo.addPath("copyright-holders"), CopyrightHolderAddRequest(Set(CopyrightHolder.unsafeFrom(name))))(
          Some(rootUser),
        ).filterOrFail(_ == StatusCode.Ok)(Exception("could not add the matrix copyright holder")),
      ),
      send = (name, user) =>
        put(
          legalInfo.addPath("copyright-holders"),
          CopyrightHolderReplaceRequest(CopyrightHolder.unsafeFrom(name), CopyrightHolder.unsafeFrom(name.reverse)),
        )(user),
      readBack = Some(copyrightHolderPresent),
    ),
    Row[String](
      "POST /admin/groups",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = Random.nextUUID.map(uuid => s"matrix-${uuid.toString.take(8)}"),
      send = (name, user) => post(uri"/admin/groups", groupRequest(name))(user),
      readBack = Some(groupExists),
    ),
    Row[String](
      "POST /v2/ontologies",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = freshOntologyName,
      send = (name, user) => postOntology(name)(user).map(_.code),
      readBack = Some(ontologyExists),
    ),
    Row[OntologyFixture](
      "DELETE /v2/ontologies/{ontologyIri}",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = freshOntology,
      send =
        (onto, user) => delete(uri"/v2/ontologies/${onto.iri}?lastModificationDate=${onto.lastModificationDate}")(user),
      readBack = Some(onto => ontologyExists(onto.name)),
    ),
    ontologyRow("PUT /v2/ontologies/metadata", freshOntology)(onto =>
      putJsonLd(
        uri"/v2/ontologies/metadata",
        ontologyBody(onto, topLevel = """"rdfs:label": "relabelled","""),
      ),
    ),
    ontologyRow("DELETE /v2/ontologies/comment/{ontologyIri}", ontologyWithComment)(onto =>
      delete(uri"/v2/ontologies/comment/${onto.iri}?lastModificationDate=${onto.lastModificationDate}"),
    ),
    ontologyRow("POST /v2/ontologies/classes", freshOntology)(onto =>
      postJsonLd(uri"/v2/ontologies/classes", ontologyBody(onto, graph = Seq(classEntry(onto)))),
    ),
    ontologyRow("PUT /v2/ontologies/classes", ontologyWithClass)(onto =>
      putJsonLd(
        uri"/v2/ontologies/classes",
        ontologyBody(
          onto,
          graph = Seq(s"""{ "@id": "${onto.name}:MatrixClass", "@type": "owl:Class", "rdfs:label": ${langString(
              "relabelled",
            )} }"""),
        ),
      ),
    ),
    ontologyRow("DELETE /v2/ontologies/classes/{resourceClassIri}", ontologyWithClass)(onto =>
      delete(uri"/v2/ontologies/classes/${classOf(onto)}?lastModificationDate=${onto.lastModificationDate}"),
    ),
    ontologyRow("DELETE /v2/ontologies/classes/comment/{resourceClassIri}", ontologyWithClass)(onto =>
      delete(uri"/v2/ontologies/classes/comment/${classOf(onto)}?lastModificationDate=${onto.lastModificationDate}"),
    ),
    ontologyRow("POST /v2/ontologies/properties", freshOntology)(onto =>
      postJsonLd(
        uri"/v2/ontologies/properties",
        ontologyBody(onto, graph = Seq(propertyEntry(onto, withSubject = false))),
      ),
    ),
    ontologyRow("PUT /v2/ontologies/properties", ontologyWithProperty)(onto =>
      putJsonLd(
        uri"/v2/ontologies/properties",
        ontologyBody(
          onto,
          graph = Seq(
            s"""{ "@id": "${onto.name}:hasMatrix", "@type": "owl:ObjectProperty", "rdfs:label": ${langString(
                "relabelled",
              )} }""",
          ),
        ),
      ),
    ),
    ontologyRow("PUT /v2/ontologies/properties/guielement", ontologyWithProperty)(onto =>
      putJsonLd(
        uri"/v2/ontologies/properties/guielement",
        ontologyBody(
          onto,
          graph = Seq(s"""{
                         |  "@id": "${onto.name}:hasMatrix",
                         |  "@type": "owl:ObjectProperty",
                         |  "salsah-gui:guiElement": { "@id": "salsah-gui:Textarea" },
                         |  "salsah-gui:guiAttribute": [ "cols=80", "rows=24" ]
                         |}""".stripMargin),
        ),
      ),
    ),
    ontologyRow("DELETE /v2/ontologies/properties/{propertyIri}", ontologyWithProperty)(onto =>
      delete(uri"/v2/ontologies/properties/${propertyOf(onto)}?lastModificationDate=${onto.lastModificationDate}"),
    ),
    ontologyRow("DELETE /v2/ontologies/properties/comment/{propertyIri}", ontologyWithProperty)(onto =>
      delete(
        uri"/v2/ontologies/properties/comment/${propertyOf(onto)}?lastModificationDate=${onto.lastModificationDate}",
      ),
    ),
    ontologyRow("PUT /v2/ontologies/guiorder", ontologyWithCardinality)(onto =>
      putJsonLd(
        uri"/v2/ontologies/guiorder",
        ontologyBody(
          onto,
          graph = Seq(cardinality(onto, """"owl:maxCardinality": 1, "salsah-gui:guiOrder": 2,""")),
        ),
      ),
    ),
    ontologyRow("POST /v2/ontologies/cardinalities", ontologyWithProperty)(onto =>
      postJsonLd(
        uri"/v2/ontologies/cardinalities",
        ontologyBody(onto, graph = Seq(cardinality(onto, """"owl:maxCardinality": 1,"""))),
      ),
    ),
    ontologyRow("PUT /v2/ontologies/cardinalities", ontologyWithCardinality)(onto =>
      putJsonLd(
        uri"/v2/ontologies/cardinalities",
        ontologyBody(onto, graph = Seq(cardinality(onto, """"owl:minCardinality": 0,"""))),
      ),
    ),
    ontologyRow("PATCH /v2/ontologies/cardinalities", ontologyWithCardinality)(onto =>
      patchJsonLd(
        uri"/v2/ontologies/cardinalities",
        ontologyBody(onto, graph = Seq(cardinality(onto, """"owl:maxCardinality": 1,"""))),
      ),
    ),
    Row[String](
      "POST /v2/standoff/canonicalize",
      outcomes(unauthorized, allowed, allowed, allowed, allowed, allowed),
      prepare = ZIO.attempt(FileUtil.readTextFile(Paths.get("test_data/test_route/texts/StandardHTML.xml"))),
      send = (xml, user) => {
        val uri       = uri"/v2/standoff/canonicalize"
        val mediaType = MediaType("application", "xml")
        user
          .fold(TestApiClient.postString(uri, xml, mediaType))(u => TestApiClient.postString(uri, xml, mediaType, u))
          .map(_.code)
      },
      readBack = None,
    ),
    mappingRow(
      "PUT /v3/ontologies/{ontologyIri}/classes/{classIri}/mapping",
      classDefinitionUri,
      removeMapping(classMappingUri),
    )(
      put(classMappingUri, AddClassMappingsRequest(List(externalIri))),
    ),
    mappingRow(
      "DELETE /v3/ontologies/{ontologyIri}/classes/{classIri}/mapping",
      classDefinitionUri,
      addMapping(classMappingUri, AddClassMappingsRequest(List(externalIri))),
    )(delete(mappingParam(classMappingUri))),
    mappingRow(
      "PUT /v3/ontologies/{ontologyIri}/properties/{propertyIri}/mapping",
      propertyDefinitionUri,
      removeMapping(propertyMappingUri),
    )(put(propertyMappingUri, AddPropertyMappingsRequest(List(externalIri)))),
    mappingRow(
      "DELETE /v3/ontologies/{ontologyIri}/properties/{propertyIri}/mapping",
      propertyDefinitionUri,
      addMapping(propertyMappingUri, AddPropertyMappingsRequest(List(externalIri))),
    )(delete(mappingParam(propertyMappingUri))),
    Row[Unit](
      "POST /admin/sparql/query",
      outcomes(unauthorized, forbidden, forbidden, forbidden, forbidden, allowed),
      prepare = ZIO.unit,
      send = (_, user) => {
        val query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1"
        user
          .fold(TestApiClient.postSparql(uri"/admin/sparql/query", query))(u =>
            TestApiClient.postSparql(uri"/admin/sparql/query", query, u),
          )
          .map(_.code)
      },
      readBack = None,
    ),
  )

  private def cells[T](row: Row[T]): List[Spec[env, Throwable]] =
    Principal.values.toList.flatMap(principal => row.expected.get(principal).map(principal -> _)).map {
      case (principal, expected) =>
        val writes = row.readBack.nonEmpty
        test(s"${row.key} as $principal: $expected") {
          for {
            target <- row.prepare
            caller  = if principal == Principal.Self then row.selfOf.map(_(target)) else principal.user
            before <- ZIO.foreach(row.readBack)(_(target))
            status <- row.send(target, caller)
            after  <- ZIO.foreach(row.readBack)(_(target))
          } yield expected match {
            // A changed read-back proves the read-back would see a write that a denied request made.
            case Allowed          => assertTrue(status == StatusCode.Ok, !writes || before != after)
            case Denied(response) => assertTrue(status == response, before == after)
          }
        }
    }

  override val e2eSpec = suite("Authorization matrix")(
    test("the table allows exactly the principals each declared policy allows, for every role-based endpoint") {
      for {
        endpoints  <- ZIO.serviceWith[Endpoints](_.serverEndpoints.map(_.endpoint))
        allDeclared = endpoints.flatMap(ep => ep.attribute(Policy.key).flatMap(allowedBy).map(key(ep) -> _)).toMap
        declared    = allDeclared -- exempt.keySet
        staleExempt = (exempt.keySet -- allDeclared.keySet).toList.sorted
        rowedExempt = (exempt.keySet & rows.map(_.key).toSet).toList.sorted
        table       = rows.map(row => row.key -> row.expected.collect { case (p, Allowed) => p }.toSet).toMap
        duplicates  = rows.groupBy(_.key).collect { case (k, rs) if rs.size > 1 => k }.toList.sorted
        missing     = (declared.keySet -- table.keySet).toList.sorted
        stale       = table.keySet -- declared.keySet
        unlabelled  = stale.intersect(endpoints.map(key).toSet).toList.sorted
        gone        = (stale -- unlabelled).toList.sorted
        disagreeing =
          (table.keySet & declared.keySet).toList.sorted.filter(k => table(k) != declared(k)).map { k =>
            s"$k: the row allows ${table(k).toList.sortBy(_.ordinal).mkString(", ")}, " +
              s"the policy allows ${declared(k).toList.sortBy(_.ordinal).mkString(", ")}"
          }
        missingRows = s"add a row to `rows` for each of these endpoints, writing every outcome from what the " +
                        s"endpoint must allow, never from allowedBy: ${missing.mkString(", ")}"
        wrongPolicy = "the row states the requirement: change the endpoint's policy, and change the row only when " +
                        s"the requirement itself changed:\n${disagreeing.mkString("\n")}"
        exemptGone =
          s"remove these exemptions, whose endpoints no longer exist or lost their role-based policy: ${staleExempt.mkString(", ")}"
        exemptRowed   = s"remove these exemptions, which now have a row: ${rowedExempt.mkString(", ")}"
        duplicateRows = s"merge or delete the duplicate rows of: ${duplicates.mkString(", ")}"
        goneRows      = s"remove the rows of these endpoints, which no longer exist: ${gone.mkString(", ")}"
        lostPolicy    =
          s"these endpoints lost their role-based policy; restore it and keep the row: ${unlabelled.mkString(", ")}"
      } yield (assertTrue(duplicates.isEmpty) ?? duplicateRows) &&
        (assertTrue(staleExempt.isEmpty) ?? exemptGone) &&
        (assertTrue(rowedExempt.isEmpty) ?? exemptRowed) &&
        (assertTrue(missing.isEmpty) ?? missingRows) &&
        (assertTrue(gone.isEmpty) ?? goneRows) &&
        (assertTrue(unlabelled.isEmpty) ?? lostPolicy) &&
        (assertTrue(disagreeing.isEmpty) ?? wrongPolicy)
    },
    suite("each principal gets the outcome the table expects")(rows.flatMap(cells(_))*),
  )
}

object AuthorizationMatrixE2ESpec {

  /**
   * Role-based endpoints without a matrix row (no repeatable, side-effect-free valid request), each mapped to the
   * spec that proves its 401 and 403.
   */
  val exempt: Map[String, String] = Map(
    "POST /start-compaction"                                               -> "ManagementEndpointsE2ESpec",
    "POST /v3/projects/{projectIri}/exports"                               -> "V3SystemAdminRoutesE2ESpec",
    "GET /v3/projects/{projectIri}/exports/{exportId}"                     -> "V3SystemAdminRoutesE2ESpec",
    "DELETE /v3/projects/{projectIri}/exports/{exportId}"                  -> "V3SystemAdminRoutesE2ESpec",
    "GET /v3/projects/{projectIri}/exports/{exportId}/download"            -> "V3SystemAdminRoutesE2ESpec",
    "POST /v3/projects/{projectIri}/imports"                               -> "V3SystemAdminRoutesE2ESpec",
    "GET /v3/projects/{projectIri}/imports/{importId}"                     -> "V3SystemAdminRoutesE2ESpec",
    "DELETE /v3/projects/{projectIri}/imports/{importId}"                  -> "V3SystemAdminRoutesE2ESpec",
    "POST /v3/projects/{projectIri}/data-imports"                          -> "V3SystemAdminRoutesE2ESpec",
    "GET /v3/projects/{projectIri}/data-imports/{importId}"                -> "V3SystemAdminRoutesE2ESpec",
    "DELETE /v3/projects/{projectIri}/data-imports/{importId}"             -> "V3SystemAdminRoutesE2ESpec",
    "POST /v3/export/resources/oai"                                        -> "V3SystemAdminRoutesE2ESpec",
    "POST /admin/maintenance/{action-name}"                                -> "MaintenanceBackfillValueHasXmlE2ESpec",
    "POST /admin/maintenance/users/replace-iri"                            -> "MaintenanceReplaceUserIriE2ESpec",
    "POST /admin/maintenance/projects/{projectShortcode}/replace-user-iri" -> "MaintenanceReplaceUserIriInProjectE2ESpec",
  )

  enum Principal(val user: Option[User]) {
    case Anonymous       extends Principal(None)
    case NormalUser      extends Principal(Some(normalUser))
    case AnythingMember  extends Principal(Some(anythingUser1))
    case IncunabulaAdmin extends Principal(Some(incunabulaProjectAdminUser))
    case AnythingAdmin   extends Principal(Some(anythingAdminUser))
    case Root            extends Principal(Some(rootUser))

    /** The user a self-or-admin row targets; which user that is depends on the row's fresh target. */
    case Self extends Principal(None)
  }

  enum Expected {
    case Allowed
    case Denied(status: StatusCode)
  }

  val allowed: Expected      = Expected.Allowed
  val unauthorized: Expected = Expected.Denied(StatusCode.Unauthorized)
  val forbidden: Expected    = Expected.Denied(StatusCode.Forbidden)

  /**
   * The principals a policy allows on a target in the anything project; `None` for the policies whose outcome does
   * not depend on the caller's role, which the matrix leaves out.
   */
  def allowedBy(policy: Policy): Option[Set[Principal]] = {
    import Principal.*
    policy match {
      case Policy.ProjectAdmin(_)                       => Some(Set(AnythingAdmin, Root))
      case Policy.ProjectMember(_)                      => Some(Set(AnythingMember, AnythingAdmin, Root))
      case Policy.Authenticated(_)                      => Some(Principal.values.toSet - Anonymous - Self)
      case Policy.SystemAdmin                           => Some(Set(Root))
      case Policy.SelfOrSystemAdmin(_)                  => Some(Set(Self, Root))
      case Policy.ProjectAdminOfAnyProject              => Some(Set(IncunabulaAdmin, AnythingAdmin, Root))
      case Policy.Public(_) | Policy.ObjectAccess(_, _) => None
    }
  }
}
