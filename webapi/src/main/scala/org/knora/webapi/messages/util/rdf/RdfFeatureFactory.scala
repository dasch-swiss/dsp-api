/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.feature.{FeatureFactory, FeatureFactoryConfig}
import org.knora.webapi.messages.util.rdf.jenaimpl.{JenaFormatUtil, JenaModelFactory, JenaNodeFactory}
import org.knora.webapi.messages.util.rdf.rdf4jimpl.{RDF4JFormatUtil, RDF4JModelFactory, RDF4JNodeFactory}

/**
 * A feature factory that creates RDF processing tools.
 */
object RdfFeatureFactory extends FeatureFactory {
    /**
     * The name of the feature toggle that enables the Jena implementation of the RDF façade.
     */
    private val JENA_TOGGLE_NAME = "jena-rdf-library"

    // Jena factory instances.
    private val jenaModelFactory = new JenaModelFactory
    private val jenaNodeFactory = new JenaNodeFactory
    private val jenaFormatUtil = new JenaFormatUtil(jenaModelFactory)

    // RDF4J factory instances.
    private val rdf4jModelFactory = new RDF4JModelFactory
    private val rdf4jNodeFactory = new RDF4JNodeFactory
    private val rdf4jFormatUtil = new RDF4JFormatUtil(rdf4jModelFactory)

    /**
     * Returns an [[RdfModelFactory]].
     *
     * @param featureFactoryConfig the feature factory configuration.
     * @return an [[RdfModelFactory]].
     */
    def getRdfModelFactory(featureFactoryConfig: FeatureFactoryConfig): RdfModelFactory = {
        if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
            jenaModelFactory
        } else {
            rdf4jModelFactory
        }
    }

    /**
     * Returns an [[RdfNodeFactory]].
     *
     * @param featureFactoryConfig the feature factory configuration.
     * @return an [[RdfNodeFactory]].
     */
    def getRdfNodeFactory(featureFactoryConfig: FeatureFactoryConfig): RdfNodeFactory = {
        if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
            jenaNodeFactory
        } else {
            rdf4jNodeFactory
        }
    }

    /**
     * Returns an [[RdfFormatUtil]].
     *
     * @param featureFactoryConfig the feature factory configuration.
     * @return an [[RdfFormatUtil]].
     */
    def getRdfFormatUtil(featureFactoryConfig: FeatureFactoryConfig): RdfFormatUtil = {
        if (featureFactoryConfig.getToggle(JENA_TOGGLE_NAME).isEnabled) {
            jenaFormatUtil
        } else {
            rdf4jFormatUtil
        }
    }
}
