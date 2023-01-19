/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * An update plugin that does nothing. Used for updates in which only the built-in named graphs have changed.
 */
class NoopPlugin extends UpgradePlugin {
  override def transform(model: RdfModel): Unit = {}
}
