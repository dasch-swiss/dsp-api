/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV1}

/**
 * Provides a spray-routing function for API routes that deal with resource types.
 */
class ResourceTypesRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {

    path("v1" / "resourcetypes" / Segment) { iri =>
      get { requestContext =>
        val requestMessage = for {
          userProfile <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )

          // TODO: Check that this is the IRI of a resource type and not just any IRI
          resourceTypeIri = stringFormatter.validateAndEscapeIri(
            iri,
            throw BadRequestException(s"Invalid resource class IRI: $iri")
          )

        } yield ResourceTypeGetRequestV1(resourceTypeIri, userProfile)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )
      }
    } ~ path("v1" / "resourcetypes") {
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          params = requestContext.request.uri.query().toMap

          vocabularyId = params.getOrElse(
            "vocabulary",
            throw BadRequestException("Required param vocabulary is missing")
          )

          namedGraphIri = vocabularyId match {
            case "0" => None // if param vocabulary is set to 0, query all named graphs
            case other =>
              Some(
                stringFormatter.validateAndEscapeIri(
                  vocabularyId,
                  throw BadRequestException(s"Invalid vocabulary IRI: $vocabularyId")
                )
              )
          }

        } yield ResourceTypesForNamedGraphGetRequestV1(
          namedGraph = namedGraphIri,
          featureFactoryConfig = featureFactoryConfig,
          userADM = userADM
        )

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )

      }
    } ~ path("v1" / "propertylists") {
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          params = requestContext.request.uri.query().toMap

          vocabularyId: Option[String] = params.get("vocabulary")
          resourcetypeId: Option[String] = params.get("restype")

          // either the vocabulary or the restype param is set, but not both
          _ = if (vocabularyId.nonEmpty && resourcetypeId.nonEmpty)
            throw BadRequestException("Both vocabulary and restype params are set, only one is allowed")
        } yield vocabularyId match {
          case Some("0") => // 0 means that all named graphs should be queried
            PropertyTypesForNamedGraphGetRequestV1(
              namedGraph = None,
              featureFactoryConfig = featureFactoryConfig,
              userADM = userADM
            )

          case Some(vocId) =>
            val namedGraphIri = stringFormatter.validateAndEscapeIri(
              vocId,
              throw BadRequestException(s"Invalid vocabulary IRI: $vocabularyId")
            )
            PropertyTypesForNamedGraphGetRequestV1(
              namedGraph = Some(namedGraphIri),
              featureFactoryConfig = featureFactoryConfig,
              userADM = userADM
            )

          case None => // no vocabulary id given, check for restype
            resourcetypeId match {
              case Some(restypeId) => // get property types for given resource type
                val resourceClassIri = stringFormatter.validateAndEscapeIri(
                  restypeId,
                  throw BadRequestException(s"Invalid vocabulary IRI: $restypeId")
                )
                PropertyTypesForResourceTypeGetRequestV1(restypeId, userADM)
              case None => // no params given, get all property types (behaves like vocbulary=0)
                PropertyTypesForNamedGraphGetRequestV1(
                  namedGraph = None,
                  featureFactoryConfig = featureFactoryConfig,
                  userADM = userADM
                )
            }
        }

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )

      }
    } ~ path("v1" / "vocabularies") {
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield NamedGraphsGetRequestV1(
          featureFactoryConfig = featureFactoryConfig,
          userADM = userADM
        )

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )

      }
    } ~ path("v1" / "vocabularies" / "reload") {
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield LoadOntologiesRequestV1(
          featureFactoryConfig = featureFactoryConfig,
          userADM = userADM
        )

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )
      }
    } ~ path("v1" / "subclasses" / Segment) { iri =>
      get { requestContext =>
        val requestMessage = for {
          userADM <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )

          // TODO: Check that this is the IRI of a resource type and not just any IRI
          resourceClassIri = stringFormatter.validateAndEscapeIri(
            iri,
            throw BadRequestException(s"Invalid resource class IRI: $iri")
          )
        } yield SubClassesGetRequestV1(resourceClassIri, userADM)

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )
      }
    }
  }
}
