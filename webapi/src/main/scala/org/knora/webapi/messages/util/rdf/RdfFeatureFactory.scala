/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.feature.FeatureFactory
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.util.rdf.jenaimpl._
import org.knora.webapi.messages.util.rdf.rdf4jimpl._
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

  // RDF4J singletons.
  private val rdf4jNodeFactory                                 = new RDF4JNodeFactory
  private val rdf4jModelFactory                                = new RDF4JModelFactory(rdf4jNodeFactory)
  private val rdf4jFormatUtil                                  = new RDF4JFormatUtil(modelFactory = rdf4jModelFactory, nodeFactory = rdf4jNodeFactory)
  private var rdf4jShaclValidator: Option[RDF4JShaclValidator] = None

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

      rdf4jShaclValidator = Some(
        new RDF4JShaclValidator(
          baseDir = settings.shaclShapesDir,
          rdfFormatUtil = rdf4jFormatUtil,
          nodeFactory = rdf4jNodeFactory
        )
      )
    }

  /**
   * Returns an [[RdfModelFactory]].
   *
   * @param featureFactoryConfig the feature factory configuration.
   * @return an [[RdfModelFactory]].
   */
  def getRdfModelFactory(featureFactoryConfig: FeatureFactoryConfig): RdfModelFactory =
    if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
      jenaModelFactory
    } else {
      rdf4jModelFactory
    }

  /**
   * Returns an [[RdfNodeFactory]].
   *
   * @param featureFactoryConfig the feature factory configuration.
   * @return an [[RdfNodeFactory]].
   */
  def getRdfNodeFactory(featureFactoryConfig: FeatureFactoryConfig): RdfNodeFactory =
    if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
      jenaNodeFactory
    } else {
      rdf4jNodeFactory
    }

  /**
   * Returns an [[RdfFormatUtil]].
   *
   * @param featureFactoryConfig the feature factory configuration.
   * @return an [[RdfFormatUtil]].
   */
  def getRdfFormatUtil(featureFactoryConfig: FeatureFactoryConfig): RdfFormatUtil =
    if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
      jenaFormatUtil
    } else {
      rdf4jFormatUtil
    }

  def getShaclValidator(featureFactoryConfig: FeatureFactoryConfig): ShaclValidator = {
    def notInitialised: Nothing = throw AssertionException("RdfFeatureFactory has not been initialised")

    if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
      jenaShaclValidator.getOrElse(notInitialised)
    } else {
      rdf4jShaclValidator.getOrElse(notInitialised)
    }
  }
}
