"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.13.5"
AKKA_VERSION = "2.6.5"
AKKA_HTTP_VERSION = "10.2.4"
JENA_VERSION = "3.14.0"
METRICS_VERSION = "4.0.1"

# SIPI - digest takes precedence!
SIPI_REPOSITORY = "daschswiss/sipi"
SIPI_VERSION = "3.3.1"
SIPI_IMAGE = SIPI_REPOSITORY
SIPI_IMAGE_DIGEST = "sha256:67a0e8c16a67914f2765a1c7906e781383b835cfe72cd19c763b7bc9eb7a38a5"

# Jena Fuseki - digest takes precedence!
FUSEKI_REPOSITORY = "daschswiss/apache-jena-fuseki"
FUSEKI_VERSION = "2.0.2"  # contains Fuseki 4.2.0
FUSEKI_IMAGE = FUSEKI_REPOSITORY
FUSEKI_IMAGE_DIGEST = "sha256:7b04aa3a9b51a419948f1cd92ce6504435b9930726a2f714be2e37bd7a32e624"
