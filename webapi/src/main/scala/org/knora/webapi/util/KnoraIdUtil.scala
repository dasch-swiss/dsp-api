/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import scala.annotation.tailrec
import scala.math.BigInt

object KnoraIdUtil {
    private val Big256 = BigInt("256")
    private val ResourceIdFactor = BigInt("982451653")
    private val ValueIdFactor = BigInt("961751491")
    private val FileValueIdFactor = BigInt("961750463")
    private val ProjectIdFactor = BigInt("961750903")
    private val PersonIdFactor = BigInt("961752349")
    private val InstitutionIdFactor = BigInt("961756489")
    private val HListIdFactor = BigInt("961754009")
    private val SelectionIdFactor = BigInt("961754011")

    private val CanonicalUuidLength = 36
    private val Base64UuidLength = 22
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
      * @param uuid the UUID to be encoded.
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
      * Converts a SALSAH resource ID into a Knora resource IRI.
      *
      * @param salsahResourceID the SALSAH resource ID.
      * @return a resource IRI.
      */
    def salsahResourceId2Iri(salsahResourceID: String): IRI = {
        val knoraResourceID = salsahId2KnoraId(BigInt(salsahResourceID), ResourceIdFactor)
        s"http://data.knora.org/$knoraResourceID"
    }

    /**
      * Creates a new resource IRI based on a UUID.
      *
      * @return a new resource IRI.
      */
    def makeRandomResourceIri: IRI = {
        val knoraResourceID = makeRandomBase64EncodedUuid
        s"http://data.knora.org/$knoraResourceID"
    }

