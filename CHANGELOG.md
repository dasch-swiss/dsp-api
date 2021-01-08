# Changelog

## Next release (01/01/1970)

## Maintenance

- #1773 DSP-667 CI is failing to test upgrade correctly
- #1761 DSP-1099 Update Bazel maven rules to see if it fixes problems with macOS Big Sur
- #1772 chore(rdf-api): Use the Jena RDF API implementation by default (DSP-1153)

---

## v13.0.0-rc.25 (08/12/2020)

## Enhancements

- #1768 DSP-1106 Update Permission
- #1767 enhancement(triplestore): Use N-Quads instead of TriG for repository upgrade (DSP-1129)
- #1764 DSP-1033 Reposition List Nodes
- #1762 feat(rdf-api): Add a general-purpose SHACL validation utility (DSP-930)
- #1759 feat: Add an RDF processing façade (2nd iteration) (DSP-1083)
- #1760 (DSP-1031) Delete list items
- #1753 Edit lists routes (DSP-597 )
- #1758 feat(api-v2): Control JSON-LD nesting via an HTTP header (DSP-1084)

## Bug fixes

- #1763 fix(sipi): Don't expect API v1 status code (DSP-1114)

## Documentation

- #1771 docs: Update README (DSP-1142)

## Maintenance

- #1770 refactor: Use java.nio.file.Path instead of java.io.File (DSP-1124)
- #1765 DSP-1094 Upgrade Swagger version
- #1766 style: Add Scalafmt config file
- #1769 style: Reformat code with Scalafmt (DSP-1137)
- #1754 feat(api-v2): Add an RDF processing façade (DSP-1020)
- #1757 build: bazel workspace cleanup

---

## v13.0.0-rc.24 (13/11/2020)

- #1756 DSP-1052 : Migration task to replace empty strings with dummy "FIXME"

---

## v13.0.0-rc.23 (09/11/2020)

## Bug fixes

- #1755 DSP-1029: Add the missing dependency

---

## v13.0.0-rc.22 (09/11/2020)

#### Breaking changes:

