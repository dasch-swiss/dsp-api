"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.13.5"
AKKA_VERSION = "2.6.5"
AKKA_HTTP_VERSION = "10.2.4"
JENA_VERSION = "3.14.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_VERSION = "3.3.0"
SIPI_IMAGE = SIPI_REPOSITORY
SIPI_IMAGE_DIGEST = "sha256:24ff26999d3727aa4a0c4751fc5706761cea01e9df02e08e676160f234d8506d"

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "2.0.1"  # contains Fuseki 4.2.0
FUSEKI_IMAGE = FUSEKI_REPOSITORY
FUSEKI_IMAGE_DIGEST = "sha256:4ecc6ccd09e3e1016f4489d9a23d76ccdf025666c54a2ab3a73b4e67710d642f"
