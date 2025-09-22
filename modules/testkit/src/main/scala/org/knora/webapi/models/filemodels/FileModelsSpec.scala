/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import java.time.Instant
import java.util.UUID
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.DocumentFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.models.filemodels.FileType.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import zio.test.*
import zio.json.*
import zio.json.ast.*

object FileModelsSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val fileNamePDF     = "document-file.pdf"
  private val fileNameImage   = "image.jp2"
  private val fileNameVideo   = "video.mp4"
  private val fileNameAudio   = "audio.mpeg"
  private val fileNameText    = "text.txt"
  private val fileNameArchive = "archive.zip"

  override val spec = suite("FileModelsUtil")(
    suite("creating a JSON-LD context")(
      test("handle `anything` ontology correctly") {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                                |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                                |  }
                                |""".stripMargin

        assertTrue(FileModelUtil.getJsonLdContext("anything") == expectedContext)
      },
      test("handle non-specified ontology correctly if no ontology IRI is provided") {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                |  }
                                |""".stripMargin

        assertTrue(FileModelUtil.getJsonLdContext("knora-api") == expectedContext)
      },
      test("handle non-specified ontology correctly if an ontology IRI is provided") {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                                |    "biblio": "http://www.knora.org/ontology/0801/biblio"
                                |  }
                                |""".stripMargin

        val actual = FileModelUtil.getJsonLdContext(
          ontology = "biblio",
          ontologyIRI = Some("http://www.knora.org/ontology/0801/biblio"),
        )
        assertTrue(actual == expectedContext)
      },
    ),
    suite("FileModels")(
      suite("creating an UploadFileRequest")(
        test("create a valid representation of a DocumentRepresentation with default values") {
          val documentRepresentation = UploadFileRequest.make(
            fileType = FileType.DocumentFile(),
            internalFilename = fileNamePDF,
          )
          val actualFileType = documentRepresentation.fileType.asInstanceOf[DocumentFile]
          assertTrue(
            documentRepresentation.internalFilename == fileNamePDF,
            actualFileType.pageCount.contains(1),
            actualFileType.dimX.contains(100),
            actualFileType.dimY.contains(100),
          )
        },
        test("create a valid representation of a DocumentRepresentation with custom values") {
          val internalFilename = "document-file.doc"
          val dimX             = Some(20)
          val dimY             = Some(30)
          val pageCount        = Some(550)
          val customLabel      = "a custom label"
          val documentRepresentation = UploadFileRequest.make(
            fileType = FileType.DocumentFile(
              pageCount = pageCount,
              dimX = dimX,
              dimY = dimY,
            ),
            internalFilename = internalFilename,
            label = customLabel,
          )
          val actualFileType: FileType.DocumentFile =
            documentRepresentation.fileType.asInstanceOf[FileType.DocumentFile]
          assertTrue(
            documentRepresentation.internalFilename == internalFilename,
            documentRepresentation.label == customLabel,
            actualFileType.pageCount == pageCount,
            actualFileType.dimX == dimX,
            actualFileType.dimY == dimY,
          )
        },
        test("create a valid representation of a StillImageRepresentation with default values") {
          val stillImageRepresentation = UploadFileRequest.make(
            fileType = FileType.StillImageFile(),
            internalFilename = fileNameImage,
          )
          val actualFileType = stillImageRepresentation.fileType.asInstanceOf[FileType.StillImageFile]
          assertTrue(
            stillImageRepresentation.internalFilename == fileNameImage,
            actualFileType.dimX == 100,
            actualFileType.dimY == 100,
          )
        },
        test("create a valid representation of a StillImageRepresentation with custom values") {
          val stillImageRepresentation = UploadFileRequest.make(
            fileType = FileType.StillImageFile(dimX = 10, dimY = 10),
            internalFilename = fileNameImage,
          )
          val actualFileType = stillImageRepresentation.fileType.asInstanceOf[FileType.StillImageFile]
          assertTrue(
            stillImageRepresentation.internalFilename == fileNameImage,
            actualFileType.dimX == 10,
            actualFileType.dimY == 10,
          )
        },
        test("create a valid representation of a MovingImageRepresentation with default values") {
          val movingImageRepresentation = UploadFileRequest.make(
            fileType = MovingImageFile(),
            internalFilename = fileNameVideo,
          )
          val actualFileType = movingImageRepresentation.fileType.asInstanceOf[MovingImageFile]
          assertTrue(
            movingImageRepresentation.internalFilename == fileNameVideo,
            actualFileType.dimX == 100,
            actualFileType.dimY == 100,
          )
        },
        test("create a valid representation of a MovingImageRepresentation with custom values") {
          val movingImageRepresentation = UploadFileRequest.make(
            fileType = MovingImageFile(10, 11),
            internalFilename = fileNameVideo,
          )
          val actualFileType = movingImageRepresentation.fileType.asInstanceOf[MovingImageFile]
          assertTrue(
            movingImageRepresentation.internalFilename == fileNameVideo,
            actualFileType.dimX == 10,
            actualFileType.dimY == 11,
          )
        },
        test("create a valid representation of a AudioRepresentation") {
          val audioRepresentation = UploadFileRequest.make(
            fileType = FileType.AudioFile,
            internalFilename = fileNameAudio,
          )
          assertTrue(
            audioRepresentation.fileType == FileType.AudioFile,
            audioRepresentation.internalFilename == fileNameAudio,
          )
        },
        test("create a valid representation of a TextRepresentation") {
          val textRepresentation = UploadFileRequest.make(
            fileType = FileType.TextFile,
            internalFilename = fileNameText,
          )
          assertTrue(
            textRepresentation.fileType == FileType.TextFile,
            textRepresentation.internalFilename == fileNameText,
          )
        },
        test("create a valid representation of a ArchiveRepresentation") {
          val archiveRepresentation = UploadFileRequest.make(
            fileType = FileType.ArchiveFile,
            internalFilename = fileNameArchive,
          )
          assertTrue(
            archiveRepresentation.fileType == FileType.ArchiveFile,
            archiveRepresentation.internalFilename == fileNameArchive,
          )
        },
      ),
      suite("generating a JSON-LD representation of a UploadFileRequest")(
        test("correctly serialize a DocumentRepresentation with default values") {
          val documentRepresentation = UploadFileRequest.make(
            fileType = FileType.DocumentFile(),
            internalFilename = fileNamePDF,
          )
          val actual = documentRepresentation.toJsonLd().fromJson[Json]
          val expected = Json.Obj(
            "@type" -> Json.Str("knora-api:DocumentRepresentation"),
            "knora-api:hasDocumentFileValue" -> Json.Obj(
              "@type"                          -> Json.Str("knora-api:DocumentFileValue"),
              "knora-api:fileValueHasFilename" -> Json.Str(fileNamePDF),
            ),
            "knora-api:attachedToProject" -> Json.Obj(
              "@id" -> Json.Str("http://rdfh.ch/projects/0001"),
            ),
            "rdfs:label" -> Json.Str("test label"),
            "@context" -> Json.Obj(
              "rdf"       -> Json.Str("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
              "knora-api" -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
              "rdfs"      -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
              "xsd"       -> Json.Str("http://www.w3.org/2001/XMLSchema#"),
            ),
          )
          assertTrue(actual == Right(expected))
        },
        test("correctly serialize a DocumentRepresentation with custom values") {
          val className    = Some("ThingDocument")
          val ontologyName = "biblio"
          val shortcode    = SharedTestDataADM.beolProject.shortcode
          val ontologyIRI  = SharedTestDataADM.beolProject.ontologies.find(_.endsWith(ontologyName))
          val label        = "a custom label"

          val documentRepresentation = UploadFileRequest.make(
            fileType = FileType.DocumentFile(pageCount = None, dimX = Some(20), dimY = None),
            internalFilename = fileNamePDF,
            label = label,
          )
          val actual = documentRepresentation
            .toJsonLd(
              className = className,
              ontologyName = ontologyName,
              shortcode = shortcode,
              ontologyIRI = ontologyIRI,
            )
            .fromJson[Json]
          val expected = Json.Obj(
            "@type" -> Json.Str(s"$ontologyName:${className.get}"),
            "knora-api:hasDocumentFileValue" -> Json.Obj(
              "@type"                          -> Json.Str("knora-api:DocumentFileValue"),
              "knora-api:fileValueHasFilename" -> Json.Str(fileNamePDF),
            ),
            "knora-api:attachedToProject" -> Json.Obj(
              "@id" -> Json.Str(s"http://rdfh.ch/projects/$shortcode"),
            ),
            "rdfs:label" -> Json.Str(label),
            "@context" -> Json.Obj(
              "rdf"        -> Json.Str("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
              "knora-api"  -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
              "rdfs"       -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
              "xsd"        -> Json.Str("http://www.w3.org/2001/XMLSchema#"),
              ontologyName -> Json.Str(ontologyIRI.get),
            ),
          )
          assertTrue(actual == Right(expected))
        },
      ),
      suite("generating a message representation of a UploadFileRequest")(
        test("correctly serialize a DocumentRepresentation with default values") {
          val documentRepresentation = UploadFileRequest.make(
            fileType = FileType.DocumentFile(),
            internalFilename = fileNamePDF,
          )
          val msg = documentRepresentation.toMessage()
          assertTrue(
            msg.resourceClassIri == FileModelUtil.getFileRepresentationClassIri(FileType.DocumentFile()),
            msg.label == "test label",
            msg.values == Map(
              FileModelUtil.getFileRepresentationPropertyIri(FileType.DocumentFile()) -> List(
                CreateValueInNewResourceV2(
                  valueContent = DocumentFileValueContentV2(
                    ontologySchema = ApiV2Complex,
                    fileValue = FileValueV2(
                      internalFilename = fileNamePDF,
                      internalMimeType = "application/pdf",
                      originalFilename = None,
                      originalMimeType = Some("application/pdf"),
                      None,
                      None,
                      None,
                    ),
                    pageCount = Some(1),
                    dimX = Some(100),
                    dimY = Some(100),
                    comment = None,
                  ),
                  customValueIri = None,
                  customValueUUID = None,
                  customValueCreationDate = None,
                  permissions = None,
                ),
              ),
            ),
            msg.projectADM == SharedTestDataADM.anythingProject,
            msg.permissions.isEmpty,
          )
        },
        test("correctly serialize a DocumentRepresentation with custom values") {
          val pageCount                  = None
          val dimX                       = Some(20)
          val dimY                       = None
          val project                    = SharedTestDataADM.beolProject
          val shortcode                  = project.shortcode
          val label                      = "a custom label"
          val resourceIRI                = sf.makeRandomResourceIri(shortcode)
          val comment                    = Some("This is a custom comment")
          val internalMimetype           = Some("application/msword")
          val originalFilename           = Some("document-file.docm")
          val originalMimeType           = Some("application/vnd.ms-word.document.macroEnabled.12")
          val customValueIRI             = Some(sf.makeRandomResourceIri(shortcode).toSmartIri)
          val customValueUUID            = Some(UUID.randomUUID())
          val customValueCreationDate    = Some(Instant.now())
          val valuePermissions           = Some("V knora-admin:UnknownUser,knora-admin:KnownUser|M knora-admin:ProjectMember")
          val resourcePermissions        = Some("V knora-admin:UnknownUser|M knora-admin:ProjectMember,knora-admin:KnownUser")
          val valuePropertyIRI           = "http://www.knora.org/ontology/0801/biblio#hasThingDocumentValue".toSmartIri
          val resourceClassIRI           = "http://www.knora.org/ontology/0801/biblio#Book".toSmartIri
          val customResourceCreationDate = Some(Instant.now())

          val documentRepresentation = UploadFileRequest.make(
            fileType = FileType.DocumentFile(pageCount = pageCount, dimX = dimX, dimY = dimY),
            internalFilename = fileNamePDF,
            label = label,
          )
          val msg = documentRepresentation.toMessage(
            resourceIri = Some(resourceIRI),
            comment = comment,
            internalMimeType = internalMimetype,
            originalFilename = originalFilename,
            originalMimeType = originalMimeType,
            customValueIri = customValueIRI,
            customValueUUID = customValueUUID,
            customValueCreationDate = customValueCreationDate,
            valuePermissions = valuePermissions,
            resourcePermissions = resourcePermissions,
            resourceCreationDate = customResourceCreationDate,
            resourceClassIRI = Some(resourceClassIRI),
            valuePropertyIRI = Some(valuePropertyIRI),
            project = Some(project),
          )
          assertTrue(
            msg.resourceIri.contains(resourceIRI.toSmartIri),
            msg.label == label,
            msg.permissions == resourcePermissions,
            msg.projectADM == project,
            msg.creationDate == customResourceCreationDate,
            msg.resourceClassIri == resourceClassIRI,
            msg.values ==
              Map(
                valuePropertyIRI -> List(
                  CreateValueInNewResourceV2(
                    valueContent = DocumentFileValueContentV2(
                      ontologySchema = ApiV2Complex,
                      fileValue = FileValueV2(
                        internalFilename = fileNamePDF,
                        internalMimeType = internalMimetype.get,
                        originalFilename = originalFilename,
                        originalMimeType = originalMimeType,
                        None,
                        None,
                        None,
                      ),
                      pageCount = pageCount,
                      dimX = dimX,
                      dimY = dimY,
                      comment = comment,
                    ),
                    customValueIri = customValueIRI,
                    customValueUUID = customValueUUID,
                    customValueCreationDate = customValueCreationDate,
                    permissions = valuePermissions,
                  ),
                ),
              ),
          )
        },
      ),
      suite("creating a ChangeFileRequest")(
        test("create a valid representation of a DocumentRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))

          val change = ChangeFileRequest.make(
            fileType = FileType.DocumentFile(),
            internalFilename = fileNamePDF,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          val actualFileType = change.fileType.asInstanceOf[DocumentFile]
          assertTrue(
            change.internalFilename == fileNamePDF,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == "DocumentRepresentation",
            change.ontologyName == "knora-api",
            actualFileType.pageCount.contains(1),
            actualFileType.dimX.contains(100),
            actualFileType.dimY.contains(100),
          )
        },
        test("create a valid representation of a DocumentRepresentation with custom values") {
          val resourceIRI  = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI     = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val pageCount    = Some(33)
          val dimX         = Some(44)
          val dimY         = Some(55)
          val className    = "CustomDocumentResource"
          val ontologyName = "anything"

          val change = ChangeFileRequest.make(
            fileType = FileType.DocumentFile(
              pageCount = pageCount,
              dimX = dimX,
              dimY = dimY,
            ),
            internalFilename = fileNamePDF,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
            className = Some(className),
            ontologyName = ontologyName,
          )
          val actualFileType = change.fileType.asInstanceOf[DocumentFile]
          assertTrue(
            change.internalFilename == fileNamePDF,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == className,
            change.ontologyName == ontologyName,
            actualFileType.pageCount == pageCount,
            actualFileType.dimX == dimX,
            actualFileType.dimY == dimY,
          )
        },
        test("create a valid representation of a StillImageRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))

          val change = ChangeFileRequest.make(
            fileType = FileType.StillImageFile(),
            internalFilename = fileNameImage,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          val actualFileType = change.fileType.asInstanceOf[StillImageFile]
          assertTrue(
            change.internalFilename == fileNameImage,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == "StillImageRepresentation",
            change.ontologyName == "knora-api",
            actualFileType.dimX == 100,
            actualFileType.dimY == 100,
          )
        },
        test("create a valid representation of a MovingImageRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))

          val change = ChangeFileRequest.make(
            fileType = MovingImageFile(),
            internalFilename = fileNameVideo,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          val actualFileType = change.fileType.asInstanceOf[MovingImageFile]
          assertTrue(
            change.internalFilename == fileNameVideo,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == "MovingImageRepresentation",
            change.ontologyName == "knora-api",
            actualFileType.dimX == 100,
            actualFileType.dimY == 100,
          )
        },
        test("create a valid representation of a AudioRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))

          val change = ChangeFileRequest.make(
            fileType = FileType.AudioFile,
            internalFilename = fileNameAudio,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          assertTrue(
            change.fileType == FileType.AudioFile,
            change.internalFilename == fileNameAudio,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == "AudioRepresentation",
            change.ontologyName == "knora-api",
          )
        },
        test("create a valid representation of a TextRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))

          val change = ChangeFileRequest.make(
            fileType = FileType.TextFile,
            internalFilename = fileNameText,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          assertTrue(
            change.fileType == FileType.TextFile,
            change.internalFilename == fileNameText,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == "TextRepresentation",
            change.ontologyName == "knora-api",
          )
        },
        test("create a valid representation of a ArchiveRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("0000"))

          val change = ChangeFileRequest.make(
            fileType = FileType.ArchiveFile,
            internalFilename = fileNameArchive,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          assertTrue(
            change.fileType == FileType.ArchiveFile,
            change.internalFilename == fileNameArchive,
            change.valueIRI == valueIRI,
            change.resourceIRI == resourceIRI,
            change.className == "ArchiveRepresentation",
            change.ontologyName == "knora-api",
          )
        },
      ),
      suite("generating a JSON-LD representation of a ChangeFileRequest")(
        test("correctly serialize a DocumentRepresentation with default values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("7777"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("7777"))

          val documentRepresentation = ChangeFileRequest.make(
            fileType = FileType.DocumentFile(),
            internalFilename = fileNamePDF,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
          )
          val actual = documentRepresentation.toJsonLd.fromJson[Json]
          val expected = Json.Obj(
            "@id"   -> Json.Str(resourceIRI),
            "@type" -> Json.Str("knora-api:DocumentRepresentation"),
            "knora-api:hasDocumentFileValue" -> Json.Obj(
              "@id"                            -> Json.Str(valueIRI),
              "@type"                          -> Json.Str("knora-api:DocumentFileValue"),
              "knora-api:fileValueHasFilename" -> Json.Str(fileNamePDF),
            ),
            "@context" -> Json.Obj(
              "rdf"       -> Json.Str("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
              "knora-api" -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
              "rdfs"      -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
              "xsd"       -> Json.Str("http://www.w3.org/2001/XMLSchema#"),
            ),
          )
          assertTrue(actual == Right(expected))
        },
        test("correctly serialize a DocumentRepresentation with custom values") {
          val resourceIRI = sf.makeRandomResourceIri(Shortcode.unsafeFrom("7777"))
          val valueIRI    = sf.makeRandomResourceIri(Shortcode.unsafeFrom("7777"))
          val className   = "CustomDocumentRepresentation"
          val prefix      = "onto"

          val documentRepresentation = ChangeFileRequest.make(
            fileType = FileType.DocumentFile(),
            internalFilename = fileNamePDF,
            resourceIri = resourceIRI,
            valueIri = valueIRI,
            className = Some(className),
            ontologyName = prefix,
          )
          val actual = documentRepresentation.toJsonLd.fromJson[Json]
          val expected = Json.Obj(
            "@id"   -> Json.Str(resourceIRI),
            "@type" -> Json.Str(s"$prefix:$className"),
            "knora-api:hasDocumentFileValue" -> Json.Obj(
              "@id"                            -> Json.Str(valueIRI),
              "@type"                          -> Json.Str("knora-api:DocumentFileValue"),
              "knora-api:fileValueHasFilename" -> Json.Str(fileNamePDF),
            ),
            "@context" -> Json.Obj(
              "rdf"       -> Json.Str("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
              "knora-api" -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
              "rdfs"      -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
              "xsd"       -> Json.Str("http://www.w3.org/2001/XMLSchema#"),
            ),
          )
          assertTrue(actual == Right(expected))
        },
      ),
    ),
  )
}
