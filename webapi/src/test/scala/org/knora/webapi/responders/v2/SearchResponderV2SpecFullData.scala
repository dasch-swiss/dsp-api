package org.knora.webapi.responders.v2

import java.time.Instant
import java.util.UUID

import akka.actor.ActorSystem
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM._
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class SearchResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

  implicit lazy val system: ActorSystem = ActorSystem("webapi")

  val fulltextSearchForNarr: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        label = "b4v",
        resourceIri = "http://rdfh.ch/resources/-2e6G_noRH-0BzzGkt7Qnw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:39Z"),
        resourceUUID = stringFormatter.decodeUuid("-2e6G_noRH-0BzzGkt7Qnw"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 8.\nHolzschnitt zu Kap. 8: Guten Rat verschmähen.\nEin Narr, dessen Linke mit einem Falknerhandschuh geschützt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm.\nUnkoloriert.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/-2e6G_noRH-0BzzGkt7Qnw/values/9ad344462626",
            valueHasUUID = stringFormatter.decodeUuid("9ad344462626"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:39Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "n7r",
        resourceIri = "http://rdfh.ch/resources/-4Hta14aSU2LjsMOvoe_lA",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:42Z"),
        resourceUUID = stringFormatter.decodeUuid("-4Hta14aSU2LjsMOvoe_lA"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 78.\nHolzschnitt zu Kap. 78: Von Narren, die sich selbst Bedrückung verschaffen.\nEin Narr, dem ein Esel auf den Rücken zu springen versucht, stürzt zu Boden.\n11.6 x 8.4 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"103\".")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/-4Hta14aSU2LjsMOvoe_lA/values/a15ed7017826",
            valueHasUUID = stringFormatter.decodeUuid("a15ed7017826"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:42Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "r8v",
        resourceIri = "http://rdfh.ch/resources/-EAaDSarSLqcgWBxIBTfUg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:46Z"),
        resourceUUID = stringFormatter.decodeUuid("-EAaDSarSLqcgWBxIBTfUg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 111.\nHolzschnitt zu Kap. 111: Entschuldigung des Dichters\nEin Narr hat seine Attribute – die Kappe und das Zepter - hinter sich abgelegt und kniet betend vor einem Altar. In seinen Händen hält er die Kappe des Gelehrten.  Von hinten nähert sich eine fünfköpfige Narrenschar und kommentiert das Geschehen mit erregten Gesten, 11.6 x 8.4 cm.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/-EAaDSarSLqcgWBxIBTfUg/values/22d399f78f26",
            valueHasUUID = stringFormatter.decodeUuid("22d399f78f26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:46Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "d7r",
        resourceIri = "http://rdfh.ch/resources/-xfmn8-4Qi6q0nCGeZvaBQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:48Z"),
        resourceUUID = stringFormatter.decodeUuid("-xfmn8-4Qi6q0nCGeZvaBQ"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 20.\nHolzschnitt zu Kap. 20: Vom Finden fremden Eigentums.\nEin Narr streckt seine Hände nach einem Schatz im Boden aus. Von Hinten tritt der Teufel in Gestalt eines Mischwesens heran und flüstert dem Narr mit einem Blasebalg einen Gedanken ein, 11.6 x 8.5 cm.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/-xfmn8-4Qi6q0nCGeZvaBQ/values/e097b50d3526",
            valueHasUUID = stringFormatter.decodeUuid("e097b50d3526"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:48Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "b5v",
        resourceIri = "http://rdfh.ch/resources/0-TZQXbJTleHBEM5PdscWw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:39Z"),
        resourceUUID = stringFormatter.decodeUuid("0-TZQXbJTleHBEM5PdscWw"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 9.\nHolzschnitt Lemmer 1979, S. 115: Variante für Kap. 9.\nEin junger, reich gekleideter Mann zieht seine Narrenkappe an einem Strick hinter sich her. Der Bildinhalt stimmt weitgehend mit dem ursprünglichen Holzschnitt überein: zusätzlich zur Kappe zieht der Narr hier auch ein Zepter hinter sich her.\n11.8 x 8.4 cm.\nUnkoloriert.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/0-TZQXbJTleHBEM5PdscWw/values/d58396742f26",
            valueHasUUID = stringFormatter.decodeUuid("d58396742f26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:39Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "p2v",
        resourceIri = "http://rdfh.ch/resources/08JxnmdMSdu_4DihTUbdAg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:50Z"),
        resourceUUID = stringFormatter.decodeUuid("08JxnmdMSdu_4DihTUbdAg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 101.\nHolzschnitt zu Kap. 101: Von der Ohrenbläserei\nVor einer Landschaftskulisse flüstert ein Narr, der ein Zepter in seiner Rechten hält, einem anderen Narr, der links neben ihm steht und sich begierig zur Seite neigt, etwas ins Ohr, 11.6 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/08JxnmdMSdu_4DihTUbdAg/values/aa4c9b028726",
            valueHasUUID = stringFormatter.decodeUuid("aa4c9b028726"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:50Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "e6v",
        resourceIri = "http://rdfh.ch/resources/0QwaQ4JKSTGwe_NCi6wihQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:40Z"),
        resourceUUID = stringFormatter.decodeUuid("0QwaQ4JKSTGwe_NCi6wihQ"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 30.\nHolzschnitt zu Kap. 30: Von Pfründenjägern.\nEin Narr lädt so viele Säcke auf seinen Esel, dass dieser unter der Last zusammenbricht.\n11.5 x 8.2 cm.\nUnkoloriert.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/0QwaQ4JKSTGwe_NCi6wihQ/values/ba7c74c53b26",
            valueHasUUID = stringFormatter.decodeUuid("ba7c74c53b26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:40Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "k3r",
        resourceIri = "http://rdfh.ch/resources/0zNyXyzNQFm-iamKjSkeYg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:49Z"),
        resourceUUID = stringFormatter.decodeUuid("0zNyXyzNQFm-iamKjSkeYg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 65.\nHolzschnitt zu Kap. 65: Von Astrologie und anderem Aberglauben.\nEin Narr, an dessen Seite ein Fuchsschwanz hängt, will einen Gelehrten überreden, auf die Gestirne und den Flug der Vögel zu achten. Dafür fasst er den Gelehrten bei der Schulter des Gelehrten und weist mit seiner Rechten zum Himmel.\n11.6 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/0zNyXyzNQFm-iamKjSkeYg/values/ee2787de6526",
            valueHasUUID = stringFormatter.decodeUuid("ee2787de6526"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:49Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "p7r",
        resourceIri = "http://rdfh.ch/resources/12nMo1fqSOWkDg2YRWnTlw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        resourceUUID = stringFormatter.decodeUuid("12nMo1fqSOWkDg2YRWnTlw"),
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:42Z"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 89.\nHolzschnitt zu Kap. 89: Von törichtem Tausch.\nEin Narr, der in einer Landschaft einem Mann begegnet, tauscht mit diesem sein Maultier gegen einen Dudelsack.\n11.7 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"119\"."
              )
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/12nMo1fqSOWkDg2YRWnTlw/values/c4c08f657f26",
            valueHasUUID = stringFormatter.decodeUuid("c4c08f657f26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:42Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "o2v",
        resourceIri = "http://rdfh.ch/resources/19lzzvPvRXymR-eSe8gAnQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:46Z"),
        resourceUUID = stringFormatter.decodeUuid("19lzzvPvRXymR-eSe8gAnQ"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, überreicht einem bärtigen Alten ein Geschenk, kratzt sich dabei aber unschlüssig am Kopf, 11.6 x 8.3 cm."
              )
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/19lzzvPvRXymR-eSe8gAnQ/values/00040fe08326",
            valueHasUUID = stringFormatter.decodeUuid("00040fe08326"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:46Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "k8r",
        resourceIri = "http://rdfh.ch/resources/1TLr1UJ-TB-6PLPfF4kwww",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:45Z"),
        resourceUUID = stringFormatter.decodeUuid("1TLr1UJ-TB-6PLPfF4kwww"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 70.\nHolzschnitt zu Kap. 70: Nicht Vorsorgen in der Zeit.\nEin ärmlich gekleideter Narr, der einen Strick mit sich trägt zieht umher. Rechts unten kauert ein Bär, der an seiner Pfote saugt. Hinten sammeln Insekten einen Futtervorrat.\n11.7 x 8.5 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/1TLr1UJ-TB-6PLPfF4kwww/values/651b58877326",
            valueHasUUID = stringFormatter.decodeUuid("651b58877326"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:45Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "c6r",
        resourceIri = "http://rdfh.ch/resources/1j4E5-PSR5Cnxe-B1Dmt9g",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:47Z"),
        resourceUUID = stringFormatter.decodeUuid("1j4E5-PSR5Cnxe-B1Dmt9g"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 11.\nHolzschnitt zu Kap. 11: Von Missachtung der Heiligen Schrift.\nEin Narr, der zwei Bücher mit Füssen tritt, spricht mit einem in ein Leichentuch gehüllten, wiedererweckten Toten, der auf seiner Bahre hockt.\n11.6 x 8.5 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/1j4E5-PSR5Cnxe-B1Dmt9g/values/21ba18052226",
            valueHasUUID = stringFormatter.decodeUuid("21ba18052226"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:47Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "b8r",
        resourceIri = "http://rdfh.ch/resources/2E9I0Q9xSzS-g2T8y_GMMg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:44Z"),
        resourceUUID = stringFormatter.decodeUuid("2E9I0Q9xSzS-g2T8y_GMMg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch über Karten und Würfen in Streit. Während der eine einen Dolch zückt und der andere nach seinem Schwert greift, sitzt ein älterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/2E9I0Q9xSzS-g2T8y_GMMg/values/b18eb0c42c26",
            valueHasUUID = stringFormatter.decodeUuid("b18eb0c42c26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:44Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "h8r",
        resourceIri = "http://rdfh.ch/resources/2aZq3UeaSPShCZpiv62H1A",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:48Z"),
        resourceUUID = stringFormatter.decodeUuid("2aZq3UeaSPShCZpiv62H1A"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 56.\nHolzschnitt zu Kap. 56: Alle Macht nimmt einmal ein Ende.\nHolzschnitt identisch mit Kap. 37: Die Hand Gottes treibt das Glücksrad an, auf dem ein Esel und zwei Mischwesen – halb Esel, halb Narr – herumgewirbelt werden. Der von der Abwärtsbewegung des Rades Mitgerissene stürzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/2aZq3UeaSPShCZpiv62H1A/values/e4a695915b26",
            valueHasUUID = stringFormatter.decodeUuid("e4a695915b26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:48Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "f3r",
        resourceIri = "http://rdfh.ch/resources/2mBnruqDRMWf6jeo1fb4EA",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:48Z"),
        resourceUUID = stringFormatter.decodeUuid("2mBnruqDRMWf6jeo1fb4EA"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 32.\nHolzschnitt zu Kap. 32: Vom Bewachen der Frauentugend.\nIm Vordergrund giesst ein Narr Wasser in einen Brunnen, ein zweiter reinigt Ziegelsteine. Dahinter steht ein dritter Narr, der eine Heuschreckenherde hütet. Er blickt zu einem Gebäude, aus dessen Fenster eine Frau schaut und ihm „hu(e)t fast“ zuruft, 11.7 x 8.6 cm.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/2mBnruqDRMWf6jeo1fb4EA/values/d2ca0d903d26",
            valueHasUUID = stringFormatter.decodeUuid("d2ca0d903d26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:48Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "p3v",
        resourceIri = "http://rdfh.ch/resources/3IiGawhMSZK8M0jRCo8eLg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:46Z"),
        resourceUUID = stringFormatter.decodeUuid("3IiGawhMSZK8M0jRCo8eLg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 102.\nHolzschnitt zu Kap. 102: Von Falschheit und Betrug\nEin Narr hantiert mit Gerätschaften aus der Alchimistenküche an einem Ofen. Ihm assistiert eine Gelehrter, der Hinter ihm steht. Ein zweiter Narr hockt vor einem Weinfass und rührt mit einem Knochen in dessen Inhalt herum, 11.6 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/3IiGawhMSZK8M0jRCo8eLg/values/b6f3e7e78726",
            valueHasUUID = stringFormatter.decodeUuid("b6f3e7e78726"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:46Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "n3r",
        resourceIri = "http://rdfh.ch/resources/3L71Or56S3GzQzIU-AjQOA",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:49Z"),
        resourceUUID = stringFormatter.decodeUuid("3L71Or56S3GzQzIU-AjQOA"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 89.\nHolzschnitt zu Kap. 89: Von törichtem Tausch.\nEin Narr, der in einer Landschaft einem Mann begegnet, tauscht mit diesem sein Maultier gegen einen Dudelsack, 11.7 x 8.3 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/3L71Or56S3GzQzIU-AjQOA/values/3e6de9f27e26",
            valueHasUUID = stringFormatter.decodeUuid("3e6de9f27e26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:49Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "e5v",
        resourceIri = "http://rdfh.ch/resources/3TcOKBj7R1WhhLgYNy08Fw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:40Z"),
        resourceUUID = stringFormatter.decodeUuid("3TcOKBj7R1WhhLgYNy08Fw"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, während sich unter dem Narren die Hölle in Gestalt eines gefrässigen Drachenkopfs auftut.\n11.7 x 8.5 cm.\nUnkoloriert.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/3TcOKBj7R1WhhLgYNy08Fw/values/3429ce523b26",
            valueHasUUID = stringFormatter.decodeUuid("3429ce523b26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:40Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "e7r",
        resourceIri = "http://rdfh.ch/resources/3_Z7FdthfxDUDfQxK0gdjg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:48Z"),
        resourceUUID = stringFormatter.decodeUuid("3_Z7FdthfxDUDfQxK0gdjg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom Nörgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, hält seine Hand schützend über die Augen, während er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/3_Z7FdthfxDUDfQxK0gdjg/values/2882816d3a26",
            valueHasUUID = stringFormatter.decodeUuid("2882816d3a26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:44Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "f7r",
        resourceIri = "http://rdfh.ch/resources/3dDvRcapRauO5sw_Dx6K8w",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:44Z"),
        resourceUUID = stringFormatter.decodeUuid("3dDvRcapRauO5sw_Dx6K8w"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 37.\nHolzschnitt zu Kap. 37: Von der Wandelbarkeit des Glücks.\nHolzschnitt identisch mit Kap. 56. Die Hand Gottes treibt das Glücksrad an, auf dem ein Esel und zwei Mischwesen – halb Esel, halb Narr – herumgewirbelt werden. Der von der Abwärtsbewegung des Rades Mitgerissene stürzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/3dDvRcapRauO5sw_Dx6K8w/values/25c9f8884826",
            valueHasUUID = stringFormatter.decodeUuid("25c9f8884826"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:44Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "k3r",
        resourceIri = "http://rdfh.ch/resources/3v9z1DeGTJS-iBIgG9_Ofw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:45Z"),
        resourceUUID = stringFormatter.decodeUuid("3v9z1DeGTJS-iBIgG9_Ofw"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 65.\nHolzschnitt zu Kap. 65: Von Astrologie und anderem Aberglauben.\nEin Narr, an dessen Seite ein Fuchsschwanz hängt, will einen Gelehrten überreden, auf die Gestirne und den Flug der Vögel zu achten. Dafür fasst er den Gelehrten bei der Schulter des Gelehrten und weist mit seiner Rechten zum Himmel.\n11.6 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/3v9z1DeGTJS-iBIgG9_Ofw/values/c779184a7126",
            valueHasUUID = stringFormatter.decodeUuid("c779184a7126"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:45Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "c3r",
        resourceIri = "http://rdfh.ch/resources/4G9DJIBkSfKdMg8yxStjjg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:44Z"),
        resourceUUID = stringFormatter.decodeUuid("4G9DJIBkSfKdMg8yxStjjg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 8.\nHolzschnitt zu Kap. 8: Guten Rat verschmähen.\nEin Narr, dessen Linke mit einem Falknerhandschuh geschützt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/4G9DJIBkSfKdMg8yxStjjg/values/125a433b2f26",
            valueHasUUID = stringFormatter.decodeUuid("125a433b2f26"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:44Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "d1v",
        resourceIri = "http://rdfh.ch/resources/4J6hWAUKQtyRqQ1CLpDrCQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:39Z"),
        resourceUUID = stringFormatter.decodeUuid("4J6hWAUKQtyRqQ1CLpDrCQ"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 19.\nHolzschnitt zu Kap. 19: Von überflüssigen Schwätzen.\nEin Narr steht mit herausgestreckter Zunge unter einem Baum. Er erblickt in dessen Krone das Nest eines Spechts, der unten ein Loch in den Stamm hämmert.\n11.6 x 8.5 cm.\nUnkoloriert.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/4J6hWAUKQtyRqQ1CLpDrCQ/values/1d6e62d43426",
            valueHasUUID = stringFormatter.decodeUuid("1d6e62d43426"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:39Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "d7r",
        resourceIri = "http://rdfh.ch/resources/4WfLxyL3QDe8A8XWucD9rg",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:44Z"),
        resourceUUID = stringFormatter.decodeUuid("4WfLxyL3QDe8A8XWucD9rg"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 20.\nHolzschnitt zu Kap. 20: Vom Finden fremden Eigentums.\nEin Narr streckt seine Hände nach einem Schatz im Boden aus. Von Hinten tritt der Teufel in Gestalt eines Mischwesens heran und flüstert dem Narr mit einem Blasebalg einen Gedanken ein. 11.6 x 8.5 cm.\n")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/4WfLxyL3QDe8A8XWucD9rg/values/a3c108473526",
            valueHasUUID = stringFormatter.decodeUuid("a3c108473526"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:44Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "c2r",
        resourceIri = "http://rdfh.ch/resources/5-nqe82tSnC82x01bE4zEw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:47Z"),
        resourceUUID = stringFormatter.decodeUuid("5-nqe82tSnC82x01bE4zEw"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some(
                "Beginn Kapitel 7.\nHolzschnitt zu Kap. 7: Vom Zwietracht stiften.\nEin Narr wird von zwei Mühlsteinen zerdrückt, ein zweiter hat seinen Finger in einer Türangel eingeklemmt und wird von einem dritten Narren beobachtet, der so hinter einer Wand verborgen ist, dass die Ohren seiner Narrenkappe sein Versteck verraten.\n11.7 x 8.4 cm.")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/5-nqe82tSnC82x01bE4zEw/values/096c7f3a2026",
            valueHasUUID = stringFormatter.decodeUuid("096c7f3a2026"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser,knora-admin:KnownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:47Z"),
            attachedToUser = "http://rdfh.ch/users/b83acc5f05",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      )
    )
  )

  val fulltextSearchForDinge: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        label = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        resourceIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg",
        permissions = "CR knora-admin:Creator|V knora-admin:ProjectMember",
        userPermission = ChangeRightsPermission,
        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        projectADM = SharedTestDataADM.anythingProject,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        resourceUUID = stringFormatter.decodeUuid("jT0UHG9_wtaX23VoYydmGg"),
        values = Map("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri -> Vector(
          ReadTextValueV2(
            valueContent = TextValueContentV2(
              standoff = Vector(
                StandoffTagV2(
                  endParentIndex = None,
                  originalXMLID = None,
                  uuid = UUID.fromString("2e136103-2a4b-4e59-ac8f-79a53f54b496"),
                  endPosition = 45,
                  startParentIndex = None,
                  attributes = Nil,
                  startIndex = 0,
                  endIndex = None,
                  dataType = None,
                  startPosition = 0,
                  standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
                ),
                StandoffTagV2(
                  endParentIndex = None,
                  originalXMLID = None,
                  uuid = UUID.fromString("80133696-26a1-4941-967b-6bf210d7d5e1"),
                  endPosition = 19,
                  startParentIndex = Some(0),
                  attributes = Vector(StandoffTagIriAttributeV2(
                    standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasLink".toSmartIri,
                    value = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
                  )),
                  startIndex = 1,
                  endIndex = None,
                  dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                  startPosition = 14,
                  standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag".toSmartIri
                )
              ),
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = Some(MappingXMLtoStandoff(
                namespace = Map("noNamespace" -> Map(
                  "tbody" -> Map("noClass" -> XMLTag(
                    name = "tbody",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableBodyTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "pre" -> Map("noClass" -> XMLTag(
                    name = "pre",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffPreTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "ol" -> Map("noClass" -> XMLTag(
                    name = "ol",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffOrderedListTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "hr" -> Map("noClass" -> XMLTag(
                    name = "hr",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffLineTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h4" -> Map("noClass" -> XMLTag(
                    name = "h4",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader4Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h3" -> Map("noClass" -> XMLTag(
                    name = "h3",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader3Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "li" -> Map("noClass" -> XMLTag(
                    name = "li",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffListElementTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "br" -> Map("noClass" -> XMLTag(
                    name = "br",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBrTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "u" -> Map("noClass" -> XMLTag(
                    name = "u",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnderlineTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "strike" -> Map("noClass" -> XMLTag(
                    name = "strike",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffStrikethroughTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "ul" -> Map("noClass" -> XMLTag(
                    name = "ul",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnorderedListTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "a" -> Map(
                    "salsah-link" -> XMLTag(
                      name = "a",
                      mapping = XMLTagToStandoffClass(
                        standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag",
                        attributesToProps = Map(),
                        dataType = Some(XMLStandoffDataTypeClass(
                          standoffDataTypeClass = StandoffDataTypeClasses.StandoffLinkTag,
                          dataTypeXMLAttribute = "href"
                        ))
                      ),
                      separatorRequired = false
                    ),
                    "internal-link" -> XMLTag(
                      name = "a",
                      mapping = XMLTagToStandoffClass(
                        standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag",
                        attributesToProps = Map(),
                        dataType = Some(XMLStandoffDataTypeClass(
                          standoffDataTypeClass = StandoffDataTypeClasses.StandoffInternalReferenceTag,
                          dataTypeXMLAttribute = "href"
                        ))
                      ),
                      separatorRequired = false
                    ),
                    "noClass" -> XMLTag(
                      name = "a",
                      mapping = XMLTagToStandoffClass(
                        standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffUriTag",
                        attributesToProps = Map(),
                        dataType = Some(XMLStandoffDataTypeClass(
                          standoffDataTypeClass = StandoffDataTypeClasses.StandoffUriTag,
                          dataTypeXMLAttribute = "href"
                        ))
                      ),
                      separatorRequired = false
                    )
                  ),
                  "text" -> Map("noClass" -> XMLTag(
                    name = "text",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag",
                      attributesToProps = Map("noNamespace" -> Map(
                        "documentType" -> "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType")),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "strong" -> Map("noClass" -> XMLTag(
                    name = "strong",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "code" -> Map("noClass" -> XMLTag(
                    name = "code",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCodeTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h2" -> Map("noClass" -> XMLTag(
                    name = "h2",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader2Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "td" -> Map("noClass" -> XMLTag(
                    name = "td",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableCellTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "em" -> Map("noClass" -> XMLTag(
                    name = "em",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffItalicTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "tr" -> Map("noClass" -> XMLTag(
                    name = "tr",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableRowTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "cite" -> Map("noClass" -> XMLTag(
                    name = "cite",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCiteTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "blockquote" -> Map("noClass" -> XMLTag(
                    name = "blockquote",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBlockquoteTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "p" -> Map("noClass" -> XMLTag(
                    name = "p",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h6" -> Map("noClass" -> XMLTag(
                    name = "h6",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader6Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h1" -> Map("noClass" -> XMLTag(
                    name = "h1",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader1Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "sub" -> Map("noClass" -> XMLTag(
                    name = "sub",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSubscriptTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "sup" -> Map("noClass" -> XMLTag(
                    name = "sup",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSuperscriptTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "h5" -> Map("noClass" -> XMLTag(
                    name = "h5",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader5Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "table" -> Map("noClass" -> XMLTag(
                    name = "table",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  ))
                )),
                defaultXSLTransformation = None
              )),
              xslt = None,
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some("Ich liebe die Dinge, sie sind alles f\u00FCr mich.")
            ),
            valueHasMaxStandoffStartIndex = Some(1),
            valueIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg/values/1",
            valueHasUUID = stringFormatter.decodeUuid("1"),
            permissions = "CR knora-admin:Creator",
            userPermission = ChangeRightsPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:54Z"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            deletionInfo = None
          ),
          ReadTextValueV2(
            valueContent = TextValueContentV2(
              standoff = Vector(
                StandoffTagV2(
                  endParentIndex = None,
                  originalXMLID = None,
                  uuid = UUID.fromString("fd583868-2a3c-4941-a330-990f5a972f71"),
                  endPosition = 25,
                  startParentIndex = None,
                  attributes = Nil,
                  startIndex = 0,
                  endIndex = None,
                  dataType = None,
                  startPosition = 0,
                  standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
                ),
                StandoffTagV2(
                  endParentIndex = None,
                  originalXMLID = None,
                  uuid = UUID.fromString("59a36237-95a9-4acc-8361-7c8fac311063"),
                  endPosition = 16,
                  startParentIndex = Some(0),
                  attributes = Vector(StandoffTagIriAttributeV2(
                    standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasLink".toSmartIri,
                    value = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
                  )),
                  startIndex = 1,
                  endIndex = None,
                  dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                  startPosition = 11,
                  standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag".toSmartIri
                )
              ),
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = Some(MappingXMLtoStandoff(
                namespace = Map("noNamespace" -> Map(
                  "tbody" -> Map("noClass" -> XMLTag(
                    name = "tbody",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableBodyTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "pre" -> Map("noClass" -> XMLTag(
                    name = "pre",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffPreTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "ol" -> Map("noClass" -> XMLTag(
                    name = "ol",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffOrderedListTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "hr" -> Map("noClass" -> XMLTag(
                    name = "hr",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffLineTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h4" -> Map("noClass" -> XMLTag(
                    name = "h4",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader4Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h3" -> Map("noClass" -> XMLTag(
                    name = "h3",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader3Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "li" -> Map("noClass" -> XMLTag(
                    name = "li",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffListElementTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "br" -> Map("noClass" -> XMLTag(
                    name = "br",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBrTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "u" -> Map("noClass" -> XMLTag(
                    name = "u",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnderlineTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "strike" -> Map("noClass" -> XMLTag(
                    name = "strike",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffStrikethroughTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "ul" -> Map("noClass" -> XMLTag(
                    name = "ul",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnorderedListTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "a" -> Map(
                    "salsah-link" -> XMLTag(
                      name = "a",
                      mapping = XMLTagToStandoffClass(
                        standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag",
                        attributesToProps = Map(),
                        dataType = Some(XMLStandoffDataTypeClass(
                          standoffDataTypeClass = StandoffDataTypeClasses.StandoffLinkTag,
                          dataTypeXMLAttribute = "href"
                        ))
                      ),
                      separatorRequired = false
                    ),
                    "internal-link" -> XMLTag(
                      name = "a",
                      mapping = XMLTagToStandoffClass(
                        standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag",
                        attributesToProps = Map(),
                        dataType = Some(XMLStandoffDataTypeClass(
                          standoffDataTypeClass = StandoffDataTypeClasses.StandoffInternalReferenceTag,
                          dataTypeXMLAttribute = "href"
                        ))
                      ),
                      separatorRequired = false
                    ),
                    "noClass" -> XMLTag(
                      name = "a",
                      mapping = XMLTagToStandoffClass(
                        standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffUriTag",
                        attributesToProps = Map(),
                        dataType = Some(XMLStandoffDataTypeClass(
                          standoffDataTypeClass = StandoffDataTypeClasses.StandoffUriTag,
                          dataTypeXMLAttribute = "href"
                        ))
                      ),
                      separatorRequired = false
                    )
                  ),
                  "text" -> Map("noClass" -> XMLTag(
                    name = "text",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag",
                      attributesToProps = Map("noNamespace" -> Map(
                        "documentType" -> "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType")),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "strong" -> Map("noClass" -> XMLTag(
                    name = "strong",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "code" -> Map("noClass" -> XMLTag(
                    name = "code",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCodeTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h2" -> Map("noClass" -> XMLTag(
                    name = "h2",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader2Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "td" -> Map("noClass" -> XMLTag(
                    name = "td",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableCellTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "em" -> Map("noClass" -> XMLTag(
                    name = "em",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffItalicTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "tr" -> Map("noClass" -> XMLTag(
                    name = "tr",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableRowTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "cite" -> Map("noClass" -> XMLTag(
                    name = "cite",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCiteTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "blockquote" -> Map("noClass" -> XMLTag(
                    name = "blockquote",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBlockquoteTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "p" -> Map("noClass" -> XMLTag(
                    name = "p",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h6" -> Map("noClass" -> XMLTag(
                    name = "h6",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader6Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "h1" -> Map("noClass" -> XMLTag(
                    name = "h1",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader1Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "sub" -> Map("noClass" -> XMLTag(
                    name = "sub",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSubscriptTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "sup" -> Map("noClass" -> XMLTag(
                    name = "sup",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSuperscriptTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = false
                  )),
                  "h5" -> Map("noClass" -> XMLTag(
                    name = "h5",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader5Tag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  )),
                  "table" -> Map("noClass" -> XMLTag(
                    name = "table",
                    mapping = XMLTagToStandoffClass(
                      standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableTag",
                      attributesToProps = Map(),
                      dataType = None
                    ),
                    separatorRequired = true
                  ))
                )),
                defaultXSLTransformation = None
              )),
              xslt = None,
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some("Na ja, die Dinge sind OK.")
            ),
            valueHasMaxStandoffStartIndex = Some(1),
            valueIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg/values/2",
            valueHasUUID = stringFormatter.decodeUuid("2"),
            permissions = "CR knora-admin:Creator",
            userPermission = ChangeRightsPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:54Z"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            deletionInfo = None
          )
        )),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ))
  )

  val constructQueryForBooksWithTitleZeitgloecklein: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
          None
        ),
        StatementPattern(QueryVariable("book"),
                         IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
                         QueryVariable("title"),
                         None)
      ),
      querySchema = Some(ApiV2Simple)
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
          None
        ),
        StatementPattern(QueryVariable("book"),
                         IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
                         QueryVariable("title"),
                         None),
        StatementPattern(
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("title"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        FilterPattern(
          CompareExpression(
            QueryVariable("title"),
            CompareExpressionOperator.EQUALS,
            XsdLiteral("Zeitglöcklein des Lebens und Leidens Christi",
                       "http://www.w3.org/2001/XMLSchema#string".toSmartIri)
          ))
      ),
      positiveEntities = Set(
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
        IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
        QueryVariable("book"),
        QueryVariable("title")
      ),
      querySchema = Some(ApiV2Simple)
    ),
    querySchema = Some(ApiV2Simple)
  )

  val booksWithTitleZeitgloeckleinResponse: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        resourceUUID = stringFormatter.decodeUuid("7dGkt1CLKdZbrxVj324eaw"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw/values/c3295339",
            valueHasUUID = stringFormatter.decodeUuid("c3295339"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
            attachedToUser = "http://rdfh.ch/users/91e19f1e01",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:23Z"),
        resourceUUID = stringFormatter.decodeUuid("i4egXDOr2dZR3JRcdlapSQ"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadTextValueV2(
            valueContent = TextValueContentV2(
              ontologySchema = InternalSchema,
              valueHasLanguage = None,
              comment = None,
              maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")
            ),
            valueHasMaxStandoffStartIndex = None,
            valueIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ/values/d9a522845006",
            valueHasUUID = stringFormatter.decodeUuid("d9a522845006"),
            permissions =
              "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
            userPermission = ViewPermission,
            previousValueIri = None,
            valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
            attachedToUser = "http://rdfh.ch/users/91e19f1e01",
            deletionInfo = None
          ))),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      )
    )
  )

  val constructQueryForBooksWithoutTitleZeitgloecklein: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
          None
        ),
        StatementPattern(QueryVariable("book"),
                         IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
                         QueryVariable("title"),
                         None)
      ),
      querySchema = Some(ApiV2Simple)
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
          None
        ),
        StatementPattern(QueryVariable("book"),
                         IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
                         QueryVariable("title"),
                         None),
        StatementPattern(
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("title"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        FilterPattern(
          CompareExpression(
            QueryVariable("title"),
            CompareExpressionOperator.NOT_EQUALS,
            XsdLiteral("Zeitglöcklein des Lebens und Leidens Christi",
                       "http://www.w3.org/2001/XMLSchema#string".toSmartIri)
          ))
      ),
      positiveEntities = Set(
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
        IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
        QueryVariable("book"),
        QueryVariable("title")
      ),
      querySchema = Some(ApiV2Simple)
    ),
    querySchema = Some(ApiV2Simple)
  )
}
