/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.api.admin.Requests.ListCreateChildNodeRequest
import org.knora.webapi.slice.api.admin.Requests.ListCreateRootNodeRequest
import org.knora.webapi.slice.common.domain.LanguageCode.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.MutableTestIri

object CreateAdminListsEndpointsE2ESpec extends E2EZSpec {

  private val customListIri      = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/qq54wdGKR0S5zsbR5-9wtg")
  private val customChildNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/vQgijJZKSqawFooJPyhYkw")

  private val newListIri     = new MutableTestIri
  private val firstChildIri  = new MutableTestIri
  private val secondChildIri = new MutableTestIri

  override val e2eSpec = suite("The admin lists route (/admin/lists)")(
    suite("creating list items with a custom Iri")(
      test("create a list with the provided custom Iri") {
        val createReq = ListCreateRootNodeRequest(
          Some(customListIri),
          Comments.unsafeFrom(Seq(StringLiteralV2.from("XXXXX", EN))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New list with a custom IRI", EN))),
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ListGetResponseADM, ListCreateRootNodeRequest](uri"/admin/lists", createReq, anythingAdminUser)
          .flatMap(_.assert200)
          .map(_.list.listinfo)
          .map(info =>
            assertTrue(
              info.id == customListIri.value,
              info.labels == StringLiteralSequenceV2(Vector(StringLiteralV2.from("New list with a custom IRI", EN))),
            ),
          )
      },
      test("return a DuplicateValueException during list creation when the supplied list IRI is not unique") {
        // duplicate list IRI
        val createReq = ListCreateRootNodeRequest(
          Some(customListIri),
          Comments.unsafeFrom(Seq(StringLiteralV2.from("XXXXX", EN))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New List", EN))),
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ListGetResponseADM, ListCreateRootNodeRequest](uri"/admin/lists", createReq, anythingAdminUser)
          .map(response =>
            assertTrue(
              response.code == StatusCode.BadRequest,
              response.body.left.exists(_.contains(s"IRI: '$customListIri' already exists, try another one.")),
            ),
          )
      },
      test("add a child with a custom IRI") {
        val createReq = ListCreateChildNodeRequest(
          Some(customChildNodeIri),
          Some(Comments.unsafeFrom(Seq(StringLiteralV2.from("XXXXX", EN)))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New List Node", EN))),
          Some(ListName.unsafeFrom("node with a custom IRI")),
          customListIri,
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ChildNodeInfoGetResponseADM, ListCreateChildNodeRequest](
            uri"/admin/lists/$customListIri",
            createReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .map(info => assertTrue(info.id == customChildNodeIri.value))
      },
    ),
    suite("used to create list items")(
      test("create a list") {
        val createReq = ListCreateRootNodeRequest(
          None,
          Comments.unsafeFrom(Seq(StringLiteralV2.from("XXXX", EN))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("Neue Liste", DE))),
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ListGetResponseADM, ListCreateRootNodeRequest](uri"/admin/lists", createReq, anythingAdminUser)
          .flatMap(_.assert200)
          .map(_.list)
          .tap(list => ZIO.succeed(newListIri.set(list.listinfo.id)))
          .map(list =>
            assertTrue(
              list.listinfo.projectIri == anythingProjectIri.value,
              list.listinfo.labels.stringLiterals == Vector(StringLiteralV2.from("Neue Liste", DE)),
              list.listinfo.comments.stringLiterals == Vector(StringLiteralV2.from("XXXX", EN)),
              list.children.isEmpty,
            ),
          )
      },
      test("return a ForbiddenException if the user creating the list is not project or system admin") {
        val createReq = ListCreateRootNodeRequest(
          None,
          Comments.unsafeFrom(Seq(StringLiteralV2.from("XXXX", EN))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("Neue Liste", DE))),
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ListGetResponseADM, ListCreateRootNodeRequest](uri"/admin/lists", createReq, anythingUser1)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("add child to list - to the root node") {
        val createReq = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(Seq(StringLiteralV2.from("New First Child List Node Comment", EN)))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New First Child List Node Value", EN))),
          Some(ListName.unsafeFrom("first")),
          newListIri.asListIri,
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ChildNodeInfoGetResponseADM, ListCreateChildNodeRequest](
            uri"/admin/lists/${newListIri.asListIri}",
            createReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .tap(info => ZIO.succeed(firstChildIri.set(info.id)))
          .map(info =>
            assertTrue(
              info.labels.stringLiterals == Seq(StringLiteralV2.from("New First Child List Node Value", EN)),
              info.comments.stringLiterals == Seq(StringLiteralV2.from("New First Child List Node Comment", EN)),
              info.position == 0,
              info.hasRootNode == newListIri.get,
            ),
          )
      },
      test("add second child to list - to the root node") {
        val addSecondChildToRoot = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(Seq(StringLiteralV2.from("New Second Child List Node Comment", EN)))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New Second Child List Node Value", EN))),
          Some(ListName.unsafeFrom("second")),
          newListIri.asListIri,
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ChildNodeInfoGetResponseADM, ListCreateChildNodeRequest](
            uri"/admin/lists/${newListIri.asListIri}",
            addSecondChildToRoot,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .tap(info => ZIO.succeed(secondChildIri.set(info.id)))
          .map(info =>
            assertTrue(
              info.labels.stringLiterals == Seq(StringLiteralV2.from("New Second Child List Node Value", EN)),
              info.comments.stringLiterals == Seq(StringLiteralV2.from("New Second Child List Node Comment", EN)),
              info.position == 1,
              info.hasRootNode == newListIri.get,
            ),
          )
      },
      test("insert new child in a specific position") {
        val insertChild = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(Seq(StringLiteralV2.from("Inserted List Node Comment", EN)))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("Inserted List Node Label", EN))),
          Some(ListName.unsafeFrom("child with position")),
          newListIri.asListIri,
          Some(Position.unsafeFrom(1)),
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ChildNodeInfoGetResponseADM, ListCreateChildNodeRequest](
            uri"/admin/lists/${newListIri.asListIri}",
            insertChild,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .tap(info => ZIO.succeed(secondChildIri.set(info.id)))
          .map(info =>
            assertTrue(
              info.labels.stringLiterals == Seq(StringLiteralV2.from("Inserted List Node Label", EN)),
              info.comments.stringLiterals == Seq(StringLiteralV2.from("Inserted List Node Comment", EN)),
              info.position == 1,
              info.hasRootNode == newListIri.get,
            ),
          )
      },
      test("add child to second child node") {
        val addChildToSecondChild = ListCreateChildNodeRequest(
          None,
          Some(Comments.unsafeFrom(Seq(StringLiteralV2.from("New Third Child List Node Comment", EN)))),
          Labels.unsafeFrom(Seq(StringLiteralV2.from("New Third Child List Node Value", EN))),
          Some(ListName.unsafeFrom("third")),
          secondChildIri.asListIri,
          None,
          anythingProjectIri,
        )
        TestApiClient
          .postJson[ChildNodeInfoGetResponseADM, ListCreateChildNodeRequest](
            uri"/admin/lists/${secondChildIri.asListIri}",
            addChildToSecondChild,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .map(info =>
            assertTrue(
              info.labels.stringLiterals == Seq(StringLiteralV2.from("New Third Child List Node Value", EN)),
              info.comments.stringLiterals == Seq(StringLiteralV2.from("New Third Child List Node Comment", EN)),
              info.position == 0,
              info.hasRootNode == newListIri.get,
            ),
          )
      },
    ),
  )
}
