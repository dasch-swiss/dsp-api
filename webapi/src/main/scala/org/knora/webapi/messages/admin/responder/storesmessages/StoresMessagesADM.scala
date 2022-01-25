/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.storesmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

sealed trait StoreResponderRequestADM extends KnoraRequestADM

/**
 * Requests to load the triplestore with data referenced inside [[RdfDataObject]]. Any data contained inside the
 * triplestore will be deleted first.
 *
 * @param rdfDataObjects       a sequence of [[RdfDataObject]] objects containing the path to the data and the name of
 *                             the named graph into which the data should be loaded.
 * @param prependDefaults      should a default set of [[RdfDataObject]]s be prepended. The default is `false`.
 * @param featureFactoryConfig the feature factory configuration.
 */
case class ResetTriplestoreContentRequestADM(
  rdfDataObjects: Seq[RdfDataObject],
  prependDefaults: Boolean = false,
  featureFactoryConfig: FeatureFactoryConfig
) extends StoreResponderRequestADM

case class ResetTriplestoreContentResponseADM(message: String) extends KnoraResponseADM with StoresADMJsonProtocol {
  def toJsValue = resetTriplestoreContentResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API ADM JSON for property values.
 */
trait StoresADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

  /* Very strange construct at the end is needed, but I don't really understand why and what it means */
  implicit val resetTriplestoreContentResponseADMFormat: RootJsonFormat[ResetTriplestoreContentResponseADM] =
    jsonFormat[String, ResetTriplestoreContentResponseADM](ResetTriplestoreContentResponseADM, "message")
}
