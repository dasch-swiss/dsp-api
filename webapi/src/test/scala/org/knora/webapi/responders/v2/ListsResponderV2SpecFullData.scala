package org.knora.webapi.responders.v2

import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.listsmessages.{ListGetResponseV2, NodeGetResponseV2}
import org.knora.webapi.util.StringFormatter

class ListsResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

    val treeList = ListGetResponseV2(
        list = ListADM(
            listinfo = ListRootNodeInfoADM(
                id = "http://rdfh.ch/lists/0001/treeList",
                projectIri = "http://rdfh.ch/projects/0001",
                name = Some("treelistroot"),
                labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                    value = "Tree list root",
                    language = Some("en")
                ))),
                comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
            ),
            children = Vector(
                listsmessages.ListChildNodeADM(
                    children = Nil,
                    name = Some("Tree list node 01"),
                    id = "http://rdfh.ch/lists/0001/treeList01",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "Tree list node 01",
                        language = None
                    ))),
                    position = 0,
                    hasRootNode = "http://rdfh.ch/lists/0001/treeList",
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListChildNodeADM(
                    children = Nil,
                    name = Some("Tree list node 02"),
                    id = "http://rdfh.ch/lists/0001/treeList02",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "Baumlistenknoten 02",
                        language = None
                    ))),
                    position = 1,
                    hasRootNode = "http://rdfh.ch/lists/0001/treeList",
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListChildNodeADM(
                    children = Vector(
                        ListChildNodeADM(
                            children = Nil,
                            name = Some("Tree list node 10"),
                            id = "http://rdfh.ch/lists/0001/treeList10",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tree list node 10",
                                language = None
                            ))),
                            position = 0,
                            hasRootNode = "http://rdfh.ch/lists/0001/treeList",
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListChildNodeADM(
                            children = Nil,
                            name = Some("Tree list node 11"),
                            id = "http://rdfh.ch/lists/0001/treeList11",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tree list node 11",
                                language = None
                            ))),
                            position = 1,
                            hasRootNode = "http://rdfh.ch/lists/0001/treeList",
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("Tree list node 03"),
                    id = "http://rdfh.ch/lists/0001/treeList03",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "Tree list node 03",
                        language = None
                    ))),
                    position = 2,
                    hasRootNode = "http://rdfh.ch/lists/0001/treeList",
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])

                )
            )
        ),
        userLang = "de",
        fallbackLang = "en"
    )

    val treeNode = NodeGetResponseV2(
        node = listsmessages.ListChildNodeInfoADM(
            name = Some("Tree list node 11"),
            id = "http://rdfh.ch/lists/0001/treeList11",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "Tree list node 11",
                language = Some("en")
            ))),
            position = 1,
            hasRootNode = "http://rdfh.ch/lists/0001/treeList",
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        userLang = "de",
        fallbackLang = "en"
    )

}