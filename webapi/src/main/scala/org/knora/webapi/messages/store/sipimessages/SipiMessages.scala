/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.store.sipimessages

import java.io.File

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.FileValueV1
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages V1

/**
  * An abstract trait for messages that can be sent to the [[org.knora.webapi.store.iiif.IIIFManager]]
  */
sealed trait IIIFRequest

/**
  * An abstract trait for messages that can be sent to [[org.knora.webapi.store.iiif.SipiConnector]].
  */
sealed trait SipiRequestV1 extends IIIFRequest

/**
  * Abstract trait to represent a conversion request to Sipi Connector.
  *
  * For each type of conversion request, an implementation of `toFormData` must be provided.
  *
  */
sealed trait SipiConversionRequestV1 extends SipiRequestV1 {
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

    def toJsValue(): JsValue
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
case class SipiConversionPathRequestV1(originalFilename: String,
                                       originalMimeType: String,
                                       source: File,
                                       userProfile: UserProfileV1) extends SipiConversionRequestV1 {

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

    def toJsValue = RepresentationV1JsonProtocol.SipiConversionPathRequestV1Format.write(this)
}

/**
  * Represents an binary file that has been temporarily stored by Sipi (GUI-case). Knora route received a request telling it about
  * a file that is already managed by Sipi. The binary file data have already been sent to Sipi by the client (browser-based GUI).
  * Knora has to tell Sipi about the name of the file to be converted.
  * For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
  *
  * @param originalFilename the original name of the binary file.
  * @param originalMimeType the MIME type of the binary file (e.g. image/tiff).
  * @param filename         the name of the binary file created by SIPI.
  * @param userProfile      the user making the request.
  */

case class SipiConversionFileRequestV1(originalFilename: String,
                                       originalMimeType: String,
                                       filename: String,
                                       userProfile: UserProfileV1) extends SipiConversionRequestV1 {

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

    def toJsValue = RepresentationV1JsonProtocol.SipiConversionFileRequestV1Format.write(this)

}


/**
  * Represents the response received from SIPI after an image conversion request.
  *
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
  * @param file_type         type of file that has been converted (image).
  */
case class SipiImageConversionResponse(nx_full: Int,
                                       ny_full: Int,
                                       mimetype_full: String,
                                       filename_full: String,
                                       nx_thumb: Int,
                                       ny_thumb: Int,
                                       mimetype_thumb: String,
                                       filename_thumb: String,
                                       original_mimetype: String,
                                       original_filename: String,
                                       file_type: String)

/**
  * Represents the response received from Sipi after a text file store request.
  *
  * @param mimetype          mime type of the text file.
  * @param charset           encoding of the text file.
  * @param filename          filename of the text file.
  * @param original_mimetype original mime type of the text file (equals `mimetype`).
  * @param original_filename original name of the text file.
  * @param file_type         type of file that has been stored (text).
  */
case class SipiTextResponse(mimetype: String,
                            charset: String,
                            filename: String,
                            original_mimetype: String,
                            original_filename: String,
                            file_type: String)


object SipiConstants {
    // TODO: Shall we better use an ErrorHandlingMap here?
    // map file types converted by Sipi to file value properties in Knora
    val fileType2FileValueProperty = Map(
        FileType.TEXT -> OntologyConstants.KnoraBase.HasTextFileValue,
        FileType.IMAGE -> OntologyConstants.KnoraBase.HasStillImageFileValue,
        FileType.MOVIE -> OntologyConstants.KnoraBase.HasMovingImageFileValue,
        FileType.AUDIO -> OntologyConstants.KnoraBase.HasAudioFileValue,
        FileType.BINARY -> OntologyConstants.KnoraBase.HasDocumentFileValue

    )

