# Makefile for Sphinx documentation
#

# You can set these variables from the command line.
FORMATDOCDIR  = _format_docu
FORMATSRCDIR  = ../salsah/src/typescript_interfaces
FORMATDOCDIRV2  = _format_docu_v2
FORMATSRCDIRV2  = format_v2/JSONLD
TYPEDOC       = typedoc

# User-friendly check for sphinx-build
ifeq ($(shell which $(SPHINXBUILD) >/dev/null 2>&1; echo $$?), 1)
$(error The '$(SPHINXBUILD)' command was not found. Make sure you have Sphinx installed, then set the SPHINXBUILD environment variable to point to the full path of the '$(SPHINXBUILD)' executable. Alternatively you can add the directory with the executable to your PATH. If you don't have Sphinx installed, grab it from http://sphinx-doc.org/)
endif

clean:
	rm -rf $(BUILDDIR)/*
	rm -rf $(FORMATDOCDIR)/*


jsonformat:
	$(TYPEDOC) --out $(FORMATDOCDIR) --exclude **/sampleRequests/* --readme none --name "Knora API V1 Format Documentation" --module commonjs $(FORMATSRCDIR)
	$(TYPEDOC) --out $(FORMATDOCDIRV2) --exclude **/$(FORMATSRCDIRV2)/samples/* --readme none --name "Knora API V2 JSON-LD Format Documentation" --module commonjs $(FORMATSRCDIRV2)
	@echo "Format Docs Build finished. The index page for V1 is $(FORMATDOCDIR)/index.html, for V2 $(FORMATDOCDIRV2)/index.html"

jsonformattest:
	$(TYPEDOC) --out $(FORMATDOCDIR) --readme none --name "Knora API V1 Format Documentation" --module commonjs $(FORMATSRCDIR)
	$(TYPEDOC) --out $(FORMATDOCDIRV2) --readme none --name "Knora API V2 JSON-LD Format Documentation" --module commonjs $(FORMATSRCDIRV2)
	@echo "Sample requests test finished."