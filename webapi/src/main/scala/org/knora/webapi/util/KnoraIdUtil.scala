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

import java.nio.ByteBuffer
import java.util.{Base64, UUID}

import org.knora.webapi._

object KnoraIdUtil {
    private val CanonicalUuidLength = 36
    private val Base64UuidLength = 22

    // The domain name used to construct Knora IRIs. If the project ID has been registered with
    // the Data and Service Center for the Humanities, the IRI will be dereferenceable.
    private val IriDomain = "rdfh.ch"
}

/**
  * Converts SALSAH data IDs to Knora IRIs, generates random Knora IRIs for new data, and provides functions
  * for manipulating Knora IRIs.
  */
class KnoraIdUtil {

    import KnoraIdUtil._

    private val base64Encoder = Base64.getUrlEncoder.withoutPadding
    private val base64Decoder = Base64.getUrlDecoder

    /**
      * Generates a type 4 UUID using [[java.util.UUID]], and Base64-encodes it using a URL and filename safe
      * Base64 encoder from [[java.util.Base64]], without padding. This results in a 22-character string that
      * can be used as a unique identifier in IRIs.
      *
      * @return a random, Base64-encoded UUID.
      */
    def makeRandomBase64EncodedUuid: String = {
        val uuid = UUID.randomUUID
        base64EncodeUuid(uuid)
    }

    /**
      * Base64-encodes a [[UUID]] using a URL and filename safe Base64 encoder from [[java.util.Base64]],
      * without padding. This results in a 22-character string that can be used as a unique identifier in IRIs.
      *
      * @param uuid the [[UUID]] to be encoded.
      * @return a 22-character string representing the UUID.
      */
    def base64EncodeUuid(uuid: UUID): String = {
        val bytes = Array.ofDim[Byte](16)
        val byteBuffer = ByteBuffer.wrap(bytes)
        byteBuffer.putLong(uuid.getMostSignificantBits)
        byteBuffer.putLong(uuid.getLeastSignificantBits)
        base64Encoder.encodeToString(bytes)
    }

    /**
      * Decodes a Base64-encoded UUID.
      *
      * @param base64Uuid the Base64-encoded UUID to be decoded.
      * @return the equivalent [[UUID]].
      */
    def base64DecodeUuid(base64Uuid: String): UUID = {
        val bytes = base64Decoder.decode(base64Uuid)
        val byteBuffer = ByteBuffer.wrap(bytes)
        new UUID(byteBuffer.getLong, byteBuffer.getLong)
    }

    /**
      * Encodes a [[UUID]] as a string in one of two formats:
      *
      * - The canonical 36-character format.
      * - The 22-character Base64-encoded format returned by [[base64EncodeUuid]].
      *
      * @param uuid      the UUID to be encoded.
      * @param useBase64 if `true`, uses Base64 encoding.
      * @return the encoded UUID.
      */
    def encodeUuid(uuid: UUID, useBase64: Boolean): String = {
        if (useBase64) {
            base64EncodeUuid(uuid)
        } else {
            uuid.toString
        }
    }

    /**
      * Decodes a string representing a UUID in one of two formats:
      *
      * - The canonical 36-character format.
      * - The 22-character Base64-encoded format returned by [[base64EncodeUuid]].
      *
      * @param uuidStr the string to be decoded.
      * @return the equivalent [[UUID]].
      */
    def decodeUuid(uuidStr: String): UUID = {
        if (uuidStr.length == 22) {
            base64DecodeUuid(uuidStr)
        } else {
            UUID.fromString(uuidStr)
        }
    }

    /**
      * Checks if a string is the right length to be a canonical or Base64-encoded UUID.
      *
      * @param idStr the string to check.
      * @return `true` if the string is the right length to be a canonical or Base64-encoded UUID.
      */
    def couldBeUuid(idStr: String): Boolean = {
        idStr.length == CanonicalUuidLength || idStr.length == Base64UuidLength
    }

    /**
      * Creates a new resource IRI based on a UUID.
      *
      * @param projectShortname the project's unique, short identifier.
      * @return a new resource IRI.
      */
    def makeRandomResourceIri(projectShortname: String): IRI = {
        val knoraResourceID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/$projectShortname/$knoraResourceID"
    }

    /**
      * Creates a new value IRI based on a UUID.
      *
      * @param resourceIri the IRI of the resource that will contain the value.
      * @return a new value IRI.
      */
    def makeRandomValueIri(resourceIri: IRI): IRI = {
        val knoraValueID = makeRandomBase64EncodedUuid
        s"$resourceIri/values/$knoraValueID"
    }

