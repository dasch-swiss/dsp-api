package org.knora.webapi.models.filemodels

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.CoreSpec
import dsp.errors.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.DocumentFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.UpdateValueContentV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.time.Instant
import java.util.UUID

class FileModelsSpec extends CoreSpec {
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val fileNamePDF                       = "document-file.pdf"
  private val fileNameImage                     = "image.jp2"
  private val fileNameVideo                     = "video.mp4"
  private val fileNameAudio                     = "audio.mpeg"
  private val fileNameText                      = "text.txt"
  private val fileNameArchive                   = "archive.zip"

  "FileModelsUtil," when {

    "creating a JSON-LD context," should {

      "handle `anything` ontology correctly" in {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                                |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                                |  }
                                |""".stripMargin

        val context = FileModelUtil.getJsonLdContext("anything")
        context should equal(expectedContext)
      }

      "handle non-specified ontology correctly if no ontology IRI is provided" in {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                |  }
                                |""".stripMargin

        val context = FileModelUtil.getJsonLdContext("knora-api")
        context should equal(expectedContext)
      }

      "handle non-specified ontology correctly if an ontology IRI is provided" in {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                                |    "biblio": "http://www.knora.org/ontology/0801/biblio"
                                |  }
                                |""".stripMargin

        val context = FileModelUtil.getJsonLdContext(
          ontology = "biblio",
          ontologyIRI = Some("http://www.knora.org/ontology/0801/biblio")
        )
        context should equal(expectedContext)
      }

    }
  }

  "FileModels," when {

    "creating an UploadFileRequest," should {

      "create a valid representation of a DocumentRepresentation with default values" in {
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF
        )
        documentRepresentation.fileType match {
          case FileType.DocumentFile(pg, x, y) =>
            pg should equal(Some(1))
            x should equal(Some(100))
            y should equal(Some(100))
          case _ =>
            throw AssertionException(s"FileType ${documentRepresentation.fileType} did not match DocumentFile(_, _, _)")
        }
        documentRepresentation.internalFilename should equal(fileNamePDF)
      }

      "create a valid representation of a DocumentRepresentation with custom values" in {
        val internalFilename = "document-file.doc"
        val dimX             = Some(20)
        val dimY             = Some(30)
        val pageCount        = Some(550)
        val customLabel      = "a custom label"
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(
            pageCount = pageCount,
            dimX = dimX,
            dimY = dimY
          ),
          internalFilename = internalFilename,
          label = customLabel
        )
        documentRepresentation.fileType match {
          case FileType.DocumentFile(pg, x, y) =>
            pg should equal(pageCount)
            x should equal(dimX)
            y should equal(dimY)
          case _ =>
            throw AssertionException(s"FileType ${documentRepresentation.fileType} did not match DocumentFile(_, _, _)")
        }
        documentRepresentation.internalFilename should equal(internalFilename)
        documentRepresentation.label should equal(customLabel)
      }

      "create a valid representation of a StillImageRepresentation with default values" in {
        val stillImageRepresentation = UploadFileRequest.make(
          fileType = FileType.StillImageFile(),
          internalFilename = fileNameImage
        )
        stillImageRepresentation.fileType match {
          case FileType.StillImageFile(x, y) =>
            x should equal(100)
            y should equal(100)
          case _ =>
            throw AssertionException(
              s"FileType ${stillImageRepresentation.fileType} did not match StillImageFile(_, _)"
            )
        }
        stillImageRepresentation.internalFilename should equal(fileNameImage)
      }

      "create a valid representation of a StillImageRepresentation with custom values" in {
        val stillImageRepresentation = UploadFileRequest.make(
          fileType = FileType.StillImageFile(dimX = 10, dimY = 10),
          internalFilename = fileNameImage
        )
        stillImageRepresentation.fileType match {
          case FileType.StillImageFile(x, y) =>
            x should equal(10)
            y should equal(10)
          case _ =>
            throw AssertionException(
              s"FileType ${stillImageRepresentation.fileType} did not match StillImageFile(_, _)"
            )
        }
        stillImageRepresentation.internalFilename should equal(fileNameImage)
      }

      "create a valid representation of a MovingImageRepresentation with default values" in {
        val movingImageRepresentation = UploadFileRequest.make(
          fileType = FileType.MovingImageFile(),
          internalFilename = fileNameVideo
        )
        movingImageRepresentation.fileType match {
          case FileType.MovingImageFile(x, y) =>
            x should equal(100)
            y should equal(100)
          case _ =>
            throw AssertionException(
              s"FileType ${movingImageRepresentation.fileType} did not match MovingImageFile(_, _)"
            )
        }
        movingImageRepresentation.internalFilename should equal(fileNameVideo)
      }

      "create a valid representation of a MovingImageRepresentation with custom values" in {
        val movingImageRepresentation = UploadFileRequest.make(
          fileType = FileType.MovingImageFile(10, 11),
          internalFilename = fileNameVideo
        )
        movingImageRepresentation.fileType match {
          case FileType.MovingImageFile(x, y) =>
            x should equal(10)
            y should equal(11)
          case _ =>
            throw AssertionException(
              s"FileType ${movingImageRepresentation.fileType} did not match MovingImageFile(_, _)"
            )
        }
        movingImageRepresentation.internalFilename should equal(fileNameVideo)
      }

      "create a valid representation of a AudioRepresentation" in {
        val audioRepresentation = UploadFileRequest.make(
          fileType = FileType.AudioFile,
          internalFilename = fileNameAudio
        )
        audioRepresentation.fileType should equal(FileType.AudioFile)
        audioRepresentation.internalFilename should equal(fileNameAudio)
      }

      "create a valid representation of a TextRepresentation" in {
        val textRepresentation = UploadFileRequest.make(
          fileType = FileType.TextFile,
          internalFilename = fileNameText
        )
        textRepresentation.fileType should equal(FileType.TextFile)
        textRepresentation.internalFilename should equal(fileNameText)
      }

      "create a valid representation of a ArchiveRepresentation" in {
        val archiveRepresentation = UploadFileRequest.make(
          fileType = FileType.ArchiveFile,
          internalFilename = fileNameArchive
        )
        archiveRepresentation.fileType should equal(FileType.ArchiveFile)
        archiveRepresentation.internalFilename should equal(fileNameArchive)
      }

    }

    "generating a JSON-LD representation of a UploadFileRequest," should {

      "correctly serialize a DocumentRepresentation with default values" in {
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF
        )
        val json = documentRepresentation.toJsonLd().parseJson
        val expectedJSON = Map(
          "@type" -> "knora-api:DocumentRepresentation".toJson,
          "knora-api:hasDocumentFileValue" -> Map(
            "@type"                          -> "knora-api:DocumentFileValue",
            "knora-api:fileValueHasFilename" -> fileNamePDF
          ).toJson,
          "knora-api:attachedToProject" -> Map(
            "@id" -> "http://rdfh.ch/projects/0001"
          ).toJson,
          "rdfs:label" -> "test label".toJson,
          "@context" -> Map(
            "rdf"       -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
            "rdfs"      -> "http://www.w3.org/2000/01/rdf-schema#",
            "xsd"       -> "http://www.w3.org/2001/XMLSchema#"
          ).toJson
        ).toJson
        json should equal(expectedJSON)
      }

      "correctly serialize a DocumentRepresentation with custom values" in {
        val className    = Some("ThingDocument")
        val ontologyName = "biblio"
        val shortcode    = SharedTestDataADM.beolProject.shortcode
        val ontologyIRI  = SharedTestDataADM.beolProject.ontologies.find(_.endsWith(ontologyName))
        val label        = "a custom label"

        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(pageCount = None, dimX = Some(20), dimY = None),
          internalFilename = fileNamePDF,
          label = label
        )
        val json = documentRepresentation
          .toJsonLd(
            className = className,
            ontologyName = ontologyName,
            shortcode = shortcode,
            ontologyIRI = ontologyIRI
          )
          .parseJson
        val expectedJSON = Map(
          "@type" -> s"$ontologyName:${className.get}".toJson,
          "knora-api:hasDocumentFileValue" -> Map(
            "@type"                          -> "knora-api:DocumentFileValue",
            "knora-api:fileValueHasFilename" -> fileNamePDF
          ).toJson,
          "knora-api:attachedToProject" -> Map(
            "@id" -> s"http://rdfh.ch/projects/$shortcode"
          ).toJson,
          "rdfs:label" -> label.toJson,
          "@context" -> Map(
            "rdf"        -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "knora-api"  -> "http://api.knora.org/ontology/knora-api/v2#",
            "rdfs"       -> "http://www.w3.org/2000/01/rdf-schema#",
            "xsd"        -> "http://www.w3.org/2001/XMLSchema#",
            ontologyName -> ontologyIRI.get
          ).toJson
        ).toJson
        json should equal(expectedJSON)
      }

    }

    "generating a message representation of a UploadFileRequest," should {

      "correctly serialize a DocumentRepresentation with default values" in {
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF
        )
        val msg = documentRepresentation.toMessage()

        msg.resourceClassIri should equal(FileModelUtil.getFileRepresentationClassIri(FileType.DocumentFile()))
        msg.label should equal("test label")
        msg.values should equal(
          Map(
            FileModelUtil.getFileRepresentationPropertyIri(FileType.DocumentFile()) -> List(
              CreateValueInNewResourceV2(
                valueContent = DocumentFileValueContentV2(
                  ontologySchema = ApiV2Complex,
                  fileValue = FileValueV2(
                    internalFilename = fileNamePDF,
                    internalMimeType = "application/pdf",
                    originalFilename = None,
                    originalMimeType = Some("application/pdf")
                  ),
                  pageCount = Some(1),
                  dimX = Some(100),
                  dimY = Some(100),
                  comment = None
                ),
                customValueIri = None,
                customValueUUID = None,
                customValueCreationDate = None,
                permissions = None
              )
            )
          )
        )
        msg.projectADM should equal(SharedTestDataADM.anythingProject)
        msg.permissions should equal(None)
      }

      "correctly serialize a DocumentRepresentation with custom values" in {
        val pageCount                  = None
        val dimX                       = Some(20)
        val dimY                       = None
        val project                    = SharedTestDataADM.beolProject
        val shortcode                  = project.shortcode
        val label                      = "a custom label"
        val resourceIRI                = stringFormatter.makeRandomResourceIri(shortcode)
        val comment                    = Some("This is a custom comment")
        val internalMimetype           = Some("application/msword")
        val originalFilename           = Some("document-file.docm")
        val originalMimeType           = Some("application/vnd.ms-word.document.macroEnabled.12")
        val customValueIRI             = Some(stringFormatter.makeRandomResourceIri(shortcode).toSmartIri)
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
          label = label
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
          project = Some(project)
        )

        msg.resourceIri should equal(Some(resourceIRI.toSmartIri))
        msg.label should equal(label)
        msg.permissions should equal(resourcePermissions)
        msg.projectADM should equal(project)
        msg.creationDate should equal(customResourceCreationDate)
        msg.resourceClassIri should equal(resourceClassIRI)
        msg.values should equal(
          Map(
            valuePropertyIRI -> List(
              CreateValueInNewResourceV2(
                valueContent = DocumentFileValueContentV2(
                  ontologySchema = ApiV2Complex,
                  fileValue = FileValueV2(
                    internalFilename = fileNamePDF,
                    internalMimeType = internalMimetype.get,
                    originalFilename = originalFilename,
                    originalMimeType = originalMimeType
                  ),
                  pageCount = pageCount,
                  dimX = dimX,
                  dimY = dimY,
                  comment = comment
                ),
                customValueIri = customValueIRI,
                customValueUUID = customValueUUID,
                customValueCreationDate = customValueCreationDate,
                permissions = valuePermissions
              )
            )
          )
        )
      }

    }

    "creating a ChangeFileRequest," should {

      "create a valid representation of a DocumentRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI    = stringFormatter.makeRandomResourceIri("0000")

        val change = ChangeFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        change.fileType match {
          case FileType.DocumentFile(pg, x, y) =>
            pg should equal(Some(1))
            x should equal(Some(100))
            y should equal(Some(100))
          case _ => throw AssertionException(s"FileType ${change.fileType} did not match DocumentFile(_, _, _)")
        }
        change.internalFilename should equal(fileNamePDF)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal("DocumentRepresentation")
        change.ontologyName should equal("knora-api")
      }

      "create a valid representation of a DocumentRepresentation with custom values" in {
        val resourceIRI  = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI     = stringFormatter.makeRandomResourceIri("0000")
        val pageCount    = Some(33)
        val dimX         = Some(44)
        val dimY         = Some(55)
        val className    = "CustomDocumentResource"
        val ontologyName = "anything"

        val change = ChangeFileRequest.make(
          fileType = FileType.DocumentFile(
            pageCount = pageCount,
            dimX = dimX,
            dimY = dimY
          ),
          internalFilename = fileNamePDF,
          resourceIri = resourceIRI,
          valueIri = valueIRI,
          className = Some(className),
          ontologyName = ontologyName
        )
        change.fileType match {
          case FileType.DocumentFile(pg, x, y) =>
            pg should equal(pageCount)
            x should equal(dimX)
            y should equal(dimY)
          case _ => throw AssertionException(s"FileType ${change.fileType} did not match DocumentFile(_, _, _)")
        }
        change.internalFilename should equal(fileNamePDF)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal(className)
        change.ontologyName should equal(ontologyName)
      }

      "create a valid representation of a StillImageRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI    = stringFormatter.makeRandomResourceIri("0000")

        val change = ChangeFileRequest.make(
          fileType = FileType.StillImageFile(),
          internalFilename = fileNameImage,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        change.fileType match {
          case FileType.StillImageFile(x, y) =>
            x should equal(100)
            y should equal(100)
          case _ => throw AssertionException(s"FileType ${change.fileType} did not match StillImageFile(_, _)")
        }
        change.internalFilename should equal(fileNameImage)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal("StillImageRepresentation")
        change.ontologyName should equal("knora-api")
      }

      "create a valid representation of a MovingImageRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI    = stringFormatter.makeRandomResourceIri("0000")

        val change = ChangeFileRequest.make(
          fileType = FileType.MovingImageFile(),
          internalFilename = fileNameVideo,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        change.fileType match {
          case FileType.MovingImageFile(x, y) =>
            x should equal(100)
            y should equal(100)
          case _ => throw AssertionException(s"FileType ${change.fileType} did not match MovingImageFile(_, _)")
        }
        change.internalFilename should equal(fileNameVideo)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal("MovingImageRepresentation")
        change.ontologyName should equal("knora-api")
      }

      "create a valid representation of a AudioRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI    = stringFormatter.makeRandomResourceIri("0000")

        val change = ChangeFileRequest.make(
          fileType = FileType.AudioFile,
          internalFilename = fileNameAudio,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        change.fileType should equal(FileType.AudioFile)
        change.internalFilename should equal(fileNameAudio)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal("AudioRepresentation")
        change.ontologyName should equal("knora-api")
      }

      "create a valid representation of a TextRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI    = stringFormatter.makeRandomResourceIri("0000")

        val change = ChangeFileRequest.make(
          fileType = FileType.TextFile,
          internalFilename = fileNameText,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        change.fileType should equal(FileType.TextFile)
        change.internalFilename should equal(fileNameText)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal("TextRepresentation")
        change.ontologyName should equal("knora-api")
      }

      "create a valid representation of a ArchiveRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("0000")
        val valueIRI    = stringFormatter.makeRandomResourceIri("0000")

        val change = ChangeFileRequest.make(
          fileType = FileType.ArchiveFile,
          internalFilename = fileNameArchive,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        change.fileType should equal(FileType.ArchiveFile)
        change.internalFilename should equal(fileNameArchive)
        change.valueIRI should equal(valueIRI)
        change.resourceIRI should equal(resourceIRI)
        change.className should equal("ArchiveRepresentation")
        change.ontologyName should equal("knora-api")
      }

    }

    "generating a JSON-LD representation of a ChangeFileRequest," should {

      "correctly serialize a DocumentRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("7777")
        val valueIRI    = stringFormatter.makeRandomResourceIri("7777")

        val documentRepresentation = ChangeFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        val json = documentRepresentation.toJsonLd.parseJson
        val expectedJSON = Map(
          "@id"   -> resourceIRI.toJson,
          "@type" -> "knora-api:DocumentRepresentation".toJson,
          "knora-api:hasDocumentFileValue" -> Map(
            "@id"                            -> valueIRI,
            "@type"                          -> "knora-api:DocumentFileValue",
            "knora-api:fileValueHasFilename" -> fileNamePDF
          ).toJson,
          "@context" -> Map(
            "rdf"       -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
            "rdfs"      -> "http://www.w3.org/2000/01/rdf-schema#",
            "xsd"       -> "http://www.w3.org/2001/XMLSchema#"
          ).toJson
        ).toJson
        json should equal(expectedJSON)
      }

      "correctly serialize a DocumentRepresentation with custom values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("7777")
        val valueIRI    = stringFormatter.makeRandomResourceIri("7777")
        val className   = "CustomDocumentRepresentation"
        val prefix      = "onto"

        val documentRepresentation = ChangeFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF,
          resourceIri = resourceIRI,
          valueIri = valueIRI,
          className = Some(className),
          ontologyName = prefix
        )
        val json = documentRepresentation.toJsonLd.parseJson
        val expectedJSON = Map(
          "@id"   -> resourceIRI.toJson,
          "@type" -> s"$prefix:$className".toJson,
          "knora-api:hasDocumentFileValue" -> Map(
            "@id"                            -> valueIRI,
            "@type"                          -> "knora-api:DocumentFileValue",
            "knora-api:fileValueHasFilename" -> fileNamePDF
          ).toJson,
          "@context" -> Map(
            "rdf"       -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
            "rdfs"      -> "http://www.w3.org/2000/01/rdf-schema#",
            "xsd"       -> "http://www.w3.org/2001/XMLSchema#"
          ).toJson
        ).toJson
        json should equal(expectedJSON)
      }
    }

    "generating a message representation of a ChangeFileRequest," should {

      "correctly serialize a DocumentRepresentation with default values" in {
        val resourceIRI = stringFormatter.makeRandomResourceIri("7777")
        val valueIRI    = stringFormatter.makeRandomResourceIri("7777")

        val documentRepresentation = ChangeFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileNamePDF,
          resourceIri = resourceIRI,
          valueIri = valueIRI
        )
        val msg = documentRepresentation.toMessage()
        msg.updateValue should equal(
          UpdateValueContentV2(
            resourceIri = resourceIRI,
            resourceClassIri = "http://api.knora.org/ontology/knora-api/v2#DocumentFileValue".toSmartIri,
            propertyIri = "http://api.knora.org/ontology/knora-api/v2#hasDocumentFileValue".toSmartIri,
            valueIri = valueIRI,
            valueContent = FileModelUtil.getFileValueContent(
              fileType = FileType.DocumentFile(),
              internalFilename = fileNamePDF,
              internalMimeType = Some("application/pdf"),
              originalFilename = None,
              originalMimeType = Some("application/pdf"),
              comment = None
            ),
            permissions = None,
            valueCreationDate = None,
            newValueVersionIri = None
          )
        )
      }

      "correctly serialize a DocumentRepresentation with custom values" in {
        val resourceIRI             = stringFormatter.makeRandomResourceIri("7777")
        val valueIRI                = stringFormatter.makeRandomResourceIri("7777")
        val className               = "CustomDocumentRepresentation"
        val prefix                  = "onto"
        val fileName                = "wordfile.docx"
        val internalMimeType        = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val originalFileName        = "original-wordfile.doc"
        val originalMimetype        = "application/msword"
        val customComment           = "a comment on this word file"
        val valuePermission         = Some("V knora-admin:UnknownUser,knora-admin:KnownUser|M knora-admin:ProjectMember")
        val customValueCreationDate = Some(Instant.now())
        val customValueVersionIRI   = Some(stringFormatter.makeRandomResourceIri("7777").toSmartIri)
        val resourceClassIRI        = "http://www.knora.org/ontology/0801/biblio#Book".toSmartIri

        val documentRepresentation = ChangeFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = fileName,
          resourceIri = resourceIRI,
          valueIri = valueIRI,
          className = Some(className),
          ontologyName = prefix
        )
        val msg = documentRepresentation.toMessage(
          internalMimeType = Some(internalMimeType),
          originalFilename = Some(originalFileName),
          originalMimeType = Some(originalMimetype),
          comment = Some(customComment),
          permissions = valuePermission,
          valueCreationDate = customValueCreationDate,
          newValueVersionIri = customValueVersionIRI,
          resourceClassIRI = Some(resourceClassIRI)
        )
        println(msg)
        msg.updateValue should equal(
          UpdateValueContentV2(
            resourceIri = resourceIRI,
            resourceClassIri = resourceClassIRI,
            propertyIri = "http://api.knora.org/ontology/knora-api/v2#hasDocumentFileValue".toSmartIri,
            valueIri = valueIRI,
            valueContent = FileModelUtil.getFileValueContent(
              fileType = FileType.DocumentFile(),
              internalFilename = fileName,
              internalMimeType = Some(internalMimeType),
              originalFilename = Some(originalFileName),
              originalMimeType = Some(originalMimetype),
              comment = Some(customComment)
            ),
            permissions = valuePermission,
            valueCreationDate = customValueCreationDate,
            newValueVersionIri = customValueVersionIRI
          )
        )
      }
    }
  }
}