- [#1724](https://github.com/dasch-swiss/knora-api/pull/1724) | test: Collect client test data from E2E tests (DSP-724)
- [#1727](https://github.com/dasch-swiss/knora-api/pull/1727) | DSP-740 Update List Name
- [#1722](https://github.com/dasch-swiss/knora-api/pull/1722) | feat(api-v1): Change API v1 file uploads to work like API v2 (DSP-41, PR 3)
- [#1233](https://github.com/dasch-swiss/knora-api/pull/1233) | feat(api-v1): Change API v1 file uploads to work like API v2
- [#1708](https://github.com/dasch-swiss/knora-api/pull/1708) | Get Project Permissions

#### Enhancements:

- [#1403](https://github.com/dasch-swiss/knora-api/pull/1403) | feat: Add time value type
- [#1537](https://github.com/dasch-swiss/knora-api/pull/1537) | build: Add env var to set triplestore actor pool
- [#1649](https://github.com/dasch-swiss/knora-api/pull/1649) | feat(api-v2): Allow querying for rdfs:label in Gravsearch
- [#1742](https://github.com/dasch-swiss/knora-api/pull/1742) | feat: Add feature toggles (DSP-910)
- [#1741](https://github.com/dasch-swiss/knora-api/pull/1741) | DSP-804: create a child node with a custom IRI
- [#1734](https://github.com/dasch-swiss/knora-api/pull/1734) | feat(api-v2): Add metadata routes (DSP-662)
- [#1739](https://github.com/dasch-swiss/knora-api/pull/1739) | enhancement(api-v2): Optimise checking isDeleted (DSP-848)
- [#1664](https://github.com/dasch-swiss/knora-api/pull/1664) | feat(api-v2): Add support for text file upload (DSP-44)
- [#1652](https://github.com/dasch-swiss/knora-api/pull/1652) | DSP-377 Support Islamic calendar
- [#1717](https://github.com/dasch-swiss/knora-api/pull/1717) | enhancement(gravsearch): Optimise queries by moving up statements with resource IRIs
- [#1713](https://github.com/dasch-swiss/knora-api/pull/1713) | feat(gravsearch): Allow comparing variables representing resource IRIs
- [#1710](https://github.com/dasch-swiss/knora-api/pull/1710) | update ontology metadata with a comment
- [#1704](https://github.com/dasch-swiss/knora-api/pull/1704) | feat(api-v2): Add test data
- [#1703](https://github.com/dasch-swiss/knora-api/pull/1703) | Add comments to ontology metadata 
- [#1686](https://github.com/dasch-swiss/knora-api/pull/1686) | feat(api-v2): Accept custom timestamps in update/delete requests
- [#1692](https://github.com/dasch-swiss/knora-api/pull/1692) | Create Permissions
- [#1696](https://github.com/dasch-swiss/knora-api/pull/1696) | feat(api-v2): Make inference optional in Gravsearch
- [#1697](https://github.com/dasch-swiss/knora-api/pull/1697) | fix(sipi): Improve performance of file value query
- [#1698](https://github.com/dasch-swiss/knora-api/pull/1698) | feat(api-v2): Accept custom new value IRI when updating value
- [#1700](https://github.com/dasch-swiss/knora-api/pull/1700) | hierarchically ordered Sequence of base classes
- [#1689](https://github.com/dasch-swiss/knora-api/pull/1689) | build: bump SIPI to v3.0.0-rc.5 (DSP-547)
- [#1679](https://github.com/dasch-swiss/knora-api/pull/1679) | Gravsearch optimisations
- [#1663](https://github.com/dasch-swiss/knora-api/pull/1663) | build: add support for SIPI v3.0.0-rc.3 (DSP-433)
- [#1660](https://github.com/dasch-swiss/knora-api/pull/1660) | feat(gravsearch): Remove deprecated functions
- [#1653](https://github.com/dasch-swiss/knora-api/pull/1653) | build:  dockerize fuseki (dsp-30)

#### Bug Fixes:

- [#1626](https://github.com/dasch-swiss/knora-api/pull/1626) | fix(gravsearch): Prevent duplicate results
- [#1587](https://github.com/dasch-swiss/knora-api/pull/1587) | fix (webapi): Add enforcing of restrictions for username and email
- [#1576](https://github.com/dasch-swiss/knora-api/pull/1576) | Add missing env var
- [#1571](https://github.com/dasch-swiss/knora-api/pull/1571) | fixed date string format
- [#1564](https://github.com/dasch-swiss/knora-api/pull/1564) | enable click on save button in case of recoverable error
- [#1751](https://github.com/dasch-swiss/knora-api/pull/1751) | DSP-1022 SIPI_EXTERNAL_HOSTNAME doesn't contain the external hostname
- [#1749](https://github.com/dasch-swiss/knora-api/pull/1749) | fix(api-v2): Don't check file extensions of XSL files and Gravsearch templates (DSP-1005)
- [#1748](https://github.com/dasch-swiss/knora-api/pull/1748) | DSP-756 Tests failing because Knora version header and route are incorrect
- [#1746](https://github.com/dasch-swiss/knora-api/pull/1746) | DSP-932: Don't allow missing StringLiteralV2 value if language tag given
- [#1744](https://github.com/dasch-swiss/knora-api/pull/1744) | DSP-917 Releases pushed to Dockerhub from DSP-API are "dirty"
- [#1733](https://github.com/dasch-swiss/knora-api/pull/1733) | DSP-470 Intermittent bind errors
- [#1728](https://github.com/dasch-swiss/knora-api/pull/1728) | fix(api-v2): Fix post-update check for resource with standoff link (DSP-841)
- [#1723](https://github.com/dasch-swiss/knora-api/pull/1723) | chore(build): Bump testcontainers version (DSP-755)
- [#1706](https://github.com/dasch-swiss/knora-api/pull/1706) | Fix of update of list node info and update of project info
- [#1712](https://github.com/dasch-swiss/knora-api/pull/1712) | fix: failing repository upgrade at startup (DSP-654)
- [#1709](https://github.com/dasch-swiss/knora-api/pull/1709) | fix(OntologyResponderV2): Fix ontology cache update when ontology metadata changed
- [#1701](https://github.com/dasch-swiss/knora-api/pull/1701) | reverse change of Permission JSONs
- [#1693](https://github.com/dasch-swiss/knora-api/pull/1693) | fix(api-v2): Fix generated SPARQL for updating property comment
- [#1699](https://github.com/dasch-swiss/knora-api/pull/1699) | fix(gravsearch): When link property compared in filter, don't compare link value property, too
- [#1691](https://github.com/dasch-swiss/knora-api/pull/1691) | fix: server header (DSP-537)
- [#1681](https://github.com/dasch-swiss/knora-api/pull/1681) | fix: init db scripts (DSP-511)
- [#1669](https://github.com/dasch-swiss/knora-api/pull/1669) | fix: loading of data (DSP-445)

#### Documentation:

- [#1598](https://github.com/dasch-swiss/knora-api/pull/1598) | doc: fix sipi docs link
- [#1609](https://github.com/dasch-swiss/knora-api/pull/1609) | fix complex schema url
- [#1568](https://github.com/dasch-swiss/knora-api/pull/1568) | fixed the URI for the query
- [#1726](https://github.com/dasch-swiss/knora-api/pull/1726) | PersmissionsDocs: remove the attribute
- [#1725](https://github.com/dasch-swiss/knora-api/pull/1725) | docs: Update required mkdocs package
- [#1711](https://github.com/dasch-swiss/knora-api/pull/1711) | update developer and create resource docs 
- [#1684](https://github.com/dasch-swiss/knora-api/pull/1684) | developer guideline
- [#1685](https://github.com/dasch-swiss/knora-api/pull/1685) | docs(api-v2): Document what happens when a resource has a link to a deleted resource
- [#1688](https://github.com/dasch-swiss/knora-api/pull/1688) | docs: fix broken links
- [#1694](https://github.com/dasch-swiss/knora-api/pull/1694) | docs: fix publishing
- [#1621](https://github.com/dasch-swiss/knora-api/pull/1621) | fixing typos for list rendering

#### Other:

- [#1750](https://github.com/dasch-swiss/knora-api/pull/1750) | Update README.md
- [#1747](https://github.com/dasch-swiss/knora-api/pull/1747) | DSP-920 Renaming default github branch to "main" ;  Move to the same base branch
- [#1740](https://github.com/dasch-swiss/knora-api/pull/1740) | DSP-877 Upload api-client-test-data to GitHub release
- [#1738](https://github.com/dasch-swiss/knora-api/pull/1738) | DSP-877 Upload api-client-test-data to GitHub release
- [#1736](https://github.com/dasch-swiss/knora-api/pull/1736) | DSP-877 Upload api-client-test-data to GitHub release
- [#1730](https://github.com/dasch-swiss/knora-api/pull/1730) | DSP-816: Generate client test data for health route
- [#1719](https://github.com/dasch-swiss/knora-api/pull/1719) | change possibly conflictual env var USERNAME (DSP-706)
- [#1720](https://github.com/dasch-swiss/knora-api/pull/1720) | DSP-620 Update release process
- [#1714](https://github.com/dasch-swiss/knora-api/pull/1714) | test: fix generation of test data (DSP-665)
- [#1716](https://github.com/dasch-swiss/knora-api/pull/1716) | bulid: fix sipi image version (DSP-677)
- [#1718](https://github.com/dasch-swiss/knora-api/pull/1718) | DSP-702 Add template for PRs
- [#1715](https://github.com/dasch-swiss/knora-api/pull/1715) | chore(api-v2): Switch from JSONLD-Java to Titanium
- [#1707](https://github.com/dasch-swiss/knora-api/pull/1707) | chore: Update ci workflow
- [#1702](https://github.com/dasch-swiss/knora-api/pull/1702) | Add PR labels (DSP-607)
- [#1695](https://github.com/dasch-swiss/knora-api/pull/1695) | refactor(gravsearch): Clarify optimisations
- [#1678](https://github.com/dasch-swiss/knora-api/pull/1678) | refactor: first steps towards more independent packages (DSP-513)
- [#1680](https://github.com/dasch-swiss/knora-api/pull/1680) | build: bump rules_docker and instructions for installing bazelisk
- [#1674](https://github.com/dasch-swiss/knora-api/pull/1674) | build: add mkdocs for documentation generation (DSP-460)
- [#1480](https://github.com/dasch-swiss/knora-api/pull/1480) | build: add bazel (DSP-437)
- [#1666](https://github.com/dasch-swiss/knora-api/pull/1666) | Fix gren issue in github actions workflow
- [#1662](https://github.com/dasch-swiss/knora-api/pull/1662) | Publish on release only
- [#1661](https://github.com/dasch-swiss/knora-api/pull/1661) | Automated release notes

#### Dependencies:

- [#1721](https://github.com/dasch-swiss/knora-api/pull/1721) | chore: bump sipi to rc.7 (DSP-733)
- [#1735](https://github.com/dasch-swiss/knora-api/pull/1735) | DSP-496 Bump Apache Jena Fuseki and Apache Jena Libraries to 3.16
- [#1737](https://github.com/dasch-swiss/knora-api/pull/1737) | DSP-842 Bump used Bazel version to newly released 3.7.0
- [#1743](https://github.com/dasch-swiss/knora-api/pull/1743) | chore(build): Upgrade Sipi to 3.0.0-rc.8 (DSP-916)
- [#1745](https://github.com/dasch-swiss/knora-api/pull/1745) | chore(build): Update ScalaTest (DSP-919)
- [#1752](https://github.com/dasch-swiss/knora-api/pull/1752) | DSP-1017 Upgrade to Sipi v3.0.0-rc.9

---

## v13.0.0-rc.21 (09/11/2020)

#### Breaking changes:

- [#1724](https://github.com/dasch-swiss/knora-api/pull/1724) | test: Collect client test data from E2E tests (DSP-724)
- [#1727](https://github.com/dasch-swiss/knora-api/pull/1727) | DSP-740 Update List Name
- [#1722](https://github.com/dasch-swiss/knora-api/pull/1722) | feat(api-v1): Change API v1 file uploads to work like API v2 (DSP-41, PR 3)
- [#1233](https://github.com/dasch-swiss/knora-api/pull/1233) | feat(api-v1): Change API v1 file uploads to work like API v2
- [#1708](https://github.com/dasch-swiss/knora-api/pull/1708) | Get Project Permissions

#### Enhancements:

- [#1403](https://github.com/dasch-swiss/knora-api/pull/1403) | feat: Add time value type
- [#1649](https://github.com/dasch-swiss/knora-api/pull/1649) | feat(api-v2): Allow querying for rdfs:label in Gravsearch
- [#1742](https://github.com/dasch-swiss/knora-api/pull/1742) | feat: Add feature toggles (DSP-910)
- [#1741](https://github.com/dasch-swiss/knora-api/pull/1741) | DSP-804: create a child node with a custom IRI
- [#1734](https://github.com/dasch-swiss/knora-api/pull/1734) | feat(api-v2): Add metadata routes (DSP-662)
- [#1739](https://github.com/dasch-swiss/knora-api/pull/1739) | enhancement(api-v2): Optimise checking isDeleted (DSP-848)
- [#1664](https://github.com/dasch-swiss/knora-api/pull/1664) | feat(api-v2): Add support for text file upload (DSP-44)
- [#1652](https://github.com/dasch-swiss/knora-api/pull/1652) | DSP-377 Support Islamic calendar
- [#1717](https://github.com/dasch-swiss/knora-api/pull/1717) | enhancement(gravsearch): Optimise queries by moving up statements with resource IRIs
- [#1713](https://github.com/dasch-swiss/knora-api/pull/1713) | feat(gravsearch): Allow comparing variables representing resource IRIs
- [#1710](https://github.com/dasch-swiss/knora-api/pull/1710) | update ontology metadata with a comment
- [#1704](https://github.com/dasch-swiss/knora-api/pull/1704) | feat(api-v2): Add test data
- [#1703](https://github.com/dasch-swiss/knora-api/pull/1703) | Add comments to ontology metadata 
- [#1686](https://github.com/dasch-swiss/knora-api/pull/1686) | feat(api-v2): Accept custom timestamps in update/delete requests
- [#1692](https://github.com/dasch-swiss/knora-api/pull/1692) | Create Permissions
- [#1696](https://github.com/dasch-swiss/knora-api/pull/1696) | feat(api-v2): Make inference optional in Gravsearch
- [#1697](https://github.com/dasch-swiss/knora-api/pull/1697) | fix(sipi): Improve performance of file value query
- [#1698](https://github.com/dasch-swiss/knora-api/pull/1698) | feat(api-v2): Accept custom new value IRI when updating value
- [#1700](https://github.com/dasch-swiss/knora-api/pull/1700) | hierarchically ordered Sequence of base classes
- [#1689](https://github.com/dasch-swiss/knora-api/pull/1689) | build: bump SIPI to v3.0.0-rc.5 (DSP-547)
- [#1679](https://github.com/dasch-swiss/knora-api/pull/1679) | Gravsearch optimisations
- [#1663](https://github.com/dasch-swiss/knora-api/pull/1663) | build: add support for SIPI v3.0.0-rc.3 (DSP-433)
- [#1660](https://github.com/dasch-swiss/knora-api/pull/1660) | feat(gravsearch): Remove deprecated functions
- [#1653](https://github.com/dasch-swiss/knora-api/pull/1653) | build:  dockerize fuseki (dsp-30)

#### Bug Fixes:

- [#1626](https://github.com/dasch-swiss/knora-api/pull/1626) | fix(gravsearch): Prevent duplicate results
- [#1587](https://github.com/dasch-swiss/knora-api/pull/1587) | fix (webapi): Add enforcing of restrictions for username and email
- [#1751](https://github.com/dasch-swiss/knora-api/pull/1751) | DSP-1022 SIPI_EXTERNAL_HOSTNAME doesn't contain the external hostname
- [#1749](https://github.com/dasch-swiss/knora-api/pull/1749) | fix(api-v2): Don't check file extensions of XSL files and Gravsearch templates (DSP-1005)
- [#1748](https://github.com/dasch-swiss/knora-api/pull/1748) | DSP-756 Tests failing because Knora version header and route are incorrect
- [#1746](https://github.com/dasch-swiss/knora-api/pull/1746) | DSP-932: Don't allow missing StringLiteralV2 value if language tag given
- [#1744](https://github.com/dasch-swiss/knora-api/pull/1744) | DSP-917 Releases pushed to Dockerhub from DSP-API are "dirty"
- [#1733](https://github.com/dasch-swiss/knora-api/pull/1733) | DSP-470 Intermittent bind errors
- [#1728](https://github.com/dasch-swiss/knora-api/pull/1728) | fix(api-v2): Fix post-update check for resource with standoff link (DSP-841)
- [#1723](https://github.com/dasch-swiss/knora-api/pull/1723) | chore(build): Bump testcontainers version (DSP-755)
- [#1706](https://github.com/dasch-swiss/knora-api/pull/1706) | Fix of update of list node info and update of project info
- [#1712](https://github.com/dasch-swiss/knora-api/pull/1712) | fix: failing repository upgrade at startup (DSP-654)
- [#1709](https://github.com/dasch-swiss/knora-api/pull/1709) | fix(OntologyResponderV2): Fix ontology cache update when ontology metadata changed
- [#1701](https://github.com/dasch-swiss/knora-api/pull/1701) | reverse change of Permission JSONs
- [#1693](https://github.com/dasch-swiss/knora-api/pull/1693) | fix(api-v2): Fix generated SPARQL for updating property comment
- [#1699](https://github.com/dasch-swiss/knora-api/pull/1699) | fix(gravsearch): When link property compared in filter, don't compare link value property, too
- [#1691](https://github.com/dasch-swiss/knora-api/pull/1691) | fix: server header (DSP-537)
- [#1681](https://github.com/dasch-swiss/knora-api/pull/1681) | fix: init db scripts (DSP-511)
- [#1669](https://github.com/dasch-swiss/knora-api/pull/1669) | fix: loading of data (DSP-445)

#### Documentation:

- [#1598](https://github.com/dasch-swiss/knora-api/pull/1598) | doc: fix sipi docs link
- [#1609](https://github.com/dasch-swiss/knora-api/pull/1609) | fix complex schema url
- [#1568](https://github.com/dasch-swiss/knora-api/pull/1568) | fixed the URI for the query
- [#1726](https://github.com/dasch-swiss/knora-api/pull/1726) | PersmissionsDocs: remove the attribute
- [#1725](https://github.com/dasch-swiss/knora-api/pull/1725) | docs: Update required mkdocs package
- [#1711](https://github.com/dasch-swiss/knora-api/pull/1711) | update developer and create resource docs 
- [#1684](https://github.com/dasch-swiss/knora-api/pull/1684) | developer guideline
- [#1685](https://github.com/dasch-swiss/knora-api/pull/1685) | docs(api-v2): Document what happens when a resource has a link to a deleted resource
- [#1688](https://github.com/dasch-swiss/knora-api/pull/1688) | docs: fix broken links
- [#1694](https://github.com/dasch-swiss/knora-api/pull/1694) | docs: fix publishing
- [#1621](https://github.com/dasch-swiss/knora-api/pull/1621) | fixing typos for list rendering

#### Other:

- [#1750](https://github.com/dasch-swiss/knora-api/pull/1750) | Update README.md
- [#1747](https://github.com/dasch-swiss/knora-api/pull/1747) | DSP-920 Renaming default github branch to "main" ;  Move to the same base branch
- [#1740](https://github.com/dasch-swiss/knora-api/pull/1740) | DSP-877 Upload api-client-test-data to GitHub release
- [#1738](https://github.com/dasch-swiss/knora-api/pull/1738) | DSP-877 Upload api-client-test-data to GitHub release
- [#1736](https://github.com/dasch-swiss/knora-api/pull/1736) | DSP-877 Upload api-client-test-data to GitHub release
- [#1730](https://github.com/dasch-swiss/knora-api/pull/1730) | DSP-816: Generate client test data for health route
- [#1719](https://github.com/dasch-swiss/knora-api/pull/1719) | change possibly conflictual env var USERNAME (DSP-706)
- [#1720](https://github.com/dasch-swiss/knora-api/pull/1720) | DSP-620 Update release process
- [#1714](https://github.com/dasch-swiss/knora-api/pull/1714) | test: fix generation of test data (DSP-665)
- [#1716](https://github.com/dasch-swiss/knora-api/pull/1716) | bulid: fix sipi image version (DSP-677)
- [#1718](https://github.com/dasch-swiss/knora-api/pull/1718) | DSP-702 Add template for PRs
- [#1715](https://github.com/dasch-swiss/knora-api/pull/1715) | chore(api-v2): Switch from JSONLD-Java to Titanium
- [#1707](https://github.com/dasch-swiss/knora-api/pull/1707) | chore: Update ci workflow
- [#1702](https://github.com/dasch-swiss/knora-api/pull/1702) | Add PR labels (DSP-607)
- [#1695](https://github.com/dasch-swiss/knora-api/pull/1695) | refactor(gravsearch): Clarify optimisations
- [#1678](https://github.com/dasch-swiss/knora-api/pull/1678) | refactor: first steps towards more independent packages (DSP-513)
- [#1680](https://github.com/dasch-swiss/knora-api/pull/1680) | build: bump rules_docker and instructions for installing bazelisk
- [#1674](https://github.com/dasch-swiss/knora-api/pull/1674) | build: add mkdocs for documentation generation (DSP-460)
- [#1480](https://github.com/dasch-swiss/knora-api/pull/1480) | build: add bazel (DSP-437)
- [#1666](https://github.com/dasch-swiss/knora-api/pull/1666) | Fix gren issue in github actions workflow
- [#1662](https://github.com/dasch-swiss/knora-api/pull/1662) | Publish on release only
- [#1661](https://github.com/dasch-swiss/knora-api/pull/1661) | Automated release notes

---

## v12.0.0 (27/01/2020)

#### Breaking API Changes

- [#1439](https://github.com/dasch-swiss/knora-api/issues/1439) JSON-LD Serialization of an xsd:dateTimeStamp

#### New Features and Enhancements

- [#1509](https://github.com/dasch-swiss/knora-api/pull/1509) Support lists admin endpoint
- [#1466](https://github.com/dasch-swiss/knora-api/pull/1466) Optimise generated SPARQL

#### Bug Fixes

- [#1569](https://github.com/dasch-swiss/knora-api/issues/1569) broken ark
- [#1559](https://github.com/dasch-swiss/knora-api/issues/1559) Admin lists: createChildNode should send a httpPost request, not httpPut

---

## v11.0.0 (16/12/2019)

#### Breaking Changes

- [#1344](https://github.com/dasch-swiss/knora-api/issues/1344) Gravsearch ForbiddenResource result and permissions of linked resources 
- [#1202](https://github.com/dasch-swiss/knora-api/issues/1202) Implement upload of PDF and text files in API v2. Users with files in Sipi under `/server` must move them to `/images` when upgrading.

#### Bug Fixes:

- [#1531](https://github.com/dasch-swiss/knora-api/issues/1531) Sipi's mimetype_consistency fails with .bin file
- [#1430](https://github.com/dasch-swiss/knora-api/issues/1430) Creating the first resource with an image inside a project fails with Sipi not finding the project folder 
- [#924](https://github.com/dasch-swiss/knora-api/issues/924) Get dependent resources Iris

---

## v10.1.1 (27/11/2019)

---

## v10.1.0 (27/11/2019)

---

## v10.0.0 (22/10/2019)

#### Breaking Changes

- [#1346](https://github.com/dasch-swiss/knora-api/issues/1346) Richtext/HTML in page anchor link

#### Enhancements:

- [#1457](https://github.com/dasch-swiss/knora-api/issues/1457) Upgrade sipi to 2.0.1

#### Bug Fixes:

- [#1460](https://github.com/dasch-swiss/knora-api/issues/1460) Build banner in README is broken

#### Documentation:

- [#1481](https://github.com/dasch-swiss/knora-api/issues/1481) build badge in README has broken link

#### Other

- [#1449](https://github.com/dasch-swiss/knora-api/issues/1449) Add Makefile-based task execution
- [#1401](https://github.com/dasch-swiss/knora-api/issues/1401) Enable testing docs generation in Travis

---

## v9.1.0 (26/09/2019)

#### Enhancements:

- [#1421](https://github.com/dhlab-basel/Knora/issues/1421) Physically deleting a resource

#### Documentation:

- [#1407](https://github.com/dhlab-basel/Knora/issues/1407) Document ARK URLs for projects

---

## v9.0.0 (29/08/2019)

#### Breaking Changes

- [#1411](https://github.com/dhlab-basel/Knora/issues/1411) Moved `/admin/groups/members/GROUP_IRI` to `/admin/groups/GROUP_IRI/members`
- [#1231](https://github.com/dhlab-basel/Knora/issues/1231) Change value permissions
- [#763](https://github.com/dhlab-basel/Knora/issues/763) refactor splitMainResourcesAndValueRdfData so it uses SparqlExtendedConstructResponse

#### Enhancements:

- [#1373](https://github.com/dhlab-basel/Knora/issues/1373) The startup ends in a thrown exception if the triplestore is not up-to-date
- [#1364](https://github.com/dhlab-basel/Knora/issues/1364) Add support for Redis cache
- [#1360](https://github.com/dhlab-basel/Knora/issues/1360) Build and publish Knora version specific docker images for GraphDB Free and SE
- [#1358](https://github.com/dhlab-basel/Knora/issues/1358) Add admin route to dump project data

#### Bug Fixes:

- [#1394](https://github.com/dhlab-basel/Knora/issues/1394) Using dockerComposeUp to start the stack, fails to find Redis at startup

#### Documentation:

- [#1386](https://github.com/dhlab-basel/Knora/issues/1386) Add lists admin API documentation

#### Other

- [#1412](https://github.com/dhlab-basel/Knora/issues/1412) Change release notes to be based on issues

---

## v8.0.0 (14/06/2019)
- [feature(webapi): Add GraphDB-Free startup support (#1351)](https://github.com/dhlab-basel/Knora/commit/5ecb54c563dc2ec38dbbcdf544c5f86f0ce90d0d) - @subotic
- [feature(webapi): Add returning of fixed public user information (#1348)](https://github.com/dhlab-basel/Knora/commit/ff6b140bf7e6b8b481bb1773a5accc3ba5d5d9fe) - @subotic
- [feat(api-v2): No custom permissions higher than defaults (#1337)](https://github.com/dhlab-basel/Knora/commit/7b61b49d7686a13a79f5f25c5f0e20cab0b6c12f) - @benjamingeer
- [feat(upgrade): Improve upgrade framework (#1345)](https://github.com/dhlab-basel/Knora/commit/06487b1e6b227cc7794e53f19e30551774015686) - @benjamingeer
- [test(webapi): Add new user authentication (#1201)](https://github.com/dhlab-basel/Knora/commit/1845eb1a0caa0441483d28176b84a2a59cfebe3a) - @subotic
- [chore(webapi): Add request duration logging (#1347)](https://github.com/dhlab-basel/Knora/commit/9b701f9adcb3e710e8e39bc35c3cb7964bde531a) - @subotic
- [feat(api-v2): Make values citable (#1322)](https://github.com/dhlab-basel/Knora/commit/9f99af11ca466da8943f2d6b44342fed2beca9ba) - @benjamingeer
- [Leibniz ontology  (#1326)](https://github.com/dhlab-basel/Knora/commit/56e311d03dfed2b42f490abab01a0d612be11d15) - @SepidehAlassi
- [feature(webapi): add CORS allow header (#1340)](https://github.com/dhlab-basel/Knora/commit/64177807a070a36c6c852b8f7d79645f45d0ce7b) - @subotic
- [fix(sipi): Return permissions for a previous version of a file value. (#1339)](https://github.com/dhlab-basel/Knora/commit/9a3cee3b665fa1fdd82615932f43b1bd8551f402) - @benjamingeer
- [fix(scripts): add admin ontology data to correct graph (#1333)](https://github.com/dhlab-basel/Knora/commit/002eca45187e4c7ec30129c57ccaa095547a420e) - @subotic
- [fix(sipi): Don't try to read a file value in a deleted resource. (#1329)](https://github.com/dhlab-basel/Knora/commit/3adb22e88615e7edcdf2c2d2c8ab905dbe7f40db) - @benjamingeer
- [docs(api-v2): Fix sample responses. (#1327)](https://github.com/dhlab-basel/Knora/commit/904c638b537350e2cfe881ccf3e9b51d955150c1) - @benjamingeer
- [fix(api-v2): Fix typo. (#1325)](https://github.com/dhlab-basel/Knora/commit/72d89dc4a870fa8bd6f1ac60b0743a022adbb99c) - @benjamingeer
- [Handle List Nodes in Response (#1321)](https://github.com/dhlab-basel/Knora/commit/611f42880902065d28fcb84742f90757840d9ed5) - @tobiasschweizer
- [feat(api-v2): Return standoff markup separately from text values (#1307)](https://github.com/dhlab-basel/Knora/commit/ffbb5965223dfaea6cec77201fea5c9fd11bbc67) - @benjamingeer
- [BEOL: Import comments for Meditationes (#1281)](https://github.com/dhlab-basel/Knora/commit/9480f42faa64873a82fb2528a0bc8011669c7c49) - @tobiasschweizer
- [feat(triplestore): Log SPARQL query if triplestore doesn't respond. (#1292)](https://github.com/dhlab-basel/Knora/commit/522b3a9f6effad2d9fa1332c85e4a406a846d223) - @benjamingeer
- [Support list nodes in Gravsearch (#1314)](https://github.com/dhlab-basel/Knora/commit/0a1845c54a26d4fe00d2084daa7bff73ae571280) - @tobiasschweizer

---

## v7.0.0 (03/05/2019)
- [fix(api-v2): Cache base class IRIs correctly when creating/updating class (#1311)](https://github.com/dhlab-basel/Knora/commit/db8b938f605aad966de4f77d269be404f56f2a14) - @benjamingeer
- [chore(standoff): Use Base64-encoded UUIDs in standoff tags. (#1301)](https://github.com/dhlab-basel/Knora/commit/20736f737e84ba540fe022e3929cda645ef3137d) - @benjamingeer
- [feat(api-v2): Allow a resource to be created as a specified user (#1306)](https://github.com/dhlab-basel/Knora/commit/2b2961e6279dcf811cfdfed3c27e3b0923001d98) - @benjamingeer
- [feat(admin): Give the admin ontology an external schema (#1291)](https://github.com/dhlab-basel/Knora/commit/31ab1ca9196c365628108380cbedb69cd3249df5) - @benjamingeer
- [fix(api-v2): Remove INFORMATION SEPARATOR TWO from text in the simple schema. (#1299)](https://github.com/dhlab-basel/Knora/commit/f888cc68649cde6314bf186454f7682e25597d5d) - @benjamingeer
- [test: Compare Knora response with its class definition (#1297)](https://github.com/dhlab-basel/Knora/commit/df8af5ddfe1c173f23446b38adf3fb13b13d83be) - @benjamingeer
- [docs(api-admin): fix description of the change password payload (#1285)](https://github.com/dhlab-basel/Knora/commit/5c0db97e0e26bf8e5b23d393a1bf6ae896e16b15) - @loicjaouen
- [fix(api-v1): Fix double escaping of newline. (#1296)](https://github.com/dhlab-basel/Knora/commit/855a51d838617a3733d6c67d8f657c47c8781032) - @benjamingeer
- [fix (tei beol): fix problems in XSLT (#1260)](https://github.com/dhlab-basel/Knora/commit/a568257c6897ffb75285a36c86f8176bfd7d1958) - @tobiasschweizer
- [refactor(ontology): Make knora-admin a separate ontology (#1263)](https://github.com/dhlab-basel/Knora/commit/11c20080e433bb20e39f90ac036c6387c161ff99) - @benjamingeer
- [a handfull of changes in documentation and error messages (#1278)](https://github.com/dhlab-basel/Knora/commit/9bf02d41fd7429bf10b9a64c8a52ae6137b55b2a) - @loicjaouen
- [docs: fix missing username (#1269)](https://github.com/dhlab-basel/Knora/commit/897a6ec95ddfb6d60abb7941b1f92a63485a2aab) - @loicjaouen
- [feat(api-v2): Get resources in a particular class from a project (#1251)](https://github.com/dhlab-basel/Knora/commit/480ef721548b3552875a614cc408ab7b72527b9d) - @benjamingeer
- [fix(sipi): Improve error checking of Sipi's knora.json response. (#1279)](https://github.com/dhlab-basel/Knora/commit/d3e8bb94bbd5db460c55f508f7d5cfe5d80d1c05) - @benjamingeer
- [feat(api-v2): Return user's permission on resources and values (#1257)](https://github.com/dhlab-basel/Knora/commit/30321b806e24cdafcb91210f26881f5dab46ed91) - @benjamingeer
- [fix(api-v1): Escape rdfs:label in bulk import. (#1276)](https://github.com/dhlab-basel/Knora/commit/4781384a8bc1f0e8698659929d7a0b6693179d7e) - @benjamingeer
- [chore(webapi): Remove persistent map code (#1254)](https://github.com/dhlab-basel/Knora/commit/26496703eff89a52d591f81f9e021731d452df7a) - @benjamingeer
- [docs (api-v2): Update outdated ARK documentation. (#1252)](https://github.com/dhlab-basel/Knora/commit/b3ecebec2bf6fb3c49ea34a4b103bcbcdc7fd51a) - @benjamingeer
- [Update build.properties (#1265)](https://github.com/dhlab-basel/Knora/commit/5401943b7c81e0efb21c2b231c98a7afed60ede6) - @subotic

---

## v6.0.1 (22/03/2019)
- [chore: releasing-v6.0.1 (#1270)](https://github.com/dhlab-basel/Knora/commit/f65a02a82050ebde72cbdbe89faba217646d1ce5) - @subotic
- [chore(webapi): Add script for loading of a minimal set of data (#1267)](https://github.com/dhlab-basel/Knora/commit/7ed1425d84a42a7115af4885a44e4adc467e6eae) - @subotic
- [fix (beolPersonLabel) typo in label of hasBirthPlace (#1248)](https://github.com/dhlab-basel/Knora/commit/a08117737e2d6b159a2a569801938b67d76a95ce) - @SepidehAlassi
- [fix (webapi): message typo (#1244)](https://github.com/dhlab-basel/Knora/commit/9cea41a3a5ad6630f1fbc75e2656533ae6bf6da2) - @subotic
- [Unescape standoff string attributes when verifying text value update (#1242)](https://github.com/dhlab-basel/Knora/commit/af35c9520ccf0dceab0eb3bba83aa7c7aba8491b) - @benjamingeer
- [docs: fix user admin api (#1237)](https://github.com/dhlab-basel/Knora/commit/4fcda61f6dc667f5950b15bb7a6536bd503a9565) - @subotic

---

## v6.0.0 (28/02/2019)
# Release Notes

- MAJOR: Use HTTP POST to mark resources and values as deleted (#1203)

- MAJOR: Reorganize user and project routes (#1209)

- FEATURE: Secure routes returning user information (#961)

- MAJOR: Change all `xsd:dateTimeStamp` to `xsd:dateTime` in the triplestore (#1211).
  Existing data must be updated; see `upgrade/1211-datetime` for instructions.

- FIX: Ignore order of attributes when comparing standoff (#1224).

- FEATURE: Query version history (#1214)

- FIX: Don't allow conflicting cardinalities (#1229)

- MAJOR: Remove preview file values (#1230). Existing data must be updated;
  see `upgrade/1230-delete-previews` for instructions.

---

## v5.0.0 (05/02/2019)
# Release Notes

- MAJOR: Fix property names for incoming links (#1144))
- MAJOR: Generate and resolve ARK URLs for resources (#1161). Projects
  that have resource IRIs that do not conform to the format specified in
  https://docs.knora.org/paradox/03-apis/api-v2/knora-iris.html#iris-for-data
  must update them.
- MAJOR: Use project shortcode in IIIF URLs (#1191). If you have file value IRIs containing the substring `/reps/`, you must replace `/reps/` with `/values/`.

- FEATURE: Update resource metadata in API v2 (#1131)
- FEATURE: Allow setting resource creation date in bulk import #1151)
- FEATURE: The `v2/authentication` route now also initiates cookie creation (the same as `v1/authentication`) (#1159)
- FEATURE: Allow to specify restricted view settings for a project which Sipi will adhere to (#690).

- FIX: Triplestore connection error when using dockerComposeUp (#1122)
- FIX: Reject link value properties in Gravsearch queries in the simple schema (#1145)
- FIX: Fix error-checking when updating cardinalities in ontology API (#1142)
- FIX: Allow hasRepresentation in an ontology used in a bulk import (#1171)
- FIX: Set cookie domain to the value specified in `application.conf` with the setting `cookie-domain` (#1169)
- FIX: Fix processing of shared property in bulk import (#1182)

---

## v4.0.0 (12/12/2018)
# v4.0.0 Release Notes

- MAJOR CHANGE: mapping creation request and response formats have changed (#1094)
- MINOR CHANGE: Update technical user docs (#1085)
- BUGFIX CHANGE: Fix permission checking in API v2 resource creation (#1104)
---

## v3.0.0 (30/11/2018)
# v3.0.0 Release Notes

- [BREAKING ONTOLOGY CHANGE] The property `knora-base:username` was added and is required for `knora-base:User`. (#1047)
- [BREAKING API CHANGE] The `/admin/user` API has changed due to adding the `username` property. (#1047)
- [FIX] Incorrect standoff to XML conversion if empty tag has empty child tag (#1054)
- [FEATURE] Add default permission caching (#1062)
- [FIX] Fix unescaping in update check and reading standoff URL (#1074)
- [FIX] Incorrect standoff to XML conversion if empty tag has empty child tag (#1054)
- [FEATURE] Create image file values in API v2 (#1011). Requires Sipi with tagged commit `v1.4.1-SNAPSHOT` or later.
---

## v2.1.0 (02/11/2018)
### New features:

- Implement graph query in API v2 (#1009)
- Expose additional `webapi` settings as environment variables. Please see the [Configuration](https://docs.knora.org/paradox/04-deployment/configuration.html) section in the documentation for more information (#1025)

### Bugfixes:

- sipi container config / sipi not able to talk to knora (#994)
---

## v2.1.0-snapshot (22/10/2018)

---

## v2.0.0 (13/09/2018)
This is the first release with the new version numbering convention. From now on, if any changes
to the existing data are necessary for a release, then this release will have its major number increased.
Please see the [Release Versioning Convention](https://github.com/dhlab-basel/Knora#release-versioning-convention) description.

### Required changes to existing data:

- a `knora-base:ListNode` must have at least one `rdfs:label`. (@github[#991](#990))

### New features:

- add developer-centric docker-compose.yml for starting the Knora / GraphDB / Sipi / Salsah1 (@github[#979](#979))
- configure `webapi` and `salsah1` thorough environment variables (@github[#979](#979))
- update for Java 10 (@github[#979](#979))
- comment out the generation of fat jars from `KnoraBuild.sbt` (for now) (@github[#979](#979))
- update ehcache (@github[#979](#979))
- update sbt to 1.2.1 (@github[#979](#979))
- remove Kamon monitoring (for now) since we don't see anything meaningful there. We probably will have to instrument Knora by hand and then use Kamon for access. (@github[#979](#979))
- update Dockerfiles for `webapi` and `salsah1` (@github[#979](#979))
- follow subClassOf when including ontologies in XML import schemas (@github[#991](#991))
- add support for adding list child nodes (@github[#991](#990))
- add support for shared ontologies (@github[#987](#987))

### Bugfixes:

- trouble with xml-checker and/or consistency-checker during bulk import (@github[#978](#978))
- ontology API error with link values (@github[#988](#988))
---

## v1.7.1 (29/08/2018)
Knora-Stack compatible versions:
---
Knora v1.7.1 - Salsah v2.1.2 - Sipi v1.4.0 - GraphDB v8.5.0

- doc (webapi): add yourkit acknowledgment (#983)
- Don't allow class with cardinalities on P and on a subproperty of P (#982)
- doc (webapi): add LHTT project shortcode (#981)
- feature (webapi): not return or allow changing of built-in users (#975) 
- fix (webapi): startup check does not detect running triplestore (#969)
- Fix bulk import parsing bug and limit concurrent client connections (#973)
---

## v1.7.0 (16/08/2018)
See the closed tickets on the [v1.7.0 milestone](https://github.com/dhlab-basel/Knora/milestone/11).

Knora-Stack compatible versions:
---

Knora v1.7.0 - Salsah v2.1.0 - Sipi v1.4.0 - GraphDB v8.5.0

Required changes to existing data:
----------------------------------

- To use the inferred Gravsearch predicate `knora-api:standoffTagHasStartAncestor`,
  you must recreate your repository with the updated `KnoraRules.pie`.

New features:
-------------

- Gravsearch queries can now match standoff markup (#910).
- Add Graphdb-Free initialization scripts for local and docker installation (#955).
- Create temp dirs at startup (#951)
- Update versions of monitoring tools (#951)


Bugfixes:
---------

- timeout or java.lang.OutOfMemoryError when using /v1/resources/xmlimportschemas/ for some ontologies (#944)
- Timeout cleanup (#951)
- Add separate dispatchers (#945)

---

## v1.6.0 (29/06/2018)
v1.6.0 Release Notes
====================

See the
[release](https://github.com/dhlab-basel/Knora/releases/tag/v1.6.0) and closed tickets on the
[v1.6.0 milestone](https://github.com/dhlab-basel/Knora/milestone/10) on Github.

Required changes to existing data:
----------------------------------

- A project is now required to have at least one description, so potentially a description will need
  to be added to those projects that don't have one.

New features:
-------------

General:

- Added a `/health` endpoint
- KnoraService waits on startup for a triplestore before trying to load the ontologies

Gravsearch enhancements:

- Accept queries in POST requests (@github[#650](#650)).
- Allow a Gravsearch query to specify the IRI of the main resource (@github[#871](#871)) (by allowing `BIND`).
- Allow `lang` to be used with `!=`.
- A `UNION` or `OPTIONAL` can now be nested in an `OPTIONAL` (@github[#882](#882)).
- Gravsearch now does type inference (@github[#884](#884)).
- The Knora API v2 complex schema can now be used in Gravsearch, making it possible to search
  for list nodes (@github[#899](#899)).

Admin API:

- Make project description required (@github[#875](#875)).

Conversion to TEI:

- Conversion of standard standoff entities to TEI
- Custom conversion of project specific standoff entities and metadata to TEI

Sipi integration:

- The Knora specific Sipi configuration and scripts can now be found under the `sipi/` directory (@github[#404](#404)).
- Documentation on how Sipi can be started changed (@github[#404](#404)).

Bugfixes:
---------

- Allow a class or property definition to have more than one object for `rdf:type` (@github[#885](#885)).
- Exclude list values from v2 fulltext search (@github[#906](#906)).

Gravsearch fixes:

- Allow the `lang` function to be used in a comparison inside AND/OR (@github[#846](#846)).
- Fix the processing of resources with multiple incoming links that use the same property (@github[#878](#878)).
- Fix the parsing of a FILTER inside an OPTIONAL (@github[#879](#879)).
- Require the `match` function to be the top-level expression in a `FILTER`.

---

## v1.5.0 (31/05/2018)
See [v1.5.0 milestone](https://github.com/dhlab-basel/Knora/milestone/9) for a full list of closed tickets.

New features:
-------------

- Resources can be returned in the simple ontology schema (#833).
- Text values can specify the language of the text (#819).
- Responses can be returned in Turtle and RDF/XML (#851).

Bugfixes:
---------

- Incorrect representation of IRI object values in JSON-LD (#835)
- GenerateContributorsFile broken (#797)
---

## v1.4.0 (30/04/2018)
Required changes to existing data:
----------------------------------

- Every ontology must now have the property `knora-base:attachedToProject`, which points to the IRI of the project that is responsible for the ontology. This must be added to each project-specific ontology in existing repositories. All built-in ontologies have been updated to have this property, and must, therefore, be reloaded into existing repositories.  
The property `knora-base:projectOntology` has been removed, and must be removed from project definitions in existing repositories.

- Every project now needs to have the property `knora-base:projectShortcode` set.

New features:
-------------

- Added OpenAPI / Swagger API documentation route
- The Knora API server now checks the validity of ontologies on startup.
- The property ``knora-base:projectShortcode`` is now a required property (was optional).

Bugfixes:
---------

-   API v1 extended search was not properly handling multiple conditions
    on list values (issue \#800)
-   Fix image orientation in SALSAH 1 (issue \#726)

---

## v1.3.1 (06/04/2018)

---

## v1.3.0 (28/03/2018)
### Required changes to existing data:

#### 1. Replace salsah-gui ontology

You must replace the ``salsah-gui`` ontology that you have in the triplestore with the one
in ``salsah-gui.ttl``.

### New features:

- More support for salsah-gui elements and attributes in ontologies
  - Serve the ``salsah-gui`` ontology in API v2 in the default schema.
  - Show ``salsah-gui:guiElement`` and ``salsah-gui:guiAttribute`` when serving ontologies in API v2 in the default schema.
  - Allow ``salsah-gui:guiElement`` and ``salsah-gui:guiAttribute`` to be included in new property definitions created via API v2.
  - Change ``salsah-gui`` so that GraphDB's consistency checker can check the use of ``guiElement`` and ``guiAttribute``.
- Changes to ``application.conf``. The ``sipi`` and ``web-api`` sections have received a big update, adding separate settings  for internal and external host settings:

```

    app {
        knora-api {
            // relevant for direct communication inside the knora stack
            internal-host = "0.0.0.0"
            internal-port = 3333

            // relevant for the client, i.e. browser
            external-protocol = "http" // optional ssl termination needs to be done by the proxy
            external-host = "0.0.0.0"
            external-port = 3333
        }

        sipi {
            // relevant for direct communication inside the knora stack
            internal-protocol = "http"
            internal-host = "localhost"
            internal-port = 1024

            // relevant for the client, i.e. browser
            external-protocol = "http"
            external-host = "localhost"
            external-port = 1024

            prefix = "knora"
            file-server-path = "server"
            path-conversion-route = "convert_from_binaries"
            file-conversion-route = "convert_from_file"
            image-mime-types = ["image/tiff", "image/jpeg", "image/png", "image/jp2"]
            movie-mime-types = []
            sound-mime-types = []
        }

        salsah1 {
            base-url = "http://localhost:3335/"
            project-icons-basepath = "project-icons/"
        }
    }
```

### Bugfixes:

- When API v2 served ``knora-api`` (default schema), ``salsah-gui:guiElement`` and ``salsah-gui:guiAttribute`` were not shown in properties in that ontology.
- The predicate ``salsah-gui:guiOrder`` was not accepted when creating a property via API v2.
