/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import dsp.errors.AssertionException
import org.knora.webapi.feature.FeatureFactory
import org.knora.webapi.messages.util.rdf.jenaimpl._
import org.knora.webapi.settings.KnoraSettingsImpl

/**
 * A feature factory that creates RDF processing tools.
 */
object RdfFeatureFactory extends FeatureFactory {

  /**
   * The name of the feature toggle that enables the Jena implementation of the RDF façade.
   */
  private val JENA_TOGGLE_NAME = "jena-rdf-library"

  // Jena singletons.
  private val jenaNodeFactory                                = new JenaNodeFactory
  private val jenaModelFactory                               = new JenaModelFactory(jenaNodeFactory)
  private val jenaFormatUtil                                 = new JenaFormatUtil(modelFactory = jenaModelFactory, nodeFactory = jenaNodeFactory)
  private var jenaShaclValidator: Option[JenaShaclValidator] = None

  /**
   * Initialises the [[RdfFeatureFactory]]. This method must be called once, on application startup.
   *
   * @param settings the application settings.
   */
  def init(settings: KnoraSettingsImpl): Unit =
    // Construct the SHACL validators, which need the application settings.
    this.synchronized {
      jenaShaclValidator = Some(
        new JenaShaclValidator(
          baseDir = settings.shaclShapesDir,
          rdfFormatUtil = jenaFormatUtil,
          nodeFactory = jenaNodeFactory
        )
      )
    }

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

  def getShaclValidator(): ShaclValidator = {
    def notInitialised: Nothing = throw AssertionException("RdfFeatureFactory has not been initialised")

    jenaShaclValidator.getOrElse(notInitialised)
  }
}
