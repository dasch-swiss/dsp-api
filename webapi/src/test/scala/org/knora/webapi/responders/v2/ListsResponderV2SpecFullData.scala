package org.knora.webapi.responders.v2

import org.knora.webapi.messages.admin.responder.listsmessages.{ListADM, ListInfoADM, ListNodeADM, ListNodeInfoADM}
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.listsmessages.{ListGetResponseV2, NodeGetResponseV2}
import org.knora.webapi.util.StringFormatter

class ListsResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

    val treeList = ListGetResponseV2(
        list = ListADM(
            listinfo = ListInfoADM(
                id = "http://rdfh.ch/lists/0001/treeList",
                projectIri = "http://rdfh.ch/projects/0001",
                labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                    value = "Tree list root",
                    language = Some("en")
                ))),
                comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
            ),
            children = Vector(
                ListNodeADM(
                    children = Nil,
                    name = Some("Tree list node 01"),
                    id = "http://rdfh.ch/lists/0001/treeList01",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "Tree list node 01",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("Tree list node 02"),
                    id = "http://rdfh.ch/lists/0001/treeList02",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "Baumlistenknoten 02",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("Tree list node 10"),
                            id = "http://rdfh.ch/lists/0001/treeList10",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tree list node 10",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("Tree list node 11"),
                            id = "http://rdfh.ch/lists/0001/treeList11",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tree list node 11",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("Tree list node 03"),
                    id = "http://rdfh.ch/lists/0001/treeList03",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "Tree list node 03",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            )
        ),
        userLang = "de",
        fallbackLang = "en"
    )

    val treeNode = NodeGetResponseV2(
        node = ListNodeInfoADM(
            name = Some("Tree list node 11"),
            id = "http://rdfh.ch/lists/0001/treeList11",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "Tree list node 11",
                language = Some("en")
            ))),
            position = Some(1),
            hasRootNode = Some("http://rdfh.ch/lists/0001/treeList"),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        userLang = "de",
        fallbackLang = "en"
    )

}