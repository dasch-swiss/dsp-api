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

package org.knora.webapi.messages.v1respondermessages.sipimessages

import java.io.File

import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.{StillImageFileValueV1, FileValueV1}
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * Abstract trait to represent a conversion request to Sipi Responder.
  *
  * For each type of conversion request, an implementation of `toFormData` must be provided.
  *
  */
sealed trait SipiResponderConversionRequestV1 extends SipiResponderRequestV1 {
    val originalFilename: String
    val originalMimeType: String
    val userProfile: UserProfileV1

    /**
      * Creates a Map representing the parameters to be submitted to Sipi's conversion routes.
      * This method must be implemented for each type of conversion request
      * because different Sipi routes are called and the parameters differ.
      *
      * @return a Map of key-value pairs that can be turned into form data by Sipi responder.
      */
    def toFormData(): Map[String, String]
}


/**
  * Represents a binary file that has been temporarily stored by Knora (non GUI-case). Knora route received a multipart request
  * containing binary data which it saved to a temporary location, so it can be accessed by Sipi. Knora has to delete that file afterwards.
  * For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
  *
  * @param originalFilename the original name of the binary file.
  * @param originalMimeType the MIME type of the binary file (e.g. image/tiff).
  * @param source           the temporary location of the source file on disk (absolute path).
  * @param userProfile      the user making the request.
  */
case class SipiResponderConversionPathRequestV1(originalFilename: String,
                                                originalMimeType: String,
                                                source: File,
                                                userProfile: UserProfileV1) extends SipiResponderConversionRequestV1 {

    /**
      * Creates the parameters needed to call the Sipi route convert_path.
      *
      * Required parameters:
      * - originalFilename: original name of the file to be converted.
      * - originalMimeType: original mime type of the file to be converted.
      * - source: path to the file to be converted (file was created by Knora).
      *
      * @return a Map of key-value pairs that can be turned into form data by Sipi responder.
      */
    def toFormData() = {
        Map(
            "originalFilename" -> originalFilename,
            "originalMimeType" -> originalMimeType,
            "source" -> source.toString
        )
    }
}

/**
  * Represents an binary file that has been temporarily stored by Sipi (GUI-case). Knora route recieved a request telling it about
  * a file that is already managed by Sipi. The binary file data have already been sent to Sipi by the client (browser-based GUI).
  * Knora has to tell Sipi about the name of the file to be converted.
  * For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
  *
  * @param originalFilename the original name of the binary file.
  * @param originalMimeType the MIME type of the binary file (e.g. image/tiff).
  * @param filename         the name of the binary file created by SIPI.
  * @param userProfile      the user making the request.
  */

case class SipiResponderConversionFileRequestV1(originalFilename: String,
                                                originalMimeType: String,
                                                filename: String,
                                                userProfile: UserProfileV1) extends SipiResponderConversionRequestV1 {

    /**
      * Creates the parameters needed to call the Sipi route convert_file.
      *
      * Required parameters:
      * - originalFilename: original name of the file to be converted.
      * - originalMimeType: original mime type of the file to be converted.
      * - filename: name of the file to be converted (already managed by Sipi).
      *
      * @return a Map of key-value pairs that can be turned into form data by Sipi responder.
      */
    def toFormData() = {
        Map(
            "originalFilename" -> originalFilename,
            "originalMimeType" -> originalMimeType,
            "filename" -> filename
        )
    }

}

/**
  * Abstract trait that represents any response returned by SIPI.
  */
sealed trait SipiConversionResponse {
    val status: Int
}

/**
  * Represents an error message returned by SIPI
  *
  * @param status  status code rerurned by SIPI.
  * @param message description of the error.
  */
case class SipiErrorConversionResponse(status: Int, message: String) extends SipiConversionResponse {
    override def toString() = {
        s"Sipi status code is ${status}, Sipi error message: ${message}"
    }
}

