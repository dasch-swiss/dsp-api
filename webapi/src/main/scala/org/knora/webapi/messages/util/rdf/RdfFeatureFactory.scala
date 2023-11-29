/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.messages.util.rdf.jenaimpl.*

/**
 * A feature factory that creates RDF processing tools.
 */
object RdfFeatureFactory {

  // Jena singletons.
  private val jenaNodeFactory  = new JenaNodeFactory
  private val jenaModelFactory = new JenaModelFactory(jenaNodeFactory)
  private val jenaFormatUtil   = new RdfFormatUtil(jenaModelFactory, jenaNodeFactory)

  /**
   * Returns an [[RdfModelFactory]].
   *
   * @return an [[RdfModelFactory]].
   */
  def getRdfModelFactory(): JenaModelFactory = jenaModelFactory

  /**
   * Returns an [[RdfNodeFactory]].
   *
   * @return an [[RdfNodeFactory]].
   */
  def getRdfNodeFactory(): JenaNodeFactory = jenaNodeFactory

  /**
   * Returns an [[RdfFormatUtil]].
   *
   * @return an [[RdfFormatUtil]].
   */
  def getRdfFormatUtil(): RdfFormatUtil = jenaFormatUtil
}
