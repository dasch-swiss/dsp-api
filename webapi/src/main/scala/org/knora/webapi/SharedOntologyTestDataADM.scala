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

package org.knora.webapi

object SharedOntologyTestDataADM {

    // anything
    val ANYTHING_ONTOLOGY_IRI: IRI =  OntologyConstants.KnoraInternal.InternalOntologyStart + "/0001/anything"
    val ANYTHING_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0001/anything"

    // images
    val IMAGES_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/00FF/images"
    val IMAGES_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/00FF/images"
    val IMAGES_TITEL_PROPERTY: IRI = IMAGES_ONTOLOGY_IRI + "#" + "titel"
    val IMAGES_BILD_RESOURCE_CLASS: IRI = IMAGES_ONTOLOGY_IRI + "#" + "bild"

    // beol
    val BEOL_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/0801/beol"
    val BEOL_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0801/beol"

    // biblio
    val BIBLIO_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/0801/biblio"
    val BIBLIO_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0801/biblio"

    // incunabula
    val INCUNABULA_ONTOLOGY_IRI: IRI =  OntologyConstants.KnoraInternal.InternalOntologyStart + "/0803/incunabula"
    val INCUNABULA_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0803/incunabula"
    val INCUNABULA_BOOK_RESOURCE_CLASS: IRI = INCUNABULA_ONTOLOGY_IRI + "#" + "book"
    val INCUNABULA_PAGE_RESOURCE_CLASS: IRI = INCUNABULA_ONTOLOGY_IRI + "#" + "page"

    // dokubib
    val DOKUBIB_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/0804/dokubib"
    val DOKUBIB_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0804/dokubib"

    // webern
    val WEBERN_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/08AE/webern"
    val WEBERN_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/08AE/webern"
}
