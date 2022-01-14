/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import com.typesafe.config.Config
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

/**
 */
object RdfDataObjectFactory {
  def apply(conf: Config): RdfDataObject = {
    val path = conf.getString("path")
    val name = conf.getString("name")
    new RdfDataObject(path, name)
  }
}
