/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.json.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.domain.ServiceName

@RunWith(classOf[DspZTestJUnitRunner])
class TaskCreatorSpec extends ZIOSpecDefault {

  private val userIri = UserIri.unsafeFrom("http://rdfh.ch/users/root")
  private val service = ServiceName.unsafeFrom("dsp-repository")

  private val byUser: TaskCreator    = TaskCreator.ByUser(userIri)
  private val byService: TaskCreator = TaskCreator.ByService(service)

  def spec: Spec[Any, Any] = suite("TaskCreator")(
    test("a user encodes as its bare IRI string") {
      assertTrue(byUser.toJson == "\"http://rdfh.ch/users/root\"")
    },
    test("a service encodes as a urn:dsp:service: string") {
      assertTrue(byService.toJson == "\"urn:dsp:service:dsp-repository\"")
    },
    test("a bare user IRI decodes as a user") {
      assertTrue("\"http://rdfh.ch/users/root\"".fromJson[TaskCreator] == Right(byUser))
    },
    test("a service string decodes as a service") {
      assertTrue(
        "\"urn:dsp:service:dsp-repository\"".fromJson[TaskCreator] == Right(byService),
      )
    },
    test("a service prefix with an invalid name fails to decode") {
      assertTrue(
        "\"urn:dsp:service:Not_Valid\"".fromJson[TaskCreator].isLeft,
        "\"urn:dsp:service:\"".fromJson[TaskCreator].isLeft,
      )
    },
    test("a string that is neither a user IRI nor a service fails to decode") {
      assertTrue(
        "\"dsp-repository\"".fromJson[TaskCreator].isLeft,
        "\"urn:dsp:user:dsp-repository\"".fromJson[TaskCreator].isLeft,
      )
    },
    test("a JSON object fails to decode") {
      assertTrue("""{"ByUser":{"iri":"http://rdfh.ch/users/root"}}""".fromJson[TaskCreator].isLeft)
    },
  )
}
