/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import spray.json.JsonWriter
import zio.Scope
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.JsonEncoder
import zio.json.ast.Json
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

object ListMessagesADMZioJsonSpec extends ZIOSpecDefault with IntegrationTestListADMJsonProtocol {
  object TestData {
    val listRootNodeInfoADM: ListRootNodeInfoADM = ListRootNodeInfoADM(
      id = "http://rdfh.ch/lists/73d0ec0302",
      projectIri = "http://rdfh.ch/projects/00FF",
      labels = StringLiteralSequenceV2(
        Vector(
          StringLiteralV2.from("Title", Some("en")),
          StringLiteralV2.from("Titel", Some("de")),
          StringLiteralV2.from("Titre", Some("fr")),
        ),
      ),
      comments = StringLiteralSequenceV2(
        Vector(StringLiteralV2.from("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de"))),
      ),
    )

    val listChildNodeInfoADM: ListChildNodeInfoADM = ListChildNodeInfoADM(
      id = "http://rdfh.ch/lists/00FF/526f26ed04",
      name = Some("sommer"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Sommer", None))),
      comments = StringLiteralSequenceV2.empty,
      position = 0,
      hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab",
    )

    val listChildNodeADM: ListChildNodeADM = ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList01",
      name = Some("Tree list node 01"),
      labels = StringLiteralSequenceV2(
        Vector(StringLiteralV2.from(value = "Tree list node 01", language = Some("en"))),
      ),
      comments = StringLiteralSequenceV2.empty,
      children = Seq.empty[ListChildNodeADM],
      position = 0,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList",
    )

    val listChildNodeADMs: Seq[ListChildNodeADM] = Seq(
      listChildNodeADM,
      ListChildNodeADM(
        id = "http://rdfh.ch/lists/0001/treeList02",
        name = Some("Tree list node 02"),
        labels = StringLiteralSequenceV2(
          Vector(
            StringLiteralV2.from(value = "Baumlistenknoten 02", language = Some("de")),
            StringLiteralV2.from(value = "Tree list node 02", language = Some("en")),
          ),
        ),
        comments = StringLiteralSequenceV2.empty,
        children = Seq.empty[ListChildNodeADM],
        position = 1,
        hasRootNode = "http://rdfh.ch/lists/0001/treeList",
      ),
      ListChildNodeADM(
        id = "http://rdfh.ch/lists/0001/treeList03",
        name = Some("Tree list node 03"),
        labels = StringLiteralSequenceV2(
          Vector(StringLiteralV2.from(value = "Tree list node 03", language = Some("en"))),
        ),
        comments = StringLiteralSequenceV2.empty,
        children = Seq(
          ListChildNodeADM(
            id = "http://rdfh.ch/lists/0001/treeList10",
            name = Some("Tree list node 10"),
            labels = StringLiteralSequenceV2(
              Vector(StringLiteralV2.from(value = "Tree list node 10", language = Some("en"))),
            ),
            comments = StringLiteralSequenceV2.empty,
            children = Seq.empty[ListChildNodeADM],
            position = 0,
            hasRootNode = "http://rdfh.ch/lists/0001/treeList",
          ),
          ListChildNodeADM(
            id = "http://rdfh.ch/lists/0001/treeList11",
            name = Some("Tree list node 11"),
            labels = StringLiteralSequenceV2(
              Vector(StringLiteralV2.from(value = "Tree list node 11", language = Some("en"))),
            ),
            comments = StringLiteralSequenceV2.empty,
            children = Seq.empty[ListChildNodeADM],
            position = 1,
            hasRootNode = "http://rdfh.ch/lists/0001/treeList",
          ),
        ),
        position = 2,
        hasRootNode = "http://rdfh.ch/lists/0001/treeList",
      ),
    )

