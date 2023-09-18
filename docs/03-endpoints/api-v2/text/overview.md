# Overview

DSP-API supports various ways of handling textual data:


## Text in RDF

Textual data can be included directly in the data stored in DSP-API. 
This is the default way of handling text in the DSP. 
There are three ways of representing textual data in DSP-API,
two of which are fully supported by DSP-APP and DSP-TOOLS.

Texts stored in RDF can be searched using both full-text search and structured queries.

### Simple Text

If a text requires no formatting, it can simply be stored as a string in a `knora-base:TextValue`. 
This is sufficient in many cases, especially for shorter texts like names, titles, identifiers, etc.


### Text with Formatting

For text requiring regular markup, `knora-base:TextValue` can be used 
in combination with the DSP's standard standoff markup.

This allows for the following markup:

- structural markup
    - paragraphs
    - headings levels 1-6
    - ordered lists
    - unordered lists
    - tables
    - line breaks
    - horizontal rules
    - code blocks
    - block quotes
- typographical markup
    - italics
    - bold
    - underline
    - strikethrough
    - subscript
    - superscript
- semantic markup
    - links
    - DSP internal links

DSP-APP provides a text editor for conveniently editing text with standard standoff markup.

More details can be found [here](standard-standoff.md).


### Text with Custom Markup

It is possible to create custom XML-to-Schema mappings,
which allows for creating project specific custom markup for text values. 
Details can be found [here](custom-standoff.md).

!!! info
    Custom markup is not supported by DSP-TOLS and is viewe-only in DSP-APP.  
    Creating custom markup is relatively involved, so that it should only be used by projects working with complex textual data.


## File Based

Text files of various formats (Word, PDF, XML, etc.) can be uploaded to the media file server. 
For more details, see [here](../../../01-introduction/file-formats.md)

This allows for easy upload and retrieval of the file. 
However, it does not allow for searching within the file content.


## TEI XML

All text values in DSP-API using stanodff markup can be converted to TEI XML as described [here](tei-xml.md).

!!! info
    Improved support for TEI XML is in planning.
