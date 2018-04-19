# Makefile for API V1 and V2 typescript documentation
#

# You can set these variables from the command line.
FORMATSRCDIRV1  = ../salsah1/src/typescript_interfaces
FORMATDOCDIRV1  = target/site/api-v1
FORMATSRCDIRV2  = src/api-v2
FORMATDOCDIRV2  = target/site/api-v2

TYPEDOC         = typedoc

# User-friendly check for sphinx-build
ifeq ($(shell which $(TYPEDOC) >/dev/null 2>&1; echo $$?), 1)
$(error The '$(TYPEDOC)' command was not found. Make sure you have Typedoc installed.
endif



.PHONY: help clean

help:
	@echo "Please use \`make <target>' where <target> is one of"
	@echo "  jsonformat     to make a documentation of the json request and response format"
	@echo "  jsonformattest to make a documentation of the json request and response format"


clean:
	rm -rf $(FORMATDOCDIRV1)/*
	rm -rf $(FORMATDOCDIRV2)/*


jsonformat:
	$(TYPEDOC) --out $(FORMATDOCDIRV1) --exclude **/sampleRequests/* --readme none --name "Knora API V1 Format Documentation" --module commonjs $(FORMATSRCDIRV1)
	$(TYPEDOC) --out $(FORMATDOCDIRV2) --exclude **/$(FORMATSRCDIRV2)/samples/* --readme none --name "Knora API V2 JSON-LD Format Documentation" --module commonjs $(FORMATSRCDIRV2)
	@echo "Format Docs Build finished. The index page for V1 is $(FORMATDOCDIR)/index.html, for V2 $(FORMATDOCDIRV2)/index.html"

jsonformattest:
	$(TYPEDOC) --out $(FORMATDOCDIRV1) --readme none --name "Knora API V1 Format Documentation" --module commonjs $(FORMATSRCDIRV1)
	$(TYPEDOC) --out $(FORMATDOCDIRV2) --readme none --name "Knora API V2 JSON-LD Format Documentation" --module commonjs $(FORMATSRCDIRV2)
	@echo "Sample requests test finished."