    val listRootNodeADM: ListRootNodeADM = ListRootNodeADM(
      id = "http://rdfh.ch/lists/73d0ec0302",
      projectIri = "http://rdfh.ch/projects/00FF",
      name = Some("name"),
      labels = StringLiteralSequenceV2(
        Vector(
          StringLiteralV2.from("Title", Some("en")),
          StringLiteralV2.from("Titel", Some("de")),
          StringLiteralV2.from("Titre", Some("fr")),
        ),
      ),
      comments = StringLiteralSequenceV2(
        Vector(StringLiteralV2.from("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de"))),
      ),
      children = Seq.empty[ListChildNodeADM],
    )
  }

  val spec: Spec[TestEnvironment with Scope, Any] = suite("List models")(
    suite("serialization")(
      suite("ListNodeInfoADM")(
        test("ListRootNodeInfoADM") {
          assertZioAndSprayJsonAreIdentical(TestData.listRootNodeInfoADM)
        },
        test("ListChildNodeInfoADM") {
          assertZioAndSprayJsonAreIdentical(TestData.listChildNodeInfoADM)
        },
      ),
      suite("ListItemADM")(
        test("ListADM") {
          assertZioAndSprayJsonAreIdentical(
            ListADM(TestData.listRootNodeInfoADM, TestData.listChildNodeADMs),
          )
        },
        test("NodeADM") {
          val nodeAdm = NodeADM(TestData.listChildNodeInfoADM, children = Seq.empty[ListChildNodeADM])
          assertZioAndSprayJsonAreIdentical(nodeAdm)
        },
      ),
      suite("NodeInfoGetResponseADM")(
        test("RootNodeInfoGetResponseADM") {
          assertZioAndSprayJsonAreIdentical(RootNodeInfoGetResponseADM(TestData.listRootNodeInfoADM))
        },
        test("ChildNodeInfoGetResponseADM") {
          assertZioAndSprayJsonAreIdentical(ChildNodeInfoGetResponseADM(TestData.listChildNodeInfoADM))
        },
      ),
      suite("ListItemGetResponseADM")(
        test("ListGetResponseADM") {
          assertZioAndSprayJsonAreIdentical(
            ListGetResponseADM(ListADM(TestData.listRootNodeInfoADM, Seq.empty)),
          )
        },
        test("ListNodeGetResponseADM") {
          assertZioAndSprayJsonAreIdentical(
            ListNodeGetResponseADM(NodeADM(TestData.listChildNodeInfoADM, TestData.listChildNodeADMs)),
          )
        },
      ),
    ),
    test("ListsGetResponseADM") {
      assertZioAndSprayJsonAreIdentical(ListsGetResponseADM(Seq(TestData.listRootNodeInfoADM)))
    },
    suite("ListItemGetResponseADM")(
      test("ListGetResponseADM") {
        assertZioAndSprayJsonAreIdentical(
          ListGetResponseADM(ListADM(TestData.listRootNodeInfoADM, Seq.empty)),
        )
      },
      test("ListNodeGetResponseADM") {
        assertZioAndSprayJsonAreIdentical(
          ListNodeGetResponseADM(NodeADM(TestData.listChildNodeInfoADM, TestData.listChildNodeADMs)),
        )
      },
    ),
    suite("ListNodeADM")(
      test("ListRootNodeADM")(
        assertZioAndSprayJsonAreIdentical(TestData.listRootNodeADM),
      ),
      test("ListChildNodeADM")(
        assertZioAndSprayJsonAreIdentical(TestData.listChildNodeADM),
      ),
    ),
    suite("ListItemDeleteResponseADM")(
      test("ListDeleteResponseADM") {
        assertZioAndSprayJsonAreIdentical(
          ListDeleteResponseADM(iri = "http://rdfh.ch/lists/73d0ec0302", deleted = true),
        )
      },
      test("ChildNodeDeleteResponseADM") {
        assertZioAndSprayJsonAreIdenticalIgnoreDiscriminator(
          ChildNodeDeleteResponseADM(TestData.listRootNodeADM),
        )
      },
    ),
    test("CanDeleteListResponseADM") {
      assertZioAndSprayJsonAreIdentical(
        CanDeleteListResponseADM("http://rdfh.ch/lists/73d0ec0302", canDeleteList = true),
      )
    },
    test("ListNodeCommentsDeleteResponseADM") {
      assertZioAndSprayJsonAreIdentical(
        ListNodeCommentsDeleteResponseADM("http://rdfh.ch/lists/73d0ec0302", commentsDeleted = true),
      )
    },
    test("NodePositionChangeResponseADM") {
      assertZioAndSprayJsonAreIdenticalIgnoreDiscriminator(
        NodePositionChangeResponseADM(TestData.listRootNodeADM),
      ) && assertZioAndSprayJsonAreIdenticalIgnoreDiscriminator(
        NodePositionChangeResponseADM(TestData.listChildNodeADM),
      )
    },
  )

  private def assertZioAndSprayJsonAreIdentical[A](obj: A)(implicit encoder: JsonEncoder[A], writer: JsonWriter[A]) =
    assertJsonStringIdentical(obj.toJsonPretty, writer.write(obj).prettyPrint)

  private def assertJsonStringIdentical[A](zioJson: String, sprayJson: String) = {
    println("\nZIO:\n" + zioJson)
    println("\nSpray:\n" + sprayJson)
    assertTrue(sprayJson.fromJson[Json] == zioJson.fromJson[Json])
  }

  private def assertZioAndSprayJsonAreIdenticalIgnoreDiscriminator[A](
    obj: A,
  )(implicit encoder: JsonEncoder[A], writer: JsonWriter[A]) = {

    val zioJson = obj.toJsonPretty
      // remove all lines starting with "type" used as @jsonDiscriminator for zio encoding of sealed traits
      .replaceAll("(?m)^[\\s]*\"type\".*$", "")

    assertJsonStringIdentical(zioJson, writer.write(obj).prettyPrint)
  }
}
