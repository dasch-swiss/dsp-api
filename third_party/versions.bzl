"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.12.11"
AKKA_VERSION = "2.6.5"
AKKA_HTTP_VERSION = "10.1.12"
JENA_VERSION = "3.14.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_VERSION = "3.0.0-rc.8"
SIPI_TAG = "v" + SIPI_VERSION
SIPI_IMAGE = SIPI_REPOSITORY + ":" + SIPI_VERSION
SIPI_IMAGE_DIGEST = "sha256:dce8f37fa9cf758955fb8a40b5341e55edb4ae356349af447e0622299c3f687a"

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "1.0.5" # contains Fuseki 3.16
FUSEKI_TAG = FUSEKI_VERSION
FUSEKI_IMAGE = FUSEKI_REPOSITORY + ":" + FUSEKI_TAG
FUSEKI_IMAGE_DIGEST = "sha256:5caba3d092ccc04fe8dc988137d97c012b020eeb649f439511429d6b4ae467ec"
