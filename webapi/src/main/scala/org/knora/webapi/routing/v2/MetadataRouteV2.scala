package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import org.knora.webapi.routing.v2.ResourcesRouteV2.ResourcesBasePath
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData}


object MetadataRouteV2 {
    val MetadataBasePath: PathMatcher[Unit] = PathMatcher("v2" / "metadata")
}
/**
 * Provides a routing function for API v2 routes that deal with metadata.
 */
class MetadataRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    import MetadataRouteV2._

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = createMetadata ~ getMetadata

    /**
     * Route to store metadata.
     */
    private def createMetadata: Route = path(MetadataBasePath) {
        post {
            ???
        }
    }

    /**
     * Route to get metadata.
     */
    private def getMetadata: Route = path(MetadataBasePath) {
        get {
            ???
        }
    }
}
