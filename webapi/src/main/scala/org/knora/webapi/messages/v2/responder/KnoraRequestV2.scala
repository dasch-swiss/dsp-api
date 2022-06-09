/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder

import akka.actor.ActorRef
import com.typesafe.scalalogging.Logger
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.messages.util.rdf.Turtle
import org.knora.webapi.settings.KnoraSettingsImpl

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * A trait for request messages that are constructed as an [[RdfModel]].
 */
trait KnoraRdfModelRequestV2 {

  /**
   * An [[RdfModel]] representing the request.
   */
  val rdfModel: RdfModel

  /**
   * Returns a Turtle representation of the graph.
   */
  def toTurtle(): String =
    RdfFeatureFactory
      .getRdfFormatUtil()
      .format(
        rdfModel = rdfModel,
        rdfFormat = Turtle,
        prettyPrint = false
      )
}

/**
 * A trait for objects that can generate case class instances based on JSON-LD input.
 *
 * @tparam C the type of the case class that can be generated.
 */
trait KnoraJsonLDRequestReaderV2[C] {

  /**
   * Converts JSON-LD input into a case class instance.
   *
   * @param jsonLDDocument       the JSON-LD input.
   * @param apiRequestID         the UUID of the API request.
   * @param requestingUser       the user making the request.
   * @param appActor             a reference to the application actor.
   * @param featureFactoryConfig the feature factory configuration.
   * @param settings             the application settings.
   * @param log                  a logging adapter.
   * @return a case class instance representing the input.
   */
  def fromJsonLD(
    jsonLDDocument: JsonLDDocument,
    apiRequestID: UUID,
    requestingUser: UserADM,
    appActor: ActorRef,
    settings: KnoraSettingsImpl,
    log: Logger
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]
}
