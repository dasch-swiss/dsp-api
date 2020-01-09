GRAPHDB_HEAP_SIZE := 5G

KNORA_API_IMAGE := bazel/docker/knora-api:knora-api
KNORA_GRAPHDB_SE_IMAGE := bazel/docker/knora-graphdb-se:knora-graphdb-se
KNORA_GRAPHDB_FREE_IMAGE := bazel/docker/knora-graphdb-free:knora-graphdb-free
KNORA_SIPI_IMAGE := bazel/docker/knora-sipi:knora-sipi
KNORA_UPGRADE_IMAGE := bazel/docker/knora-upgrade:knora-upgrade
KNORA_SALSAH1_IMAGE := bazel/docker/knora-salsah1:knora-salsah1

ifeq ($(GIT_EMAIL),)
  GIT_EMAIL := $(shell git config user.email)
endif

ifeq ($(KNORA_GDB_LICENSE),)
  KNORA_GDB_LICENSE := unknown
endif

ifeq ($(KNORA_GDB_IMPORT),)
  KNORA_GDB_IMPORT := unknown
endif

ifeq ($(KNORA_GDB_HOME),)
  KNORA_GDB_HOME := unknown
endif

ifeq ($(GDB_HEAP_SIZE),)
  KNORA_GDB_HEAP_SIZE := $(GRAPHDB_HEAP_SIZE)
else
  KNORA_GDB_HEAP_SIZE := $(GDB_HEAP_SIZE)
endif

UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
  DOCKERHOST :=  $(shell ifconfig en0 | grep inet | grep -v inet6 | cut -d ' ' -f2)
else
  DOCKERHOST := $(shell ip route get 8.8.8.8 | awk '{print $NF; exit}')
endif
