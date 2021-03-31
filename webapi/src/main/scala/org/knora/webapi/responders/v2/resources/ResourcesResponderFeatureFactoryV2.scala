package org.knora.webapi.responders.v2.resources

import org.knora.webapi.feature.{FeatureFactory, FeatureFactoryConfig}
import org.knora.webapi.messages.util.ResponderData

/**
  * Constructs [[ResourcesResponderV2]] instances based on feature factory configuration.
  */
object ResourcesResponderFeatureFactoryV2 extends FeatureFactory {
  def makeResourcesResponderV2(responderData: ResponderData,
                               featureFactoryConfig: FeatureFactoryConfig): ResourcesResponderV2 = {
    if (featureFactoryConfig.getToggle("event-based-updates").isEnabled) {
      new EventBasedResourcesResponderV2(responderData)
    } else {
      new ClassicResourcesResponderV2(responderData)
    }
  }
}
