# context
These files are materials for test cases in `StandoffV1R2RSpec`.

The `mapping*` files describe mapping used to translate the `xml` encoded text into Knora's Standoff format.

# mappings

## Letter
Is the mapping used for lettter transcription

- mapping file: `mappingForLetter.xml`
- example text files: `letter.xml`, `letter2.xml`, `letter3.xml`

## StandardHTML
Is the mapping used to translate html markup using the default mapping `OntologyConstants.KnoraBase.StandardMapping`.

- mapping file: `mappingForStandardHTML.xml`  
- example text file: `StandardHTML`

Note: that you can use this mapping to generate the default mapping that is distributed 
as `knora-ontologies/standoff-data.ttl`, for example to extend it, but you have to rework the generated
resources UUID to maintain backward compatibility.
The Salsah default HTML editor should be able to produce this markup.

## HTML
Custom mapping of HTML for project specific tagging.

- mapping file: `mappingForHTML.xml`
- example file: `HTML.xml`

Note: this covers part of the StandardHTML mapping because knora does not yet allow multiple mappings encoding.
