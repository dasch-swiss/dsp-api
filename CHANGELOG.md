# Changelog

## [0.8.1](https://github.com/dasch-swiss/dsp-ingest/compare/v0.8.0...v0.8.1) (2024-03-15)


### Maintenance

* Patch dependency updates ([#189](https://github.com/dasch-swiss/dsp-ingest/issues/189)) ([4d1bb0e](https://github.com/dasch-swiss/dsp-ingest/commit/4d1bb0ef80d90bf0e748952998673619e30c4557))

## [0.8.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.7.0...v0.8.0) (2024-03-12)


### Enhancements

* Display size in bytes in asset-overview report columns ([#188](https://github.com/dasch-swiss/dsp-ingest/issues/188)) ([f389a31](https://github.com/dasch-swiss/dsp-ingest/commit/f389a31707c9e913444fc40180af7dbc6082a8fd))
* Prevent mapping csv download during bulk ingest of project (DEV-3402) ([#185](https://github.com/dasch-swiss/dsp-ingest/issues/185)) ([fce7415](https://github.com/dasch-swiss/dsp-ingest/commit/fce7415f67a14d0abdd2ac09a984f87c6694822f))


### Maintenance

* Start release PRs as draft ([#187](https://github.com/dasch-swiss/dsp-ingest/issues/187)) ([432c069](https://github.com/dasch-swiss/dsp-ingest/commit/432c06948338bf17fd334391abc3b34cad39b58c))

## [0.7.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.6.3...v0.7.0) (2024-03-08)


### Enhancements

* Add assets overview report ([#175](https://github.com/dasch-swiss/dsp-ingest/issues/175)) ([612963e](https://github.com/dasch-swiss/dsp-ingest/commit/612963efca6efb2e2f5ca771b3cfb8b7a6bf20dd))
* Allow a single bulk-ingest per project only (DEV-3379) ([#182](https://github.com/dasch-swiss/dsp-ingest/issues/182)) ([c79c785](https://github.com/dasch-swiss/dsp-ingest/commit/c79c7857492f73764644742742dd22ca10984304))
* Make get /projects/&lt;shortcode&gt;/bulk-ingest/mapping.csv respond with a file (DEV-3380) ([#181](https://github.com/dasch-swiss/dsp-ingest/issues/181)) ([43cd64d](https://github.com/dasch-swiss/dsp-ingest/commit/43cd64da82adc7c9ea6a6d1cdcd04e94d7cc7f65))
* Write asset overview report as csv instead of json (DEV-3300) ([#179](https://github.com/dasch-swiss/dsp-ingest/issues/179)) ([eaf7aaf](https://github.com/dasch-swiss/dsp-ingest/commit/eaf7aafe2bd9ea6d290fe6c1915cedf61495f838))


### Maintenance

* Add CODEOWNERS (DEV-3378) ([#183](https://github.com/dasch-swiss/dsp-ingest/issues/183)) ([cb9e383](https://github.com/dasch-swiss/dsp-ingest/commit/cb9e38364b6c0a39be71a3c8a592617d963160f0))
* **formatting:** Add trailing commas ([#180](https://github.com/dasch-swiss/dsp-ingest/issues/180)) ([3a7611b](https://github.com/dasch-swiss/dsp-ingest/commit/3a7611b3f3eedea810df37f9ca0348cbf63d3d00))
* Minor dependency updates ([#177](https://github.com/dasch-swiss/dsp-ingest/issues/177)) ([62fe0a9](https://github.com/dasch-swiss/dsp-ingest/commit/62fe0a99d77831a8e26885beba0ceed07077e568))
* Patch dependency updates ([#176](https://github.com/dasch-swiss/dsp-ingest/issues/176)) ([574d411](https://github.com/dasch-swiss/dsp-ingest/commit/574d41190e470ce29bd5ce4ee9b5b6201415fd42))
* Update knora-sipi to v30.9.0 ([#184](https://github.com/dasch-swiss/dsp-ingest/issues/184)) ([37f1708](https://github.com/dasch-swiss/dsp-ingest/commit/37f1708db5a39c36fe9d4002c1727aa5a18f2413))

## [0.6.3](https://github.com/dasch-swiss/dsp-ingest/compare/v0.6.2...v0.6.3) (2024-02-29)


### Bug Fixes

* Fix extracting moving image metadata ([#174](https://github.com/dasch-swiss/dsp-ingest/issues/174)) ([fd4765c](https://github.com/dasch-swiss/dsp-ingest/commit/fd4765ca051fcfc4b8d40e3f14ee035966c1a654))
* **sipi:** Fix creating a valid JPX from grey scale JPEG (DEV-3259) ([#172](https://github.com/dasch-swiss/dsp-ingest/issues/172)) ([a734a3c](https://github.com/dasch-swiss/dsp-ingest/commit/a734a3cb23c3943be2f4523d35a8770f11e7ae03))

## [0.6.2](https://github.com/dasch-swiss/dsp-ingest/compare/v0.6.1...v0.6.2) (2024-02-15)


### Maintenance

* Dependency updates ([#171](https://github.com/dasch-swiss/dsp-ingest/issues/171)) ([8902b1e](https://github.com/dasch-swiss/dsp-ingest/commit/8902b1e7d3ed847532e493c8815536d8fa211e32))
* Major dependency updates ([#170](https://github.com/dasch-swiss/dsp-ingest/issues/170)) ([3f7e717](https://github.com/dasch-swiss/dsp-ingest/commit/3f7e717b063983263cf3e8da5d7f08435191e232))
* Minor dependency updates ([#169](https://github.com/dasch-swiss/dsp-ingest/issues/169)) ([0fa24ff](https://github.com/dasch-swiss/dsp-ingest/commit/0fa24ff5913717c356bd1e53a84632f6d9cc7e93))
* Patch dependency updates ([#168](https://github.com/dasch-swiss/dsp-ingest/issues/168)) ([444aa35](https://github.com/dasch-swiss/dsp-ingest/commit/444aa358f08d078dc63e89d24712aa9a42f90169))
* Safer command interpolation (DEV-3184) ([#166](https://github.com/dasch-swiss/dsp-ingest/issues/166)) ([9190f82](https://github.com/dasch-swiss/dsp-ingest/commit/9190f823e8f6b4f548be006729bb45ecc793a50d))

## [0.6.1](https://github.com/dasch-swiss/dsp-ingest/compare/v0.6.0...v0.6.1) (2024-02-09)


### Maintenance

* Dependency updates ([#164](https://github.com/dasch-swiss/dsp-ingest/issues/164)) ([282a733](https://github.com/dasch-swiss/dsp-ingest/commit/282a733042ec236a7f2aa64a0f994ee6af5d1468))
* **logging:** Improve logging ([#160](https://github.com/dasch-swiss/dsp-ingest/issues/160)) ([e18aca9](https://github.com/dasch-swiss/dsp-ingest/commit/e18aca9138187f415be7dac5286da392edaf8a7b))
* Minor dependency updates ([#163](https://github.com/dasch-swiss/dsp-ingest/issues/163)) ([30750b3](https://github.com/dasch-swiss/dsp-ingest/commit/30750b39ac7767a6d65f266c166946a3d62601e7))
* Patch dependency updates ([#162](https://github.com/dasch-swiss/dsp-ingest/issues/162)) ([ef4eb10](https://github.com/dasch-swiss/dsp-ingest/commit/ef4eb10a09b4f6a0ee8afaec14ac556999698c40))
* Update Sipi dependency ([#165](https://github.com/dasch-swiss/dsp-ingest/issues/165)) ([dbee73d](https://github.com/dasch-swiss/dsp-ingest/commit/dbee73d3c63991b73667bc2094967c0986206245))

## [0.6.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.5.0...v0.6.0) (2024-01-16)


### Enhancements

* Add maintenance action to update all asset metadata incl. mimetypes (dev-3140) ([#155](https://github.com/dasch-swiss/dsp-ingest/issues/155)) ([45de659](https://github.com/dasch-swiss/dsp-ingest/commit/45de6590e92d413498190f61e91462ef852abee6))
* Add maintenance action which extracts dimensions for all Stillmages (DEV-3125) ([#146](https://github.com/dasch-swiss/dsp-ingest/issues/146)) ([f8b4556](https://github.com/dasch-swiss/dsp-ingest/commit/f8b45561bbe477dfef4ea1769fbc7f77aef6bbba))
* Add support for mime types (DEV-3139) ([#147](https://github.com/dasch-swiss/dsp-ingest/issues/147)) ([8c5838b](https://github.com/dasch-swiss/dsp-ingest/commit/8c5838bc27958723e2f8fb5f5bb357fff6d4945b))


### Bug Fixes

* Escape values in mapping CSV (DEV-3160) ([#154](https://github.com/dasch-swiss/dsp-ingest/issues/154)) ([4059991](https://github.com/dasch-swiss/dsp-ingest/commit/4059991bd820ebaf376a11b1be82f415f0b0c667))
* Fix typo for Excel file extensions, enable ingest of *.xlsx ([#144](https://github.com/dasch-swiss/dsp-ingest/issues/144)) ([ae5e80c](https://github.com/dasch-swiss/dsp-ingest/commit/ae5e80c67437a53c8088683886cb7766aa2228ca))
* Typo MIME type ([#148](https://github.com/dasch-swiss/dsp-ingest/issues/148)) ([06de145](https://github.com/dasch-swiss/dsp-ingest/commit/06de145901033c1c2241050c33439a42fe6a3e1e))
* Use a stable version for Sipi base image ([#149](https://github.com/dasch-swiss/dsp-ingest/issues/149)) ([a97197a](https://github.com/dasch-swiss/dsp-ingest/commit/a97197a638b6b44cf1e4c5dc86b1b48e31272109))


### Maintenance

* Add AssetFolder as AugmentedPath ([#157](https://github.com/dasch-swiss/dsp-ingest/issues/157)) ([77e2736](https://github.com/dasch-swiss/dsp-ingest/commit/77e2736c78193e9197ae7cea95f05ee622f02a7a))
* Introduce AugmentedPath and replace different implementations ([#156](https://github.com/dasch-swiss/dsp-ingest/issues/156)) ([d31b3e1](https://github.com/dasch-swiss/dsp-ingest/commit/d31b3e104ae9bb7e61eab27d26936d6338133a15))
* Major dependency updates ([#152](https://github.com/dasch-swiss/dsp-ingest/issues/152)) ([fab4c7b](https://github.com/dasch-swiss/dsp-ingest/commit/fab4c7bfa7b6150f7915bfa93d879260fd21ebcf))
* Patch dependency updates ([#151](https://github.com/dasch-swiss/dsp-ingest/issues/151)) ([8b825d7](https://github.com/dasch-swiss/dsp-ingest/commit/8b825d7ab8e0cfce12e9323ce143168edef0d98d))
* Patch dependency updates ([#159](https://github.com/dasch-swiss/dsp-ingest/issues/159)) ([56ceadd](https://github.com/dasch-swiss/dsp-ingest/commit/56ceadd3746d0333e6f35c6b48c4110b30ce3a81))
* Update license headers to 2024 ([#153](https://github.com/dasch-swiss/dsp-ingest/issues/153)) ([ca09571](https://github.com/dasch-swiss/dsp-ingest/commit/ca09571e4ec9082e4fdc602f613452c6f04127cd))
* Update to Eclipse Temurin Java 21 (DEV-2770) ([#150](https://github.com/dasch-swiss/dsp-ingest/issues/150)) ([f688409](https://github.com/dasch-swiss/dsp-ingest/commit/f68840987a9e0523de1a826c0a2846a9187d0949))


### Documentation

* Add documentation for bulk-ingest (DEV-3133) ([#158](https://github.com/dasch-swiss/dsp-ingest/issues/158)) ([e377c0e](https://github.com/dasch-swiss/dsp-ingest/commit/e377c0edf178c7176b1f087a85993a10ac08d4fb))

## [0.5.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.4.0...v0.5.0) (2023-12-15)


### Enhancements

* Add asset info endpoint and image meta data extraction ([#139](https://github.com/dasch-swiss/dsp-ingest/issues/139)) ([a6578da](https://github.com/dasch-swiss/dsp-ingest/commit/a6578dade1a9945b8564b9b0dc5ba10127a7ca76))
* Add get mapping.csv and finalize endpoints ([#137](https://github.com/dasch-swiss/dsp-ingest/issues/137)) ([aac2747](https://github.com/dasch-swiss/dsp-ingest/commit/aac27472e86b496f4da07a3d39358b4b74e9fc37))
* Add moving image support (metadata and keyframe extraction) ([#136](https://github.com/dasch-swiss/dsp-ingest/issues/136)) ([191ce7d](https://github.com/dasch-swiss/dsp-ingest/commit/191ce7ddbfdbfaa447a17423250b1a1a10e2c2d9))
* Add openapi documentation generation (DEV-3071) ([#143](https://github.com/dasch-swiss/dsp-ingest/issues/143)) ([7272b78](https://github.com/dasch-swiss/dsp-ingest/commit/7272b784be2fb8a1ba2498efe819dce7b6eeab61))


### Maintenance

* Minor dependency updates ([#142](https://github.com/dasch-swiss/dsp-ingest/issues/142)) ([e4bac59](https://github.com/dasch-swiss/dsp-ingest/commit/e4bac5983837e30c6a23fe6c87e29ffa81e394b2))
* Patch dependency updates ([#141](https://github.com/dasch-swiss/dsp-ingest/issues/141)) ([52591c2](https://github.com/dasch-swiss/dsp-ingest/commit/52591c2e1f58e8925bb5a66649c75073c13da5df))

## [0.4.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.3.3...v0.4.0) (2023-12-07)


### Enhancements

* Add bulk-ingest support for other file types DEV-2975  ([#134](https://github.com/dasch-swiss/dsp-ingest/issues/134)) ([a3a9463](https://github.com/dasch-swiss/dsp-ingest/commit/a3a946328767ead4b87e0e7e035c97ebbc3206d7))


### Bug Fixes

* Ingest fails when mapping file folder does not exist (DEV-3055) ([#130](https://github.com/dasch-swiss/dsp-ingest/issues/130)) ([e496a1b](https://github.com/dasch-swiss/dsp-ingest/commit/e496a1b8501c664f321abacf6633538f67965e46))


### Maintenance

* Add justfile for task automation ([#131](https://github.com/dasch-swiss/dsp-ingest/issues/131)) ([c8828fd](https://github.com/dasch-swiss/dsp-ingest/commit/c8828fdf45a80c32ef39729c9fa404bdbfd22d18))
* Create stubs for ingesting other file types than images ([#124](https://github.com/dasch-swiss/dsp-ingest/issues/124)) ([9f49897](https://github.com/dasch-swiss/dsp-ingest/commit/9f498975e4a4e000a48e7689994d86aeff59603e))
* Extract IngestService ([#135](https://github.com/dasch-swiss/dsp-ingest/issues/135)) ([8be5370](https://github.com/dasch-swiss/dsp-ingest/commit/8be537056ec1d3f514a279b0e80fe2d055de9fee))
* Merge ComplexAsset with Asset ([#132](https://github.com/dasch-swiss/dsp-ingest/issues/132)) ([a4659be](https://github.com/dasch-swiss/dsp-ingest/commit/a4659becf36e8495aea46f1234f1ee1c2c4a732d))
* Minor dependency updates ([#126](https://github.com/dasch-swiss/dsp-ingest/issues/126)) ([98d94dc](https://github.com/dasch-swiss/dsp-ingest/commit/98d94dc742240d444a3b64d82f60ed46d9975845))
* Patch dependency updates ([#125](https://github.com/dasch-swiss/dsp-ingest/issues/125)) ([f4e6834](https://github.com/dasch-swiss/dsp-ingest/commit/f4e6834986eebee87a3c155f019bf20b54865f1d))
* RC Dependency updates ([#127](https://github.com/dasch-swiss/dsp-ingest/issues/127)) ([66208c2](https://github.com/dasch-swiss/dsp-ingest/commit/66208c21b3a0f2823a24e7727793ea806246e507))
* Remove AssetRef inheritance with Asset ([#129](https://github.com/dasch-swiss/dsp-ingest/issues/129)) ([c0bf10f](https://github.com/dasch-swiss/dsp-ingest/commit/c0bf10f9e59478c6a0001181554a9f749ebf10d9))

## [0.3.3](https://github.com/dasch-swiss/dsp-ingest/compare/v0.3.2...v0.3.3) (2023-11-20)


### Maintenance

* Dependency updates ([#123](https://github.com/dasch-swiss/dsp-ingest/issues/123)) ([62f5229](https://github.com/dasch-swiss/dsp-ingest/commit/62f5229e82051ac6252d6c8a8438738e3fcec3d1))
* Patch dependency updates ([#122](https://github.com/dasch-swiss/dsp-ingest/issues/122)) ([a72f61c](https://github.com/dasch-swiss/dsp-ingest/commit/a72f61cec20916b7fa278db2452ab492be653c15))
* **scala-steward:** Improve dependency grouping  ([#120](https://github.com/dasch-swiss/dsp-ingest/issues/120)) ([272c21c](https://github.com/dasch-swiss/dsp-ingest/commit/272c21c19b2894dd795be1d39196c60ee9e70908))

## [0.3.2](https://github.com/dasch-swiss/dsp-ingest/compare/v0.3.1...v0.3.2) (2023-11-03)


### Maintenance

* Dependency minor/major updates ([#118](https://github.com/dasch-swiss/dsp-ingest/issues/118)) ([c53faa0](https://github.com/dasch-swiss/dsp-ingest/commit/c53faa012c17a11496c36652c9f8f45afb8dc7ca))
* Dependency patch updates ([#117](https://github.com/dasch-swiss/dsp-ingest/issues/117)) ([154c7b3](https://github.com/dasch-swiss/dsp-ingest/commit/154c7b3a75adf4db8bced4f6cac1ae14d4d9c1fd))

## [0.3.1](https://github.com/dasch-swiss/dsp-ingest/compare/v0.3.0...v0.3.1) (2023-10-23)


### Maintenance

* Force PR titles to start with an upper case letter ([#115](https://github.com/dasch-swiss/dsp-ingest/issues/115)) ([1bfeb16](https://github.com/dasch-swiss/dsp-ingest/commit/1bfeb16709374b896f42171cc2b5246415bc0c5a))
* Minor/major updates ([#113](https://github.com/dasch-swiss/dsp-ingest/issues/113)) ([05f996a](https://github.com/dasch-swiss/dsp-ingest/commit/05f996abb3d0a07c1614b7ce812a1ffd3c093a59))
* Patch updates ([#112](https://github.com/dasch-swiss/dsp-ingest/issues/112)) ([6a26ec2](https://github.com/dasch-swiss/dsp-ingest/commit/6a26ec23d4760954118d7d46eca1e850c455a10a))
* Update release PR title template ([#116](https://github.com/dasch-swiss/dsp-ingest/issues/116)) ([18720ba](https://github.com/dasch-swiss/dsp-ingest/commit/18720bae9b520987a22c293624296ff012f05ea3))

## [0.3.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.2.2...v0.3.0) (2023-10-13)


### Enhancements

* Add report listing all assets per project which have a .bak file DEV-2795 ([#110](https://github.com/dasch-swiss/dsp-ingest/issues/110)) ([e5b9346](https://github.com/dasch-swiss/dsp-ingest/commit/e5b9346fdee5e8b42d9e8849888258de46028848))


### Maintenance

* Add Java 21 to build and test DEV-2769 ([#99](https://github.com/dasch-swiss/dsp-ingest/issues/99)) ([a1b2397](https://github.com/dasch-swiss/dsp-ingest/commit/a1b2397f42eb4fb8c313c7be7d084f42f04d8491))
* Add Scala Steward configuration ([#109](https://github.com/dasch-swiss/dsp-ingest/issues/109)) ([dacb81f](https://github.com/dasch-swiss/dsp-ingest/commit/dacb81fbbd52e045666d752a050d858927fe68f8))
* Replace zio-http endpoints with tapir endpoints DEV-2746 ([#95](https://github.com/dasch-swiss/dsp-ingest/issues/95)) ([9f7c790](https://github.com/dasch-swiss/dsp-ingest/commit/9f7c790e77a0daba990a707a956243a823f346ce))
* Update commons-io to 2.14.0 ([#104](https://github.com/dasch-swiss/dsp-ingest/issues/104)) ([58a953e](https://github.com/dasch-swiss/dsp-ingest/commit/58a953e1e4f49395878289cbadea2c3bf508f627))
* Update formatter, use the same .scalafmt as in dsp-api ([#111](https://github.com/dasch-swiss/dsp-ingest/issues/111)) ([4428f35](https://github.com/dasch-swiss/dsp-ingest/commit/4428f35855f12c9026dc95170f163b7a8f5e694f))
* Update zio-metrics-connectors, ... to 2.2.0 ([#106](https://github.com/dasch-swiss/dsp-ingest/issues/106)) ([b1ae6a7](https://github.com/dasch-swiss/dsp-ingest/commit/b1ae6a75a2831fe13dc7f40064e45398fb8c34e0))
* Update zio-prelude to 1.0.0-RC21 ([#107](https://github.com/dasch-swiss/dsp-ingest/issues/107)) ([9dcfc52](https://github.com/dasch-swiss/dsp-ingest/commit/9dcfc52a09fe11a08ecacd53d69f250b1b28dd03))
* Update zio, zio-streams, zio-test, ... to 2.0.18 ([#105](https://github.com/dasch-swiss/dsp-ingest/issues/105)) ([ed4d6e2](https://github.com/dasch-swiss/dsp-ingest/commit/ed4d6e2495da4b4be1fe1653e4e7a2f218cdd149))

## [0.2.2](https://github.com/dasch-swiss/dsp-ingest/compare/v0.2.1...v0.2.2) (2023-09-28)


### Bug Fixes

* Include folder name in mapping.csv in order to prevent collisions ([#91](https://github.com/dasch-swiss/dsp-ingest/issues/91)) ([ec428ae](https://github.com/dasch-swiss/dsp-ingest/commit/ec428ae4f95dfe2568f471cee991bc22f3dff82d))


### Maintenance

* add check-pr-title GH action ([#92](https://github.com/dasch-swiss/dsp-ingest/issues/92)) ([935d6c3](https://github.com/dasch-swiss/dsp-ingest/commit/935d6c3d0fa56dc582957fce2087632c227ee2a2))
* Align ApiProblem class naming with HTTP Status codes ([#96](https://github.com/dasch-swiss/dsp-ingest/issues/96)) ([1349544](https://github.com/dasch-swiss/dsp-ingest/commit/13495447c67a1a06b7bb95622c790a3f9d07fbc7))
* Introduce types for ImageAsset, OriginalFile and DerivativeFile ([#93](https://github.com/dasch-swiss/dsp-ingest/issues/93)) ([49aa0ed](https://github.com/dasch-swiss/dsp-ingest/commit/49aa0edc9925359b4e310d21759e60ada6c74cd2))
* Update and rename check-pr-title workflow ([#98](https://github.com/dasch-swiss/dsp-ingest/issues/98)) ([6a8642d](https://github.com/dasch-swiss/dsp-ingest/commit/6a8642d35c48b6f22c2e2003003b3abb702fc67c))
* Update sbt to 1.9.6 ([#100](https://github.com/dasch-swiss/dsp-ingest/issues/100)) ([e004ad1](https://github.com/dasch-swiss/dsp-ingest/commit/e004ad1ef9b72d165b859c84d02b1e726a8db581))

## [0.2.1](https://github.com/dasch-swiss/dsp-ingest/compare/v0.2.0...v0.2.1) (2023-09-15)


### Maintenance

* Update Scala version and dependencies ([#89](https://github.com/dasch-swiss/dsp-ingest/issues/89)) ([02abc99](https://github.com/dasch-swiss/dsp-ingest/commit/02abc99812a46ce1d49e33781ac61657b4f3a9c4))

## [0.2.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.1.5...v0.2.0) (2023-09-14)


### Enhancements

* Add bulk ingest for images ([#80](https://github.com/dasch-swiss/dsp-ingest/issues/80)) ([0d5d991](https://github.com/dasch-swiss/dsp-ingest/commit/0d5d9915bd4d39dc91dbf44ab47c893e8e2ab01c))


### Bug Fixes

* Compare file extensions in lower case ([9b53cc6](https://github.com/dasch-swiss/dsp-ingest/commit/9b53cc612e7dd6ffa1e9cfb4d970ad49eaa13b1a))
* Finetuning bulk ingest ([#83](https://github.com/dasch-swiss/dsp-ingest/issues/83)) ([13f5913](https://github.com/dasch-swiss/dsp-ingest/commit/13f59139bdab245ac8fa7fa143f02ad8add44ec1))
* Skip images which Sipi fails to transcode while creating originals ([#81](https://github.com/dasch-swiss/dsp-ingest/issues/81)) ([af53ebf](https://github.com/dasch-swiss/dsp-ingest/commit/af53ebfefe1cd2175697cf7fc7feee17b47851de))

## [0.1.5](https://github.com/dasch-swiss/dsp-ingest/compare/v0.1.4...v0.1.5) (2023-09-06)


### Bug Fixes

* Allow underscore in AssetIds ([#78](https://github.com/dasch-swiss/dsp-ingest/issues/78)) ([f12ad5c](https://github.com/dasch-swiss/dsp-ingest/commit/f12ad5cc0db1a384aaf12bcf58c335716377111f))

## [0.1.4](https://github.com/dasch-swiss/dsp-ingest/compare/v0.1.3...v0.1.4) (2023-08-29)


### Maintenance

* Add name to publish-docker-image job ([#71](https://github.com/dasch-swiss/dsp-ingest/issues/71)) ([9bad079](https://github.com/dasch-swiss/dsp-ingest/commit/9bad079efd6c940110990ec7d75d462add932c79))

## [0.1.3](https://github.com/dasch-swiss/dsp-ingest/compare/v0.1.2...v0.1.3) (2023-08-28)


### Maintenance

* Trigger on release not push of a tag ([61f23c4](https://github.com/dasch-swiss/dsp-ingest/commit/61f23c42795e79862fdf00e9460ea0bdd1d9d1c2))

## [0.1.2](https://github.com/dasch-swiss/dsp-ingest/compare/v0.1.1...v0.1.2) (2023-08-28)


### Maintenance

* Send google chat notification for a release ([#68](https://github.com/dasch-swiss/dsp-ingest/issues/68)) ([0c85881](https://github.com/dasch-swiss/dsp-ingest/commit/0c8588164547e3c591c675d2421f3bc45750d421))

## [0.1.1](https://github.com/dasch-swiss/dsp-ingest/compare/v0.1.0...v0.1.1) (2023-08-25)


### Maintenance

* Add Contributing section to README ([#65](https://github.com/dasch-swiss/dsp-ingest/issues/65)) ([f6539f3](https://github.com/dasch-swiss/dsp-ingest/commit/f6539f3323ca40bf4d1b32fb0585fdcba91cd791))
* Publish docker artefact only once per release ([#66](https://github.com/dasch-swiss/dsp-ingest/issues/66)) ([4ddd67c](https://github.com/dasch-swiss/dsp-ingest/commit/4ddd67c21b792387f213c3e96d4d1eef1613369a))

## [0.1.0](https://github.com/dasch-swiss/dsp-ingest/compare/v0.0.5...v0.1.0) (2023-08-24)


### Enhancements

* Add authentication DEV-2296 ([#15](https://github.com/dasch-swiss/dsp-ingest/issues/15)) ([436b403](https://github.com/dasch-swiss/dsp-ingest/commit/436b403ab52b4c70dbe3f7258df302e0e767cedf))
* Add import endpoint DEV-2107 ([#11](https://github.com/dasch-swiss/dsp-ingest/issues/11)) ([abf5496](https://github.com/dasch-swiss/dsp-ingest/commit/abf54963dbb243c237cd8bcbf7a20ce5c8dda42e))
* Add info endpoint ([#16](https://github.com/dasch-swiss/dsp-ingest/issues/16)) ([96de600](https://github.com/dasch-swiss/dsp-ingest/commit/96de60072209bf0c0f72e0addc1d1a35e0aa6f2e))
* Add topleft correction maintenance DEV-1650 ([#53](https://github.com/dasch-swiss/dsp-ingest/issues/53)) ([f37d543](https://github.com/dasch-swiss/dsp-ingest/commit/f37d543a17e42207f4eeac4882d91256d730e926))
* In create-originals maintenance action update AssetInfo, and create originalFilename using a provided mapping ([#52](https://github.com/dasch-swiss/dsp-ingest/issues/52)) ([df2507e](https://github.com/dasch-swiss/dsp-ingest/commit/df2507ead2c8c4f6a7f68ff389af4dfa83e6f56c))


### Bug Fixes

* Change api projects root path, align with dsp-api ([#36](https://github.com/dasch-swiss/dsp-ingest/issues/36)) ([2b1ed2a](https://github.com/dasch-swiss/dsp-ingest/commit/2b1ed2ab3924c1b042aafc0050ad5d3af771bfd4))
* Enable request streaming ([#41](https://github.com/dasch-swiss/dsp-ingest/issues/41)) ([32813dd](https://github.com/dasch-swiss/dsp-ingest/commit/32813dd0bbe97855a82bd5e63fb5358e77680c0c))


### Maintenance

* Add FilesystemCheck on startup ([#42](https://github.com/dasch-swiss/dsp-ingest/issues/42)) ([9023c7e](https://github.com/dasch-swiss/dsp-ingest/commit/9023c7e3b5a58fee4b45f53ba677c6758ac35d04))
* Add release-please github action ([#63](https://github.com/dasch-swiss/dsp-ingest/issues/63)) ([413f8e9](https://github.com/dasch-swiss/dsp-ingest/commit/413f8e9dd542860bb5647fcb2e97557c71e20329))
* add Scala Steward ([#14](https://github.com/dasch-swiss/dsp-ingest/issues/14)) ([4413324](https://github.com/dasch-swiss/dsp-ingest/commit/44133249a205a2b2dca501ebe7d468ecf01b6081))
* add SIPI to the Docker image ([#8](https://github.com/dasch-swiss/dsp-ingest/issues/8)) ([30f8ef0](https://github.com/dasch-swiss/dsp-ingest/commit/30f8ef072bcf01a020442b7cece2a1b40ea1ab76))
* add workflow that pushes to Docker Hub on merge with main branch ([#13](https://github.com/dasch-swiss/dsp-ingest/issues/13)) ([cb09068](https://github.com/dasch-swiss/dsp-ingest/commit/cb09068d059b5b0a36c27723bbf23eabdc763732))
* Align logging with dsp-api ([#17](https://github.com/dasch-swiss/dsp-ingest/issues/17)) ([2263f99](https://github.com/dasch-swiss/dsp-ingest/commit/2263f99ffe246ea2472957f1747ce1219481f1be))
* Minor improvements ([#18](https://github.com/dasch-swiss/dsp-ingest/issues/18)) ([29f3230](https://github.com/dasch-swiss/dsp-ingest/commit/29f323020fc5c826ae10adb8dddf0b5501c72404))
* publish-docker also on pushing tags ([dddca47](https://github.com/dasch-swiss/dsp-ingest/commit/dddca478069df726d9698f42084b66a5e3e1f31b))
* Recreate originals DEV-2451 ([#51](https://github.com/dasch-swiss/dsp-ingest/issues/51)) ([a096c94](https://github.com/dasch-swiss/dsp-ingest/commit/a096c9428a38d890621960e28df0aea8e7b05c67))
* remove IntelliJ IDEA files ([#5](https://github.com/dasch-swiss/dsp-ingest/issues/5)) ([2d728c4](https://github.com/dasch-swiss/dsp-ingest/commit/2d728c4358b31bba983afafab5c209305b98ea01))
* Trigger ci workflow when pushing tags ([#62](https://github.com/dasch-swiss/dsp-ingest/issues/62)) ([664815a](https://github.com/dasch-swiss/dsp-ingest/commit/664815ae694d1b0a558244b5ef2697cd1babf59d))


### Documentation

* setup mkdocs and add documentation using OpenAPI ([#26](https://github.com/dasch-swiss/dsp-ingest/issues/26)) ([46714a0](https://github.com/dasch-swiss/dsp-ingest/commit/46714a06f955c70ec8103c5540336d1e17e65390))
