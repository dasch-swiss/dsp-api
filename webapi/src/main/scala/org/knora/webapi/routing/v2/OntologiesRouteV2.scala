/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import dsp.errors.BadRequestException
import dsp.valueobjects.Schema.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.api.OntologyV2RequestParser
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.security.Authenticator

/**
 * Provides a routing function for API v2 routes that deal with ontologies.
 */
final case class OntologiesRouteV2()(
  private implicit val runtime: Runtime[
    AppConfig & Authenticator & IriConverter & MessageRelay & OntologyV2RequestParser & StringFormatter,
  ],
) {

  private val ontologiesBasePath: PathMatcher[Unit] = PathMatcher("v2" / "ontologies")

  private val allLanguagesKey = "allLanguages"

  def makeRoute: Route =
    dereferenceOntologyIri() ~
      getOntologyMetadata

  private def dereferenceOntologyIri(): Route = path("ontology" / Segments) { (_: List[String]) =>
    get { requestContext =>
      // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
      // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
      // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
      // project-specific API ontology, prefix it with routeData.appConfig.externalOntologyIriHostAndPort to get the
      // ontology IRI.
      val ontologyIriTask = getOntologySmartIri(requestContext)

      val requestTask = for {
        ontologyIri    <- ontologyIriTask
        params          = requestContext.request.uri.query().toMap
        allLanguagesStr = params.get(allLanguagesKey)
        allLanguages    = ValuesValidator.optionStringToBoolean(allLanguagesStr, fallback = false)
        user           <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
      } yield OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, user)

      val targetSchemaTask = ontologyIriTask.flatMap(getTargetSchemaFromOntology)

      RouteUtilV2.runRdfRouteZ(requestTask, requestContext, targetSchemaTask)
    }
  }

  private def getTargetSchemaFromOntology(iri: OntologyIri) =
    ZIO.fromOption(iri.smartIri.getOntologySchema).orElseFail(BadRequestException(s"Invalid ontology IRI: $iri"))

  private def getOntologySmartIri(
    requestContext: RequestContext,
  ): ZIO[AppConfig & IriConverter & StringFormatter, BadRequestException, OntologyIri] = {
    val urlPath = requestContext.request.uri.path.toString
    ZIO.serviceWithZIO[AppConfig] { appConfig =>
      val externalOntologyIriHostAndPort = appConfig.knoraApi.externalOntologyIriHostAndPort
      ZIO.serviceWithZIO[StringFormatter] { sf =>
        for {
          iri <- if (sf.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                   ZIO.succeed(OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath)
                 } else if (sf.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                   ZIO.succeed("http://" + externalOntologyIriHostAndPort + urlPath)
                 } else {
                   ZIO.fail(BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath"))
                 }
          ontologyIri <- RouteUtilZ.ontologyIri(iri)
        } yield ontologyIri
      }
    }
  }

  private def getOntologyMetadata: Route =
    path(ontologiesBasePath / "metadata") {
      get { requestContext =>
        val requestTask = RouteUtilV2
          .getProjectIri(requestContext)
          .map(_.toSet)
          .map(OntologyMetadataGetByProjectRequestV2(_))
        RouteUtilV2.runRdfRouteZ(requestTask, requestContext)
      }
    }
}
