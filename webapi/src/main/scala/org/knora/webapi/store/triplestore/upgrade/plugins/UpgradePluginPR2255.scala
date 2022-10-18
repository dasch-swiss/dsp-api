/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger

import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import org.apache.jena.sparql.function.library.uuid

/**
 * Transforms a repository for DSP-API PR2255.
 * Transforms incorrect value of project IRIs from the one containing either shortcode or
 * not suppored UUID version to UUID v4 base64 encoded.
 */
class UpgradePluginPR2255(log: Logger) extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  override def transform(model: RdfModel): Unit = {
    val statementsToRemove: collection.mutable.Set[Statement] = collection.mutable.Set.empty
    val statementsToAdd: collection.mutable.Set[Statement]    = collection.mutable.Set.empty

    ProjectsIrisToChange.shortcodesToUuids.foreach { case (iriToFind, iriToChange) =>
      val updatedNode = nodeFactory.makeIriNode(iriToChange)

      for {
        statement <- model

        _ = if (statement.subj.stringValue == iriToFind) {
              statementsToRemove += statement

              statementsToAdd += nodeFactory.makeStatement(
                subj = updatedNode,
                pred = statement.pred,
                obj = statement.obj
              )
            }

        _ = if (statement.obj.stringValue == iriToFind) {
              statementsToRemove += statement

              statementsToAdd += nodeFactory.makeStatement(
                subj = statement.subj,
                pred = statement.pred,
                obj = updatedNode
              )
            }
      } yield ()
    }

    model.removeStatements(statementsToRemove.toSet)
    model.addStatements(statementsToAdd.toSet)

    log.info(
      s"Transformed ${statementsToAdd.iterator.size} project IRIs."
    )
  }
}