    /**
      * Converts a SALSAH value ID into a Knora value IRI.
      *
      * @param salsahResourceID the SALSAH ID of the resource that the value belongs to.
      * @param salsahValueID    the SALSAH value ID.
      * @return a value IRI.
      */
    def salsahValueId2Iri(salsahResourceID: String, salsahValueID: String): IRI = {
        val knoraResourceID = salsahId2KnoraId(BigInt(salsahResourceID), ResourceIdFactor)
        val knoraValueID = salsahId2KnoraId(BigInt(salsahValueID), ValueIdFactor)
        s"http://data.knora.org/$knoraResourceID/values/$knoraValueID"
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
      * Converts a SALSAH file value ID into a Knora file value IRI.
      *
      * @param salsahResourceID  the SALSAH ID of the Representation resource that the file value belongs to.
      * @param salsahFileValueID the SALSAH file value ID.
      * @return a file value IRI.
      */
    def salsahFileValueId2Iri(salsahResourceID: String, salsahFileValueID: String): IRI = {
        val knoraResourceID = salsahId2KnoraId(BigInt(salsahResourceID), ResourceIdFactor)
        val knoraFileValueID = salsahId2KnoraId(BigInt(salsahFileValueID), FileValueIdFactor)
        s"http://data.knora.org/$knoraResourceID/reps/$knoraFileValueID"
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
      * Converts a SALSAH institution ID into a Knora institution IRI.
      *
      * @param salsahInstitutionID the SALSAH institution ID.
      * @return an institution IRI.
      */
    def salsahInstitutionId2Iri(salsahInstitutionID: String): IRI = {
        val knoraInstitutionID = salsahId2KnoraId(BigInt(salsahInstitutionID), InstitutionIdFactor)
        s"http://data.knora.org/institutions/$knoraInstitutionID"
    }

    /**
      * Creates a new institution IRI based on a UUID.
      *
      * @return a new institution IRI.
      */
    def makeRandomInstitutionIri: IRI = {
        val knoraInstitutionID = makeRandomBase64EncodedUuid
        s"http://data.knora.org/institutions/$knoraInstitutionID"
    }

    /**
      * Converts a SALSAH project ID into a Knora project IRI.
      *
      * @param salsahProjectID the SALSAH project ID.
      * @return a project IRI.
      */
    def salsahProjectId2Iri(salsahProjectID: String): IRI = {
        val knoraProjectID = salsahId2KnoraId(BigInt(salsahProjectID), ProjectIdFactor)
        s"http://data.knora.org/projects/$knoraProjectID"
    }

    /**
      * Creates a new project IRI based on a UUID.
      *
      * @return a new project IRI.
      */
    def makeRandomProjectIri: IRI = {
        val knoraProjectID = makeRandomBase64EncodedUuid
        s"http://data.knora.org/projects/$knoraProjectID"
    }

    /**
      * Creates a new group IRI based on a UUID.
      *
      * @return a new group IRI.
      */
    def makeRandomGroupIri: String = {
        val knoraGroupID = makeRandomBase64EncodedUuid
        s"http:://data.knora.org/groups/$knoraGroupID"
    }

    /**
      * Converts a SALSAH person ID into a Knora person IRI.
      *
      * @param salsahPersonID the SALSAH person ID.
      * @return a person IRI.
      */
    def salsahPersonId2Iri(salsahPersonID: String): IRI = {
        val knoraPersonID = salsahId2KnoraId(BigInt(salsahPersonID), PersonIdFactor)
        s"http://data.knora.org/users/$knoraPersonID"
    }

    /**
      * Creates a new person IRI based on a UUID.
      *
      * @return a new person IRI.
      */
    def makeRandomPersonIri: IRI = {
        val knoraPersonID = makeRandomBase64EncodedUuid
        s"http://data.knora.org/users/$knoraPersonID"
    }

    /**
      * Converts a SALSAH hlist ID into a Knora hierarchical list IRI.
      *
      * @param salsahHListID the SALSAH hlist ID.
      * @return a hierarchical list IRI.
      */
    def salsahHListId2Iri(salsahHListID: String): IRI = {
        val knoraHListID = salsahId2KnoraId(BigInt(salsahHListID), HListIdFactor)
        s"http://data.knora.org/lists/$knoraHListID"
    }

    /**
      * Creates a new hierarchical list IRI based on a UUID.
      *
      * @return a new hierarchical list IRI.
      */
    def makeRandomHListIri: IRI = {
        val knoraHListID = makeRandomBase64EncodedUuid
        s"http://data.knora.org/lists/$knoraHListID"
    }

    /**
      * Converts a SALSAH selection ID into a Knora selection IRI.
      *
      * @param salsahSelectionID the SALSAH selection ID.
      * @return a selection IRI.
      */
    def salsahSelectionId2Iri(salsahSelectionID: String): IRI = {
        val knoraSelectionID = salsahId2KnoraId(BigInt(salsahSelectionID), SelectionIdFactor)
        s"http://data.knora.org/lists/$knoraSelectionID"
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
      * Creates a ned permission IRI based on a UUID
      * @return the IRI of the permission object
      */
    def makeRandomPermissionIri: IRI = {
        val knoraPermissionID = makeRandomBase64EncodedUuid
        s"http://data.knora.org/permissions/$knoraPermissionID"
    }

    ////////////////////////////////////////////////
    // Helper methods
    ////////////////////////////////////////////////
    /**
      * Implements an algorithm for generating Knora IDs from SALSAH IDs. Based on the PHP implementation in SALSAH, in
      * `scripts/RDF-Export/rdf-uuid-from.php`.
      *
      * @param id     the SALSAH ID
      * @param factor the factor to multiply the ID by.
      * @return the resulting Knora ID.
      */
    private def salsahId2KnoraId(id: BigInt, factor: BigInt): String = {
        @tailrec
        def reduceProduct(product: BigInt, results: Vector[String]): Vector[String] = {
            if (product > BigInt(0)) {
                val remainder = product % Big256
                reduceProduct((product - remainder) / Big256, results :+ f"$remainder%02x")
            } else {
                results
            }
        }

        reduceProduct(id * factor, Vector.empty[String]).mkString
    }
}