/**
  * Represents the response received from SIPI after an image conversion request.
  *
  * @param status            status code returned by SIPI.
  * @param nx_full           x dim of the full quality representation.
  * @param ny_full           y dim of the full quality representation.
  * @param mimetype_full     mime type of the full quality representation.
  * @param filename_full     filename of the full quality representation.
  * @param nx_thumb          x dim of the thumbnail representation.
  * @param ny_thumb          y dim of the thumbnail representation.
  * @param mimetype_thumb    mime type of the thumbnail representation.
  * @param filename_thumb    filename of the thumbnail representation.
  * @param original_mimetype mime type of the original file.
  * @param original_filename name of the original file.
  * @param file_type         type of file that has been converted (image, audio, video etc.)
  */
case class SipiImageConversionResponse(status: Int,
                                       nx_full: Int,
                                       ny_full: Int,
                                       mimetype_full: String,
                                       filename_full: String,
                                       nx_thumb: Int,
                                       ny_thumb: Int,
                                       mimetype_thumb: String,
                                       filename_thumb: String,
                                       original_mimetype: String,
                                       original_filename: String,
                                       file_type: String) extends SipiConversionResponse


object SipiConstants {
    // TODO: Shall we better use an ErrorHandlingMap here?
    // map file types converted by Sipi to file value properties in Knora
    val fileType2FileValueProperty = Map(
        FileType.IMAGE -> OntologyConstants.KnoraBase.HasStillImageFileValue,
        FileType.MOVIE -> OntologyConstants.KnoraBase.HasMovingImageFileValue,
        FileType.AUDIO -> OntologyConstants.KnoraBase.HasAudioFileValue,
        FileType.BINARY -> OntologyConstants.KnoraBase.HasDocumentFileValue

    )

    object FileType extends Enumeration {
        // the string representations correspond to Sipi's internal enum.
        val IMAGE = Value(0, "image")
        val MOVIE = Value(1, "movie")
        val AUDIO = Value(2, "audio")
        val BINARY = Value(3, "binary")

        val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

        /**
          * Given the name of a file type in this enumeration, returns the file type. If the file type is not found, throws an
          * [[SipiException]].
          *
          * @param filetype the name of the file type.
          * @return the requested file type.
          */
        def lookup(filetype: String): Value = {
            valueMap.get(filetype) match {
                case Some(ftype) => ftype
                case None => throw SipiException(message = s"File type $filetype returned by Sipi not found in enumeration")
            }
        }

    }

    object StillImage {
        val fullQuality = "full"
        val thumbnailQuality = "thumbnail"
    }
}

/**
  * Response from SIPIResponder to a [[SipiResponderConversionRequestV1]] representing one or more [[FileValueV1]].
  *
  * @param fileValuesV1 a list of [[FileValueV1]]
  */
case class SipiResponderConversionResponseV1(fileValuesV1: Vector[FileValueV1], file_type: SipiConstants.FileType.Value)


/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `SipiResponderV1`.
  */
sealed trait SipiResponderRequestV1 extends KnoraRequestV1

/**
  * A Knora v1 API request message that requests information about a `FileValue`.
  *
  * @param fileValueIri the IRI of the file value to be queried.
  * @param userProfile  the profile of the user making the request.
  */
case class SipiFileInfoGetRequestV1(fileValueIri: IRI, userProfile: UserProfileV1) extends SipiResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for a information about a `FileValue`.
  *
  * @param permissionCode a code representing the user's maximum permission on the file.
  * @param path           the path to the file.
  */
case class SipiFileInfoGetResponseV1(permissionCode: Option[Int],
                                     path: Option[String]) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.sipiFileInfoGetResponseV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object RepresentationV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    implicit val sipiFileInfoGetResponseV1Format: RootJsonFormat[SipiFileInfoGetResponseV1] = jsonFormat2(SipiFileInfoGetResponseV1)
    implicit val sipiErrorConversionResponseFormat = jsonFormat2(SipiErrorConversionResponse)
    implicit val sipiImageConversionResponseFormat = jsonFormat12(SipiImageConversionResponse)
}