    object FileType extends Enumeration {
        // the string representations correspond to Sipi's internal enum.
        val IMAGE = Value(0, "image")
        val TEXT = Value(1, "text")
        val MOVIE = Value(2, "movie")
        val AUDIO = Value(3, "audio")
        val BINARY = Value(4, "binary")

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
  * Response from SIPIResponder to a [[SipiConversionRequestV1]] representing one or more [[FileValueV1]].
  *
  * @param fileValuesV1 a list of [[FileValueV1]]
  */
case class SipiConversionResponseV1(fileValuesV1: Vector[FileValueV1], file_type: SipiConstants.FileType.Value)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting V1

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object RepresentationV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    /**
      * Converts between [[SipiConversionPathRequestV1]] objects and [[JsValue]] objects.
      */
    implicit object SipiConversionPathRequestV1Format extends RootJsonFormat[SipiConversionPathRequestV1] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[SipiConversionPathRequestV1]] into [[JsValue]] for formatting as JSON.
          *
          * @param request the [[SipiConversionPathRequestV1]] to be converted.
          * @return a [[JsValue]].
          */
        def write(request: SipiConversionPathRequestV1): JsValue = {

            val fields = Map(
                "originalFilename" -> request.originalFilename.toJson,
                "originalMimeType" -> request.originalMimeType.toJson,
                "source" -> request.source.toString.toJson
            )

            JsObject(fields)
        }
    }

    /**
      * Converts between [[SipiConversionFileRequestV1]] objects and [[JsValue]] objects.
      */
    implicit object SipiConversionFileRequestV1Format extends RootJsonFormat[SipiConversionFileRequestV1] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[SipiConversionFileRequestV1]] into [[JsValue]] for formatting as JSON.
          *
          * @param request the [[SipiConversionFileRequestV1]] to be converted.
          * @return a [[JsValue]].
          */
        def write(request: SipiConversionFileRequestV1): JsValue = {

            val fields = Map(
                "originalFilename" -> request.originalFilename.toJson,
                "originalMimeType" -> request.originalMimeType.toJson,
                "filename" -> request.filename.toJson
            )

            JsObject(fields)
        }
    }

    implicit val sipiImageConversionResponseFormat: RootJsonFormat[SipiImageConversionResponse] = jsonFormat11(SipiImageConversionResponse)
    implicit val textStoreResponseFormat: RootJsonFormat[SipiTextResponse] = jsonFormat6(SipiTextResponse)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages V2

/**
  * An abstract trait for messages that can be sent to [[org.knora.webapi.store.iiif.SipiConnector]].
  */
sealed trait SipiRequestV2 extends IIIFRequest {
    def requestingUser: UserADM
}

/**
  * Requests file metadata from Sipi. A successful response is a [[GetImageMetadataResponseV2]].
  *
  * @param fileUrl        the URL at which Sipi can serve the file.
  * @param requestingUser the user making the request.
  */
case class GetImageMetadataRequestV2(fileUrl: String,
                                     requestingUser: UserADM) extends SipiRequestV2


/**
  * Represents a response from Sipi providing metadata about an image file.
  *
  * @param originalFilename the image's original filename.
  * @param originalMimeType the image's original MIME type.
  * @param width            the image's width in pixels.
  * @param height           the image's height in pixels.
  */
case class GetImageMetadataResponseV2(originalFilename: String,
                                      originalMimeType: String,
                                      width: Int,
                                      height: Int)

object GetImageMetadataResponseV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val getImageMetadataResponseV2Format: RootJsonFormat[GetImageMetadataResponseV2] = jsonFormat4(GetImageMetadataResponseV2)
}

/**
  * Asks Sipi to move a file from temporary to permanent storage.
  *
  * @param internalFilename the name of the file.
  * @param requestingUser   the user making the request.
  */
case class MoveTemporaryFileToPermanentStorageRequestV2(internalFilename: String,
                                                        requestingUser: UserADM) extends SipiRequestV2

/**
  * Asks Sipi to delete a temporary file.
  *
  * @param internalFilename the name of the file.
  * @param requestingUser   the user making the request.
  */
case class DeleteTemporaryFileRequestV2(internalFilename: String,
                                        requestingUser: UserADM) extends SipiRequestV2


/**
  * Asks Sipi for a text file. Currently only for UTF8 encoded text files.
  *
  * @param fileUrl        the URL pointing to the file.
  * @param requestingUser the user making the request.
  */
case class SipiGetTextFileRequest(fileUrl: String,
                                  requestingUser: UserADM) extends SipiRequestV2

/**
  * Represents a response for [[SipiGetTextFileRequest]].
  * @param content the file content.
  */
case class SipiGetTextFileResponse(content: String)

