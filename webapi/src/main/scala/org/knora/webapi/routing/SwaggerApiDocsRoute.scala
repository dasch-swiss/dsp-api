/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.server.Route
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.models.auth.BasicAuthDefinition
import io.swagger.models.{ExternalDocs, Scheme}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.admin.lists._

/**
 * Provides the '/api-docs' endpoint serving the 'swagger.json' OpenAPI specification
 */
class SwaggerApiDocsRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with SwaggerHttpService {

  // List all routes here
  override val apiClasses: Set[Class[_]] = Set(
    classOf[GroupsRouteADM],
    classOf[OldListsRouteADMFeature],
    classOf[DeleteListItemsRouteADM],
    classOf[PermissionsRouteADM],
    classOf[ProjectsRouteADM],
    classOf[StoreRouteADM],
    classOf[UsersRouteADM],
    classOf[HealthRoute]
  )

  override val schemes: List[Scheme] = if (settings.externalKnoraApiProtocol == "http") {
    List(Scheme.HTTP)
  } else if (settings.externalKnoraApiProtocol == "https") {
    List(Scheme.HTTPS)
  } else {
    List(Scheme.HTTP)
  }

  // swagger will publish at: http://locahost:3333/api-docs/swagger.json

  override val host: String = settings.externalKnoraApiHostPort // the url of your api, not swagger's json endpoint
  override val basePath = "/" //the basePath for the API you are exposing
  override val apiDocsPath = "api-docs" //where you want the swagger-json endpoint exposed
  override val info: Info = Info(version = "1.8.0") //provides license and other description details
  override val externalDocs: Option[ExternalDocs] = Some(new ExternalDocs("Knora Docs", "http://docs.knora.org"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    routes

}
