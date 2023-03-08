/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.messages.util.rdf.jenaimpl._

/**
 * A feature factory that creates RDF processing tools.
 */
object RdfFeatureFactory {

  // Jena singletons.
  private val jenaNodeFactory  = new JenaNodeFactory
  private val jenaModelFactory = new JenaModelFactory(jenaNodeFactory)
  private val jenaFormatUtil   = new JenaFormatUtil(modelFactory = jenaModelFactory, nodeFactory = jenaNodeFactory)

  /**
   * Returns an [[RdfModelFactory]].
   *
   * @return an [[RdfModelFactory]].
   */
  def getRdfModelFactory(): RdfModelFactory = jenaModelFactory

  /**
   * Returns an [[RdfNodeFactory]].
   *
   * @return an [[RdfNodeFactory]].
   */
  def getRdfNodeFactory(): RdfNodeFactory = jenaNodeFactory

  /**
   * Returns an [[RdfFormatUtil]].
   *
   * @return an [[RdfFormatUtil]].
   */
  def getRdfFormatUtil(): RdfFormatUtil = jenaFormatUtil
}
