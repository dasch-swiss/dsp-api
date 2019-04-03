package org.knora.webapi.util.clientapi

import java.io.File
import java.net.URLEncoder
import java.nio.file.Paths

import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils
import org.knora.webapi.ClientApiGenerationException
import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassInfoContentV2, InputOntologyV2, PropertyInfoContentV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.util.{FileUtil, SmartIri, StringFormatter}
import org.rogach.scallop._

import scala.util.Try

/**
  * A command-line program for generating client API class definitions based on RDF class definitions served by Knora.
  */
object GenerateClientClass {
    def main(args: Array[String]) {
        // Get the command-line arguments.
        val conf = new GenerateClientClassConf(args)

        // Initialise StringFormatter.
        StringFormatter.initForClient(knoraHostAndPort = s"${conf.host()}:${conf.port()}")
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Parse the requested class IRI.
        val classIri: SmartIri = conf.classIri().toSmartIriWithErr(throw ClientApiGenerationException(s"Invalid class IRI: ${conf.classIri()}"))

        // Generate the client class.
        val clientClassGenerator = new ClientClassGenerator(conf)
        val clientClassSourceCodeTry: Try[ClientClassSourceCode] = Try(clientClassGenerator.generateClientClass(classIri))
        val clientClassSourceCode: ClientClassSourceCode = clientClassSourceCodeTry.get

        // Save the client class to a file.
        val outputFile: File = Paths.get(conf.outputDir(), clientClassSourceCode.filename).toFile
        FileUtil.writeTextFile(file = outputFile, content = clientClassSourceCode.sourceCode)
    }
}

/**
  * Parses command-line arguments.
  */
class GenerateClientClassConf(arguments: Seq[String]) extends ScallopConf(arguments) {
    val host: ScallopOption[String] = opt[String](default = Some("0.0.0.0"))
    val port: ScallopOption[Int] = opt[Int](default = Some(3333))
    val useHttps: ScallopOption[Boolean] = opt[Boolean]()
    val classIri: ScallopOption[String] = opt[String](required = true)
    val target: ScallopOption[String] = opt[String](required = true)
    val outputDir: ScallopOption[String] = opt[String](default = Some("."))
    verify()
}

/**
  * The names of the available code generation targets.
  */
object TargetNames {
    val JsonTypeScript = "JsonTypeScript"
}

/**
  * Generates client API source code based on RDF class definitions served by Knora.
  *
  * @param conf            the command-line options.
  */
class ClientClassGenerator(conf: GenerateClientClassConf) {
    private val httpClient: HttpClient = HttpClients.createDefault
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Generates client API source code representing the given RDF class.
      *
      * @param classIri the IRI of the class.
      * @return client API source code representing the class.
      */
    def generateClientClass(classIri: SmartIri): ClientClassSourceCode = {
        val clientClassDef: ClientClassDefinition = makeClientClassDef(classIri)
        val generatorBackEnd: GeneratorBackEnd = getGeneratorBackEnd(conf.target())
        generatorBackEnd.generateClientClassSourceCode(clientClassDef)
    }

    /**
      * Gets a [[GeneratorBackEnd]] for the specified target.
      *
      * @param target the name of the target for which source code is to be generated (e.g. the name
      *               of the programming language).
      * @return a [[GeneratorBackEnd]] for the specified target.
      */
    private def getGeneratorBackEnd(target: String): GeneratorBackEnd = {
        target match {
            case TargetNames.JsonTypeScript => new JsonTypeScriptBackEnd
            case other => throw ClientApiGenerationException(s"Target $other not found")
        }
    }

    /**
      * Generates a client API class definition for the specified RDF class.
      *
      * @param classIri the IRI of the class.
      * @return a [[ClientClassDefinition]] representing the class.
      */
    private def makeClientClassDef(classIri: SmartIri): ClientClassDefinition = {
        val rdfClassDef = getRdfClassDef(classIri)
        val rdfPropertyDefs = getRdfPropertyDefs(rdfClassDef)
        val generatorFrontEnd = new GeneratorFrontEnd
        generatorFrontEnd.rdfClassDef2ClientClassDef(rdfClassDef, rdfPropertyDefs)
    }

    /**
      * Gets an RDF class definition representing the specified class.
      *
      * @param classIri the IRI of the class.
      * @return a [[ClassInfoContentV2]] representing the class.
      */
    private def getRdfClassDef(classIri: SmartIri): ClassInfoContentV2 = {
        val classApiPath = s"/v2/ontologies/classes/${URLEncoder.encode(classIri.toString, "UTF-8")}"
        val classOntology: InputOntologyV2 = getKnoraOntologyResponse(classApiPath)
        classOntology.classes.getOrElse(classIri, throw ClientApiGenerationException(s"Class <$classIri> not found"))
    }

    /**
      * Gets RDF class definitions representing the Knora properties that have cardinalities in the specified class.
      *
      * @param rdfClassDef an RDF class definition.
      * @return a map of property IRIs to RDF property definitions.
      */
    private def getRdfPropertyDefs(rdfClassDef: ClassInfoContentV2): Map[SmartIri, PropertyInfoContentV2] = {
        val rdfPropertyIris: Set[SmartIri] = rdfClassDef.directCardinalities.keySet.filter {
            propertyIri => propertyIri.isKnoraEntityIri
        }

        val ontologyIrisForKnoraProperties: Set[SmartIri] = rdfPropertyIris.map(_.getOntologyFromEntity)

        val ontologiesWithKnoraProperties: Map[SmartIri, InputOntologyV2] = ontologyIrisForKnoraProperties.map {
            ontologyIri =>
                val ontologyApiPath = s"/v2/ontologies/allentities/${URLEncoder.encode(ontologyIri.toString, "UTF-8")}"
                val inputOntology = getKnoraOntologyResponse(ontologyApiPath)
                ontologyIri -> inputOntology
        }.toMap

        rdfPropertyIris.map {
            propertyIri =>
                val ontology = ontologiesWithKnoraProperties(propertyIri.getOntologyFromEntity)
                propertyIri -> ontology.properties.getOrElse(propertyIri, throw ClientApiGenerationException(s"Property <$propertyIri> not found"))
        }.toMap
    }

    /**
      * Makes a GET request to Knora and returns the response as an [[InputOntologyV2]].
      *
      * @param apiPath the Knora API path to be used in the request.
      * @return an [[InputOntologyV2]] representing the response.
      */
    private def getKnoraOntologyResponse(apiPath: String): InputOntologyV2 = {
        val schema = if (conf.useHttps()) "https" else "http"
        val uri = s"$schema://${conf.host()}:${conf.port()}/$apiPath"
        val httpGet = new HttpGet(uri)
        val response = httpClient.execute(httpGet)

        val responseStrTry: Try[String] = Try {
            val statusCode = response.getStatusLine.getStatusCode

            if (statusCode / 100 != 2) {
                throw ClientApiGenerationException(s"Knora responded with error $statusCode: ${response.getStatusLine.getReasonPhrase}")
            }

            Option(response.getEntity) match {
                case Some(entity) => EntityUtils.toString(entity)
                case None => throw ClientApiGenerationException(s"Knora returned an empty response.")
            }
        }

        val responseStr: String = responseStrTry.get
        InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(responseStr))
    }

}
