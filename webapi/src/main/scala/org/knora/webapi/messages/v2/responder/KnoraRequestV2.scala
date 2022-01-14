/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder

import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.util.Timeout
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, RdfFeatureFactory, RdfModel, Turtle}
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.{ExecutionContext, Future}

/**
 * A tagging trait for messages that can be sent to Knora API v2 responders.
 */
trait KnoraRequestV2

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
  def toTurtle(featureFactoryConfig: FeatureFactoryConfig): String =
    RdfFeatureFactory
      .getRdfFormatUtil(featureFactoryConfig)
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
   * @param responderManager     a reference to the responder manager.
   * @param storeManager         a reference to the store manager.
   * @param featureFactoryConfig the feature factory configuration.
   * @param settings             the application settings.
   * @param log                  a logging adapter.
   * @return a case class instance representing the input.
   */
  def fromJsonLD(
    jsonLDDocument: JsonLDDocument,
    apiRequestID: UUID,
    requestingUser: UserADM,
    responderManager: ActorRef,
    storeManager: ActorRef,
    featureFactoryConfig: FeatureFactoryConfig,
    settings: KnoraSettingsImpl,
    log: LoggingAdapter
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]
}
