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

Text with standard standoff markup can be transformed to TEI XML as described [in the tei xml documentation](tei-xml.md).

## Canonical Form and Change Detection

When a `TextValue` with standard standoff markup is read back,
the XML that DSP-API returns is semantically equivalent (isomorphic) to the XML that was submitted,
but not necessarily byte-identical.
The round-trip from XML to standoff and back normalises the markup:
attribute order, the form of empty elements (`<br></br>` becomes `<br/>`),
character-entity encoding and insignificant whitespace may all differ.
A direct string comparison between the submitted XML and the stored XML is therefore unreliable.

This matters for clients that keep their own copy of the data
and want to detect whether a rich-text value has actually changed before syncing an update
(for example, to avoid creating redundant value versions).
Because only DSP-API knows its own canonical form,
the comparison has to be made against that form.

The `POST /v2/standoff/canonicalize` endpoint returns the canonical form of a piece of
standard-mapping rich-text XML: the exact XML that DSP-API would return when reading such a value back.
The conversion is idempotent, so a client can canonicalize its own source
and compare the result against the `textValueAsXml` of the stored value
to decide whether the value has changed.

```text
POST http://HOST/v2/standoff/canonicalize
Content-Type: application/xml
Authorization: Bearer TOKEN

<text documentType="html"><p>Some <strong>rich</strong> text.</p></text>
```

The response body is the canonical XML, served as `application/xml`.
Only the standard mapping is supported, and the request must be authenticated.

This is an experimental feature and may change or be removed without notice.
It should not be relied upon for production integrations.

## Footnotes

The `<footnote>` tag is an anchor tag indicating where in the text a footnote should be placed.
It must not contain any text or other tags.

The contents of the footnote are stored in the `content` attribute of the `<footnote>` tag.
These may contain further markup, so the content of the footnote must be valid XML.

In order to result in valid xml, the content of the footnote must be properly escaped
so that it does not contain any characters that are not allowed in XML.
Special characters like `<`, `>`, `&`, `"`, and `'` must be replaced by their respective XML entities,
i.e. `&lt;`, `&gt;`, `&amp;`, `&quot;`, and `&apos;`.

The following example shows how a footnote is used in the XML:

```xml
<text>
  <p>
    Some text with a 
    footnote<footnote content="Text with &lt;a href=&quot;...&quot;&gt;markup&lt;/a&gt;." /> 
    in it.
  </p>
</text>
```

**Note:**  
Footnote support is still in an early stage, and may change in the future.
There are some known limitations:

- CKE does not support footnotes out of the box. DSP-APP uses a custom build of CKE that supports footnotes.
- The content of footnotes is not covered by the full text search.
- The content of footnotes may contain further markup, but this will not be converted to standoff by the API.
  For that reason, markup in footnotes can not be searched for through gravsearch
  and hence outgoing and incoming links will not be displayed in DSP-APP.

