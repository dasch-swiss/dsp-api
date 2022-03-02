"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.13.7"
AKKA_VERSION = "2.6.17"
AKKA_HTTP_VERSION = "10.2.4"
JENA_VERSION = "4.4.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_IMAGE_DIGEST = "sha256:235afabf427d4e8619d10f3858ab63b6c26809ef27184f69951e098f005faa02"  # 3.3.4

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "2.0.8"  # contains Fuseki 4.4.0 (with log4j 2.17.1)
FUSEKI_IMAGE_DIGEST_AMD64 = "sha256:41e9d9608bea994f308edde27114f05f861f60c458bf64bf9c42b0d4a9799c6b"
FUSEKI_IMAGE_DIGEST_ARM64 = "sha256:14b6771e154f3a9cc7ca2894b5f58b47da1287ae0cfb60b96ec073ce56cdbed2"
