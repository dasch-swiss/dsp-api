/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo.rdf

import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.model.impl.SimpleNamespace
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri

object Vocabulary {

  object KnoraBase {
    private val kb = "http://www.knora.org/ontology/knora-base#"

    val NS: Namespace = new SimpleNamespace("knora-base", kb)

    val linkValue = iri(kb + "LinkValue")

    val isDeleted         = iri(kb + "isDeleted")
    val attachedToUser    = iri(kb + "attachedToUser")
    val attachedToProject = iri(kb + "attachedToProject")
    val hasPermissions    = iri(kb + "hasPermissions")
    val creationDate      = iri(kb + "creationDate")

    val valueHasString    = iri(kb + "valueHasString")
    val valueHasUUID      = iri(kb + "valueHasUUID")
    val valueHasComment   = iri(kb + "valueHasComment")
    val valueHasOrder     = iri(kb + "valueHasOrder")
    val valueCreationDate = iri(kb + "valueCreationDate")

    val valueHasInteger               = iri(kb + "valueHasInteger")
    val valueHasBoolean               = iri(kb + "valueHasBoolean")
    val valueHasDecimal               = iri(kb + "valueHasDecimal")
    val valueHasUri                   = iri(kb + "valueHasUri")
    val valueHasStartJDN              = iri(kb + "valueHasStartJDN")
    val valueHasEndJDN                = iri(kb + "valueHasEndJDN")
    val valueHasStartPrecision        = iri(kb + "valueHasStartPrecision")
    val valueHasEndPrecision          = iri(kb + "valueHasEndPrecision")
    val valueHasCalendar              = iri(kb + "valueHasCalendar")
    val valueHasColor                 = iri(kb + "valueHasColor")
    val valueHasGeometry              = iri(kb + "valueHasGeometry")
    val valueHasListNode              = iri(kb + "valueHasListNode")
    val valueHasIntervalStart         = iri(kb + "valueHasIntervalStart")
    val valueHasIntervalEnd           = iri(kb + "valueHasIntervalEnd")
    val valueHasTimeStamp             = iri(kb + "valueHasTimeStamp")
    val valueHasGeonameCode           = iri(kb + "valueHasGeonameCode")
    val valueHasRefCount              = iri(kb + "valueHasRefCount")
    val valueHasLanguage              = iri(kb + "valueHasLanguage")
    val valueHasMapping               = iri(kb + "valueHasMapping")
    val valueHasMaxStandoffStartIndex = iri(kb + "valueHasMaxStandoffStartIndex")
    val valueHasStandoff              = iri(kb + "valueHasStandoff")

    val internalFilename = iri(kb + "internalFilename")
    val internalMimeType = iri(kb + "internalMimeType")
    val originalFilename = iri(kb + "originalFilename")
    val originalMimeType = iri(kb + "originalMimeType")
    val dimX             = iri(kb + "dimX")
    val dimY             = iri(kb + "dimY")
    val externalUrl      = iri(kb + "externalUrl")
    val pageCount        = iri(kb + "pageCount")

    val standoffTagHasStartIndex    = iri(kb + "standoffTagHasStartIndex")
    val standoffTagHasEndIndex      = iri(kb + "standoffTagHasEndIndex")
    val standoffTagHasStartParent   = iri(kb + "standoffTagHasStartParent")
    val standoffTagHasEndParent     = iri(kb + "standoffTagHasEndParent")
    val standoffTagHasOriginalXMLID = iri(kb + "standoffTagHasOriginalXMLID")
    val standoffTagHasUUID          = iri(kb + "standoffTagHasUUID")
    val standoffTagHasStart         = iri(kb + "standoffTagHasStart")
    val standoffTagHasEnd           = iri(kb + "standoffTagHasEnd")

  }
}