object ProjectsIrisToChange {
  val prefix = "http://rdfh.ch/projects/"
  val shortcodesToUuids: Map[String, String] = Map(
    s"${prefix}0001" -> s"${prefix}Lw3FC39BSzCwvmdOaTyLqQ",
    s"${prefix}00FF" -> s"${prefix}MTvoB0EJRrqovzRkWXqfkA",
    s"${prefix}0101" -> s"${prefix}WtDaFRUwSH6duM8PRpGi_A",
    s"${prefix}0102" -> s"${prefix}hjcK0cxDRkqdt8eINPfC_g",
    s"${prefix}0103" -> s"${prefix}GTsqfu_hQR61SayOgcIXrg",
    s"${prefix}0105" -> s"${prefix}YoKaSYdXR02Fweunjh3VJw",
    s"${prefix}0106" -> s"${prefix}Qb2nkulcSKGRWZSblJMGtw",
    s"${prefix}0107" -> s"${prefix}qWcT5mpMQeSE1g5XcackCw",
    s"${prefix}0110" -> s"${prefix}nrq0XmaxTxCQRsP5kouQVA",
    s"${prefix}0111" -> s"${prefix}7e7xZGHkTaeeCAmp94Q88A",
    s"${prefix}0112" -> s"${prefix}QNSP2JJRTEyh6A0ZtpRdPQ",
    s"${prefix}0114" -> s"${prefix}4ARR04AUS5adXKf2wsCxHA",
    s"${prefix}0115" -> s"${prefix}XdWLXzaWS7u2JJ6dz3Ek_w",
    s"${prefix}0116" -> s"${prefix}Fq5c_IG_T_2_PeMwtsKqqg",
    s"${prefix}0118" -> s"${prefix}fyv4FvFkTMmQzOFaPJWHnQ",
    s"${prefix}0119" -> s"${prefix}v3fTcTDLTySEpH5AdOz7rQ",
    s"${prefix}0121" -> s"${prefix}R7bt9MZxQzKoHCFI43sAoQ",
    s"${prefix}0123" -> s"${prefix}i19a2kqSRCmln8dJ32eSjw",
    s"${prefix}0700" -> s"${prefix}3WKFwYUQTsW2zJhucAqeMw",
    s"${prefix}0701" -> s"${prefix}UczbMFpDSiuoDX9m9_EGmA",
    s"${prefix}0702" -> s"${prefix}ftIlaxJBSDWXeb5UUD248A",
    s"${prefix}0703" -> s"${prefix}JlEfUOhVRRiwyCuU_aSxSQ",
    s"${prefix}0704" -> s"${prefix}pQ5nZMiUSpKsgTNCfHUUZQ",
    s"${prefix}0741" -> s"${prefix}uIHCtVr0SniEnqYQU8l3jg",
    s"${prefix}0761" -> s"${prefix}cnVmqdw3TkiXP7RJnyl7xg",
    s"${prefix}0800" -> s"${prefix}HEL1CriESE2MJNzNBeutRg",
    s"${prefix}0803" -> s"${prefix}yISnUYe6SYmoyuqeMdW39w",
    s"${prefix}0804" -> s"${prefix}oIjhUsZmQLuJ0VMGvJ2pfg",
    s"${prefix}0805" -> s"${prefix}V4dTgDq2Q9GdAi1IBT_Q7g",
    s"${prefix}0806" -> s"${prefix}UzTunPytT2W52aHcsiBsKw",
    s"${prefix}0807" -> s"${prefix}YSrefTMXTAGDBqSTdploDQ",
    s"${prefix}080A" -> s"${prefix}XSqIcun_R5mAY6J4xF6W5g",
    s"${prefix}080C" -> s"${prefix}LPIlHkRhQYm5Cem1ne6EeQ",
    s"${prefix}0811" -> s"${prefix}pyRvVOMdT7idqD5DjLNO3w",
    s"${prefix}081C" -> s"${prefix}W0LqvAWZSeGhCCyjvbKGVg",
    s"${prefix}0824" -> s"${prefix}7C9bPusQTgaPB3CK9ZAYtQ",
    s"${prefix}0826" -> s"${prefix}0ZKXMOBTSIeE65NWjvc3xg",
    s"${prefix}0827" -> s"${prefix}dETKYIDFSPux9gWDtOCqEg",
    s"${prefix}082A" -> s"${prefix}L4wkw5TYTs2iCmBC15pC3w",
    s"${prefix}082B" -> s"${prefix}P6EfsNG0Ri6UYHScQ8fJqA",
    s"${prefix}082C" -> s"${prefix}4ok9ymjaRjCVJiRKPTIgnA",
    s"${prefix}082D" -> s"${prefix}N6oJE4aUSFiH16B716T2sQ",
    s"${prefix}082E" -> s"${prefix}n9c9_jI4RcWxcX1Rz7HRJg",
    s"${prefix}082F" -> s"${prefix}Gh2PyGh0Q0qAiAQsw8T1WQ",
    s"${prefix}0836" -> s"${prefix}lWqHYI7RRaCywJxkwF0yOg",
    s"${prefix}0838" -> s"${prefix}nSZIDnvrSQObkFog95HKCw",
    s"${prefix}0839" -> s"${prefix}Tvruhq2VTXCPdH9Vxrtwmw",
    s"${prefix}083D" -> s"${prefix}bVZw1zw0TYeuT2a2kBaQOA",
    s"${prefix}083E" -> s"${prefix}2jRwR4SiSFq6z35E2p4CXg",
    s"${prefix}08AE" -> s"${prefix}NeWmPqGNQ5KVMAG6L8AjNA",
    s"${prefix}0987" -> s"${prefix}l2ywBrSsRdm0FMBfZWzJgA",
    s"${prefix}1111" -> s"${prefix}YVTg6MPdQkWjz_n74zyV6w",
    s"${prefix}1234" -> s"${prefix}oTUOXoKSSh6hEJtJJQQ6Rw",
    s"${prefix}2021" -> s"${prefix}havLGPuCTZOo72WaI97TDg",
    s"${prefix}3233" -> s"${prefix}VoeibkMcTsifvjCHv_WnHA",
    s"${prefix}3769" -> s"${prefix}YoMAsZtzQNyy3vejxXZokQ",
    s"${prefix}yTerZGyxjZVqFMNNKXCDPF"
      -> s"${prefix}bL0y8GRuTUiFmvF1oXbeFQ"
  )
}
