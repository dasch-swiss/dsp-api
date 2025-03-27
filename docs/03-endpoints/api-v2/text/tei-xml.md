    <xsl:template match="text()"> </xsl:template>


</xsl:transform>
```

You can use the functions `knora-api:iaf` and `knora-api:dateformat` in your own XSLT in case you want to support `correspSearch`.

The complete request looks like this:

```
HTTP GET request to http://host/v2/tei/resourceIri&textProperty=textPropertyIri&mappingIri=mappingIri&gravsearchTemplateIri=gravsearchTemplateIri&teiHeaderXSLTIri=teiHeaderXSLTIri
```
