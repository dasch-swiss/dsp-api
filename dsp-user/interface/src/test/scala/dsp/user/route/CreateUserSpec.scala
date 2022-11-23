/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.user.route

import zhttp.http._
import zio._
import zio.test.Assertion._
import zio.test._

import dsp.config.AppConfig
import dsp.errors.ValidationException
import dsp.user.handler.UserHandler
import dsp.user.repo.impl.UserRepoMock
import dsp.util._

/**
 * This spec is used to test [[dsp.user.route.CreateUser]].
 */
object CreateUserSpec extends ZIOSpecDefault {

  def spec = (createUserTests)

  private val createUserTests = suite("createUser")(
    test("return an error when the payload is invalid (empty body)") {
      val request: Request = Request(
        version = Version.`HTTP/1.1`,
        method = Method.POST,
        url = URL(!! / "admin" / "users"),
        headers = Headers.empty,
        body = Body.empty
      )

      for {
        userHandler <- ZIO.service[UserHandler]
        error       <- CreateUser.route(request, userHandler).exit
      } yield assert(error)(
        fails(equalTo(ValidationException(s"Invalid payload: Unexpected end of input")))
      )
    },
    test("return an error when the payload is invalid (missing attribute)") {
      for {
        userHandler <- ZIO.service[UserHandler]
        request = Request(
                    version = Version.`HTTP/1.1`,
                    method = Method.POST,
                    url = URL(!! / "admin" / "users"),
                    headers = Headers.empty,
                    body = Body
                      .fromString("""{
                        "givenName": "Hans",
                        "username": "hansmuster",
                        "email": "hans.muster@example.org",
                        "password": "test",
                        "language": "en",
                        "status": true,
                        "systemAdmin": false
                      }""".stripMargin)
                  )
        error <- CreateUser.route(request, userHandler).map(_.body).exit
      } yield assert(error)(
        fails(equalTo(ValidationException(s"Invalid payload: .familyName(missing)")))
      )
    },
    test("return an error when the payload contains invalid data") {
      for {
        userHandler <- ZIO.service[UserHandler]
        request = Request(
                    version = Version.`HTTP/1.1`,
                    method = Method.POST,
                    url = URL(!! / "admin" / "users"),
                    headers = Headers.empty,
                    body = Body
                      .fromString("""{
                        "givenName": "Hans",
                        "familyName": "Muster",
                        "username": "hansmuster",
                        "email": "hans.musterexample.org",
                        "password": "test",
                        "language": "en",
                        "status": true,
                        "systemAdmin": false
                      }""".stripMargin)
                  )
        error <- CreateUser.route(request, userHandler).map(_.body).exit
        _      = println(error)
      } yield assert(error)(
        fails(equalTo(ValidationException(s"List(Email is invalid.)")))
      )
    },
    test("return all errors when the payload contains multiple invalid data") {
      for {
        userHandler <- ZIO.service[UserHandler]
        request = Request(
                    version = Version.`HTTP/1.1`,
                    method = Method.POST,
                    url = URL(!! / "admin" / "users"),
                    headers = Headers.empty,
                    body = Body
                      .fromString("""{
                        "givenName": "Hans",
                        "familyName": "Muster",
                        "username": "hansmuster",
                        "email": "hans.musterexample.org",
                        "password": "test",
                        "language": "ch",
                        "status": true,
                        "systemAdmin": false
                      }""".stripMargin)
                  )
        error <- CreateUser.route(request, userHandler).map(_.body).exit
        _      = println(error)
      } yield assert(error)(
        fails(equalTo(ValidationException(s"List(Email is invalid., LanguageCode 'ch' is invalid.)")))
      )
    },
    test("successfully create a user") {
      for {
        userHandler <- ZIO.service[UserHandler]
        request = Request(
                    version = Version.`HTTP/1.1`,
                    method = Method.POST,
                    url = URL(!! / "admin" / "users"),
                    headers = Headers.empty,
                    body = Body
                      .fromString("""{
                        "givenName": "Hans",
                        "familyName": "Muster",
                        "username": "hansmuster",
                        "email": "hans.muster@example.org",
                        "password": "test",
                        "language": "en",
                        "status": true,
                        "systemAdmin": false
                      }""".stripMargin)
                  )
        response    <- CreateUser.route(request, userHandler).map(_.body)
        responseStr <- response.asString
      } yield assertTrue(
        responseStr.startsWith(
          """{"id":"http://rdfh.ch/users/89ac5805-6c7f-4a95-aeb2-e85e74aa216d","givenName":"Hans","familyName":"Muster","username":"hansmuster","email":"hans.muster@example.org","password":"$2a"""
        ) &&
          responseStr.endsWith(
            """"language":"en","status":true}"""
          )
      )
    }
  ).provide(UuidGeneratorTest.layer, AppConfig.live, UserRepoMock.layer, UserHandler.layer)

}
