FROM stain/jena-fuseki:3.14.0

# add our fuseki config
COPY stage/jena-fuseki/config.ttl $FUSEKI_BASE/config.ttl
