/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import java.io.File
import java.net.URLEncoder
import java.nio.file.{Files, Paths}

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpEntity, _}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.resourcemessages.{CreateResourceApiRequestV1, CreateResourceValueV1}
import org.knora.webapi.messages.v1.responder.valuemessages.CreateRichtextV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.{FileWriteException, IRI, ITSpec, InvalidApiJsonException}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


object SipiV1ITSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing sipi integration. A running SIPI server is needed!
  */
class SipiV1ITSpec extends ITSpec(SipiV1ITSpec.config) with TriplestoreJsonProtocol {

    private val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything"),
        RdfDataObject(path = "_test_data/ontologies/beol-onto.ttl", name = "http://www.knora.org/ontology/beol"),
        RdfDataObject(path = "_test_data/all_data/beol-data.ttl", name = "http://www.knora.org/data/beol")
    )

    private val rootUser = "root"
    private val anythingUser = "anything-user"
    private val password = "test"

    "Check if SIPI is running" in {
        // Contact the SIPI fileserver to see if Sipi is running
        // Plase make sure that 1) fileserver.docroot is set in config file and 2) it contains a file test.html
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request, 5.second)
        assert(response.status == StatusCodes.OK, s"SIPI is probably not running! ${response.status}")
    }

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    object ResponseUtils {

        def getStringMemberFromResponse(response: HttpResponse, memberName: String): IRI = {

            // get the specified string member of the response
            val resIdFuture: Future[String] = response.entity.toStrict(5.seconds).map {
                responseBody =>
                    val resBodyAsString = responseBody.data.decodeString("UTF-8")
                    resBodyAsString.parseJson.asJsObject.fields.get(memberName) match {
                        case Some(JsString(entitiyId)) => entitiyId
                        case None => throw InvalidApiJsonException(s"The response does not contain a field called '$memberName'")
                        case other => throw InvalidApiJsonException(s"The response does not contain a field '$memberName' of type JsString, but ${other}")
                    }
            }

            // wait for the Future to complete
            Await.result(resIdFuture, 5.seconds)

        }

    }

    object RequestParams {

        val createResourceParams = CreateResourceApiRequestV1(
            restype_id = "http://www.knora.org/ontology/incunabula#page",
            properties = Map(
                "http://www.knora.org/ontology/incunabula#pagenum" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = "test_page",
                        textattr = None,
                        resource_reference = None
                    ))
                )),
                "http://www.knora.org/ontology/incunabula#origname" -> Seq(CreateResourceValueV1(
                    richtext_value = Some(CreateRichtextV1(
                        utf8str = "test",
                        textattr = None,
                        resource_reference = None
                    ))
                )),
                "http://www.knora.org/ontology/incunabula#partOf" -> Seq(CreateResourceValueV1(
                    link_value = Some("http://data.knora.org/5e77e98d2603")
                )),
                "http://www.knora.org/ontology/incunabula#seqnum" -> Seq(CreateResourceValueV1(
                    int_value = Some(999)
                ))
            ),
            label = "test",
            project_id = "http://data.knora.org/projects/77275339"
        )

        val pathToFile = "_test_data/test_route/images/Chlaus.jpg"

        def createTmpFileDir() = {
            // check if tmp datadir exists and create it if not
            if (!Files.exists(Paths.get(settings.tmpDataDir))) {
                try {
                    val tmpDir = new File(settings.tmpDataDir)
                    tmpDir.mkdir()
                } catch {
                    case e: Throwable => throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
                }
            }
        }

        // a mapping from XML to standoff entities
        val mappingXML =
            """<?xml version="1.0" encoding="UTF-8"?>
              |<mapping xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="mapping.xsd">
              |  <mappingElement>
              |    <tag><name>text</name><namespace>noNamespace</namespace></tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffRootTag</classIri>
              |      <attributes>
              |        <attribute><attributeName>documentType</attributeName><namespace>noNamespace</namespace><propertyIri>http://www.knora.org/ontology/knora-base#standoffRootTagHasDocumentType</propertyIri></attribute>
              |      </attributes>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>p</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffParagraphTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>i</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffItalicTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>b</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/knora-base#StandoffBoldTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>facsimile</name>
              |      <namespace>noNamespace</namespace></tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/beol#StandoffFacsimileTag</classIri>
              |      <datatype><type>http://www.knora.org/ontology/knora-base#StandoffUriTag</type>
              |        <attributeName>src</attributeName>
              |      </datatype>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>math</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/beol#StandoffMathTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |  <mappingElement>
              |    <tag>
              |      <name>ref</name>
              |      <namespace>noNamespace</namespace>
              |    </tag>
              |    <standoffClass>
              |      <classIri>http://www.knora.org/ontology/beol#StandoffReferenceTag</classIri>
              |    </standoffClass>
              |  </mappingElement>
              |
              |</mapping>
            """.stripMargin


            val letterXML =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<text documentType="letter">
                  |      <p>
                  |         <facsimile src="http://localhost/noFacsimile.jpg"/> Vir Clarissime et Celeberrime Fautor Honoratissime </p>
                  |      <p>Ecce mitto ut promiseram enodationem difficultatis paulo ante discessum meum a Te motae contra infinitorum methodum. Vix posueram pedem in Scapham Hagam<ref>[Text folgt]</ref> petens cum missis distractionibus, quibus Tecum colloquens adhucdum detinebar, anguem in herba latentem detegerem, videremque in eo laborare objectionem Tuam quod quantitatem aliquam ad quam non attendisti tanquam nihil neglexeris cum tamen revera non modo sit aliquid finiti sed ipsa prorsus infinita ut jam patebit ([Ita] praeter propter argumentabaris): <i>Sint </i>[Figur folgt]<ref> [Link folgt] </ref>
                  |         <i> </i>
                  |         <math>AG</math>
                  |         <i>, </i>
                  |         <math>AM</math>
                  |         <i> asymtoti hyperboles </i>
                  |         <math>FCL</math>
                  |         <i> cujus natura (positis </i>
                  |         <math>AB=x</math>
                  |         <i>, </i>
                  |         <math>BC=y</math>
                  |         <i>) exprimitur per hanc aequationem </i>
                  |         <math>xxy=a^{3}</math>
                  |         <i>; Constat subtangentem </i>
                  |         <math>BO</math>
                  |         <i> esse </i>
                  |         <math>\frac{1}{2}AB</math>
                  |         <i>: Ergo si </i>
                  |         <math>CN</math>
                  |         <i> parallela ipsi </i>
                  |         <math>AB</math>
                  |         <i> producatur ad </i>
                  |         <math>E</math>
                  |         <i> ita ut </i>
                  |         <math>NE</math>
                  |         <i> sit </i>
                  |         <math>=BO</math>
                  |         <i> seu dimidiae </i>
                  |         <math>AB</math>
                  |         <i>, idemque si fiat ubique generabitur nova hyperbola </i>
                  |         <math>IQE</math>
                  |         <i> cujus areae elementum </i>
                  |         <math>En</math>
                  |         <i> erit aequale prioris elemento correspondenti</i>
                  |         <math>Cb</math>
                  |         <i>; unde elementis in summam collectis erit area quaevis </i>
                  |         <math>NRQE</math>
                  |         <i>, aequalis areae correspondenti</i>
                  |         <math>SBCP</math>: optime! hoc omnes concedent: <i>Ast cum </i>
                  |         <math>AS</math>
                  |         <i> sit arbitraria</i> (porro inferebas) <i>ubicunque enim sit punctum </i>
                  |         <math>S</math>
                  |         <i> semper erit </i>
                  |         <math>SBCP=NRQE</math>
                  |         <i>, poterimus ponere </i>
                  |         <math>AS=0</math>
                  |         <i>, unde sequitur totum spatium asymtoticum in infinitum extensum </i>
                  |         <math>GABCF</math>
                  |         <i> aequale fore toti alteri spatio asymtotico pariter in infinitum extenso</i>
                  |         <math>GNEI</math>
                  |         <i>; interim cum ubique </i>
                  |         <math>NC</math>
                  |         <i>, </i>
                  |         <math>RP</math>
                  |         <i> etc. sint duplae ipsarum </i>
                  |         <math>NE</math>
                  |         <i>, </i>
                  |         <math>RQ</math>
                  |         <i> etc. adeoque et ipsum spatium </i>
                  |         <math>GNCF</math>
                  |         <i> sit duplum spatii </i>
                  |         <math>GNEI</math>
                  |         <i>, erit potiori jure sp. </i>
                  |         <math>GABCF</math>
                  |         <i> saltem Duplum spatii </i>
                  |         <math>GNEI</math>
                  |         <i> ac proinde haec duo spatia non possunt esse aequalia, contra prius ratiocinium, quomodo agitur hac concilianda?</i> hic ni fallor est sensus objectionis Tuae; ad quam ut breviter respondeam, velim consideres, <math>AS</math> nunquam posse assumi absolute <math>=0</math>, nam punctum <math>P</math>
                  |         <facsimile src="http://localhost/noFacsimile.jpg"/> semper existere debet in hyperbola nunquam vero in asymtoto <math>AG</math>; et quamvis in infinitum intelligatur removeri a puncto <math>C</math> ita ut ad asymtoton data quavis assignabili propius accedat, distantia tamen in ipso infinito non omnino evanescet sed erit <i>aliquid</i> licet infinite exigua: Hocque clarum est ex eo quod solidum [sub]<math>PS</math> et <math>ASq.</math> constanti cubo <math>a^{3}</math> aequari debet; id vero fieri non posset nisi utraque tam <math>AS</math> quam <math>PS</math> esset aliquid reale, etenim ex non quanto seu ex absolute nihilo multiplicato per quantitatem licet infinitam, non potest produci aliquid. His bene intellectis, nego jam sequi ex priori ratiocinio spatium <math>GABCF</math> aequari spatio <math>GNEI</math>: quippe exinde nihil aliud concludi potest quam quod assumta <math>AK</math> infinite parva et ducta <math>KH</math> asymtoto parallela, fieri debeat spatium <math>GNEI</math> aequale spatio <math>HKBCF</math>: id quod minime absurdum est, nullamque contradictionem implicat quin potius probitatem calculi differentialis et integr. egregie confirmat; quoniam enim ex [postremo] ratiocinio spatium <math>GNCF</math> seu <math>GABCF</math> duplum est spatii <math>GNEI</math>, hoc vero ut modo ostensum aequale spatio <math>HKBCF</math>, sequitur <math>GABCF</math> duplum esse ipsius <math>HKBCF</math> ideoque <math>GAKH</math> (id est rectangulum sub abscissa infinite parva <math>AK</math> et applicata infinita <math>KH</math>) = spatio <math>HKBCF</math> = (addita quantitate finita <math>MBCL</math> ad infinitam <math>HKBCF</math>) <math>HKMLCF</math>; At generaliter verum est (notantibus id etiam jam pridem Robervallio, Cavallerio, Paschalio, Fermatio, Wallisio aliisque) quod per calculum integralium facillime invenitur, rectangulum scilicet sub abscissa <math>AS</math> et applicata <math>PS</math> aequari spatio hyperbolico <math>MSPCL</math>. Interim mirum Tibi videri non debet neve methodus differentialium ideo suspecta quod rectangulum <math>AKH</math> latitudinis infinite exiguae <math>AK</math> reperiatur aequale spatio infinito <math>HKMLCF</math>, siquidem hoc rectangulum revera infinitum esse non obstante quod habeat latitudinem infinite parvam patet ex ipsa aequatione ad hyperbolam <math>xxy=a^{3}</math>, quae resoluta in proportionem dat <math>x.a::aa.xy</math>, unde si <math>x</math> seu <math>AK</math> sit infinite parva id est infinities minor quam Determinata et finita <math>a</math>, erit pariter <math>aa</math> seu quadratum finitum infinities minus quam <math>xy</math> proindeque <math>xy</math> seu rectangulum <math>AKH</math> revera est infinitum. Haud aliter judicandum de omni alia hyperbola <math>x^{n}y=a^{n+1}</math>, quotiescunque enim <math>n</math> unitate major est, difficultas Tua semper occurrit, nempe quia tunc semper rectangulum <math>AKH</math> evadit infinitum et comparabile cum spatio <math>HKBCF</math>, adeoque minime negli<facsimile src="http://localhost/noFacsimile.jpg"/>gendum; sed contra quotiescunque <math>n</math> unitate minor vel eidem aequalis, tunc cessat objectio, quoniam scilicet rectangulum <math>AKH</math> nunc fit infinite parvum vel finitum et incomparabile spatio <math>HKBCF</math> adeoque tuto negligi potest. Unde vides et vel hoc nomine genuinam esse responsionem quam hic dedi ad difficultatem Tuam; non dubito quin sit Tibi satisfactura. Recolligam quae Leibnitium inter et me agitata fuere diu, circa aestimationem virium ex motu corporum deducendarum, eoque utut bene multa sint per amanuensem describi curabo, Tibique si optaveris ocyus transmittam, ut videre possis quid me tandem post longas contentiones permoverit ad transeundum<ref>Im Manuskript steht "transeundem". </ref> in illius partes non enim<ref>Der folgende Satzschluss befindet sich im Manuskript am Fuss der Seite.</ref> temere nec ut viri gratiam inirem in veteratam opinionem desereri. Hîc iterum gratias solvo singulares pro multis benevolentiae Tuae signis, quibus me cum apud vos agerem ultra meritum cumulasti. Vale et fave T. Deditissimo J. Bernoulli</p>
                  |      <p>Groningae die 27 Junii St. v. 1698</p></text>
                """.stripMargin

    }

    "The Resources Endpoint" should {

        "create an 'incunabula:page' with binary data" in {

            /* for live testing do:
             * inside sipi folder: ./local/bin/sipi -config config/sipi.knora-config.lua
             * inside webapi folder ./_test_data/test_route/create_page_with_binaries.py
             */

            val fileToSend = new File(RequestParams.pathToFile)
            // check if the file exists
            assert(fileToSend.exists(), s"File ${RequestParams.pathToFile} does not exist")

            val formData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, RequestParams.createResourceParams.toJsValue.compactPrint)
                ),
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/jpeg`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            RequestParams.createTmpFileDir()
            val request = Post(baseApiUrl + "/v1/resources", formData) ~> addCredentials(BasicHttpCredentials(rootUser, password))
            val response = singleAwaitingRequest(request, 20.seconds)

            assert(response.status === StatusCodes.OK)

            // wait for the Future to complete
            val newResourceIri: String = ResponseUtils.getStringMemberFromResponse(response, "res_id")

            val requestNewResource = Get(baseApiUrl + "/v1/resources/" + URLEncoder.encode(newResourceIri, "UTF-8")) ~> addCredentials(BasicHttpCredentials(rootUser, password))
            val responseNewResource = singleAwaitingRequest(requestNewResource, 20.seconds)

            assert(responseNewResource.status == StatusCodes.OK)

            val IIIFPathFuture: Future[String] = responseNewResource.entity.toStrict(5.seconds).map {
                newResponseBody =>
                    val newResBodyAsString = newResponseBody.data.decodeString("UTF-8")

                    newResBodyAsString.parseJson.asJsObject.fields.get("resinfo") match {

                        case Some(resinfo: JsObject) =>
                            resinfo.fields.get("locdata") match {
                                case Some(locdata: JsObject) =>
                                    locdata.fields.get("path") match {
                                        case Some(JsString(path)) => path
                                        case None => throw InvalidApiJsonException("no 'path' given")
                                        case other => throw InvalidApiJsonException("'path' could not pe parsed correctly")
                                    }
                                case None => throw InvalidApiJsonException("no 'locdata' given")

                                case other => throw InvalidApiJsonException("'locdata' could not pe parsed correctly")
                            }

                        case None => throw InvalidApiJsonException("no 'resinfo' given")

                        case other => throw InvalidApiJsonException("'resinfo' could not pe parsed correctly")
                    }

            }

            // wait for the Future to complete
            val IIIFPath = Await.result(IIIFPathFuture, 5.seconds)

            // TODO: now we could try to request the path from Sipi
            // TODO: we should run Sipi in test mode so it does not do run the preflight request

            //println(IIIFPath)

        }


        "create an 'incunabula:page' with parameters" in {


        }

        "change an 'incunabula:page' with binary data" in {}

        "change an 'incunabula:page' with parameters" in {}

        "create an 'anything:thing'" in {}

        "create a mapping resource for standoff conversion and do a standoff conversion for a text value" in {

            val anythingProjectIri = "http://data.knora.org/projects/anything"

            // send mapping xml to route
            val mappingRequest = Post(baseApiUrl + "/v1/mapping/" + URLEncoder.encode(anythingProjectIri, "UTF-8"), HttpEntity(ContentTypes.`text/xml(UTF-8)`, RequestParams.mappingXML)) ~> addCredentials(BasicHttpCredentials(anythingUser, password))
            val mappingResponse: HttpResponse = singleAwaitingRequest(mappingRequest, 20.seconds)

            assert(mappingResponse.status == StatusCodes.OK, "standoff mapping creation route returned a non successful HTTP status code")

            val mappingIRI: IRI = ResponseUtils.getStringMemberFromResponse(mappingResponse, "resourceIri")

            // create standoff from XML
            val standoffCreationRequest = Post(baseApiUrl + "/v1/standoff/"
                + URLEncoder.encode("http://data.knora.org/a-thing", "UTF-8") + "/"
                + URLEncoder.encode("http://www.knora.org/ontology/anything#hasText", "UTF-8") + "/"
                + URLEncoder.encode(anythingProjectIri, "UTF-8")
                + "/" + URLEncoder.encode(mappingIRI, "UTF-8"), HttpEntity(ContentTypes.`text/xml(UTF-8)`, RequestParams.letterXML)) ~> addCredentials(BasicHttpCredentials(anythingUser, password))


            val standoffCreationResponse: HttpResponse = singleAwaitingRequest(standoffCreationRequest, 20.seconds)

            assert(standoffCreationResponse.status == StatusCodes.OK, "standoff creation route returned a non successful HTTP status code: " + standoffCreationResponse.entity.toString)

            val standoffTextValueIRI: IRI = ResponseUtils.getStringMemberFromResponse(standoffCreationResponse, "id")

            // read back the standoff text value
            val standoffRequest = Get(baseApiUrl + "/v1/standoff/" + URLEncoder.encode(standoffTextValueIRI, "UTF-8")) ~> addCredentials(BasicHttpCredentials(anythingUser, password))
            val standoffResponse: HttpResponse = singleAwaitingRequest(standoffRequest, 20.seconds)

            assert(standoffResponse.status == StatusCodes.OK, "reading back standoff failed")

            val XMLString = ResponseUtils.getStringMemberFromResponse(standoffResponse, "xml")

            // TODO: compare XML response with XML that was originally sent


        }
    }
}
