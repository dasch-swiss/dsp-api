# Standard Standoff Markup Mapping

A mapping allows for the conversion of XML to standoff representation in RDF and back. 
In order to create a `TextValue` with markup, 
the text has to be provided in XML format, 
along with the IRI of the mapping that will be used to convert the markup to standoff.

DSP-API offers a standard mapping with the IRI `http://rdfh.ch/standoff/mappings/StandardMapping`. 
The standard mapping covers the HTML elements and attributes 
supported by the GUI's text editor, [CKEditor](https://ckeditor.com/). 
(Please note that the HTML has to be encoded in strict XML syntax. 
CKeditor offers the possibility to define filter rules.
They should reflect the elements supported by the mapping.)
The standard mapping contains the following elements and attributes 
that are mapped to standoff classes and properties defined in the ontology:

- `<text>` → `standoff:StandoffRootTag`
- `<p>` → `standoff:StandoffParagraphTag`
- `<em>` → `standoff:StandoffItalicTag`
- `<strong>` → `standoff:StandoffBoldTag`
- `<u>` → `standoff:StandoffUnderlineTag`
- `<sub>` → `standoff:StandoffSubscriptTag`
- `<sup>` → `standoff:StandoffSuperscriptTag`
- `<strike>` → `standoff:StandoffStrikeTag`
- `<a href="URL">` → `knora-base:StandoffUriTag`
- `<a class="salsah-link" href="Knora IRI">` → `knora-base:StandoffLinkTag`
- `<a class="internal-link" href="#fragment">` → `knora-base:StandoffInternalReferenceTag`
- `<h1>` to `<h6>` → `standoff:StandoffHeader1Tag` to `standoff:StandoffHeader6Tag`
- `<ol>` → `standoff:StandoffOrderedListTag`
- `<ul>` → `standoff:StandoffUnrderedListTag`
- `<li>` → `standoff:StandoffListElementTag`
- `<tbody>` → `standoff:StandoffTableBodyTag`
- `<thead>` → `standoff:StandoffTableHeaderTag`
- `<table>` → `standoff:StandoffTableTag`
- `<tr>` → `standoff:StandoffTableRowTag`
- `<th>` → `standoff:StandoffTableHeaderCellTag`
- `<td>` → `standoff:StandoffTableCellTag`
- `<br>` → `standoff:StandoffBrTag`
- `<hr>` → `standoff:StandoffLineTag`
- `<pre>` → `standoff:StandoffPreTag`
- `<cite>` → `standoff:StandoffCiteTag`
- `<blockquote>` → `standoff:StandoffBlockquoteTag`
- `<code>` → `standoff:StandoffCodeTag`
- `<footnote content="footnote text">` → `standoff:StandoffFootnoteTag`

The HTML produced by CKEditor is wrapped in an XML doctype and a pair of root tags `<text>...</text>` 
and then sent to the DSP-API.
The XML sent to the GUI by the DSP-API is unwrapped accordingly.
Although the GUI supports HTML5, it is treated as if it was XHTML in strict XML notation.

Text with standard standoff markup can be transformed to TEI XML as described [here](tei-xml.md).

## Footnotes

Footnote support is currently experimental and has some limitations:

- CKE does not support footnotes out of the box. DSP-APP uses a custom build of CKE that supports footnotes.
- The content of footnotes is not covered by the full text search.
- The content of footnotes may contain further markup, but this will internally not be converted to standoff. 
  For that reason, markup in footnotes can not be searched for through gravsearch
  and hence outgoing and incoming links will not be displayed in DSP-APP.
