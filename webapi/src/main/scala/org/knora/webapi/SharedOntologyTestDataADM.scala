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
    val LocalHost_Ontology = "http://0.0.0.0:3333/ontology"

    // anything
    val ANYTHING_ONTOLOGY_IRI: IRI =  OntologyConstants.KnoraInternal.InternalOntologyStart + "/0001/anything"
    val ANYTHING_ONTOLOGY_IRI_LocalHost: IRI =  LocalHost_Ontology + "/0001/anything/v2"
    val ANYTHING_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0001/anything"
    val ANYTHING_THING_RESOURCE_CLASS_LocalHost: IRI = ANYTHING_ONTOLOGY_IRI_LocalHost + "#" + "Thing"
    val ANYTHING_HasListItem_PROPERTY_LocalHost: IRI = ANYTHING_ONTOLOGY_IRI_LocalHost + "#" + "hasListItem"
    val ANYTHING_HasDate_PROPERTY_LocalHost: IRI = ANYTHING_ONTOLOGY_IRI_LocalHost + "#" + "hasDate"

    // minimal
    val MINIMAL_ONTOLOGY_IRI: IRI =  OntologyConstants.KnoraInternal.InternalOntologyStart + "/0001/minimal"
    val MINIMAL_ONTOLOGY_IRI_LocalHost: IRI =  LocalHost_Ontology + "/0001/minimal/v2"


    // images
    val IMAGES_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/00FF/images"
    val IMAGES_ONTOLOGY_IRI_LocalHost: IRI = LocalHost_Ontology + "/00FF/images/v2"
    val IMAGES_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/00FF/images"
    val IMAGES_TITEL_PROPERTY: IRI = IMAGES_ONTOLOGY_IRI + "#" + "titel"
    val IMAGES_TITEL_PROPERTY_LocalHost: IRI = IMAGES_ONTOLOGY_IRI_LocalHost + "#" + "titel"
    val IMAGES_BILD_RESOURCE_CLASS: IRI = IMAGES_ONTOLOGY_IRI + "#" + "bild"
    val IMAGES_BILD_RESOURCE_CLASS_LocalHost: IRI = IMAGES_ONTOLOGY_IRI_LocalHost + "#" + "bild"


    // beol
    val BEOL_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/0801/beol"
    val BEOL_ONTOLOGY_IRI_LocalHost: IRI = LocalHost_Ontology + "/0801/beol/v2"
    val BEOL_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0801/beol"

    // biblio
    val BIBLIO_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/0801/biblio"
    val BIBLIO_ONTOLOGY_IRI_LocalHost: IRI = LocalHost_Ontology + "/0801/biblio/v2"
    val BIBLIO_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0801/biblio"

    // incunabula
    val INCUNABULA_ONTOLOGY_IRI: IRI =  OntologyConstants.KnoraInternal.InternalOntologyStart + "/0803/incunabula"
    val INCUNABULA_ONTOLOGY_IRI_LocalHost: IRI =  LocalHost_Ontology + "/0803/incunabula/v2"
    val INCUNABULA_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0803/incunabula"
    val INCUNABULA_BOOK_RESOURCE_CLASS: IRI = INCUNABULA_ONTOLOGY_IRI + "#" + "book"
    val INCUNABULA_BOOK_RESOURCE_CLASS_LocalHost: IRI = INCUNABULA_ONTOLOGY_IRI_LocalHost + "#" + "book"
    val INCUNABULA_PAGE_RESOURCE_CLASS: IRI = INCUNABULA_ONTOLOGY_IRI + "#" + "page"
    val INCUNABULA_PAGE_RESOURCE_CLASS_LocalHost: IRI = INCUNABULA_ONTOLOGY_IRI_LocalHost + "#" + "page"
    val INCUNABULA_PartOf_Property_LocalHost: IRI = INCUNABULA_ONTOLOGY_IRI_LocalHost + "#" + "partOfValue"

    // dokubib
    val DOKUBIB_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/0804/dokubib"
    val DOKUBIB_ONTOLOGY_IRI_LocalHost: IRI = LocalHost_Ontology + "/0804/dokubib/v2"
    val DOKUBIB_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/0804/dokubib"

    // webern
    val WEBERN_ONTOLOGY_IRI: IRI = OntologyConstants.KnoraInternal.InternalOntologyStart + "/08AE/webern"
    val WEBERN_ONTOLOGY_IRI_LocalHost: IRI = LocalHost_Ontology + "/08AE/webern/v2"
    val WEBERN_DATA_IRI: IRI = OntologyConstants.NamedGraphs.DataNamedGraphStart + "/08AE/webern"
}
