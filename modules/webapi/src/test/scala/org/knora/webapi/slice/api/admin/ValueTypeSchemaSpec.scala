/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.tapir.AnyEndpoint
import sttp.tapir.Endpoint
import sttp.tapir.EndpointIO
import sttp.tapir.EndpointInput
import sttp.tapir.EndpointOutput
import sttp.tapir.Schema
import sttp.tapir.SchemaType
import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.internal.RichEndpointOutput
import sttp.tapir.ztapir.ZPartialServerEndpoint
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError

@RunWith(classOf[DspZTestJUnitRunner])
class ValueTypeSchemaSpec extends ZIOSpecDefault {

  // Contract tests over endpoint metadata: neither stub is ever invoked.
  private val stubAuthenticator: Authenticator = new Authenticator {
    def calculateCookieName(): String                                                         = "stub"
    def invalidateToken(jwt: String): IO[AuthenticatorError, Unit]                            = ZIO.fail(AuthenticatorError.BadCredentials)
    def parseToken(jwt: String): IO[AuthenticatorError, Jwt]                                  = ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(userIri: UserIri, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(username: Username, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(email: Email, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(jwtToken: String): IO[AuthenticatorError, User] = ZIO.fail(AuthenticatorError.BadCredentials)
  }
  private val base = BaseEndpoints(stubAuthenticator, new AuthorizationRestService(null, null))

  // Kept in sync with the `AdminApiServerEndpoints` aggregator by the last test below. SparqlPassthroughEndpoints is left
  // out: it needs an AppConfig, and its bodies are SPARQL strings, not DTOs.
  private val endpointDefinitions: List[AnyRef] = List(
    AdminListsEndpoints(base),
    FilesEndpoints(base),
    GroupsEndpoints(base),
    MaintenanceEndpoints(base),
    PermissionsEndpoints(base),
    ProjectsEndpoints(base),
    ProjectsLegalInfoEndpoints(base),
    StoreEndpoints(base),
    UsersEndpoints(base),
    ViewRestrictionsByPropertyEndpoints(base),
    ViewRestrictionsEndpoints(base),
  )

  // Found reflectively, including those inside nested objects such as `Public` and `Secured`, so that a newly added
  // endpoint is covered without being registered here.
  private def endpointsOf(definition: AnyRef): List[(String, AnyEndpoint)] = {
    val cls = definition.getClass
    cls.getMethods.toList
      .filter(m => m.getParameterCount == 0 && m.getDeclaringClass == cls && !m.getName.contains("$"))
      .flatMap { m =>
        val name = s"${cls.getSimpleName}.${m.getName}"
        m.invoke(definition) match {
          case e: Endpoint[?, ?, ?, ?, ?]                                                                 => List(name -> e)
          case p: ZPartialServerEndpoint[?, ?, ?, ?, ?, ?, ?]                                             => List(name -> p.endpoint)
          case nested: AnyRef if nested.getClass.getName.startsWith(s"${cls.getName.stripSuffix("$")}$$") =>
            endpointsOf(nested)
          case _ => Nil
        }
      }
  }

  private val endpoints: List[(String, AnyEndpoint)] = endpointDefinitions.flatMap(endpointsOf)

  private def bodySchemas(e: AnyEndpoint): List[Schema[?]] = {
    val inputBody: PartialFunction[EndpointInput[?], Vector[Schema[?]]] = { case b: EndpointIO.Body[?, ?] =>
      Vector(b.codec.schema)
    }
    val outputBody: PartialFunction[EndpointOutput[?], Vector[Schema[?]]] = { case b: EndpointIO.Body[?, ?] =>
      Vector(b.codec.schema)
    }
    (e.input.traverseInputs(inputBody) ++ e.output.traverseOutputs(outputBody) ++
      e.errorOutput.traverseOutputs(outputBody)).toList
  }

  // A genuine `{value: ...}` object on the wire, not a value type.
  private val allowedWrappers = Set("PlainStringLiteralV2")

  private def isScalar(s: Schema[?]): Boolean = s.schemaType match {
    case _: SchemaType.SString[?] | _: SchemaType.SBoolean[?] | _: SchemaType.SInteger[?] | _: SchemaType.SNumber[?] |
        _: SchemaType.SArray[?, ?] =>
      true
    case _ => false
  }

  // The object schemas Tapir derives for a value type it has no Schema for: exactly one field, `value`, or, for a sealed
  // trait of case objects such as `SelfJoin`, one empty object per case.
  private def valueTypeObjects(s: Schema[?], path: String, depth: Int = 0): List[String] =
    if (depth > 20) Nil
    else
      s.schemaType match {
        case SchemaType.SProduct(fields) =>
          val self = fields match {
            case List(f) if f.name.name == "value" && isScalar(f.schema) =>
              val name = s.name.fold("<unnamed>")(_.fullName)
              if (allowedWrappers.exists(name.endsWith)) Nil else List(s"$path: $name")
            case Nil => List(s"$path: ${s.name.fold("<unnamed>")(_.fullName)} (empty object)")
            case _   => Nil
          }
          self ++ fields.flatMap(f => valueTypeObjects(f.schema, s"$path.${f.name.name}", depth + 1))
        case SchemaType.SOption(element)                  => valueTypeObjects(element, path, depth + 1)
        case SchemaType.SArray(element)                   => valueTypeObjects(element, s"$path[]", depth + 1)
        case SchemaType.SCoproduct(subtypes, _)           => subtypes.flatMap(valueTypeObjects(_, path, depth + 1))
        case SchemaType.SOpenProduct(fields, valueSchema) =>
          fields.flatMap(f => valueTypeObjects(f.schema, s"$path.${f.name.name}", depth + 1)) ++
            valueTypeObjects(valueSchema, s"$path.*", depth + 1)
        case _ => Nil
      }

  private def restrictedViewSizeType: Option[SchemaType[?]] =
    for {
      (_, e) <- endpoints.find(_._1.endsWith("postAdminProjectsByProjectShortcodeRestrictedViewSettings"))
      body   <- bodySchemas(e).headOption
      size   <- body.schemaType match {
                case SchemaType.SProduct(fields) => fields.find(_.name.name == "size").map(_.schema)
                case _                           => None
              }
    } yield size.schemaType match {
      case SchemaType.SOption(element) => element.schemaType
      case other                       => other
    }

  override def spec: Spec[TestEnvironment, Any] = suite("Value type schemas should")(
    // Also pins that the walk reaches secured endpoints: `endpoints.nonEmpty` below holds on public ones alone.
    test("render the restricted view size as a bare string, as the server accepts it") {
      assertTrue(restrictedViewSizeType.exists(_.isInstanceOf[SchemaType.SString[?]]))
    },
    test("never render a value type as an object in an admin request or response body") {
      val offenders = for {
        (name, e) <- endpoints
        schema    <- bodySchemas(e)
        offender  <- valueTypeObjects(schema, name)
      } yield offender
      assertTrue(endpoints.nonEmpty, offenders.distinct.sorted == Nil)
    },
    test("cover every endpoint definition registered in AdminApiServerEndpoints") {
      val registered = classOf[AdminApiServerEndpoints].getConstructors.toList
        .flatMap(_.getParameterTypes)
        .map(_.getSimpleName.stripSuffix("ServerEndpoints") + "Endpoints")
        .toSet
      val covered = endpointDefinitions.map(_.getClass.getSimpleName).toSet + "SparqlPassthroughEndpoints"
      assertTrue(registered.nonEmpty, registered == covered)
    },
  )
}
