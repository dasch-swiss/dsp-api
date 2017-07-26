/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util

import org.knora.webapi.IRI

/**
  * Holds an optional, mutable IRI for use in tests.
  */
class MutableTestIri {
    private var maybeIri: Option[IRI] = None

    /**
      * Checks whether an IRI is valid. If it's valid, stores the IRI, otherwise throws an exception.
      * @param iri the IRI to be stored.
      */
    def set(iri: IRI): Unit = {
        maybeIri = Some(InputValidation.toIri(iri, () => throw TestIriException(s"Got an invalid IRI: <$iri>")))
    }

    /**
      * Removes any stored IRI.
      */
    def unset(): Unit = {
        maybeIri = None
    }

    /**
      * Gets the stored IRI, or throws an exception if the IRI is not set.
      * @return the stored IRI.
      */
    def get: IRI = {
        maybeIri.getOrElse(throw TestIriException("This test could not be run because a previous test failed"))
    }
}

/**
  * Thrown if a stored IRI was needed but was not set.
  */
case class TestIriException(message: String) extends Exception(message)
