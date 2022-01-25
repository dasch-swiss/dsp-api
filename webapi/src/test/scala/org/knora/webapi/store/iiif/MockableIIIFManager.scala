/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import akka.actor.ActorRef
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.settings.SipiConnectorActorName

/**
 * A subclass of [[IIIFManager]] that allows tests to substitute standard connector for a custom one.
 *
 * @param mockStoreConnectors a [[Map]] containing the mock connectors to be used instead of the live ones.
 *                            The name of the actor (a constant from [[org.knora.webapi.store]] is used as the
 *                            key in the map.
 */
class MockableIIIFManager(mockStoreConnectors: Map[String, ActorRef]) extends IIIFManager with LiveActorMaker {

  /**
   * Initialised to the value of the key 'SipiConnectorActorName' in `mockStoreConnectors` if provided, otherwise
   * the default SipiConnector is used.
   */
  override lazy val sipiConnector: ActorRef =
    mockStoreConnectors.getOrElse(SipiConnectorActorName, makeDefaultSipiConnector)

}
