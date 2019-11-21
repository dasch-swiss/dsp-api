"""Primary location for setting Knora-API project wide versions"""

SCALA_VERSION = "2.12.8"
AKKA_VERSION = "2.5.26"
AKKA_HTTP_VERSION = "10.1.7"
JENA_VERSION = "3.4.0"
METRICS_VERSION = "4.0.1"
SIPI_VERSION = "2.0.1"
SIPI_IMAGE = "dhlabbasel/sipi:%s" % SIPI_VERSION
GDB_SE_VERSION = "9.0.0"
GDB_SE_IMAGE = "daschswiss/graphdb:%s-se" % GDB_SE_VERSION
GDB_FREE_VERSION = "9.0.0"
GDB_FREE_IMAGE = "daschswiss/graphdb:%s-free" % GDB_FREE_VERSION
