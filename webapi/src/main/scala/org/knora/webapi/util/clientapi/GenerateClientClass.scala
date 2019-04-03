package org.knora.webapi.util.clientapi

import java.net.URLEncoder

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils
import org.knora.webapi.ClientApiGenerationException
import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassInfoContentV2, InputOntologyV2, PropertyInfoContentV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.rogach.scallop._

import scala.util.Try

object GenerateClientClass {
    def main(args: Array[String]) {
        val conf = new GenerateClientClassConf(args)
        val httpClient: CloseableHttpClient = HttpClients.createDefault
        StringFormatter.initForClient(s"${conf.host()}:${conf.port()}")
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val classIri: SmartIri = conf.classIri().toSmartIriWithErr(throw ClientApiGenerationException(s"Invalid class IRI: ${conf.classIri()}"))

        val classApiPath = s"/v2/ontologies/classes/${URLEncoder.encode(classIri.toString, "UTF-8")}"
        val classOntology: InputOntologyV2 = getKnoraOntologyResponse(httpClient = httpClient, apiPath = classApiPath, conf = conf)
        val ontologyClassDef: ClassInfoContentV2 = classOntology.classes.getOrElse(classIri, throw ClientApiGenerationException(s"Class <$classIri> not found"))

        val ontologyKnoraPropertyIris: Set[SmartIri] = ontologyClassDef.directCardinalities.keySet.filter {
            propertyIri => propertyIri.isKnoraEntityIri
        }

        val ontologyIrisForKnoraProperties: Set[SmartIri] = ontologyKnoraPropertyIris.map(_.getOntologyFromEntity)

        val ontologiesWithKnoraProperties: Map[SmartIri, InputOntologyV2] = ontologyIrisForKnoraProperties.map {
            ontologyIri =>
                val ontologyApiPath = s"/v2/ontologies/allentities/${URLEncoder.encode(ontologyIri.toString, "UTF-8")}"
                val inputOntology = getKnoraOntologyResponse(httpClient = httpClient, apiPath = ontologyApiPath, conf = conf)
                ontologyIri -> inputOntology
        }.toMap

        val ontologyKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologyKnoraPropertyIris.map {
            propertyIri =>
                val ontology = ontologiesWithKnoraProperties(propertyIri.getOntologyFromEntity)
                propertyIri -> ontology.properties.getOrElse(propertyIri, throw ClientApiGenerationException(s"Property <$propertyIri> not found"))
        }.toMap

        val generatorFrontEnd = new GeneratorFrontEnd
        val clientClassDef: ClientClassDefinition = generatorFrontEnd.ontologyClassDef2ClientClassDef(ontologyClassDef, ontologyKnoraPropertyDefs)



    }


    private def getKnoraOntologyResponse(httpClient: CloseableHttpClient, apiPath: String, conf: GenerateClientClassConf): InputOntologyV2 = {
        val schema = if (conf.useHttps()) "https" else "http"
        val httpGet = new HttpGet(s"$schema//${conf.host()}:${conf.port()}/$apiPath")
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

        response.close()
        httpClient.close()

        val responseStr: String = responseStrTry.get
        InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(responseStr))
    }

    /**
      * Parses command-line arguments.
      */
    private class GenerateClientClassConf(arguments: Seq[String]) extends ScallopConf(arguments) {
        val host: ScallopOption[String] = opt[String](default = Some("0.0.0.0"))
        val port: ScallopOption[Int] = opt[Int](default = Some(3333))
        val useHttps: ScallopOption[Boolean] = opt[Boolean]()
        val classIri: ScallopOption[String] = opt[String](required = true)
        verify()
    }

}
