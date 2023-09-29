# Changelog

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
