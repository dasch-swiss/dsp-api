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

import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.{StillImageFileValueV1, FileValueV1}
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * Abstract trait to represent a conversion request to the SipiResponder
  *
  * For each type of conversion request, an implementation of `toFormData` must be provided.
  *
  */
sealed trait SipiResponderConversionRequestV1 extends SipiResponderRequestV1 {
    val originalFilename: String
    val originalMimeType: String
    val userProfile: UserProfileV1

    def toFormData(): Map[String, String]
}


/**
  * Represents an binary file that has been temporarily stored by Knora (non GUI-case).
  *
  * @param originalFilename the original name of the binary file.
  * @param originalMimeType the MIME type of the binary file (e.g. image/tiff).
  * @param source the temporary location of the source file on disk (absolute path).
  * @param userProfile the user making the request.
  */
case class SipiResponderConversionPathRequestV1(originalFilename: String,
                                                originalMimeType: String,
                                                source: String,
                                                userProfile: UserProfileV1) extends SipiResponderConversionRequestV1 {

    // create params for SIPI's route convert_path
    def toFormData() = {
        Map(
            "originalFilename" -> originalFilename,
            "originalMimeType" -> originalMimeType,
            "source" -> source
        )
    }
}

/**
  * Represents an binary file that has been temporarily stored by SIPI (GUI-case).
  *
  * @param originalFilename the original name of the binary file.
  * @param originalMimeType the MIME type of the binary file (e.g. image/tiff).
  * @param filename the name of the binary file created by SIPI.
  * @param userProfile the user making the request.
  */

case class SipiResponderConversionFileRequestV1(originalFilename: String,
                                                originalMimeType: String,
                                                filename: String,
                                                userProfile: UserProfileV1) extends SipiResponderConversionRequestV1 {

    // create params for SIPI's route convert_file
    def toFormData() = {
        Map(
            "originalFilename" -> originalFilename,
            "originalMimeType" -> originalMimeType,
            "filename" -> filename
        )
    }

}

/**
  * Represents the response received from SIPI after a conversion request.
  *
  * @param status status code returned by SIPI.
  * @param nx_full x dim of the full quality representation.
  * @param ny_full y dim of the full quality representation.
  * @param mimetype_full mime type of the full quality representation.
  * @param filename_full filename of the full quality representation.
  * @param nx_thumb x dim of the thumbnail representation.
  * @param ny_thumb y dim of the thumbnail representation.
  * @param mimetype_thumb mime type of the thumbnail representation.
  * @param filename_thumb filename of the thumbnail representation.
  * @param original_mimetype mime type of the original file.
  * @param original_filename name of the original file.
  * @param file_type type of file that has been converted (image, audio, video etc.)
  */
// TODO: This response format must be made generic for each possible file type converted by Sipi. We have to use one case class only because we do not know what to expect from Sipi
case class SipiConversionResponse(status: Int,
                                  nx_full: Option[Int],
                                  ny_full: Option[Int],
                                  mimetype_full: Option[String],
                                  filename_full: Option[String],
                                  nx_thumb: Option[Int],
                                  ny_thumb: Option[Int],
                                  mimetype_thumb: Option[String],
                                  filename_thumb: Option[String],
                                  original_mimetype: Option[String],
                                  original_filename: Option[String],
                                  file_type: Option[String]) // TODO: could be an enum




object Sipi {
    // TODO: Shall we better use an ErrorHandlingMap here?
    // map file types converted by Sipi to file value properties in Knora
    val fileType2FileValueProperty = Map(
        FileType.image -> OntologyConstants.KnoraBase.HasStillImageFileValue,
        FileType.movie -> OntologyConstants.KnoraBase.HasMovingImageFileValue,
        FileType.audio -> OntologyConstants.KnoraBase.HasAudioFileValue,
        FileType.binary -> OntologyConstants.KnoraBase.HasDocumentFileValue

    )
    // TODO: Would it be better to make this an Enumeration? However, I had problems to use it in match case statement
    // (http://stackoverflow.com/questions/24087550/scala-pattern-match-against-a-java-enum-type).
    object FileType  {
        val image = "image"
        val movie = "movie"
        val audio = "audio"
        val binary = "binary"
    }
}

/**
  * Response from SIPIResponder to a [[SipiResponderConversionRequestV1]] representing one or more [[FileValueV1]].
  *
  * @param fileValuesV1 a list of [[FileValueV1]]
  */
case class SipiResponderConversionResponseV1(fileValuesV1: Vector[FileValueV1], file_type: String)


/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `SipiResponderV1`.
  */
sealed trait SipiResponderRequestV1 extends KnoraRequestV1

/**
  * A Knora v1 API request message that requests information about a `FileValue`.
  *
  * @param fileValueIri the IRI of the file value to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class SipiFileInfoGetRequestV1(fileValueIri: IRI, userProfile: UserProfileV1) extends SipiResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for a information about a `FileValue`.
  *
  * @param permissionCode a code representing the user's maximum permission on the file.
  * @param path the path to the file.
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
    implicit val sipiConversionResponseFormat = jsonFormat12(SipiConversionResponse)
}


