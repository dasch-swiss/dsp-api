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
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.EndpointPolicyE2ESpec.key
import org.knora.webapi.slice.api.admin.Requests.*
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
        declared    = endpoints.flatMap(ep => ep.attribute(Policy.key).flatMap(allowedBy).map(key(ep) -> _)).toMap
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
        duplicateRows = s"merge or delete the duplicate rows of: ${duplicates.mkString(", ")}"
        goneRows      = s"remove the rows of these endpoints, which no longer exist: ${gone.mkString(", ")}"
        lostPolicy    =
          s"these endpoints lost their role-based policy; restore it and keep the row: ${unlabelled.mkString(", ")}"
      } yield (assertTrue(duplicates.isEmpty) ?? duplicateRows) &&
        (assertTrue(missing.isEmpty) ?? missingRows) &&
        (assertTrue(gone.isEmpty) ?? goneRows) &&
        (assertTrue(unlabelled.isEmpty) ?? lostPolicy) &&
        (assertTrue(disagreeing.isEmpty) ?? wrongPolicy)
    },
    suite("each principal gets the outcome the table expects")(rows.flatMap(cells(_))*),
  )
}

object AuthorizationMatrixE2ESpec {

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
      case Policy.ProjectAdmin(_)                    => Some(Set(AnythingAdmin, Root))
      case Policy.ProjectMember(_)                   => Some(Set(AnythingMember, AnythingAdmin, Root))
      case Policy.Authenticated(_)                   => Some(Principal.values.toSet - Anonymous)
      case Policy.SystemAdmin                        => Some(Set(Root))
      case Policy.Public(_) | Policy.ObjectAccess(_) => None
    }
  }
}
