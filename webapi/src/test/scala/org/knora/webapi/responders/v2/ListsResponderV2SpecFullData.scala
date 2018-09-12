package org.knora.webapi.responders.v2

import org.knora.webapi.SharedListsTestDataADM
import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages.ListADM
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.listsmessages.{ListGetResponseV2, NodeGetResponseV2}
import org.knora.webapi.util.StringFormatter

class ListsResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

    val treeList = ListGetResponseV2(
        list = ListADM(
            listinfo = SharedListsTestDataADM.treeListInfo,
            children = SharedListsTestDataADM.treeListChildNodes.map(_.sorted)
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