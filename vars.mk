SIPI_VERSION := 3.0.2
SIPI_REPOSITORY := daschswiss/sipi
SIPI_IMAGE := $(SIPI_REPOSITORY):$(SIPI_VERSION)

FUSEKI_HEAP_SIZE := 3G

KNORA_WEBAPI_DB_CONNECTIONS := 2
KNORA_DB_REPOSITORY_NAME := knora-test

ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git describe --tag --dirty --abbrev=7)
endif
ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git rev-parse --verify HEAD)
endif

ifeq ($(GIT_EMAIL),)
  GIT_EMAIL := $(shell git config user.email)
endif

ifeq ($(KNORA_DB_IMPORT),)
  KNORA_DB_IMPORT := unknown
endif

ifeq ($(KNORA_DB_HOME),)
  KNORA_DB_HOME := unknown
endif

UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
  DOCKERHOST :=  $(shell ifconfig en0 | grep inet | grep -v inet6 | cut -d ' ' -f2)
else
  DOCKERHOST := $(shell ip -4 addr show docker0 | grep -Po 'inet \K[\d.]+')
endif