    /**
      * Creates a mapping IRI based on a project IRI and a mapping name.
      *
      * @param projectIri the IRI of the project the mapping will belong to.
      * @return a mapping IRI.
      */
    def makeProjectMappingIri(projectIri: IRI, mappingName: String): IRI = {
        val mappingIri = s"$projectIri/mappings/$mappingName"
        // check that the mapping Iri is valid (mappingName is user input)
        InputValidation.toIri(mappingIri, () => throw BadRequestException(s"the created mapping Iri $mappingIri is invalid"))
    }

    /**
      * Creates a random ID for an element of a mapping based on a mapping IRI.
      *
      * @param mappingIri the IRI of the mapping the element belongs to.
      * @return a new mapping element IRI.
      */
    def makeRandomMappingElementIri(mappingIri: IRI): IRI = {
        val knoraMappingElementID = makeRandomBase64EncodedUuid
        s"$mappingIri/elements/$knoraMappingElementID"
    }

    /**
      * Creates an Iri used as a lock for the creation of mappings inside a given project.
      * This method will always return the same Iri for the given project Iri.
      *
      * @param projectIri the IRI of the project the mapping will belong to.
      * @return an Iri used as a lock for the creation of mappings inside a given project.
      */
    def createMappingLockIriForProject(projectIri: IRI): IRI = {
        s"$projectIri/mappings"
    }

    /**
      * Creates a new representation IRI based on a UUID.
      *
      * @param resourceIri the IRI of the resource that will have the representation.
      * @return a new representation IRI.
      */
    def makeRandomFileValueIri(resourceIri: IRI): IRI = {
        val knoraValueID = makeRandomBase64EncodedUuid
        s"$resourceIri/reps/$knoraValueID"
    }

    /**
      * Creates a new institution IRI based on a UUID.
      *
      * @return a new institution IRI.
      */
    def makeRandomInstitutionIri: IRI = {
        val knoraInstitutionID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/institutions/$knoraInstitutionID"
    }

    /**
      * Creates a new project IRI based on a UUID.
      *
      * @return a new project IRI.
      */
    def makeRandomProjectIri: IRI = {
        val knoraProjectID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/projects/$knoraProjectID"
    }

    /**
      * Creates a new group IRI based on a UUID.
      *
      * @return a new group IRI.
      */
    def makeRandomGroupIri: String = {
        val knoraGroupID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/groups/$knoraGroupID"
    }

    /**
      * Creates a new person IRI based on a UUID.
      *
      * @return a new person IRI.
      */
    def makeRandomPersonIri: IRI = {
        val knoraPersonID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/users/$knoraPersonID"
    }

    /**
      * Creates a new hierarchical list IRI based on a UUID.
      *
      * @return a new hierarchical list IRI.
      */
    def makeRandomHListIri: IRI = {
        val knoraHListID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/lists/$knoraHListID"
    }

    /**
      * Creates a new standoff tag IRI based on a UUID.
      *
      * @param valueIri the IRI of the text value containing the standoff tag.
      * @return a standoff tag IRI.
      */
    def makeRandomStandoffTagIri(valueIri: IRI): IRI = {
        val standoffTagID = makeRandomBase64EncodedUuid
        s"$valueIri/standoff/$standoffTagID"
    }

    /**
      * Converts the IRI of a property that points to a resource into the IRI of the corresponding link value property.
      *
      * @param linkPropertyIri the IRI of the property that points to a resource.
      * @return the IRI of the corresponding link value property.
      */
    def linkPropertyIriToLinkValuePropertyIri(linkPropertyIri: IRI): IRI = linkPropertyIri + "Value"

    /**
      * Converts the IRI of a property that points to a `knora-base:LinkValue` into the IRI of the corresponding link property.
      *
      * @param linkValuePropertyIri the IRI of the property that points to the `LinkValue`.
      * @return the IRI of the corresponding link property.
      */
    def linkValuePropertyIri2LinkPropertyIri(linkValuePropertyIri: IRI): IRI = {
        if (linkValuePropertyIri.endsWith("Value")) {
            linkValuePropertyIri.substring(0, linkValuePropertyIri.length - "Value".length)
        } else {
            throw InconsistentTriplestoreDataException(s"Link value predicate IRI $linkValuePropertyIri does not end with 'Value'")
        }
    }

    /**
      * Creates a new permission IRI based on a UUID.
      *
      * @return the IRI of the permission object.
      */
    def makeRandomPermissionIri: IRI = {
        val knoraPermissionID = makeRandomBase64EncodedUuid
        s"http://$IriDomain/permissions/$knoraPermissionID"
    }

    /**
      * Creates an IRI for a `knora-base:Map`.
      *
      * @param mapPath the map's path, which must be a sequence of names separated by slashes (`/`). Each name must
      *                be a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
      * @return the IRI of the map.
      */
    def makeMapIri(mapPath: String): IRI = {
        s"http://$IriDomain/maps/$mapPath"
    }
}
