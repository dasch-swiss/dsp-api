package org.knora.webapi.models.filemodels

import org.knora.webapi.CoreSpec

class FileModelsSpec extends CoreSpec {
  "FileModelsUtil" when {

    "creating a JSON-LD context" should {

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

      "handle non-specified ontology correctly" in {
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
    }
  }
  "FileModels" when {
    "creating an UploadFileRequest" should {
      "create a valid representation of a DocumentRepresentation" in {
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile,
          internalFilename = "document-file.pdf"
        )
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.internalFilename should equal("document-file.pdf")
        documentRepresentation.internalMimeType should equal(None)
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.fileType should equal(FileType.DocumentFile)
      }
    }
  }
}
