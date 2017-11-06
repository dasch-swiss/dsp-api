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

package org.knora.webapi

object SharedOntologyTestData {

    val IMAGES_ONTOLOGY_IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "00FF/images"
    val IMAGES_TITEL_PROPERTY = IMAGES_ONTOLOGY_IRI + "#" + "titel"
    val IMAGES_BILD_RESOURCE_CLASS = IMAGES_ONTOLOGY_IRI + "#" + "bild"


    val INCUNABULA_ONTOLOGY_IRI =  OntologyConstants.KnoraInternal.InternalOntologyStart + "incunabula"
    val INCUNABULA_BOOK_RESOURCE_CLASS = INCUNABULA_ONTOLOGY_IRI + "#" + "book"
    val INCUNABULA_PAGE_RESOURCE_CLASS = INCUNABULA_ONTOLOGY_IRI + "#" + "page"
}
