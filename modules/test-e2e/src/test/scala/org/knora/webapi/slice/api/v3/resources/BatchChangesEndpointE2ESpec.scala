/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import org.junit.runner.RunWith
import sttp.client4.*
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.resources.BatchChangeStatus.*
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class BatchChangesEndpointE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = anythingRdfOntologyAndData

  private val onto         = "http://0.0.0.0:3333/ontology/0001/anything/v2"
  private val hasText      = s"$onto#hasText"
  private val hasListItem  = s"$onto#hasListItem"
  private val resource     = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw"
  private val textValue    = s"$resource/values/SZyeLLmOTcCCuS3B0VksHQ"
  private val listValue    = s"$resource/values/XAhEeE3kSVqM4JPGdLt4Ew"
  private val treeList01   = "http://rdfh.ch/lists/0001/treeList01"
  private val treeList02   = "http://rdfh.ch/lists/0001/treeList02"
  private val batchUri     = uri"/v3/resources/values/batch"
  private val otherThing   = "http://rdfh.ch/0001/a-thing"
  private val unknownThing = "http://rdfh.ch/0001/does-not-exist"
  private val hasRichtext  = s"$onto#hasRichtext"
  private val richValue    = s"$resource/values/rvB4eQ5MTF-Qxq0YgkwaDg"
  private val frenchThing  = "http://rdfh.ch/0001/a-thing-with-text-valuesLanguage"
  private val frenchValue  = "http://rdfh.ch/0001/a-thing-with-text-valueslanguage/values/1"

  private def send(user: User, changes: BatchChange*) =
    TestApiClient
      .postJson[BatchChangesResponse, BatchChangesRequest](batchUri, BatchChangesRequest(changes.toList), user)
      .flatMap(_.assert200)
      .map(_.results)

  private def updateText(value: String, expected: String, newText: String) =
    BatchChange(resource, Some(hasText), Some(value), Some(expected), Some(newText))

  override val e2eSpec: Spec[env, Any] = suite("POST /v3/resources/values/batch")(
    test("updates a text value when the stored text is the expected one") {
      for {
        first <- send(anythingUser1, updateText(textValue, "test", "test, edited"))
        newIri = first.head.valueIri.getOrElse("")
        // The new version holds the new text: a change expecting it is applied.
        second <- send(anythingUser1, updateText(newIri, "test, edited", "test"))
      } yield assertTrue(
        first.map(_.status) == List(ok),
        newIri.nonEmpty,
        newIri != textValue,
        second.map(_.status) == List(ok),
      )
    },
    test("rejects a change as a conflict when the stored text differs from the expected one") {
      for {
        current <- send(anythingUser1, BatchChange(resource, Some(hasText), newValue = Some("probe")))
        probe    = current.head.valueIri.getOrElse("")
        results <- send(anythingUser1, updateText(probe, "not what is stored", "overwritten"))
        // The value was not overwritten: it still holds "probe".
        check <- send(anythingUser1, updateText(probe, "probe", "probe"))
      } yield assertTrue(
        results.map(_.status) == List(conflict),
        check.map(_.status) == List(ok),
      )
    },
    test("rejects an update of a value version that is no longer current as a conflict") {
      for {
        created <- send(anythingUser1, BatchChange(resource, Some(hasText), newValue = Some("v1")))
        v1       = created.head.valueIri.getOrElse("")
        results <- send(anythingUser1, updateText(v1, "v1", "v2"), updateText(v1, "v1", "v3"))
      } yield assertTrue(results.map(_.status) == List(ok, conflict))
    },
    test("creates a text value and updates a list value and the label in one request") {
      for {
        results <- send(
                     anythingUser1,
                     BatchChange(otherThing, Some(hasText), newValue = Some("created by batch")),
                     BatchChange(resource, Some(hasListItem), Some(listValue), Some(treeList01), Some(treeList02)),
                     BatchChange(resource, label = Some(BatchLabelChange(Some("testding"), "testding, edited"))),
                   )
      } yield assertTrue(
        results.map(_.status) == List(ok, ok, ok),
        results.map(_.index) == List(0, 1, 2),
        results.head.valueIri.nonEmpty,
      )
    },
    test("reports each failure at its index without cancelling the other changes") {
      for {
        results <- send(
                     anythingUser1,
                     BatchChange(resource, label = Some(BatchLabelChange(Some("not the label"), "x"))),
                     BatchChange(resource, newValue = Some("no property")),
                     BatchChange(unknownThing, Some(hasText), newValue = Some("x")),
                     BatchChange(otherThing, Some(hasText), newValue = Some("still written")),
                   )
      } yield assertTrue(results.map(_.status) == List(conflict, invalid, not_found, ok))
    },
    test("reports a change the user may not make as forbidden") {
      for {
        results <- send(incunabulaMemberUser, BatchChange(resource, Some(hasText), newValue = Some("x")))
      } yield assertTrue(results.map(_.status) == List(forbidden))
    },
    test("refuses to set a formatted text value, which would lose its markup") {
      for {
        results <- send(anythingUser1, BatchChange(resource, Some(hasRichtext), Some(richValue), newValue = Some("x")))
      } yield assertTrue(results.map(_.status) == List(invalid))
    },
    test("keeps the language of a text value it updates") {
      for {
        results <- send(
                     anythingUser1,
                     BatchChange(frenchThing, Some(hasText), Some(frenchValue), Some("Bonjour"), Some("Salut")),
                   )
        read <- TestApiClient.getJsonLd(uri"/v2/resources/$frenchThing", anythingUser1, identity)
        body  = read.body.getOrElse("")
      } yield assertTrue(
        results.map(_.status) == List(ok),
        body.contains("Salut"),
        body.contains("textValueHasLanguage"),
        body.contains("\"fr\""),
      )
    },
    test("creates several values in an empty property expected to be empty, and conflicts once it is filled") {
      val setList =
        (node: String) => BatchChange(otherThing, Some(hasListItem), newValue = Some(node), expectEmpty = Some(true))
      for {
        first  <- send(anythingUser1, setList(treeList01), setList(treeList02))
        second <- send(anythingUser1, setList(treeList01))
      } yield assertTrue(
        first.map(_.status) == List(ok, ok),
        second.map(_.status) == List(conflict),
      )
    },
    test("refuses `expectEmpty` on an update") {
      for {
        results <-
          send(
            anythingUser1,
            BatchChange(resource, Some(hasText), Some(textValue), newValue = Some("x"), expectEmpty = Some(true)),
          )
      } yield assertTrue(results.map(_.status) == List(invalid))
    },
    test("refuses more than the maximum number of changes") {
      val tooMany =
        List.fill(BatchChangesRequest.MaxChanges + 1)(BatchChange(otherThing, Some(hasText), newValue = Some("x")))
      for {
        response <- TestApiClient.postJson[BatchChangesResponse, BatchChangesRequest](
                      batchUri,
                      BatchChangesRequest(tooMany),
                      anythingUser1,
                    )
        _ <- response.assert400
      } yield assertCompletes
    },
  )
}
