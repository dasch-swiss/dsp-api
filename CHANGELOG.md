# Changelog

## [17.2.0](https://www.github.com/dasch-swiss/dsp-api/compare/v17.1.0...v17.2.0) (2022-01-10)


### Bug Fixes

* **search:** Return matching sub-nodes when searching for list label (DEV-158) ([#1973](https://www.github.com/dasch-swiss/dsp-api/issues/1973)) ([7e8c759](https://www.github.com/dasch-swiss/dsp-api/commit/7e8c759e8d1f132832cb8acf140be8017e48fd27))


### Enhancements

* return a DeletedResource or DeletedValue instead of 404 if a deleted resource or value is requested (DEV-226) ([#1960](https://www.github.com/dasch-swiss/dsp-api/issues/1960)) ([c78e252](https://www.github.com/dasch-swiss/dsp-api/commit/c78e2522a96daf3fc52dc73d7daddf8f2a7d8c6a))

## [17.1.0](https://www.github.com/dasch-swiss/dsp-api/compare/v17.0.4...v17.1.0) (2021-12-20)


### Enhancements

* **listsADM:** add canDeleteList route ([#1968](https://www.github.com/dasch-swiss/dsp-api/issues/1968)) ([c276625](https://www.github.com/dasch-swiss/dsp-api/commit/c27662540f4ae637c66f394c552cc7721a04cf23))


### Maintenance

* **deps:** bump log4j to 2.17.0 and Fuseki to 4.3.2 (DEV-334) ([#1972](https://www.github.com/dasch-swiss/dsp-api/issues/1972)) ([afb6587](https://www.github.com/dasch-swiss/dsp-api/commit/afb6587a772de20ebebacf9f8d628387e3b43455))

### [17.0.4](https://www.github.com/dasch-swiss/dsp-api/compare/v17.0.3...v17.0.4) (2021-12-17)


### Bug Fixes

* **authentication:** delete cookie (in chrome) on logout (DEV-325) ([#1970](https://www.github.com/dasch-swiss/dsp-api/issues/1970)) ([b2c9204](https://www.github.com/dasch-swiss/dsp-api/commit/b2c9204af61d56ac8cf486e4f30a8f5e8b6cb742))
* **candeletecardinalities:** return canDoResponse of false instead of throwing an exception for inherited cardinalities (DEV-314) ([#1966](https://www.github.com/dasch-swiss/dsp-api/issues/1966)) ([55b5d4b](https://www.github.com/dasch-swiss/dsp-api/commit/55b5d4b1e6494129553a2b70a43af506f09a8d79))
* **ontology:** cardinality of one can be added to classes as long as not used in data ([#1958](https://www.github.com/dasch-swiss/dsp-api/issues/1958)) ([2cebac7](https://www.github.com/dasch-swiss/dsp-api/commit/2cebac7d6ed8d717c09b062427154504baf4fee6))


### Maintenance

* bump logging libraries (DEV-333) ([#1969](https://www.github.com/dasch-swiss/dsp-api/issues/1969)) ([f680c4f](https://www.github.com/dasch-swiss/dsp-api/commit/f680c4ff3da0af844b96c7a6cde42e2deba0c87a))

### [17.0.3](https://www.github.com/dasch-swiss/dsp-api/compare/v17.0.2...v17.0.3) (2021-12-14)


### Maintenance

* bump Fuseki (log4shell fix) (IT-4) ([#1965](https://www.github.com/dasch-swiss/dsp-api/issues/1965)) ([86fa251](https://www.github.com/dasch-swiss/dsp-api/commit/86fa251e931e8d23308fcd2fe54b2ee574c822bb))
* **projectMetadataV2:** remove projectMetadataV2 implementation ([#1962](https://www.github.com/dasch-swiss/dsp-api/issues/1962)) ([7b95d66](https://www.github.com/dasch-swiss/dsp-api/commit/7b95d66e1adf6a3003700758494a4725fabf956d))

### [17.0.2](https://www.github.com/dasch-swiss/dsp-api/compare/v17.0.1...v17.0.2) (2021-12-10)


### Maintenance

* bump db version (add shiro.ini)(DEV-302)([#1961](https://www.github.com/dasch-swiss/dsp-api/issues/1961)) ([d147bf6](https://www.github.com/dasch-swiss/dsp-api/commit/d147bf6b662032ca83165ceaa36a3be6ebde48c6))

### [17.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v17.0.0...v17.0.1) (2021-12-06)


### Maintenance

* fix issues with fuseki (DEV-277) ([#1953](https://www.github.com/dasch-swiss/dsp-api/issues/1953)) ([4c1a5f1](https://www.github.com/dasch-swiss/dsp-api/commit/4c1a5f17ee343f9fd3f42780d8a85e399efae51b))


### Documentation

* Updated readme ([#1956](https://www.github.com/dasch-swiss/dsp-api/issues/1956)) ([774b68d](https://www.github.com/dasch-swiss/dsp-api/commit/774b68dacadb14ad337026cf3a02481bd9dc95c9))

## [17.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v16.0.1...v17.0.0) (2021-11-25)


### ⚠ BREAKING CHANGES

* add archive representation to DSP-API (DEV-17) (#1926)

### Maintenance

* bump fuseki base container version ([#1946](https://www.github.com/dasch-swiss/dsp-api/issues/1946)) ([cf8bdec](https://www.github.com/dasch-swiss/dsp-api/commit/cf8bdec91c15b07a303e6661ad8e1cc861499cfc))
* bump java and sipi version (only security updates) (DEV-263) ([#1950](https://www.github.com/dasch-swiss/dsp-api/issues/1950)) ([fe6106f](https://www.github.com/dasch-swiss/dsp-api/commit/fe6106f551cfc48822058d2ea54a3a2b1145a4e1))


### Enhancements

* add archive representation to DSP-API (DEV-17) ([#1926](https://www.github.com/dasch-swiss/dsp-api/issues/1926)) ([0123a8f](https://www.github.com/dasch-swiss/dsp-api/commit/0123a8f62b08c31e053c337d22c9d8dffaf321c5))

### [16.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v16.0.0...v16.0.1) (2021-11-22)


### Bug Fixes

* **canDeleteCardinalities:** canDeleteCardinalities checks too eagerly (DEV-187) ([#1941](https://www.github.com/dasch-swiss/dsp-api/issues/1941)) ([298ba47](https://www.github.com/dasch-swiss/dsp-api/commit/298ba470537104d4526d4d32a7b19f4821d06ac1))

## [16.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v15.1.3...v16.0.0) (2021-11-19)

### ⚠ BREAKING CHANGES

* **listsADM:** remove new lists implementation (DEV-160) ([#1932](https://www.github.com/dasch-swiss/dsp-api/issues/1932)) ([24e34dd](https://www.github.com/dasch-swiss/dsp-api/commit/24e34ddc20a4f3978b57b539db711afae34d0e7c))

### Bug Fixes

* **projectsADM:** clear cache after changing project (DEV-239) ([#1943](https://www.github.com/dasch-swiss/dsp-api/issues/1943)) ([17c5c09](https://www.github.com/dasch-swiss/dsp-api/commit/17c5c093381a351beccac3a18df5136f02a970c7))

### Maintenance

* **groupsADM:** improve value objects implementation (DEV-160) ([#1932](https://www.github.com/dasch-swiss/dsp-api/issues/1932)) ([24e34dd](https://www.github.com/dasch-swiss/dsp-api/commit/24e34ddc20a4f3978b57b539db711afae34d0e7c))
* **listsADM:** remove new lists implementation (DEV-160) ([#1932](https://www.github.com/dasch-swiss/dsp-api/issues/1932)) ([24e34dd](https://www.github.com/dasch-swiss/dsp-api/commit/24e34ddc20a4f3978b57b539db711afae34d0e7c))
* release v16.0.0 ([8e5f494](https://www.github.com/dasch-swiss/dsp-api/commit/8e5f494ca5bb2f96922f817bfbea21bdf1da73d7))
* release v16.0.0 ([ba6923d](https://www.github.com/dasch-swiss/dsp-api/commit/ba6923da875271b1b8797343238ba4f8b01dbafa))

### [15.1.3](https://www.github.com/dasch-swiss/dsp-api/compare/v15.1.2...v16.0.0) (2021-11-19)

### ⚠ BREAKING CHANGES

* **listsADM:** remove new lists implementation (DEV-160) ([#1932](https://www.github.com/dasch-swiss/dsp-api/issues/1932)) ([24e34dd](https://www.github.com/dasch-swiss/dsp-api/commit/24e34ddc20a4f3978b57b539db711afae34d0e7c))

### Bug Fixes

* **projectsADM:** clear cache after changing project (DEV-239) ([#1943](https://www.github.com/dasch-swiss/dsp-api/issues/1943)) ([17c5c09](https://www.github.com/dasch-swiss/dsp-api/commit/17c5c093381a351beccac3a18df5136f02a970c7))

### Maintenance

* **groupsADM:** improve value objects implementation (DEV-160) ([#1932](https://www.github.com/dasch-swiss/dsp-api/issues/1932)) ([24e34dd](https://www.github.com/dasch-swiss/dsp-api/commit/24e34ddc20a4f3978b57b539db711afae34d0e7c))
* **listsADM:** remove new lists implementation (DEV-160) ([#1932](https://www.github.com/dasch-swiss/dsp-api/issues/1932)) ([24e34dd](https://www.github.com/dasch-swiss/dsp-api/commit/24e34ddc20a4f3978b57b539db711afae34d0e7c))



### [15.1.2](https://www.github.com/dasch-swiss/dsp-api/compare/v15.1.1...v15.1.2) (2021-11-12)


### Maintenance

* bump bazel ([#1938](https://www.github.com/dasch-swiss/dsp-api/issues/1938)) ([39417e6](https://www.github.com/dasch-swiss/dsp-api/commit/39417e61bd41f1f4eaf0e4ad25b5944faa330dae))
* improve validation handling (DEV-228) ([#1937](https://www.github.com/dasch-swiss/dsp-api/issues/1937)) ([94d7d3f](https://www.github.com/dasch-swiss/dsp-api/commit/94d7d3fe882134c0a9b31124050a71ab7fe2deb6))

### [15.1.1](https://www.github.com/dasch-swiss/dsp-api/compare/v15.1.0...v15.1.1) (2021-11-09)


### Bug Fixes

* **list:** add support for special characters in list update (DEV-200) ([#1934](https://www.github.com/dasch-swiss/dsp-api/issues/1934)) ([3c2865c](https://www.github.com/dasch-swiss/dsp-api/commit/3c2865cf411c57891aa7b44118a6c71878338070))


### Maintenance

* **init-db:** init db test data from test server (DEV-198) ([#1936](https://www.github.com/dasch-swiss/dsp-api/issues/1936)) ([1c24bea](https://www.github.com/dasch-swiss/dsp-api/commit/1c24beaf2ce01162569c0e619bf268f4684ac753))

## [15.1.0](https://www.github.com/dasch-swiss/dsp-api/compare/v15.0.3...v15.1.0) (2021-11-03)


### Bug Fixes

* **users:** fix bug adding user to group or project (DEV-184) ([#1925](https://www.github.com/dasch-swiss/dsp-api/issues/1925)) ([a24a320](https://www.github.com/dasch-swiss/dsp-api/commit/a24a320331e47fb12474bd3345d4e909544019e5))


### Enhancements

* add value objects to list routes - old and new (DEV-65) ([#1917](https://www.github.com/dasch-swiss/dsp-api/issues/1917)) ([7752a36](https://www.github.com/dasch-swiss/dsp-api/commit/7752a364e2e361f354c060b428f9e565edd15741))


### Maintenance

* bump sipi version (DEV-188) ([#1931](https://www.github.com/dasch-swiss/dsp-api/issues/1931)) ([d302b5e](https://www.github.com/dasch-swiss/dsp-api/commit/d302b5e953e2fa3eac5e5a5bdf041dce3582e07e))
* change license to Apache 2.0 (DEV-82) ([#1924](https://www.github.com/dasch-swiss/dsp-api/issues/1924)) ([2d39a1f](https://www.github.com/dasch-swiss/dsp-api/commit/2d39a1fb4c1103fa791966a54ec6fc772d355718))
* **deps:** bump mkdocs from 1.1.2 to 1.2.3 in /docs ([#1927](https://www.github.com/dasch-swiss/dsp-api/issues/1927)) ([cbbf1b6](https://www.github.com/dasch-swiss/dsp-api/commit/cbbf1b65d4200e21c63786a2cda5fd0c15c3bfd5))
* fix warnings (DEV-80) ([#1929](https://www.github.com/dasch-swiss/dsp-api/issues/1929)) ([1368769](https://www.github.com/dasch-swiss/dsp-api/commit/136876910d1991ff0e0749c92e702f3bf438247f))

### [15.0.3](https://www.github.com/dasch-swiss/dsp-api/compare/v15.0.2...v15.0.3) (2021-10-21)


### Bug Fixes

* **list:** find list labels in full-text search ([#1922](https://www.github.com/dasch-swiss/dsp-api/issues/1922)) ([cc3b06c](https://www.github.com/dasch-swiss/dsp-api/commit/cc3b06c8638838ead6ea5753d8898a31e4fb1c40))

### [15.0.2](https://www.github.com/dasch-swiss/dsp-api/compare/v15.0.1...v15.0.2) (2021-10-14)


### Bug Fixes

* **authenticator:** improve performance ([#1914](https://www.github.com/dasch-swiss/dsp-api/issues/1914)) ([d6a0d27](https://www.github.com/dasch-swiss/dsp-api/commit/d6a0d2747eb7e39ad26b34648505ada15b7fc32b))
* **groups:** update test data and documentation to use language specific group descriptions (DEV-123) ([#1921](https://www.github.com/dasch-swiss/dsp-api/issues/1921)) ([0f45b51](https://www.github.com/dasch-swiss/dsp-api/commit/0f45b519bd0ce7842bc07633eccd5236f60086f8))
* removing cardinality of a link property (DEV-90) ([#1919](https://www.github.com/dasch-swiss/dsp-api/issues/1919)) ([c79c194](https://www.github.com/dasch-swiss/dsp-api/commit/c79c194b0f1a1c6c8f31bdb20a04ad9266008572))


### Maintenance

* **groups:** refactor groups route using value objects (DEV-66) ([#1913](https://www.github.com/dasch-swiss/dsp-api/issues/1913)) ([1cd98e6](https://www.github.com/dasch-swiss/dsp-api/commit/1cd98e638ca788b2ce93c3fa5b15be6f528ed19a))
* **knora-base:** fix typo ([#1918](https://www.github.com/dasch-swiss/dsp-api/issues/1918)) ([720aa65](https://www.github.com/dasch-swiss/dsp-api/commit/720aa65986a10536d2f33c56a548b058f91be2bb))
* **projects:** cleaner value objects usage in addProject route (DEV-119) ([#1920](https://www.github.com/dasch-swiss/dsp-api/issues/1920)) ([32b9e49](https://www.github.com/dasch-swiss/dsp-api/commit/32b9e4990bae1b17a23d08aea4f27438e4e2e9e2))

### [15.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v15.0.0...v15.0.1) (2021-09-29)


### Bug Fixes

* **candeletecardinalities:** return correct response on route negative case (DEV-36) ([#1910](https://www.github.com/dasch-swiss/dsp-api/issues/1910)) ([652c747](https://www.github.com/dasch-swiss/dsp-api/commit/652c747399aac347a935f2a6dcfb061835eb4cf1))
* **escape-special-characters:** escape special characters in user routes (DSP-1557) ([#1902](https://www.github.com/dasch-swiss/dsp-api/issues/1902)) ([689d92a](https://www.github.com/dasch-swiss/dsp-api/commit/689d92ad076b84a544719c135e1b36bc78260309))


### Maintenance

* **contributors:** remove contributors file (DEV-77) ([#1911](https://www.github.com/dasch-swiss/dsp-api/issues/1911)) ([7d925b6](https://www.github.com/dasch-swiss/dsp-api/commit/7d925b6a1a4d63a5d01cebbc18858c31e60e0f62))
* **projects:** refactor projects route with value objects (DEV-64) ([#1909](https://www.github.com/dasch-swiss/dsp-api/issues/1909)) ([172cf77](https://www.github.com/dasch-swiss/dsp-api/commit/172cf77c06844edc9924b06739bcba8916039c62))
* reformatting Scala files (DSP-1897) ([#1908](https://www.github.com/dasch-swiss/dsp-api/issues/1908)) ([8df70a2](https://www.github.com/dasch-swiss/dsp-api/commit/8df70a27c85a25237425f9a7a181d84fb121870a))

## [15.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v14.1.0...v15.0.0) (2021-09-14)


### ⚠ BREAKING CHANGES

* **ontology:** use patch instead of delete for deleting cardinalities (DSP-1700) (#1903)

### Documentation

* add username to changeable attributes (DSP-1895) ([#1904](https://www.github.com/dasch-swiss/dsp-api/issues/1904)) ([719cd0d](https://www.github.com/dasch-swiss/dsp-api/commit/719cd0dbfc60aae1c80d3b0e4e4ae09febe37791))


### Maintenance

* **ontology:** use patch instead of delete for deleting cardinalities (DSP-1700) ([#1903](https://www.github.com/dasch-swiss/dsp-api/issues/1903)) ([91ef4ec](https://www.github.com/dasch-swiss/dsp-api/commit/91ef4ec0a810ed347702bbe49b470505eb3c3067))

## [14.1.0](https://www.github.com/dasch-swiss/dsp-api/compare/v14.0.1...v14.1.0) (2021-08-19)


### Bug Fixes

* **ontology V2:** use internal iri when updating a property (DSP-1868) ([#1898](https://www.github.com/dasch-swiss/dsp-api/issues/1898)) ([a746f65](https://www.github.com/dasch-swiss/dsp-api/commit/a746f65c3f5980a45d99a8c6d4a70205e24368cb))


### Enhancements

* **v2-ontologies:** add remove cardinalities from class if property not used in resources (DSP-1700) ([#1869](https://www.github.com/dasch-swiss/dsp-api/issues/1869)) ([a30668b](https://www.github.com/dasch-swiss/dsp-api/commit/a30668b04201c29ed522ab3c48dcc0acea630f89))

### [14.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v14.0.0...v14.0.1) (2021-08-04)


### Bug Fixes

* **add-test-file:** add response file for test case (DSP-1841) ([#1894](https://www.github.com/dasch-swiss/dsp-api/issues/1894)) ([028e685](https://www.github.com/dasch-swiss/dsp-api/commit/028e685f31d95604df78ecf09aad908728fab82c))

## [14.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.12.0...v14.0.0) (2021-08-02)


### ⚠ BREAKING CHANGES

* **projects:** Change shortname to xsd:NCName forma, Escape special character in payloads of projects endpoints (DSP-1555 ) (#1886)

### Bug Fixes

* **api-v2, api-admin:** ontology name and project name should be URL safe (DSP-1749) ([#1889](https://www.github.com/dasch-swiss/dsp-api/issues/1889)) ([17601a7](https://www.github.com/dasch-swiss/dsp-api/commit/17601a78b47ed7957b4e98dd77ce354e74c2f612))
* **permissions:** reject malformed doap and ap create/update request (DSP-1328) ([#1890](https://www.github.com/dasch-swiss/dsp-api/issues/1890)) ([3e3a3ce](https://www.github.com/dasch-swiss/dsp-api/commit/3e3a3ce98a5a85aa4714388681dad55eba75c688))


### Enhancements

* **customIRIs:** custom IRIs must contain a UUID (DSP-1763) ([#1884](https://www.github.com/dasch-swiss/dsp-api/issues/1884)) ([593d9cb](https://www.github.com/dasch-swiss/dsp-api/commit/593d9cb30a7fb332f8062898bcfa07abf1e7951d))
* **projects:** Change shortname to xsd:NCName forma, Escape special character in payloads of projects endpoints (DSP-1555 ) ([#1886](https://www.github.com/dasch-swiss/dsp-api/issues/1886)) ([b3c2d5f](https://www.github.com/dasch-swiss/dsp-api/commit/b3c2d5f82072d507d8cc5c2ab5df76c40d3de22d))
* **resource-metadata:** return resource metadata after metadata update request (DSP-1828) ([#1893](https://www.github.com/dasch-swiss/dsp-api/issues/1893)) ([a4e878a](https://www.github.com/dasch-swiss/dsp-api/commit/a4e878a7354a8cc5f8d3c949a89a5bbe1d693953))
* **video:** add support for video/mp4 to both v1 and v2 (DSP-1204) ([#1891](https://www.github.com/dasch-swiss/dsp-api/issues/1891)) ([83fb4b8](https://www.github.com/dasch-swiss/dsp-api/commit/83fb4b89444de54b129aaa5f0ffc51d958210940))

## [13.12.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.11.0...v13.12.0) (2021-06-24)


### Enhancements

* **resourceHistoryEvents:** route for resource history events (DSP-1749) ([#1882](https://www.github.com/dasch-swiss/dsp-api/issues/1882)) ([f86de53](https://www.github.com/dasch-swiss/dsp-api/commit/f86de53ddcee6e4de6dfbe46fb33c6c9775ef872))

## [13.11.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.10.0...v13.11.0) (2021-06-17)


### Enhancements

* **events:** update resource last modification date event ([#1877](https://www.github.com/dasch-swiss/dsp-api/issues/1877)) ([d5e70ba](https://www.github.com/dasch-swiss/dsp-api/commit/d5e70ba25986a2a3fef344112cd46f5b13855929))


### Maintenance

* **build:** cleanup ([#1880](https://www.github.com/dasch-swiss/dsp-api/issues/1880)) ([749e8ea](https://www.github.com/dasch-swiss/dsp-api/commit/749e8eae664a12e753d73fd497b1d6236fbc711d))
* **cache-service:** add in-memory implementation ([#1870](https://www.github.com/dasch-swiss/dsp-api/issues/1870)) ([61531ab](https://www.github.com/dasch-swiss/dsp-api/commit/61531ab3a9f227049ba9fbaf4339aabadf9e576d))
* **gh-ci:** update docs deployment (DSP-1741) ([#1878](https://www.github.com/dasch-swiss/dsp-api/issues/1878)) ([ff65323](https://www.github.com/dasch-swiss/dsp-api/commit/ff6532310818e181a946634f7518108b679bee03))

## [13.10.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.9.2...v13.10.0) (2021-06-09)


### Enhancements

* **gravsearch:** use layer info for topological order permutations (DSP-1389) ([#1872](https://www.github.com/dasch-swiss/dsp-api/issues/1872)) ([b49d5ba](https://www.github.com/dasch-swiss/dsp-api/commit/b49d5bad33c3ff057b221eb2e4067618ab2afd1f))


### Documentation

* prepare documentation for docs.dasch.swiss (DSP-1721) ([#1873](https://www.github.com/dasch-swiss/dsp-api/issues/1873)) ([66751a0](https://www.github.com/dasch-swiss/dsp-api/commit/66751a08a300a2b7e7b5725f0edc0d4bd97a7cac))

### [13.9.2](https://www.github.com/dasch-swiss/dsp-api/compare/v13.9.1...v13.9.2) (2021-06-02)


### Maintenance

* **sipi:** add comments ([#1864](https://www.github.com/dasch-swiss/dsp-api/issues/1864)) ([06e8b0c](https://www.github.com/dasch-swiss/dsp-api/commit/06e8b0cac5d849a50d85124e2fa262223b9cd603))


### Documentation

* **ontology:** update term ([#1865](https://www.github.com/dasch-swiss/dsp-api/issues/1865)) ([cd37580](https://www.github.com/dasch-swiss/dsp-api/commit/cd375806a6bd313806a759276bb3009756f9b276))

### [13.9.1](https://www.github.com/dasch-swiss/dsp-api/compare/v13.9.0...v13.9.1) (2021-05-28)


### Maintenance

* **bazel:** bump bazel version ([#1866](https://www.github.com/dasch-swiss/dsp-api/issues/1866)) ([c754cbf](https://www.github.com/dasch-swiss/dsp-api/commit/c754cbf534b1c44116cbded35bf19719dc4ace12))

## [13.9.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.8.0...v13.9.0) (2021-05-25)


### Enhancements

* **api-v2:** Add routes for checking whether ontology entities can be changed (DSP-1621) ([#1861](https://www.github.com/dasch-swiss/dsp-api/issues/1861)) ([fdd098f](https://www.github.com/dasch-swiss/dsp-api/commit/fdd098f38acd2538a8a06bae79cc934703219437))

## [13.8.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.7.0...v13.8.0) (2021-05-19)


### Bug Fixes

* **api-v2:** Update subclasses in ontology cache when base class changes (DSP-1643) ([#1860](https://www.github.com/dasch-swiss/dsp-api/issues/1860)) ([beb951d](https://www.github.com/dasch-swiss/dsp-api/commit/beb951db46e9d73631667c40168a67dc07a9605c))
* **gravsearch:** don't move the patterns with resource IRI after topological sorting (DSP-1620) ([#1856](https://www.github.com/dasch-swiss/dsp-api/issues/1856)) ([6022c91](https://www.github.com/dasch-swiss/dsp-api/commit/6022c91e442058cc48c58f93da6071f5b00661fc))


### Maintenance

* **documentation:** bug fix in documentation deployment (DSP-1605) ([bb852c9](https://www.github.com/dasch-swiss/dsp-api/commit/bb852c9d1443cf49a57b25a3a085f93ba561daca))
* **documentation:** bug fix in documentation deployment (DSP-1605) ([#1854](https://www.github.com/dasch-swiss/dsp-api/issues/1854)) ([999a2bb](https://www.github.com/dasch-swiss/dsp-api/commit/999a2bba6e9b712de26f681ce628308164a6583c))


### Enhancements

* **api-v2:** Change GUI element and attribute of a property (DSP-1600) ([#1855](https://www.github.com/dasch-swiss/dsp-api/issues/1855)) ([ce9ba3a](https://www.github.com/dasch-swiss/dsp-api/commit/ce9ba3a12ba4a4847284b710117b1316429040fd))
* **api-v2:** Generate IIIF manifest (DSP-50) ([#1784](https://www.github.com/dasch-swiss/dsp-api/issues/1784)) ([74feb2c](https://www.github.com/dasch-swiss/dsp-api/commit/74feb2c0bace8bd59de657f1f4af64934ece2309))
* **conf:** Rule to dump prod data and load locally (DSP-1485) ([#1857](https://www.github.com/dasch-swiss/dsp-api/issues/1857)) ([161ea31](https://www.github.com/dasch-swiss/dsp-api/commit/161ea314b9fa547cbbedf4599e94775f79a7f4a6))
* **ontology:** Allow adding new property to a resource class in use (DSP-1629) ([#1859](https://www.github.com/dasch-swiss/dsp-api/issues/1859)) ([061875e](https://www.github.com/dasch-swiss/dsp-api/commit/061875ed21a2d574d1ab4df678bd6dbdaa305247))

## [13.7.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.6.0...v13.7.0) (2021-05-06)


### Bug Fixes

* **doc:** correct remaining incorrect copyright dates ([#1847](https://www.github.com/dasch-swiss/dsp-api/issues/1847)) ([d1473ed](https://www.github.com/dasch-swiss/dsp-api/commit/d1473ed48bba66659dacca64d71c88a15d78babc))
* **gravsearch:** Keep rdf:type knora-api:Resource when needed. ([#1835](https://www.github.com/dasch-swiss/dsp-api/issues/1835)) ([e561d94](https://www.github.com/dasch-swiss/dsp-api/commit/e561d945bc0e50388a6ff09d0421141e339050be))
* **lists:** Escape special characters in comment, label, and name of a list node (DSP-1529) ([#1846](https://www.github.com/dasch-swiss/dsp-api/issues/1846)) ([f96c069](https://www.github.com/dasch-swiss/dsp-api/commit/f96c069a360f277c580dfd17bd0c0db0f587b018))
* **test-data:** change webern shortcode in test data (DSP-1520) ([#1843](https://www.github.com/dasch-swiss/dsp-api/issues/1843)) ([5f06a10](https://www.github.com/dasch-swiss/dsp-api/commit/5f06a10b58b01bff33267dfdb15b2985dd821a92))
* **values v1 route:** fix geoname case (DSP-1487) ([#1839](https://www.github.com/dasch-swiss/dsp-api/issues/1839)) ([9d0e93e](https://www.github.com/dasch-swiss/dsp-api/commit/9d0e93e96891660e4484f9b23683aa9aefb18938))


### Documentation

* replace knora by dsp or dsp-api in documentation (DSP-1469) ([#1836](https://www.github.com/dasch-swiss/dsp-api/issues/1836)) ([923abe8](https://www.github.com/dasch-swiss/dsp-api/commit/923abe8180e0bd6e3fdd2508b6e3b8a472301317))
* **v1:** improve search docs ([#1848](https://www.github.com/dasch-swiss/dsp-api/issues/1848)) ([5a81f73](https://www.github.com/dasch-swiss/dsp-api/commit/5a81f7306adc7a70b07d4570eb0079b1ff14c2e9))


### Enhancements

* **api-v2:** Add route for changing GUI order of cardinalities ([#1850](https://www.github.com/dasch-swiss/dsp-api/issues/1850)) ([d8dbb4f](https://www.github.com/dasch-swiss/dsp-api/commit/d8dbb4f4aa788d0b4cc8c1b059205d6b57455629))
* **api-v2:** Return events describing version history of resources and values of a project ordered by data (DSP-1528) ([#1844](https://www.github.com/dasch-swiss/dsp-api/issues/1844)) ([84f7c14](https://www.github.com/dasch-swiss/dsp-api/commit/84f7c148216fc63a1df7b1994668e335aab12c51))
* **ext search v1:** add support for URI values (DSP-1522) ([#1842](https://www.github.com/dasch-swiss/dsp-api/issues/1842)) ([b119757](https://www.github.com/dasch-swiss/dsp-api/commit/b1197576e9fa07a0ceaaaedfcff22a1e2d22829e))


### Maintenance

* bumb Bazel to version with apple silicon support ([#1852](https://www.github.com/dasch-swiss/dsp-api/issues/1852)) ([286d289](https://www.github.com/dasch-swiss/dsp-api/commit/286d289913c46b726c4a37e0519e5610304daffc))
* bump scala to 2.13 ([#1851](https://www.github.com/dasch-swiss/dsp-api/issues/1851)) ([5feb915](https://www.github.com/dasch-swiss/dsp-api/commit/5feb9155647f02d8a7b7f57ba7d8d06352093cd9))
* **deps:** bump versions (DSP-1569) ([#1849](https://www.github.com/dasch-swiss/dsp-api/issues/1849)) ([f69f008](https://www.github.com/dasch-swiss/dsp-api/commit/f69f008bfe06bbf9d3701dab6ce0866cd0f6d567))

## [13.6.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.5.1...v13.6.0) (2021-03-16)

### Enhancements

* **api-v2:** Improve error message when an XSLT transformation file is not found (DSP-1404) ([#1831](https://www.github.com/dasch-swiss/dsp-api/issues/1831)) ([153a674](https://www.github.com/dasch-swiss/dsp-api/commit/153a674fda6085a338a390788e74985e071f1d4d))

### [13.5.1](https://www.github.com/dasch-swiss/dsp-api/compare/v13.5.0...v13.5.1) (2021-03-11)

### Bug Fixes

* **OntologiesRouteV2:** Reject internal ontology names in external schema (DSP-1394) ([#1827](https://www.github.com/dasch-swiss/dsp-api/issues/1827)) ([e392bf1](https://www.github.com/dasch-swiss/dsp-api/commit/e392bf16645e8472d3760e84177f5880c22304cd))
* **OntologyResponderV2:** Fix check when updating ontology label and comment (DSP-1390) ([#1826](https://www.github.com/dasch-swiss/dsp-api/issues/1826)) ([26cce48](https://www.github.com/dasch-swiss/dsp-api/commit/26cce4826253635ccf2050d61de557ce6331204b))

## [13.5.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.4.0...v13.5.0) (2021-03-08)

### Bug Fixes

* **replaceCardinalities.scala.txt:** Fix blank node insertion. ([#1829](https://www.github.com/dasch-swiss/dsp-api/issues/1829)) ([d24c5d2](https://www.github.com/dasch-swiss/dsp-api/commit/d24c5d2ccdd476fe4a58cf37ad15c74cc273e46e))

### Maintenance

* **gh-ci:** update release please configuration (DSP-1382) ([#1825](https://www.github.com/dasch-swiss/dsp-api/issues/1825)) ([7ce4b65](https://www.github.com/dasch-swiss/dsp-api/commit/7ce4b650f1213a275b3af70a379d72d8c91303b7))

### Enhancements

* Add support for audio files (DSP-1343) ([#1818](https://www.github.com/dasch-swiss/dsp-api/issues/1818)) ([7497023](https://www.github.com/dasch-swiss/dsp-api/commit/7497023c3cc117d08c3d5eaae807ac53451dae64))
* **gravsearch:** Optimise Gravsearch queries using topological sort (DSP-1327) ([#1813](https://www.github.com/dasch-swiss/dsp-api/issues/1813)) ([efbecee](https://www.github.com/dasch-swiss/dsp-api/commit/efbecee1773b2ac131a1aac46a9a37e56361248f))
* **store:** Return 404 if the triplestore returns 404. ([#1828](https://www.github.com/dasch-swiss/dsp-api/issues/1828)) ([5250f6d](https://www.github.com/dasch-swiss/dsp-api/commit/5250f6d2c566669c2a848022456929356af6558c))

## [13.4.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.3.1...v13.4.0) (2021-02-17)

### Bug Fixes

* **Lists:** fix bug in shifting the second of two children after deletion of the first one. ([#1820](https://www.github.com/dasch-swiss/dsp-api/issues/1820)) ([d92bb01](https://www.github.com/dasch-swiss/dsp-api/commit/d92bb0194d3245cef86dc91b1cbd83a8dfc0eb05))

### Enhancements

* **projects:** add default set of permissions when creating new project (DSP-1347) ([#1822](https://www.github.com/dasch-swiss/dsp-api/issues/1822)) ([b7c71ca](https://www.github.com/dasch-swiss/dsp-api/commit/b7c71ca0ac3360bf1e9434f7525fe0a9a122edbc))

### [13.3.1](https://www.github.com/dasch-swiss/dsp-api/compare/v13.3.0...v13.3.1) (2021-02-09)

### Bug Fixes

* **Lists:** fix bug in deleting the single child of a node (DSP-1355) ([#1816](https://www.github.com/dasch-swiss/dsp-api/issues/1816)) ([1d06572](https://www.github.com/dasch-swiss/dsp-api/commit/1d0657205cdc96704551902b6cac4ef8fec46ccc))

## [13.3.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.2.0...v13.3.0) (2021-02-05)

### Enhancements

* **sipi:** add storing of original and sidecar (DSP-1318) ([#1808](https://www.github.com/dasch-swiss/dsp-api/issues/1808)) ([022ed7e](https://www.github.com/dasch-swiss/dsp-api/commit/022ed7e977601543eca47b48ab65ea830c04e2f4))

## [13.2.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.1.1...v13.2.0) (2021-02-04)

### Bug Fixes

* **api-v1:** Optimise SPARQL queries. ([#1814](https://www.github.com/dasch-swiss/dsp-api/issues/1814)) ([4edc27c](https://www.github.com/dasch-swiss/dsp-api/commit/4edc27cc64000e599b4b89db8ace1eee7e088908))
* **Lists:** Repositioning the node when new position equals length of new parent's children (DSP-1322) ([#1811](https://www.github.com/dasch-swiss/dsp-api/issues/1811)) ([3fead13](https://www.github.com/dasch-swiss/dsp-api/commit/3fead1379ff1c7c7ccb977db983e1a22ee24100a))

### Enhancements

* **api-v1:** Add support for PDF files (DSP-1267) ([#1797](https://www.github.com/dasch-swiss/dsp-api/issues/1797)) ([c3b2e84](https://www.github.com/dasch-swiss/dsp-api/commit/c3b2e842c6b6524e11227d564300f4a074cec228))
* **api-v2:** Allow resubmitting existing class/property lablels/comments. ([#1812](https://www.github.com/dasch-swiss/dsp-api/issues/1812)) ([6a13852](https://www.github.com/dasch-swiss/dsp-api/commit/6a1385201876e17fc07a50666b1d504cff1b64cc))

### Maintenance

* make targets for adding metadata (DSP-1289) ([#1810](https://www.github.com/dasch-swiss/dsp-api/issues/1810)) ([9c1a70a](https://www.github.com/dasch-swiss/dsp-api/commit/9c1a70a5423d07684716c7b2823bb11777c24859))
* **salsah1:** delete from repository ([#1805](https://www.github.com/dasch-swiss/dsp-api/issues/1805))(DSP-1294) ([3251a74](https://www.github.com/dasch-swiss/dsp-api/commit/3251a742664a74e3a214861f24f9bdd416813291))

### [13.1.1](https://www.github.com/dasch-swiss/dsp-api/compare/v13.1.0...v13.1.1) (2021-01-30)

### Maintenance

* **gh-ci:** Bring back the client-test-data command to github actions ([#1804](https://www.github.com/dasch-swiss/dsp-api/issues/1804)) ([e6b0fbf](https://www.github.com/dasch-swiss/dsp-api/commit/e6b0fbf14d20c3d3bd9c74da523a18160338d4b0))
* revert release 13.1.0 ([#1800](https://www.github.com/dasch-swiss/dsp-api/issues/1800)) ([565e5ac](https://www.github.com/dasch-swiss/dsp-api/commit/565e5ac9d2c73ac0bcd91a13c358c02d27b0f93e))

## [13.1.0](https://www.github.com/dasch-swiss/dsp-api/compare/v13.0.0...v13.1.0) (2021-01-29)

### Bug Fixes

* **api-v1:** Optimise link value queries for Fuseki (DSP-1243) ([#1791](https://www.github.com/dasch-swiss/dsp-api/issues/1791)) ([b1e1b9e](https://www.github.com/dasch-swiss/dsp-api/commit/b1e1b9eeb73d7aaa0a46fc3060fd64e4d550c09f))
* **api-v2:** Don't allow an invalid cardinality on a boolean property (DSP-1236) ([#1788](https://www.github.com/dasch-swiss/dsp-api/issues/1788)) ([3d5f802](https://www.github.com/dasch-swiss/dsp-api/commit/3d5f802b54228fe6ce1f8dc8cebea1993b3f8203))
* **gravsearch:** Handle UNION scopes with FILTER correctly (DSP-1240) ([#1790](https://www.github.com/dasch-swiss/dsp-api/issues/1790)) ([61d2e86](https://www.github.com/dasch-swiss/dsp-api/commit/48d77cde965ce861c9b611f0a246509c70ea9ad0))
* **HttpTriplestoreConnector:** Always parse triplestore responses as UTF-8. ([#1789](https://www.github.com/dasch-swiss/dsp-api/issues/1789)) ([61d2e86](https://www.github.com/dasch-swiss/dsp-api/commit/61d2e868d71cf167241ab8bdbb8ddd817c06d7a6))
* **permissions**: fix getting builtin groups while creating a permission (DSP-1296 ) ([#1799](https://www.github.com/dasch-swiss/dsp-api/issues/1799)) ([d390014](https://github.com/dasch-swiss/dsp-api/commit/d390014052fe7e3327eccf97abe5fe52f500b6dc))

### Maintenance

* **gh-ci:** fix issue in the release process ([#1782](https://www.github.com/dasch-swiss/dsp-api/issues/1782)) ([afe61b7](https://www.github.com/dasch-swiss/dsp-api/commit/afe61b767248fda03a6f879075a4d19ecf72f1f6))
* **ghi-ci:** google chat release notification ([#1785](https://www.github.com/dasch-swiss/dsp-api/issues/1785)) ([4718cdc](https://www.github.com/dasch-swiss/dsp-api/commit/4718cdcbaee6f9e366c4ec8671b52c498deb5005))

### Enhancements

* **permissions:** add delete permissions: (DSP-1169) ([#1787](https://www.github.com/dasch-swiss/dsp-api/issues/1787)) ([3fe8c14](https://www.github.com/dasch-swiss/dsp-api/commit/3fe8c14f482b1ff003ee34935ab61ecca1b4f286))
* **store:** Return a clearer exception when a triplestore read timeout occurs. ([#1795](https://www.github.com/dasch-swiss/dsp-api/issues/1795)) ([0eeb3b3](https://www.github.com/dasch-swiss/dsp-api/commit/0eeb3b3b29fb5a3351e944c28ba0bc20fe7598ac))

## [13.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v12.0.0...v13.0.0) (2021-01-11)

### ⚠ BREAKING CHANGES

* New features and refactoring (#1779)

### Bug Fixes

* (dependencies) add the missing dependency ([#1755](https://www.github.com/dasch-swiss/dsp-api/issues/1755)) ([0e37d21](https://www.github.com/dasch-swiss/dsp-api/commit/0e37d21133a473a3627b5b106cd8f9229b4d3f62))
* **api-v2:** Change link value comment ([#1582](https://www.github.com/dasch-swiss/dsp-api/issues/1582)) ([faa2e55](https://www.github.com/dasch-swiss/dsp-api/commit/faa2e552c2c93d71874518caa6763bf02dbbb6c0))
* **api-v2:** Don't check file extensions of XSL files and Gravsearch templates (DSP-1005) ([#1749](https://www.github.com/dasch-swiss/dsp-api/issues/1749)) ([905766f](https://www.github.com/dasch-swiss/dsp-api/commit/905766fe054ba25db6296d57915bf0cf330b521c))
* **api-v2:** Fix custom datatypes in knora-api simple ontology ([#1601](https://www.github.com/dasch-swiss/dsp-api/issues/1601)) ([e0cfd4e](https://www.github.com/dasch-swiss/dsp-api/commit/e0cfd4eefaa2e5fc2d99bbd7491fce7dee534da5))
* **api-v2:** Fix generated SPARQL for updating property comment ([#1693](https://www.github.com/dasch-swiss/dsp-api/issues/1693)) ([7b70339](https://www.github.com/dasch-swiss/dsp-api/commit/7b70339029a4ecf0610b67d9032e49c31a91ed14))
* **api-v2:** Fix ontology deletion ([#1584](https://www.github.com/dasch-swiss/dsp-api/issues/1584)) ([70b0841](https://www.github.com/dasch-swiss/dsp-api/commit/70b0841f2edcc232a63cafb5348f0deb3e100e19))
* **api-v2:** Fix post-update check for resource with standoff link (DSP-841) ([#1728](https://www.github.com/dasch-swiss/dsp-api/issues/1728)) ([35d449f](https://www.github.com/dasch-swiss/dsp-api/commit/35d449f37a13f3a2a64206bbe722ec911635fee0))
* failing repository upgrade at startup (DSP-654) ([#1712](https://www.github.com/dasch-swiss/dsp-api/issues/1712)) ([0d6b4ee](https://www.github.com/dasch-swiss/dsp-api/commit/0d6b4eee29a7439b16e71d1c527e3efb410197a9))
* **gravsearch:** Prevent duplicate results ([#1626](https://www.github.com/dasch-swiss/dsp-api/issues/1626)) ([9313b88](https://www.github.com/dasch-swiss/dsp-api/commit/9313b88082e3d235bb5a9235fdee243c987c736b))
* **gravsearch:** When link property compared in filter, don't compare link value property, too ([#1699](https://www.github.com/dasch-swiss/dsp-api/issues/1699)) ([a3b1665](https://www.github.com/dasch-swiss/dsp-api/commit/a3b1665fb30f8edc684b22289ff22959d5a79125))
* init db scripts (DSP-511) ([#1681](https://www.github.com/dasch-swiss/dsp-api/issues/1681)) ([d4505ce](https://www.github.com/dasch-swiss/dsp-api/commit/d4505ce3729e7e1607661b9c7e07d4c3e54121f8))
* loading of data (DSP-445) ([#1669](https://www.github.com/dasch-swiss/dsp-api/issues/1669)) ([3f8d406](https://www.github.com/dasch-swiss/dsp-api/commit/3f8d406b0f44afc2d6928a44e103d613031c3f48))
* **OntologyResponderV2:** Add a global ontology cache lock ([#1637](https://www.github.com/dasch-swiss/dsp-api/issues/1637)) ([1853865](https://www.github.com/dasch-swiss/dsp-api/commit/18538650043940618e17f2f9213cebe52b9bb3fd))
* **OntologyResponderV2:** Fix ontology cache update when ontology metadata changed ([#1709](https://www.github.com/dasch-swiss/dsp-api/issues/1709)) ([4f57977](https://www.github.com/dasch-swiss/dsp-api/commit/4f57977e39f004c26595d8e3b82a68067f5ac675))
* server header (DSP-537) ([#1691](https://www.github.com/dasch-swiss/dsp-api/issues/1691)) ([8d7bee8](https://www.github.com/dasch-swiss/dsp-api/commit/8d7bee8ad02e838a81a8fd7b9cdc8d38c7b9fe8d))
* sipi makefile ([#1616](https://www.github.com/dasch-swiss/dsp-api/issues/1616)) ([73a0afe](https://www.github.com/dasch-swiss/dsp-api/commit/73a0afec47a297e704a0f59d4dbcd8181c8ec49e))
* **sipi:** Don't expect API v1 status code (DSP-1114) ([#1763](https://www.github.com/dasch-swiss/dsp-api/issues/1763)) ([3236d25](https://www.github.com/dasch-swiss/dsp-api/commit/3236d2571b98db26444aa3b448a42a88345e9075))
* **sipi:** Improve performance of file value query ([#1697](https://www.github.com/dasch-swiss/dsp-api/issues/1697)) ([8214877](https://www.github.com/dasch-swiss/dsp-api/commit/82148776b626cfba66d7274c9ea0295435f9a367))
* **test:** Fix typos in IRIs in anything-data.ttl. ([#1625](https://www.github.com/dasch-swiss/dsp-api/issues/1625)) ([23d51ce](https://www.github.com/dasch-swiss/dsp-api/commit/23d51ce5aaf789336e942c01df902943e7e70fca))
* **upgrade:** Fix log output. ([#1774](https://www.github.com/dasch-swiss/dsp-api/issues/1774)) ([b43fab0](https://www.github.com/dasch-swiss/dsp-api/commit/b43fab029995467af2f56a380f02d5532ffeabf3))
* **webapi:** unique username/email check on change user ([#1561](https://www.github.com/dasch-swiss/dsp-api/issues/1561)) ([4f26e22](https://www.github.com/dasch-swiss/dsp-api/commit/4f26e224fd062e1627f1e1350594e41764ff7614))
* **rdf-api**: Use the Jena RDF API implementation by default (DSP-1153) ([1772](https://github.com/dasch-swiss/dsp-api/pull/1772)) ([389feb4](https://github.com/dasch-swiss/dsp-api/commit/389feb49b504f619b2164befd9c54e5595e06cb6))

### Documentation

* **api-v2:** Document what happens when a resource has a link to a deleted resource ([#1685](https://www.github.com/dasch-swiss/dsp-api/issues/1685)) ([1c88651](https://www.github.com/dasch-swiss/dsp-api/commit/1c886511e3863ae24d51447144d2bcd30e29a592))
* fix broken links ([#1688](https://www.github.com/dasch-swiss/dsp-api/issues/1688)) ([9c0292c](https://www.github.com/dasch-swiss/dsp-api/commit/9c0292c4d142da93b8b7efd2e3ffc2383ec42810))
* fix make targets docker-build and docker-publish ([#1694](https://www.github.com/dasch-swiss/dsp-api/issues/1694)) ([d06b6a6](https://www.github.com/dasch-swiss/dsp-api/commit/d06b6a682a877e8363607184a25ca763f9954084))
* Update README (DSP-1142) ([#1771](https://www.github.com/dasch-swiss/dsp-api/issues/1771)) ([7ba7fc6](https://www.github.com/dasch-swiss/dsp-api/commit/7ba7fc6a85879ca336fe1b4dcf03bed3189699fd))
* Update required mkdocs package ([#1725](https://www.github.com/dasch-swiss/dsp-api/issues/1725)) ([27de65e](https://www.github.com/dasch-swiss/dsp-api/commit/27de65e4f5e35db0b8249c93a603b93dbb06e331))

### Maintenance

* **api-v2:** Delete obsolete files. ([#1634](https://www.github.com/dasch-swiss/dsp-api/issues/1634)) ([e80bf52](https://www.github.com/dasch-swiss/dsp-api/commit/e80bf527ee6d8494a4ed54669d1936a189cff00e))
* **api-v2:** Switch from JSONLD-Java to Titanium ([#1715](https://www.github.com/dasch-swiss/dsp-api/issues/1715)) ([9e28e5b](https://www.github.com/dasch-swiss/dsp-api/commit/9e28e5bdbacc3422af088028b0b2ad635a10627d))
* **build:** Bump testcontainers version. ([#1723](https://www.github.com/dasch-swiss/dsp-api/issues/1723)) ([24ae1d3](https://www.github.com/dasch-swiss/dsp-api/commit/24ae1d3de73d4a356ddd03b818bdf2a5f1c504d2))
* **build:** Update ScalaTest (DSP-919) ([#1745](https://www.github.com/dasch-swiss/dsp-api/issues/1745)) ([bbaeadd](https://www.github.com/dasch-swiss/dsp-api/commit/bbaeaddafc3cc59b259f2a321aa8d29cee923fa5))
* **build:** Upgrade Sipi to 3.0.0-rc.8 (DSP-916) ([#1743](https://www.github.com/dasch-swiss/dsp-api/issues/1743)) ([23395fc](https://www.github.com/dasch-swiss/dsp-api/commit/23395fcbdb2fa6e1a22ceea678c4aee5e125ed0c))
* bump sipi to rc.7 (DSP-733) ([#1721](https://www.github.com/dasch-swiss/dsp-api/issues/1721)) ([b635495](https://www.github.com/dasch-swiss/dsp-api/commit/b635495cbc0124b4dc7219f21bcdfad957b965ed))
* **gh-ci:** Fix gren issue ([#1666](https://www.github.com/dasch-swiss/dsp-api/issues/1666)) ([2dc5361](https://www.github.com/dasch-swiss/dsp-api/commit/2dc5361d21c9412070c1e83fe6d28859ba1daa2b))
* **gh-ci:** Publish on release only ([#1662](https://www.github.com/dasch-swiss/dsp-api/issues/1662)) ([787dca8](https://www.github.com/dasch-swiss/dsp-api/commit/787dca823c80bdeb485b134623b1f033020d3e6d))
* **rdf-api:** Use the Jena RDF API implementation by default (DSP-1153) ([#1772](https://www.github.com/dasch-swiss/dsp-api/issues/1772)) ([389feb4](https://www.github.com/dasch-swiss/dsp-api/commit/389feb49b504f619b2164befd9c54e5595e06cb6))
* Remove obsolete functions from StringFormatter. ([#1640](https://www.github.com/dasch-swiss/dsp-api/issues/1640)) ([5fa6de4](https://www.github.com/dasch-swiss/dsp-api/commit/5fa6de4e6c6b22842c771b8e5d4ffd49d29728f9))
* Update ci workflow release notes ([#1707](https://www.github.com/dasch-swiss/dsp-api/issues/1707)) ([d8e0b39](https://www.github.com/dasch-swiss/dsp-api/commit/d8e0b39a7da5d4c1e78212fb193097bfe96712f7))
* **gh-ci** CI is failing to test upgrade correctly (DSP-667) ([#1073](https://github.com/dasch-swiss/dsp-api/pull/1773)) ([13cbdab](https://github.com/dasch-swiss/dsp-api/commit/13cbdab92c87d1d5cb50c9f8a85dad246db3b1bd))
* **bazel** Update Bazel maven rules to see if it fixes problems with macOS Big Sur (DSP-1099) ([#1761](https://github.com/dasch-swiss/dsp-api/pull/1761)) ([a2c9941](https://github.com/dasch-swiss/dsp-api/commit/a2c994107508f86692e19fcd86d272cd3cc88db7))

### Enhancements

* Add an RDF processing façade (2nd iteration) (DSP-1083) ([#1759](https://www.github.com/dasch-swiss/dsp-api/issues/1759)) ([346873d](https://www.github.com/dasch-swiss/dsp-api/commit/346873d17b994a04b40704f0ea595f55d81db459))
* Add feature toggles (DSP-910) ([#1742](https://www.github.com/dasch-swiss/dsp-api/issues/1742)) ([2e6db2e](https://www.github.com/dasch-swiss/dsp-api/commit/2e6db2e3f32fc2d6fffac25b982ae85fe354f834))
* Add time value type ([#1403](https://www.github.com/dasch-swiss/dsp-api/issues/1403)) ([d925c85](https://www.github.com/dasch-swiss/dsp-api/commit/d925c851f0dfb0337ae427ef5a408161083c0ce4))
* **api-v1:** Change API v1 file uploads to work like API v2 (DSP-41, PR 3) ([#1722](https://www.github.com/dasch-swiss/dsp-api/issues/1722)) ([a824bcc](https://www.github.com/dasch-swiss/dsp-api/commit/a824bcc13f426beac9c7cc9effc26e38c9753f58))
* **api-v2:** Accept custom new value IRI when updating value ([#1698](https://www.github.com/dasch-swiss/dsp-api/issues/1698)) ([4d8f867](https://www.github.com/dasch-swiss/dsp-api/commit/4d8f8670f30c81581a0d1edd0758a15f50010df7))
* **api-v2:** Accept custom timestamps in update/delete requests ([#1686](https://www.github.com/dasch-swiss/dsp-api/issues/1686)) ([0fbe5a8](https://www.github.com/dasch-swiss/dsp-api/commit/0fbe5a80bdff9080d0cae1ce9acee2d3dffdf5cc))
* **api-v2:** Add an RDF processing façade (DSP-1020) ([#1754](https://www.github.com/dasch-swiss/dsp-api/issues/1754)) ([9170419](https://www.github.com/dasch-swiss/dsp-api/commit/9170419f052746346c4ee5ff2138ab69125a5e1e))
* **api-v2:** Add metadata routes (DSP-662) ([#1734](https://www.github.com/dasch-swiss/dsp-api/issues/1734)) ([bf48968](https://www.github.com/dasch-swiss/dsp-api/commit/bf489685f012bd28bf189a2f3908eef6717ab513))
* **api-v2:** Add support for text file upload (DSP-44) ([#1664](https://www.github.com/dasch-swiss/dsp-api/issues/1664)) ([a88d20d](https://www.github.com/dasch-swiss/dsp-api/commit/a88d20d7fee41969f6dbbbf71281102eaefe862a))
* **api-v2:** Add test data. ([#1704](https://www.github.com/dasch-swiss/dsp-api/issues/1704)) ([de14ab1](https://www.github.com/dasch-swiss/dsp-api/commit/de14ab1cf87b82f23b1d115164be51a9fad7811c))
* **api-v2:** Allow querying for rdfs:label in Gravsearch ([#1649](https://www.github.com/dasch-swiss/dsp-api/issues/1649)) ([d56004b](https://www.github.com/dasch-swiss/dsp-api/commit/d56004bb271a5012cee9a0e8f349efaa82b31bcc))
* **api-v2:** Control JSON-LD nesting via an HTTP header (DSP-1084) ([#1758](https://www.github.com/dasch-swiss/dsp-api/issues/1758)) ([b13eecf](https://www.github.com/dasch-swiss/dsp-api/commit/b13eecfcbdb3b535cac2cdfaa59fd0a3929736cb))
* **api-v2:** Make inference optional in Gravsearch ([#1696](https://www.github.com/dasch-swiss/dsp-api/issues/1696)) ([166a260](https://www.github.com/dasch-swiss/dsp-api/commit/166a26061dd434ca1d95fedf6dec75728760c78c))
* **api-v2:** Optionally return file values in full-text search results (DSP-1191) ([#1776](https://www.github.com/dasch-swiss/dsp-api/issues/1776)) ([01f59bd](https://www.github.com/dasch-swiss/dsp-api/commit/01f59bde8ccffffb737c962770783b0218b9b919))
* **api-v2:** Remove client code generation ([#1610](https://www.github.com/dasch-swiss/dsp-api/issues/1610)) ([6977ab3](https://www.github.com/dasch-swiss/dsp-api/commit/6977ab3fa7a1e4cd5ea9cc157062c65045f36559))
* **api-v2:** Remove ForbiddenResource ([#1615](https://www.github.com/dasch-swiss/dsp-api/issues/1615)) ([992596e](https://www.github.com/dasch-swiss/dsp-api/commit/992596e3e031105675ae66e8440b979b64cd08ad))
* **api-v2:** Return value UUID on value creation and update ([#1602](https://www.github.com/dasch-swiss/dsp-api/issues/1602)) ([cbed601](https://www.github.com/dasch-swiss/dsp-api/commit/cbed60134d8a2864b6ce55ba244002893b7a3d8e))
* **api-v2:** Specify custom IRIs when creating resources/values ([#1646](https://www.github.com/dasch-swiss/dsp-api/issues/1646)) ([135b039](https://www.github.com/dasch-swiss/dsp-api/commit/135b03955eed26cdcb5d869c22980259b1d328a3))
* **clientapi:** Change method signature. ([#1583](https://www.github.com/dasch-swiss/dsp-api/issues/1583)) ([c2a2559](https://www.github.com/dasch-swiss/dsp-api/commit/c2a255905d6f39638f197007b5da043533b4cd5d))
* **gh-ci:** Release please and update gh actions (DSP-1168) ([#1777](https://www.github.com/dasch-swiss/dsp-api/issues/1777)) ([593ffab](https://www.github.com/dasch-swiss/dsp-api/commit/593ffab19427794b06e1ddd39e2611deb22e7726))
* **gravsearch:** Allow comparing variables representing resource IRIs ([#1713](https://www.github.com/dasch-swiss/dsp-api/issues/1713)) ([f359c8e](https://www.github.com/dasch-swiss/dsp-api/commit/f359c8e4f3899d6e49dab395055b3bf1297c5766))
* **gravsearch:** Remove deprecated functions ([#1660](https://www.github.com/dasch-swiss/dsp-api/issues/1660)) ([5d3af46](https://www.github.com/dasch-swiss/dsp-api/commit/5d3af46d83fc54b4b5f55d317c53446cc738f908))
* New features and refactoring ([#1779](https://www.github.com/dasch-swiss/dsp-api/issues/1779)) ([9a5fb77](https://www.github.com/dasch-swiss/dsp-api/commit/9a5fb77556eabbddd58767f3c3f43a5497c461cb))
* **rdf-api:** Add a general-purpose SHACL validation utility (DSP-930) ([#1762](https://www.github.com/dasch-swiss/dsp-api/issues/1762)) ([bfd3192](https://www.github.com/dasch-swiss/dsp-api/commit/bfd3192ea04d5f42d79836cf3b8fbf17007bab71))
* **sipi:** Improve error message if XSL file not found ([#1590](https://www.github.com/dasch-swiss/dsp-api/issues/1590)) ([bbb42f6](https://www.github.com/dasch-swiss/dsp-api/commit/bbb42f6fa5351dd5ec76eb79dc251b6f4f4b3d8c))
* **triplestores:** Support Apache Jena Fuseki ([#1375](https://www.github.com/dasch-swiss/dsp-api/issues/1375)) ([82f8a55](https://www.github.com/dasch-swiss/dsp-api/commit/82f8a55986932f215f106d19d321f14b88149052))
* **upgrade:** Update repository on startup ([#1643](https://www.github.com/dasch-swiss/dsp-api/issues/1643)) ([0127dca](https://www.github.com/dasch-swiss/dsp-api/commit/0127dcae27e46bc3a4c9947282c30038c0a25d20))

## v13.0.0-rc.25 (08/12/2020)

## Enhancements

* [#1768](https://github.com/dasch-swiss/knora-api/pull/1768) | DSP-1106 Update Permission
* [#1767](https://github.com/dasch-swiss/knora-api/pull/1767) | enhancement(triplestore): Use N-Quads instead of TriG for repository upgrade (DSP-1129)
* [#1764](https://github.com/dasch-swiss/knora-api/pull/1764) | DSP-1033 Reposition List Nodes
* [#1762](https://github.com/dasch-swiss/knora-api/pull/1762) | feat(rdf-api): Add a general-purpose SHACL validation utility (DSP-930)
* [#1759](https://github.com/dasch-swiss/knora-api/pull/1759) | feat: Add an RDF processing façade (2nd iteration) (DSP-1083)
* [#1760](https://github.com/dasch-swiss/knora-api/pull/1760) | (DSP-1031) Delete list items
* [#1753](https://github.com/dasch-swiss/knora-api/pull/1753) | Edit lists routes (DSP-597 )
* [#1758](https://github.com/dasch-swiss/knora-api/pull/1758) | feat(api-v2): Control JSON-LD nesting via an HTTP header (DSP-1084)

## Bug fixes

* [#1763](https://github.com/dasch-swiss/knora-api/pull/1763) | fix(sipi): Don't expect API v1 status code (DSP-1114)

## Documentation

* [#1771](https://github.com/dasch-swiss/knora-api/pull/1771) | docs: Update README (DSP-1142)

## Maintenance

* [#1770](https://github.com/dasch-swiss/knora-api/pull/1770) | refactor: Use java.nio.file.Path instead of java.io.File (DSP-1124)
* [#1765](https://github.com/dasch-swiss/knora-api/pull/1765) | DSP-1094 Upgrade Swagger version
* [#1766](https://github.com/dasch-swiss/knora-api/pull/1766) | style: Add Scalafmt config file
* [#1769](https://github.com/dasch-swiss/knora-api/pull/1769) | style: Reformat code with Scalafmt (DSP-1137)
* [#1754](https://github.com/dasch-swiss/knora-api/pull/1754) | feat(api-v2): Add an RDF processing façade (DSP-1020)
* [#1757](https://github.com/dasch-swiss/knora-api/pull/1757) | build: bazel workspace cleanup

---

## v13.0.0-rc.24 (13/11/2020)

* [#1756](https://github.com/dasch-swiss/knora-api/pull/1756) | DSP-1052 : Migration task to replace empty strings with dummy "FIXME"

---

## v13.0.0-rc.23 (09/11/2020)

## Bug fixes

* [#1755](https://github.com/dasch-swiss/knora-api/pull/1755) | DSP-1029: Add the missing dependency

---

## v13.0.0-rc.22 (09/11/2020)

#### Breaking changes

* [#1724](https://github.com/dasch-swiss/knora-api/pull/1724) | test: Collect client test data from E2E tests (DSP-724)
* [#1727](https://github.com/dasch-swiss/knora-api/pull/1727) | DSP-740 Update List Name
* [#1722](https://github.com/dasch-swiss/knora-api/pull/1722) | feat(api-v1): Change API v1 file uploads to work like API v2 (DSP-41, PR 3)
* [#1233](https://github.com/dasch-swiss/knora-api/pull/1233) | feat(api-v1): Change API v1 file uploads to work like API v2
* [#1708](https://github.com/dasch-swiss/knora-api/pull/1708) | Get Project Permissions

#### Enhancements

* [#1403](https://github.com/dasch-swiss/knora-api/pull/1403) | feat: Add time value type
* [#1537](https://github.com/dasch-swiss/knora-api/pull/1537) | build: Add env var to set triplestore actor pool
* [#1649](https://github.com/dasch-swiss/knora-api/pull/1649) | feat(api-v2): Allow querying for rdfs:label in Gravsearch
* [#1742](https://github.com/dasch-swiss/knora-api/pull/1742) | feat: Add feature toggles (DSP-910)
* [#1741](https://github.com/dasch-swiss/knora-api/pull/1741) | DSP-804: create a child node with a custom IRI
* [#1734](https://github.com/dasch-swiss/knora-api/pull/1734) | feat(api-v2): Add metadata routes (DSP-662)
* [#1739](https://github.com/dasch-swiss/knora-api/pull/1739) | enhancement(api-v2): Optimise checking isDeleted (DSP-848)
* [#1664](https://github.com/dasch-swiss/knora-api/pull/1664) | feat(api-v2): Add support for text file upload (DSP-44)
* [#1652](https://github.com/dasch-swiss/knora-api/pull/1652) | DSP-377 Support Islamic calendar
* [#1717](https://github.com/dasch-swiss/knora-api/pull/1717) | enhancement(gravsearch): Optimise queries by moving up statements with resource IRIs
* [#1713](https://github.com/dasch-swiss/knora-api/pull/1713) | feat(gravsearch): Allow comparing variables representing resource IRIs
* [#1710](https://github.com/dasch-swiss/knora-api/pull/1710) | update ontology metadata with a comment
* [#1704](https://github.com/dasch-swiss/knora-api/pull/1704) | feat(api-v2): Add test data
* [#1703](https://github.com/dasch-swiss/knora-api/pull/1703) | Add comments to ontology metadata
* [#1686](https://github.com/dasch-swiss/knora-api/pull/1686) | feat(api-v2): Accept custom timestamps in update/delete requests
* [#1692](https://github.com/dasch-swiss/knora-api/pull/1692) | Create Permissions
* [#1696](https://github.com/dasch-swiss/knora-api/pull/1696) | feat(api-v2): Make inference optional in Gravsearch
* [#1697](https://github.com/dasch-swiss/knora-api/pull/1697) | fix(sipi): Improve performance of file value query
* [#1698](https://github.com/dasch-swiss/knora-api/pull/1698) | feat(api-v2): Accept custom new value IRI when updating value
* [#1700](https://github.com/dasch-swiss/knora-api/pull/1700) | hierarchically ordered Sequence of base classes
* [#1689](https://github.com/dasch-swiss/knora-api/pull/1689) | build: bump SIPI to v3.0.0-rc.5 (DSP-547)
* [#1679](https://github.com/dasch-swiss/knora-api/pull/1679) | Gravsearch optimisations
* [#1663](https://github.com/dasch-swiss/knora-api/pull/1663) | build: add support for SIPI v3.0.0-rc.3 (DSP-433)
* [#1660](https://github.com/dasch-swiss/knora-api/pull/1660) | feat(gravsearch): Remove deprecated functions
* [#1653](https://github.com/dasch-swiss/knora-api/pull/1653) | build:  dockerize fuseki (dsp-30)

#### Bug Fixes

* [#1626](https://github.com/dasch-swiss/knora-api/pull/1626) | fix(gravsearch): Prevent duplicate results
* [#1587](https://github.com/dasch-swiss/knora-api/pull/1587) | fix (webapi): Add enforcing of restrictions for username and email
* [#1576](https://github.com/dasch-swiss/knora-api/pull/1576) | Add missing env var
* [#1571](https://github.com/dasch-swiss/knora-api/pull/1571) | fixed date string format
* [#1564](https://github.com/dasch-swiss/knora-api/pull/1564) | enable click on save button in case of recoverable error
* [#1751](https://github.com/dasch-swiss/knora-api/pull/1751) | DSP-1022 SIPI_EXTERNAL_HOSTNAME doesn't contain the external hostname
* [#1749](https://github.com/dasch-swiss/knora-api/pull/1749) | fix(api-v2): Don't check file extensions of XSL files and Gravsearch templates (DSP-1005)
* [#1748](https://github.com/dasch-swiss/knora-api/pull/1748) | DSP-756 Tests failing because Knora version header and route are incorrect
* [#1746](https://github.com/dasch-swiss/knora-api/pull/1746) | DSP-932: Don't allow missing StringLiteralV2 value if language tag given
* [#1744](https://github.com/dasch-swiss/knora-api/pull/1744) | DSP-917 Releases pushed to Dockerhub from DSP-API are "dirty"
* [#1733](https://github.com/dasch-swiss/knora-api/pull/1733) | DSP-470 Intermittent bind errors
* [#1728](https://github.com/dasch-swiss/knora-api/pull/1728) | fix(api-v2): Fix post-update check for resource with standoff link (DSP-841)
* [#1723](https://github.com/dasch-swiss/knora-api/pull/1723) | chore(build): Bump testcontainers version (DSP-755)
* [#1706](https://github.com/dasch-swiss/knora-api/pull/1706) | Fix of update of list node info and update of project info
* [#1712](https://github.com/dasch-swiss/knora-api/pull/1712) | fix: failing repository upgrade at startup (DSP-654)
* [#1709](https://github.com/dasch-swiss/knora-api/pull/1709) | fix(OntologyResponderV2): Fix ontology cache update when ontology metadata changed
* [#1701](https://github.com/dasch-swiss/knora-api/pull/1701) | reverse change of Permission JSONs
* [#1693](https://github.com/dasch-swiss/knora-api/pull/1693) | fix(api-v2): Fix generated SPARQL for updating property comment
* [#1699](https://github.com/dasch-swiss/knora-api/pull/1699) | fix(gravsearch): When link property compared in filter, don't compare link value property, too
* [#1691](https://github.com/dasch-swiss/knora-api/pull/1691) | fix: server header (DSP-537)
* [#1681](https://github.com/dasch-swiss/knora-api/pull/1681) | fix: init db scripts (DSP-511)
* [#1669](https://github.com/dasch-swiss/knora-api/pull/1669) | fix: loading of data (DSP-445)

#### Documentation

* [#1598](https://github.com/dasch-swiss/knora-api/pull/1598) | doc: fix sipi docs link
* [#1609](https://github.com/dasch-swiss/knora-api/pull/1609) | fix complex schema url
* [#1568](https://github.com/dasch-swiss/knora-api/pull/1568) | fixed the URI for the query
* [#1726](https://github.com/dasch-swiss/knora-api/pull/1726) | PersmissionsDocs: remove the attribute
* [#1725](https://github.com/dasch-swiss/knora-api/pull/1725) | docs: Update required mkdocs package
* [#1711](https://github.com/dasch-swiss/knora-api/pull/1711) | update developer and create resource docs
* [#1684](https://github.com/dasch-swiss/knora-api/pull/1684) | developer guideline
* [#1685](https://github.com/dasch-swiss/knora-api/pull/1685) | docs(api-v2): Document what happens when a resource has a link to a deleted resource
* [#1688](https://github.com/dasch-swiss/knora-api/pull/1688) | docs: fix broken links
* [#1694](https://github.com/dasch-swiss/knora-api/pull/1694) | docs: fix publishing
* [#1621](https://github.com/dasch-swiss/knora-api/pull/1621) | fixing typos for list rendering

#### Other

* [#1750](https://github.com/dasch-swiss/knora-api/pull/1750) | Update README.md
* [#1747](https://github.com/dasch-swiss/knora-api/pull/1747) | DSP-920 Renaming default github branch to "main" ;  Move to the same base branch
* [#1740](https://github.com/dasch-swiss/knora-api/pull/1740) | DSP-877 Upload api-client-test-data to GitHub release
* [#1738](https://github.com/dasch-swiss/knora-api/pull/1738) | DSP-877 Upload api-client-test-data to GitHub release
* [#1736](https://github.com/dasch-swiss/knora-api/pull/1736) | DSP-877 Upload api-client-test-data to GitHub release
* [#1730](https://github.com/dasch-swiss/knora-api/pull/1730) | DSP-816: Generate client test data for health route
* [#1719](https://github.com/dasch-swiss/knora-api/pull/1719) | change possibly conflictual env var USERNAME (DSP-706)
* [#1720](https://github.com/dasch-swiss/knora-api/pull/1720) | DSP-620 Update release process
* [#1714](https://github.com/dasch-swiss/knora-api/pull/1714) | test: fix generation of test data (DSP-665)
* [#1716](https://github.com/dasch-swiss/knora-api/pull/1716) | bulid: fix sipi image version (DSP-677)
* [#1718](https://github.com/dasch-swiss/knora-api/pull/1718) | DSP-702 Add template for PRs
* [#1715](https://github.com/dasch-swiss/knora-api/pull/1715) | chore(api-v2): Switch from JSONLD-Java to Titanium
* [#1707](https://github.com/dasch-swiss/knora-api/pull/1707) | chore: Update ci workflow
* [#1702](https://github.com/dasch-swiss/knora-api/pull/1702) | Add PR labels (DSP-607)
* [#1695](https://github.com/dasch-swiss/knora-api/pull/1695) | refactor(gravsearch): Clarify optimisations
* [#1678](https://github.com/dasch-swiss/knora-api/pull/1678) | refactor: first steps towards more independent packages (DSP-513)
* [#1680](https://github.com/dasch-swiss/knora-api/pull/1680) | build: bump rules_docker and instructions for installing bazelisk
* [#1674](https://github.com/dasch-swiss/knora-api/pull/1674) | build: add mkdocs for documentation generation (DSP-460)
* [#1480](https://github.com/dasch-swiss/knora-api/pull/1480) | build: add bazel (DSP-437)
* [#1666](https://github.com/dasch-swiss/knora-api/pull/1666) | Fix gren issue in github actions workflow
* [#1662](https://github.com/dasch-swiss/knora-api/pull/1662) | Publish on release only
* [#1661](https://github.com/dasch-swiss/knora-api/pull/1661) | Automated release notes

#### Dependencies

* [#1721](https://github.com/dasch-swiss/knora-api/pull/1721) | chore: bump sipi to rc.7 (DSP-733)
* [#1735](https://github.com/dasch-swiss/knora-api/pull/1735) | DSP-496 Bump Apache Jena Fuseki and Apache Jena Libraries to 3.16
* [#1737](https://github.com/dasch-swiss/knora-api/pull/1737) | DSP-842 Bump used Bazel version to newly released 3.7.0
* [#1743](https://github.com/dasch-swiss/knora-api/pull/1743) | chore(build): Upgrade Sipi to 3.0.0-rc.8 (DSP-916)
* [#1745](https://github.com/dasch-swiss/knora-api/pull/1745) | chore(build): Update ScalaTest (DSP-919)
* [#1752](https://github.com/dasch-swiss/knora-api/pull/1752) | DSP-1017 Upgrade to Sipi v3.0.0-rc.9

---

## v13.0.0-rc.21 (09/11/2020)

#### Breaking changes

* [#1724](https://github.com/dasch-swiss/knora-api/pull/1724) | test: Collect client test data from E2E tests (DSP-724)
* [#1727](https://github.com/dasch-swiss/knora-api/pull/1727) | DSP-740 Update List Name
* [#1722](https://github.com/dasch-swiss/knora-api/pull/1722) | feat(api-v1): Change API v1 file uploads to work like API v2 (DSP-41, PR 3)
* [#1233](https://github.com/dasch-swiss/knora-api/pull/1233) | feat(api-v1): Change API v1 file uploads to work like API v2
* [#1708](https://github.com/dasch-swiss/knora-api/pull/1708) | Get Project Permissions

#### Enhancements

* [#1403](https://github.com/dasch-swiss/knora-api/pull/1403) | feat: Add time value type
* [#1649](https://github.com/dasch-swiss/knora-api/pull/1649) | feat(api-v2): Allow querying for rdfs:label in Gravsearch
* [#1742](https://github.com/dasch-swiss/knora-api/pull/1742) | feat: Add feature toggles (DSP-910)
* [#1741](https://github.com/dasch-swiss/knora-api/pull/1741) | DSP-804: create a child node with a custom IRI
* [#1734](https://github.com/dasch-swiss/knora-api/pull/1734) | feat(api-v2): Add metadata routes (DSP-662)
* [#1739](https://github.com/dasch-swiss/knora-api/pull/1739) | enhancement(api-v2): Optimise checking isDeleted (DSP-848)
* [#1664](https://github.com/dasch-swiss/knora-api/pull/1664) | feat(api-v2): Add support for text file upload (DSP-44)
* [#1652](https://github.com/dasch-swiss/knora-api/pull/1652) | DSP-377 Support Islamic calendar
* [#1717](https://github.com/dasch-swiss/knora-api/pull/1717) | enhancement(gravsearch): Optimise queries by moving up statements with resource IRIs
* [#1713](https://github.com/dasch-swiss/knora-api/pull/1713) | feat(gravsearch): Allow comparing variables representing resource IRIs
* [#1710](https://github.com/dasch-swiss/knora-api/pull/1710) | update ontology metadata with a comment
* [#1704](https://github.com/dasch-swiss/knora-api/pull/1704) | feat(api-v2): Add test data
* [#1703](https://github.com/dasch-swiss/knora-api/pull/1703) | Add comments to ontology metadata
* [#1686](https://github.com/dasch-swiss/knora-api/pull/1686) | feat(api-v2): Accept custom timestamps in update/delete requests
* [#1692](https://github.com/dasch-swiss/knora-api/pull/1692) | Create Permissions
* [#1696](https://github.com/dasch-swiss/knora-api/pull/1696) | feat(api-v2): Make inference optional in Gravsearch
* [#1697](https://github.com/dasch-swiss/knora-api/pull/1697) | fix(sipi): Improve performance of file value query
* [#1698](https://github.com/dasch-swiss/knora-api/pull/1698) | feat(api-v2): Accept custom new value IRI when updating value
* [#1700](https://github.com/dasch-swiss/knora-api/pull/1700) | hierarchically ordered Sequence of base classes
* [#1689](https://github.com/dasch-swiss/knora-api/pull/1689) | build: bump SIPI to v3.0.0-rc.5 (DSP-547)
* [#1679](https://github.com/dasch-swiss/knora-api/pull/1679) | Gravsearch optimisations
* [#1663](https://github.com/dasch-swiss/knora-api/pull/1663) | build: add support for SIPI v3.0.0-rc.3 (DSP-433)
* [#1660](https://github.com/dasch-swiss/knora-api/pull/1660) | feat(gravsearch): Remove deprecated functions
* [#1653](https://github.com/dasch-swiss/knora-api/pull/1653) | build:  dockerize fuseki (dsp-30)

#### Bug Fixes

* [#1626](https://github.com/dasch-swiss/knora-api/pull/1626) | fix(gravsearch): Prevent duplicate results
* [#1587](https://github.com/dasch-swiss/knora-api/pull/1587) | fix (webapi): Add enforcing of restrictions for username and email
* [#1751](https://github.com/dasch-swiss/knora-api/pull/1751) | DSP-1022 SIPI_EXTERNAL_HOSTNAME doesn't contain the external hostname
* [#1749](https://github.com/dasch-swiss/knora-api/pull/1749) | fix(api-v2): Don't check file extensions of XSL files and Gravsearch templates (DSP-1005)
* [#1748](https://github.com/dasch-swiss/knora-api/pull/1748) | DSP-756 Tests failing because Knora version header and route are incorrect
* [#1746](https://github.com/dasch-swiss/knora-api/pull/1746) | DSP-932: Don't allow missing StringLiteralV2 value if language tag given
* [#1744](https://github.com/dasch-swiss/knora-api/pull/1744) | DSP-917 Releases pushed to Dockerhub from DSP-API are "dirty"
* [#1733](https://github.com/dasch-swiss/knora-api/pull/1733) | DSP-470 Intermittent bind errors
* [#1728](https://github.com/dasch-swiss/knora-api/pull/1728) | fix(api-v2): Fix post-update check for resource with standoff link (DSP-841)
* [#1723](https://github.com/dasch-swiss/knora-api/pull/1723) | chore(build): Bump testcontainers version (DSP-755)
* [#1706](https://github.com/dasch-swiss/knora-api/pull/1706) | Fix of update of list node info and update of project info
* [#1712](https://github.com/dasch-swiss/knora-api/pull/1712) | fix: failing repository upgrade at startup (DSP-654)
* [#1709](https://github.com/dasch-swiss/knora-api/pull/1709) | fix(OntologyResponderV2): Fix ontology cache update when ontology metadata changed
* [#1701](https://github.com/dasch-swiss/knora-api/pull/1701) | reverse change of Permission JSONs
* [#1693](https://github.com/dasch-swiss/knora-api/pull/1693) | fix(api-v2): Fix generated SPARQL for updating property comment
* [#1699](https://github.com/dasch-swiss/knora-api/pull/1699) | fix(gravsearch): When link property compared in filter, don't compare link value property, too
* [#1691](https://github.com/dasch-swiss/knora-api/pull/1691) | fix: server header (DSP-537)
* [#1681](https://github.com/dasch-swiss/knora-api/pull/1681) | fix: init db scripts (DSP-511)
* [#1669](https://github.com/dasch-swiss/knora-api/pull/1669) | fix: loading of data (DSP-445)

#### Documentation

* [#1598](https://github.com/dasch-swiss/knora-api/pull/1598) | doc: fix sipi docs link
* [#1609](https://github.com/dasch-swiss/knora-api/pull/1609) | fix complex schema url
* [#1568](https://github.com/dasch-swiss/knora-api/pull/1568) | fixed the URI for the query
* [#1726](https://github.com/dasch-swiss/knora-api/pull/1726) | PersmissionsDocs: remove the attribute
* [#1725](https://github.com/dasch-swiss/knora-api/pull/1725) | docs: Update required mkdocs package
* [#1711](https://github.com/dasch-swiss/knora-api/pull/1711) | update developer and create resource docs
* [#1684](https://github.com/dasch-swiss/knora-api/pull/1684) | developer guideline
* [#1685](https://github.com/dasch-swiss/knora-api/pull/1685) | docs(api-v2): Document what happens when a resource has a link to a deleted resource
* [#1688](https://github.com/dasch-swiss/knora-api/pull/1688) | docs: fix broken links
* [#1694](https://github.com/dasch-swiss/knora-api/pull/1694) | docs: fix publishing
* [#1621](https://github.com/dasch-swiss/knora-api/pull/1621) | fixing typos for list rendering

#### Other

* [#1750](https://github.com/dasch-swiss/knora-api/pull/1750) | Update README.md
* [#1747](https://github.com/dasch-swiss/knora-api/pull/1747) | DSP-920 Renaming default github branch to "main" ;  Move to the same base branch
* [#1740](https://github.com/dasch-swiss/knora-api/pull/1740) | DSP-877 Upload api-client-test-data to GitHub release
* [#1738](https://github.com/dasch-swiss/knora-api/pull/1738) | DSP-877 Upload api-client-test-data to GitHub release
* [#1736](https://github.com/dasch-swiss/knora-api/pull/1736) | DSP-877 Upload api-client-test-data to GitHub release
* [#1730](https://github.com/dasch-swiss/knora-api/pull/1730) | DSP-816: Generate client test data for health route
* [#1719](https://github.com/dasch-swiss/knora-api/pull/1719) | change possibly conflictual env var USERNAME (DSP-706)
* [#1720](https://github.com/dasch-swiss/knora-api/pull/1720) | DSP-620 Update release process
* [#1714](https://github.com/dasch-swiss/knora-api/pull/1714) | test: fix generation of test data (DSP-665)
* [#1716](https://github.com/dasch-swiss/knora-api/pull/1716) | bulid: fix sipi image version (DSP-677)
* [#1718](https://github.com/dasch-swiss/knora-api/pull/1718) | DSP-702 Add template for PRs
* [#1715](https://github.com/dasch-swiss/knora-api/pull/1715) | chore(api-v2): Switch from JSONLD-Java to Titanium
* [#1707](https://github.com/dasch-swiss/knora-api/pull/1707) | chore: Update ci workflow
* [#1702](https://github.com/dasch-swiss/knora-api/pull/1702) | Add PR labels (DSP-607)
* [#1695](https://github.com/dasch-swiss/knora-api/pull/1695) | refactor(gravsearch): Clarify optimisations
* [#1678](https://github.com/dasch-swiss/knora-api/pull/1678) | refactor: first steps towards more independent packages (DSP-513)
* [#1680](https://github.com/dasch-swiss/knora-api/pull/1680) | build: bump rules_docker and instructions for installing bazelisk
* [#1674](https://github.com/dasch-swiss/knora-api/pull/1674) | build: add mkdocs for documentation generation (DSP-460)
* [#1480](https://github.com/dasch-swiss/knora-api/pull/1480) | build: add bazel (DSP-437)
* [#1666](https://github.com/dasch-swiss/knora-api/pull/1666) | Fix gren issue in github actions workflow
* [#1662](https://github.com/dasch-swiss/knora-api/pull/1662) | Publish on release only
* [#1661](https://github.com/dasch-swiss/knora-api/pull/1661) | Automated release notes

---

## v12.0.0 (27/01/2020)

#### Breaking API Changes

* [#1439](https://github.com/dasch-swiss/knora-api/issues/1439) JSON-LD Serialization of an xsd:dateTimeStamp

#### New Features and Enhancements

* [#1509](https://github.com/dasch-swiss/knora-api/pull/1509) Support lists admin endpoint
* [#1466](https://github.com/dasch-swiss/knora-api/pull/1466) Optimise generated SPARQL

#### Bug Fixes

* [#1569](https://github.com/dasch-swiss/knora-api/issues/1569) broken ark
* [#1559](https://github.com/dasch-swiss/knora-api/issues/1559) Admin lists: createChildNode should send a httpPost request, not httpPut

---

## v11.0.0 (16/12/2019)

#### Breaking Changes

* [#1344](https://github.com/dasch-swiss/knora-api/issues/1344) Gravsearch ForbiddenResource result and permissions of linked resources
* [#1202](https://github.com/dasch-swiss/knora-api/issues/1202) Implement upload of PDF and text files in API v2. Users with files in Sipi under `/server` must move them to `/images` when upgrading.

#### Bug Fixes

* [#1531](https://github.com/dasch-swiss/knora-api/issues/1531) Sipi's mimetype_consistency fails with .bin file
* [#1430](https://github.com/dasch-swiss/knora-api/issues/1430) Creating the first resource with an image inside a project fails with Sipi not finding the project folder
* [#924](https://github.com/dasch-swiss/knora-api/issues/924) Get dependent resources Iris

---

## v10.1.1 (27/11/2019)

---

## v10.1.0 (27/11/2019)

---

## v10.0.0 (22/10/2019)

#### Breaking Changes

* [#1346](https://github.com/dasch-swiss/knora-api/issues/1346) Richtext/HTML in page anchor link

#### Enhancements

* [#1457](https://github.com/dasch-swiss/knora-api/issues/1457) Upgrade sipi to 2.0.1

#### Bug Fixes

* [#1460](https://github.com/dasch-swiss/knora-api/issues/1460) Build banner in README is broken

#### Documentation

* [#1481](https://github.com/dasch-swiss/knora-api/issues/1481) build badge in README has broken link

#### Other

* [#1449](https://github.com/dasch-swiss/knora-api/issues/1449) Add Makefile-based task execution
* [#1401](https://github.com/dasch-swiss/knora-api/issues/1401) Enable testing docs generation in Travis

---

## v9.1.0 (26/09/2019)

#### Enhancements

* [#1421](https://github.com/dhlab-basel/Knora/issues/1421) Physically deleting a resource

#### Documentation

* [#1407](https://github.com/dhlab-basel/Knora/issues/1407) Document ARK URLs for projects

---

## v9.0.0 (29/08/2019)

#### Breaking Changes

* [#1411](https://github.com/dhlab-basel/Knora/issues/1411) Moved `/admin/groups/members/GROUP_IRI` to `/admin/groups/GROUP_IRI/members`
* [#1231](https://github.com/dhlab-basel/Knora/issues/1231) Change value permissions
* [#763](https://github.com/dhlab-basel/Knora/issues/763) refactor splitMainResourcesAndValueRdfData so it uses SparqlExtendedConstructResponse

#### Enhancements

* [#1373](https://github.com/dhlab-basel/Knora/issues/1373) The startup ends in a thrown exception if the triplestore is not up-to-date
* [#1364](https://github.com/dhlab-basel/Knora/issues/1364) Add support for Redis cache
* [#1360](https://github.com/dhlab-basel/Knora/issues/1360) Build and publish Knora version specific docker images for GraphDB Free and SE
* [#1358](https://github.com/dhlab-basel/Knora/issues/1358) Add admin route to dump project data

#### Bug Fixes

* [#1394](https://github.com/dhlab-basel/Knora/issues/1394) Using dockerComposeUp to start the stack, fails to find Redis at startup

#### Documentation

* [#1386](https://github.com/dhlab-basel/Knora/issues/1386) Add lists admin API documentation

#### Other

* [#1412](https://github.com/dhlab-basel/Knora/issues/1412) Change release notes to be based on issues

---

## v8.0.0 (14/06/2019)
* [feature(webapi): Add GraphDB-Free startup support (#1351)](https://github.com/dhlab-basel/Knora/commit/5ecb54c563dc2ec38dbbcdf544c5f86f0ce90d0d) - @subotic
* [feature(webapi): Add returning of fixed public user information (#1348)](https://github.com/dhlab-basel/Knora/commit/ff6b140bf7e6b8b481bb1773a5accc3ba5d5d9fe) - @subotic
* [feat(api-v2): No custom permissions higher than defaults (#1337)](https://github.com/dhlab-basel/Knora/commit/7b61b49d7686a13a79f5f25c5f0e20cab0b6c12f) - @benjamingeer
* [feat(upgrade): Improve upgrade framework (#1345)](https://github.com/dhlab-basel/Knora/commit/06487b1e6b227cc7794e53f19e30551774015686) - @benjamingeer
* [test(webapi): Add new user authentication (#1201)](https://github.com/dhlab-basel/Knora/commit/1845eb1a0caa0441483d28176b84a2a59cfebe3a) - @subotic
* [chore(webapi): Add request duration logging (#1347)](https://github.com/dhlab-basel/Knora/commit/9b701f9adcb3e710e8e39bc35c3cb7964bde531a) - @subotic
* [feat(api-v2): Make values citable (#1322)](https://github.com/dhlab-basel/Knora/commit/9f99af11ca466da8943f2d6b44342fed2beca9ba) - @benjamingeer
* [Leibniz ontology  (#1326)](https://github.com/dhlab-basel/Knora/commit/56e311d03dfed2b42f490abab01a0d612be11d15) - @SepidehAlassi
* [feature(webapi): add CORS allow header (#1340)](https://github.com/dhlab-basel/Knora/commit/64177807a070a36c6c852b8f7d79645f45d0ce7b) - @subotic
* [fix(sipi): Return permissions for a previous version of a file value. (#1339)](https://github.com/dhlab-basel/Knora/commit/9a3cee3b665fa1fdd82615932f43b1bd8551f402) - @benjamingeer
* [fix(scripts): add admin ontology data to correct graph (#1333)](https://github.com/dhlab-basel/Knora/commit/002eca45187e4c7ec30129c57ccaa095547a420e) - @subotic
* [fix(sipi): Don't try to read a file value in a deleted resource. (#1329)](https://github.com/dhlab-basel/Knora/commit/3adb22e88615e7edcdf2c2d2c8ab905dbe7f40db) - @benjamingeer
* [docs(api-v2): Fix sample responses. (#1327)](https://github.com/dhlab-basel/Knora/commit/904c638b537350e2cfe881ccf3e9b51d955150c1) - @benjamingeer
* [fix(api-v2): Fix typo. (#1325)](https://github.com/dhlab-basel/Knora/commit/72d89dc4a870fa8bd6f1ac60b0743a022adbb99c) - @benjamingeer
* [Handle List Nodes in Response (#1321)](https://github.com/dhlab-basel/Knora/commit/611f42880902065d28fcb84742f90757840d9ed5) - @tobiasschweizer
* [feat(api-v2): Return standoff markup separately from text values (#1307)](https://github.com/dhlab-basel/Knora/commit/ffbb5965223dfaea6cec77201fea5c9fd11bbc67) - @benjamingeer
* [BEOL: Import comments for Meditationes (#1281)](https://github.com/dhlab-basel/Knora/commit/9480f42faa64873a82fb2528a0bc8011669c7c49) - @tobiasschweizer
* [feat(triplestore): Log SPARQL query if triplestore doesn't respond. (#1292)](https://github.com/dhlab-basel/Knora/commit/522b3a9f6effad2d9fa1332c85e4a406a846d223) - @benjamingeer
* [Support list nodes in Gravsearch (#1314)](https://github.com/dhlab-basel/Knora/commit/0a1845c54a26d4fe00d2084daa7bff73ae571280) - @tobiasschweizer

---

## v7.0.0 (03/05/2019)
* [fix(api-v2): Cache base class IRIs correctly when creating/updating class (#1311)](https://github.com/dhlab-basel/Knora/commit/db8b938f605aad966de4f77d269be404f56f2a14) - @benjamingeer
* [chore(standoff): Use Base64-encoded UUIDs in standoff tags. (#1301)](https://github.com/dhlab-basel/Knora/commit/20736f737e84ba540fe022e3929cda645ef3137d) - @benjamingeer
* [feat(api-v2): Allow a resource to be created as a specified user (#1306)](https://github.com/dhlab-basel/Knora/commit/2b2961e6279dcf811cfdfed3c27e3b0923001d98) - @benjamingeer
* [feat(admin): Give the admin ontology an external schema (#1291)](https://github.com/dhlab-basel/Knora/commit/31ab1ca9196c365628108380cbedb69cd3249df5) - @benjamingeer
* [fix(api-v2): Remove INFORMATION SEPARATOR TWO from text in the simple schema. (#1299)](https://github.com/dhlab-basel/Knora/commit/f888cc68649cde6314bf186454f7682e25597d5d) - @benjamingeer
* [test: Compare Knora response with its class definition (#1297)](https://github.com/dhlab-basel/Knora/commit/df8af5ddfe1c173f23446b38adf3fb13b13d83be) - @benjamingeer
* [docs(api-admin): fix description of the change password payload (#1285)](https://github.com/dhlab-basel/Knora/commit/5c0db97e0e26bf8e5b23d393a1bf6ae896e16b15) - @loicjaouen
* [fix(api-v1): Fix double escaping of newline. (#1296)](https://github.com/dhlab-basel/Knora/commit/855a51d838617a3733d6c67d8f657c47c8781032) - @benjamingeer
* [fix (tei beol): fix problems in XSLT (#1260)](https://github.com/dhlab-basel/Knora/commit/a568257c6897ffb75285a36c86f8176bfd7d1958) - @tobiasschweizer
* [refactor(ontology): Make knora-admin a separate ontology (#1263)](https://github.com/dhlab-basel/Knora/commit/11c20080e433bb20e39f90ac036c6387c161ff99) - @benjamingeer
* [a handfull of changes in documentation and error messages (#1278)](https://github.com/dhlab-basel/Knora/commit/9bf02d41fd7429bf10b9a64c8a52ae6137b55b2a) - @loicjaouen
* [docs: fix missing username (#1269)](https://github.com/dhlab-basel/Knora/commit/897a6ec95ddfb6d60abb7941b1f92a63485a2aab) - @loicjaouen
* [feat(api-v2): Get resources in a particular class from a project (#1251)](https://github.com/dhlab-basel/Knora/commit/480ef721548b3552875a614cc408ab7b72527b9d) - @benjamingeer
* [fix(sipi): Improve error checking of Sipi's knora.json response. (#1279)](https://github.com/dhlab-basel/Knora/commit/d3e8bb94bbd5db460c55f508f7d5cfe5d80d1c05) - @benjamingeer
* [feat(api-v2): Return user's permission on resources and values (#1257)](https://github.com/dhlab-basel/Knora/commit/30321b806e24cdafcb91210f26881f5dab46ed91) - @benjamingeer
* [fix(api-v1): Escape rdfs:label in bulk import. (#1276)](https://github.com/dhlab-basel/Knora/commit/4781384a8bc1f0e8698659929d7a0b6693179d7e) - @benjamingeer
* [chore(webapi): Remove persistent map code (#1254)](https://github.com/dhlab-basel/Knora/commit/26496703eff89a52d591f81f9e021731d452df7a) - @benjamingeer
* [docs (api-v2): Update outdated ARK documentation. (#1252)](https://github.com/dhlab-basel/Knora/commit/b3ecebec2bf6fb3c49ea34a4b103bcbcdc7fd51a) - @benjamingeer
* [Update build.properties (#1265)](https://github.com/dhlab-basel/Knora/commit/5401943b7c81e0efb21c2b231c98a7afed60ede6) - @subotic

---

## v6.0.1 (22/03/2019)
* [chore: releasing-v6.0.1 (#1270)](https://github.com/dhlab-basel/Knora/commit/f65a02a82050ebde72cbdbe89faba217646d1ce5) - @subotic
* [chore(webapi): Add script for loading of a minimal set of data (#1267)](https://github.com/dhlab-basel/Knora/commit/7ed1425d84a42a7115af4885a44e4adc467e6eae) - @subotic
* [fix (beolPersonLabel) typo in label of hasBirthPlace (#1248)](https://github.com/dhlab-basel/Knora/commit/a08117737e2d6b159a2a569801938b67d76a95ce) - @SepidehAlassi
* [fix (webapi): message typo (#1244)](https://github.com/dhlab-basel/Knora/commit/9cea41a3a5ad6630f1fbc75e2656533ae6bf6da2) - @subotic
* [Unescape standoff string attributes when verifying text value update (#1242)](https://github.com/dhlab-basel/Knora/commit/af35c9520ccf0dceab0eb3bba83aa7c7aba8491b) - @benjamingeer
* [docs: fix user admin api (#1237)](https://github.com/dhlab-basel/Knora/commit/4fcda61f6dc667f5950b15bb7a6536bd503a9565) - @subotic

---

## v6.0.0 (28/02/2019)

# Release Notes

* MAJOR: Use HTTP POST to mark resources and values as deleted (#1203)

* MAJOR: Reorganize user and project routes (#1209)

* FEATURE: Secure routes returning user information (#961)

* MAJOR: Change all `xsd:dateTimeStamp` to `xsd:dateTime` in the triplestore (#1211).
  Existing data must be updated; see `upgrade/1211-datetime` for instructions.

* FIX: Ignore order of attributes when comparing standoff (#1224).

* FEATURE: Query version history (#1214)

* FIX: Don't allow conflicting cardinalities (#1229)

* MAJOR: Remove preview file values (#1230). Existing data must be updated;
  see `upgrade/1230-delete-previews` for instructions.

---

## v5.0.0 (05/02/2019)

# Release Notes

* MAJOR: Fix property names for incoming links (#1144))
* MAJOR: Generate and resolve ARK URLs for resources (#1161). Projects
  that have resource IRIs that do not conform to the format specified in
  <https://docs.knora.org/paradox/03-apis/api-v2/knora-iris.html#iris-for-data>
  must update them.
* MAJOR: Use project shortcode in IIIF URLs (#1191). If you have file value IRIs containing the substring `/reps/`, you must replace `/reps/` with `/values/`.

* FEATURE: Update resource metadata in API v2 (#1131)
* FEATURE: Allow setting resource creation date in bulk import #1151)
* FEATURE: The `v2/authentication` route now also initiates cookie creation (the same as `v1/authentication`) (#1159)
* FEATURE: Allow to specify restricted view settings for a project which Sipi will adhere to (#690).

* FIX: Triplestore connection error when using dockerComposeUp (#1122)
* FIX: Reject link value properties in Gravsearch queries in the simple schema (#1145)
* FIX: Fix error-checking when updating cardinalities in ontology API (#1142)
* FIX: Allow hasRepresentation in an ontology used in a bulk import (#1171)
* FIX: Set cookie domain to the value specified in `application.conf` with the setting `cookie-domain` (#1169)
* FIX: Fix processing of shared property in bulk import (#1182)

---

## v4.0.0 (12/12/2018)

# v4.0.0 Release Notes

* MAJOR CHANGE: mapping creation request and response formats have changed (#1094)
* MINOR CHANGE: Update technical user docs (#1085)
* BUGFIX CHANGE: Fix permission checking in API v2 resource creation (#1104)

---

## v3.0.0 (30/11/2018)

# v3.0.0 Release Notes

* [BREAKING ONTOLOGY CHANGE] The property `knora-base:username` was added and is required for `knora-base:User`. (#1047)
* [BREAKING API CHANGE] The `/admin/user` API has changed due to adding the `username` property. (#1047)
* [FIX] Incorrect standoff to XML conversion if empty tag has empty child tag (#1054)
* [FEATURE] Add default permission caching (#1062)
* [FIX] Fix unescaping in update check and reading standoff URL (#1074)
* [FIX] Incorrect standoff to XML conversion if empty tag has empty child tag (#1054)
* [FEATURE] Create image file values in API v2 (#1011). Requires Sipi with tagged commit `v1.4.1-SNAPSHOT` or later.

---

## v2.1.0 (02/11/2018)

### New features

* Implement graph query in API v2 (#1009)
* Expose additional `webapi` settings as environment variables. Please see the [Configuration](https://docs.knora.org/paradox/04-deployment/configuration.html) section in the documentation for more information (#1025)

### Bugfixes

* sipi container config / sipi not able to talk to knora (#994)

---

## v2.1.0-snapshot (22/10/2018)

---

## v2.0.0 (13/09/2018)

This is the first release with the new version numbering convention. From now on, if any changes
to the existing data are necessary for a release, then this release will have its major number increased.
Please see the [Release Versioning Convention](https://github.com/dhlab-basel/Knora#release-versioning-convention) description.

### Required changes to existing data

* a `knora-base:ListNode` must have at least one `rdfs:label`. (@github[#991](#990))

### New features

* add developer-centric docker-compose.yml for starting the Knora / GraphDB / Sipi / Salsah1 (@github[#979](#979))
* configure `webapi` and `salsah1` thorough environment variables (@github[#979](#979))
* update for Java 10 (@github[#979](#979))
* comment out the generation of fat jars from `KnoraBuild.sbt` (for now) (@github[#979](#979))
* update ehcache (@github[#979](#979))
* update sbt to 1.2.1 (@github[#979](#979))
* remove Kamon monitoring (for now) since we don't see anything meaningful there. We probably will have to instrument Knora by hand and then use Kamon for access. (@github[#979](#979))
* update Dockerfiles for `webapi` and `salsah1` (@github[#979](#979))
* follow subClassOf when including ontologies in XML import schemas (@github[#991](#991))
* add support for adding list child nodes (@github[#991](#990))
* add support for shared ontologies (@github[#987](#987))

### Bugfixes

* trouble with xml-checker and/or consistency-checker during bulk import (@github[#978](#978))
* ontology API error with link values (@github[#988](#988))

---

## v1.7.1 (29/08/2018)

Knora-Stack compatible versions
---

Knora v1.7.1 - Salsah v2.1.2 - Sipi v1.4.0 - GraphDB v8.5.0

* doc (webapi): add yourkit acknowledgment (#983)
* Don't allow class with cardinalities on P and on a subproperty of P (#982)
* doc (webapi): add LHTT project shortcode (#981)
* feature (webapi): not return or allow changing of built-in users (#975)
* fix (webapi): startup check does not detect running triplestore (#969)
* Fix bulk import parsing bug and limit concurrent client connections (#973)

---

## v1.7.0 (16/08/2018)

See the closed tickets on the [v1.7.0 milestone](https://github.com/dhlab-basel/Knora/milestone/11).

Knora-Stack compatible versions
---

Knora v1.7.0 - Salsah v2.1.0 - Sipi v1.4.0 - GraphDB v8.5.0

Required changes to existing data
----------------------------------

* To use the inferred Gravsearch predicate `knora-api:standoffTagHasStartAncestor`,
  you must recreate your repository with the updated `KnoraRules.pie`.

New features
-------------

* Gravsearch queries can now match standoff markup (#910).
* Add Graphdb-Free initialization scripts for local and docker installation (#955).
* Create temp dirs at startup (#951)
* Update versions of monitoring tools (#951)

Bugfixes
---------

* timeout or java.lang.OutOfMemoryError when using /v1/resources/xmlimportschemas/ for some ontologies (#944)
* Timeout cleanup (#951)
* Add separate dispatchers (#945)

---

## v1.6.0 (29/06/2018)

v1.6.0 Release Notes
====================

See the
[release](https://github.com/dhlab-basel/Knora/releases/tag/v1.6.0) and closed tickets on the
[v1.6.0 milestone](https://github.com/dhlab-basel/Knora/milestone/10) on Github.

Required changes to existing data
----------------------------------

* A project is now required to have at least one description, so potentially a description will need
  to be added to those projects that don't have one.

New features
-------------

General:

* Added a `/health` endpoint
* KnoraService waits on startup for a triplestore before trying to load the ontologies

Gravsearch enhancements:

* Accept queries in POST requests (@github[#650](#650)).
* Allow a Gravsearch query to specify the IRI of the main resource (@github[#871](#871)) (by allowing `BIND`).
* Allow `lang` to be used with `!=`.
* A `UNION` or `OPTIONAL` can now be nested in an `OPTIONAL` (@github[#882](#882)).
* Gravsearch now does type inference (@github[#884](#884)).
* The Knora API v2 complex schema can now be used in Gravsearch, making it possible to search
  for list nodes (@github[#899](#899)).

Admin API:

* Make project description required (@github[#875](#875)).

Conversion to TEI:

* Conversion of standard standoff entities to TEI
* Custom conversion of project specific standoff entities and metadata to TEI

Sipi integration:

* The Knora specific Sipi configuration and scripts can now be found under the `sipi/` directory (@github[#404](#404)).
* Documentation on how Sipi can be started changed (@github[#404](#404)).

Bugfixes
---------

* Allow a class or property definition to have more than one object for `rdf:type` (@github[#885](#885)).
* Exclude list values from v2 fulltext search (@github[#906](#906)).

Gravsearch fixes:

* Allow the `lang` function to be used in a comparison inside AND/OR (@github[#846](#846)).
* Fix the processing of resources with multiple incoming links that use the same property (@github[#878](#878)).
* Fix the parsing of a FILTER inside an OPTIONAL (@github[#879](#879)).
* Require the `match` function to be the top-level expression in a `FILTER`.

---

## v1.5.0 (31/05/2018)

See [v1.5.0 milestone](https://github.com/dhlab-basel/Knora/milestone/9) for a full list of closed tickets.

New features
-------------

* Resources can be returned in the simple ontology schema (#833).
* Text values can specify the language of the text (#819).
* Responses can be returned in Turtle and RDF/XML (#851).

Bugfixes
---------

* Incorrect representation of IRI object values in JSON-LD (#835)
* GenerateContributorsFile broken (#797)

---

## v1.4.0 (30/04/2018)

Required changes to existing data
----------------------------------

* Every ontology must now have the property `knora-base:attachedToProject`, which points to the IRI of the project that is responsible for the ontology. This must be added to each project-specific ontology in existing repositories. All built-in ontologies have been updated to have this property, and must, therefore, be reloaded into existing repositories.  
The property `knora-base:projectOntology` has been removed, and must be removed from project definitions in existing repositories.

* Every project now needs to have the property `knora-base:projectShortcode` set.

New features
-------------

* Added OpenAPI / Swagger API documentation route
* The Knora API server now checks the validity of ontologies on startup.
* The property ``knora-base:projectShortcode`` is now a required property (was optional).

Bugfixes
---------

* API v1 extended search was not properly handling multiple conditions
    on list values (issue \#800)
* Fix image orientation in SALSAH 1 (issue \#726)

---

## v1.3.1 (06/04/2018)

---

## v1.3.0 (28/03/2018)

### Required changes to existing data

#### 1. Replace salsah-gui ontology

You must replace the ``salsah-gui`` ontology that you have in the triplestore with the one
in ``salsah-gui.ttl``.

### New features

* More support for salsah-gui elements and attributes in ontologies
  * Serve the ``salsah-gui`` ontology in API v2 in the default schema.
  * Show ``salsah-gui:guiElement`` and ``salsah-gui:guiAttribute`` when serving ontologies in API v2 in the default schema.
  * Allow ``salsah-gui:guiElement`` and ``salsah-gui:guiAttribute`` to be included in new property definitions created via API v2.
  * Change ``salsah-gui`` so that GraphDB's consistency checker can check the use of ``guiElement`` and ``guiAttribute``.
* Changes to ``application.conf``. The ``sipi`` and ``web-api`` sections have received a big update, adding separate settings  for internal and external host settings:

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

### Bugfixes

* When API v2 served ``knora-api`` (default schema), ``salsah-gui:guiElement`` and ``salsah-gui:guiAttribute`` were not shown in properties in that ontology.
* The predicate ``salsah-gui:guiOrder`` was not accepted when creating a property via API v2.
