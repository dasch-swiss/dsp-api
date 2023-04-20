package org.knora.webapi.messages

import zio.prelude.Validation
import scala.util.matching.Regex

import dsp.errors.NotFoundException
import dsp.errors.ValidationException
import org.knora.webapi.IRI
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagIriAttributeV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagV2

object XmlPatterns {
  // A regex sub-pattern for ontology prefix labels and local entity names. According to
  // <https://www.w3.org/TR/turtle/#prefixed-name>, a prefix label in Turtle must be a valid XML NCName
  // <https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName>. Knora also requires a local entity name to
  // be an XML NCName.
  val nCNamePattern: String = """[\p{L}_][\p{L}0-9_.-]*"""

  // A regex for matching a string containing only an ontology prefix label or a local entity name.
  val nCNameRegex: Regex = ("^" + nCNamePattern + "$").r
}

object StandoffStuff {

  // In XML import data, a standoff link tag that refers to a resource described in the import must have the
  // form defined by this regex.
  val standoffLinkReferenceToClientIdForResourceRegex: Regex = ("^ref:(" + XmlPatterns.nCNamePattern + ")$").r

  /**
   * Checks whether a string is a reference to a client's ID for a resource described in an XML bulk import.
   *
   * @param s the string to be checked.
   * @return `true` if the string is an XML NCName prefixed by `ref:`.
   */
  def isStandoffLinkReferenceToClientIDForResource(s: String): Boolean =
    s match {
      case standoffLinkReferenceToClientIdForResourceRegex(_) => true
      case _                                                  => false
    }

  /**
   * Checks that a string represents a valid resource identifier in a standoff link.
   *
   * @param s               the string to be checked.
   * @param acceptClientIDs if `true`, the function accepts either an IRI or an XML NCName prefixed by `ref:`.
   *                        The latter is used to refer to a client's ID for a resource that is described in an XML bulk import.
   *                        If `false`, only an IRI is accepted.
   * @param errorFun        a function that throws an exception. It will be called if the form of the string is invalid.
   * @return the same string.
   */
  def validateStandoffLinkResourceReference(s: String, acceptClientIDs: Boolean, errorFun: => Nothing): IRI =
    validateStandoffLinkResourceReference(s, acceptClientIDs).getOrElse(errorFun)

  def validateStandoffLinkResourceReference(s: String, acceptClientIDs: Boolean): Validation[ValidationException, IRI] =
    if (acceptClientIDs && StandoffStuff.isStandoffLinkReferenceToClientIDForResource(s)) Validation.succeed(s)
    else StringFormatter.validateAndEscapeIri(s)

  /**
   * Accepts a reference from a standoff link to a resource. The reference may be either a real resource IRI
   * (referring to a resource that already exists) or a client's ID for a resource that doesn't yet exist and is
   * described in an XML bulk import. Returns the real IRI of the target resource.
   *
   * @param iri                             an IRI from a standoff link, either in the form of a real resource IRI or in the form of
   *                                        a reference to a client's ID for a resource.
   * @param clientResourceIDsToResourceIris a map of client resource IDs to real resource IRIs.
   */
  def toRealStandoffLinkTargetResourceIri(iri: IRI, clientResourceIDsToResourceIris: Map[String, IRI]): IRI =
    iri match {
      case standoffLinkReferenceToClientIdForResourceRegex(clientResourceID) =>
        clientResourceIDsToResourceIris(clientResourceID)
      case _ => iri
    }

  /**
   * Map over all standoff tags to collect IRIs that are referred to by linking standoff tags.
   *
   * @param standoffTags The list of [[StandoffTagV2]].
   * @return a set of Iris referred to in the [[StandoffTagV2]].
   */
  @throws[NotFoundException]
  def getResourceIrisFromStandoffTags(standoffTags: Seq[StandoffTagV2]): Set[IRI] =
    standoffTags.foldLeft(Set.empty[IRI]) { case (acc: Set[IRI], standoffNode: StandoffTagV2) =>
      if (standoffNode.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag)) {
        val maybeTargetIri: Option[IRI] = standoffNode.attributes.collectFirst {
          case iriTagAttr: StandoffTagIriAttributeV2
              if iriTagAttr.standoffPropertyIri.toString == OntologyConstants.KnoraBase.StandoffTagHasLink =>
            iriTagAttr.value
        }

        acc + maybeTargetIri.getOrElse(throw NotFoundException(s"No link found in $standoffNode"))
      } else {
        acc
      }
    }
}
