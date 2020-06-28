SIPI_VERSION := 2.0.1

FUSEKI_HEAP_SIZE := 3G

REPO_PREFIX := daschswiss
KNORA_API_REPO := knora-api
KNORA_FUSEKI_REPO := knora-jena-fuseki
KNORA_SIPI_REPO := knora-sipi
KNORA_ASSETS_REPO := knora-assets
KNORA_UPGRADE_REPO := knora-upgrade
KNORA_SALSAH1_REPO := knora-salsah1
KNORA_WEBAPI_DB_CONNECTIONS := 2
KNORA_DB_REPOSITORY_NAME := knora-test

ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git describe --tag --dirty --abbrev=7)
endif
ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git rev-parse --verify HEAD)
endif

ifeq ($(KNORA_API_IMAGE),)
  KNORA_API_IMAGE := $(REPO_PREFIX)/$(KNORA_API_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_FUSEKI_IMAGE),)
  KNORA_FUSEKI_IMAGE := $(REPO_PREFIX)/$(KNORA_FUSEKI_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_SIPI_IMAGE),)
  KNORA_SIPI_IMAGE := $(REPO_PREFIX)/$(KNORA_SIPI_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_ASSETS_IMAGE),)
  KNORA_ASSETS_IMAGE := $(REPO_PREFIX)/$(KNORA_ASSETS_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_UPGRADE_IMAGE),)
  KNORA_UPGRADE_IMAGE := $(REPO_PREFIX)/$(KNORA_UPGRADE_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_SALSAH1_IMAGE),)
  KNORA_SALSAH1_IMAGE := $(REPO_PREFIX)/$(KNORA_SALSAH1_REPO):$(BUILD_TAG)
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
