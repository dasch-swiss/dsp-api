"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.12.11"
AKKA_VERSION = "2.6.5"
AKKA_HTTP_VERSION = "10.2.0"
JENA_VERSION = "3.14.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_VERSION = "3.0.3"
SIPI_IMAGE = SIPI_REPOSITORY + ":" + SIPI_VERSION
SIPI_IMAGE_DIGEST = "sha256:54d1cef3b923d87fa0feaf259b44e6b683a5e94392bc4b94b35685cffc7a9ca1"

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "1.0.5" # contains Fuseki 3.16
FUSEKI_IMAGE = FUSEKI_REPOSITORY + ":" + FUSEKI_VERSION
FUSEKI_IMAGE_DIGEST = "sha256:5caba3d092ccc04fe8dc988137d97c012b020eeb649f439511429d6b4ae467ec"
