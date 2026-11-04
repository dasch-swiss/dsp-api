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
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.IriService
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.api.EndpointPolicyE2ESpec.key
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.api.admin.Requests.*
import org.knora.webapi.slice.api.admin.UsersEndpoints.Requests.*
import org.knora.webapi.slice.api.admin.model.*
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.*
import org.knora.webapi.slice.api.admin.service.UserRestService.UserResponse
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
  )

  /**
   * One endpoint of the matrix. `prepare` creates a fresh target per cell through the API, so cells stay independent
   * and no shared dataset gains records. `readBack` renders what the request may change; it is `None` for an
   * endpoint that writes nothing.
   */
  private final case class Row[T](
    key: String,
    expected: Map[Principal, Expected],
    prepare: RIO[env, T],
    send: (T, Option[User]) => RIO[env, StatusCode],
    readBack: Option[T => RIO[env, String]],
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

  private val freshUser: RIO[env, UserIri] =
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
    } yield UserIri.unsafeFrom(user.user.id)

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

  private val restrictedViewUri = anythingByIri.addPath("RestrictedViewSettings")
  private val restrictedViewSet = SetRestrictedViewRequest(Some(RestrictedView.Size.unsafeFrom("!512,512")), None)

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
    anythingRead("GET /admin/permissions/{projectIri}", permissionsOf()),
    anythingRead("GET /admin/permissions/ap/{projectIri}", permissionsOf("ap")),
    anythingRead(
      "GET /admin/permissions/ap/{projectIri}/{groupIri}",
      permissionsOf("ap").addPath("http://www.knora.org/ontology/knora-admin#ProjectMember"),
    ),
    anythingRead("GET /admin/permissions/doap/{projectIri}", permissionsOf("doap")),
    Row[String](
      "POST /admin/groups",
      outcomes(unauthorized, forbidden, forbidden, forbidden, allowed, allowed),
      prepare = Random.nextUUID.map(uuid => s"matrix-${uuid.toString.take(8)}"),
      send = (name, user) => post(uri"/admin/groups", groupRequest(name))(user),
      readBack = Some(groupExists),
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
  )

  private def cells[T](row: Row[T]): List[Spec[env, Throwable]] =
    Principal.values.toList.map { principal =>
      val expected = row.expected(principal)
      val writes   = row.readBack.nonEmpty
      test(s"${row.key} as $principal: $expected") {
        for {
          target <- row.prepare
          before <- ZIO.foreach(row.readBack)(_(target))
          status <- row.send(target, principal.user)
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
      case Policy.Authenticated(_)                      => Some(Principal.values.toSet - Anonymous)
      case Policy.SystemAdmin                           => Some(Set(Root))
      case Policy.Public(_) | Policy.ObjectAccess(_, _) => None
    }
  }
}
