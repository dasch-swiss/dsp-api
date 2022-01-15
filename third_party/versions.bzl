"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.13.7"
AKKA_VERSION = "2.6.17"
AKKA_HTTP_VERSION = "10.2.4"
JENA_VERSION = "3.14.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_VERSION = "3.3.2"
SIPI_IMAGE = SIPI_REPOSITORY
SIPI_IMAGE_DIGEST = "sha256:7cc15decdfba70ef2894aea71a9315e1a66a8d99becc42baf8ae21a13908c860"

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "2.0.5"  # contains Fuseki 4.3.2 (with log4j 2.16.0)
FUSEKI_IMAGE = FUSEKI_REPOSITORY
FUSEKI_IMAGE_DIGEST = "sha256:224b044b0486d78df123507efa1579d17c8455cabc26111dc1848c2049f57700"
