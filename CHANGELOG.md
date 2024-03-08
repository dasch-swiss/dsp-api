# Changelog

## [30.9.0](https://github.com/dasch-swiss/dsp-api/compare/v30.8.2...v30.9.0) (2024-03-07)


### Maintenance

* Add clear cache to KnoraProjectRepoLive ([#3091](https://github.com/dasch-swiss/dsp-api/issues/3091)) ([eb98c53](https://github.com/dasch-swiss/dsp-api/commit/eb98c53af5b499171920154405c773428461ab67))
* Add CODEOWNERS (DEV-3378) ([#3102](https://github.com/dasch-swiss/dsp-api/issues/3102)) ([914fe86](https://github.com/dasch-swiss/dsp-api/commit/914fe8661b7ef4876b8fbc8ded3e8286ee787e25))
* Add DSP-INGEST to docker network ([#3086](https://github.com/dasch-swiss/dsp-api/issues/3086)) ([b248cd0](https://github.com/dasch-swiss/dsp-api/commit/b248cd095ffc9da8a1d78f24c318dc8c6fc2f6f5))
* Add save to KnoraProjectRepo and use for setting the RestrictedView ([#3082](https://github.com/dasch-swiss/dsp-api/issues/3082)) ([fcd483f](https://github.com/dasch-swiss/dsp-api/commit/fcd483fb74169ab023324b4c26001337a6832989))
* Bump Sipi to 3.9.0 ([#3097](https://github.com/dasch-swiss/dsp-api/issues/3097)) ([4b6e638](https://github.com/dasch-swiss/dsp-api/commit/4b6e6382281a4e4f6b106624684bb388f9fd4948))
* Cleanup CacheService, and split KnoraUserService and UserService ([#3074](https://github.com/dasch-swiss/dsp-api/issues/3074)) ([3a21838](https://github.com/dasch-swiss/dsp-api/commit/3a21838423efebc0d315eb46b83ecc5bbddf0044))
* **formatting:** Add trailing commas ([#3084](https://github.com/dasch-swiss/dsp-api/issues/3084)) ([b0b5e25](https://github.com/dasch-swiss/dsp-api/commit/b0b5e251e2fdaf320cda17a98af7d7ed8f2ec21b))
* KnoraUserGroup and KnoraUserGroupRepo (DEV-3288) ([#3059](https://github.com/dasch-swiss/dsp-api/issues/3059)) ([8a79e93](https://github.com/dasch-swiss/dsp-api/commit/8a79e93766ba39b35b96c5457e5f9728f31fcf87))
* Migrate DELETE /admin/groups/&lt;groupIri&gt; to Tapir (DEV-1588) ([#3081](https://github.com/dasch-swiss/dsp-api/issues/3081)) ([2715aa1](https://github.com/dasch-swiss/dsp-api/commit/2715aa1e346af4faf045741011e6b7933d4993e5))
* Migrate POST /admin/group to Tapir (DEV-1588) ([#3057](https://github.com/dasch-swiss/dsp-api/issues/3057)) ([29b1ce6](https://github.com/dasch-swiss/dsp-api/commit/29b1ce6a80c2a30bea970968a825b007d60d4987))
* Migrate PUT /admin/group/&lt;groupIri&gt; to Tapir (DEV-1588) ([#3071](https://github.com/dasch-swiss/dsp-api/issues/3071)) ([8df7033](https://github.com/dasch-swiss/dsp-api/commit/8df70337c0b579f5751fb7a7d5e8c9d84e6e175f))
* Migrate PUT /admin/groups/&lt;groupIri&gt;/status to Tapir (DEV-1588) ([#3075](https://github.com/dasch-swiss/dsp-api/issues/3075)) ([2ca95ed](https://github.com/dasch-swiss/dsp-api/commit/2ca95eda375d4ac87fbcf73945d3de85b6fd5835))
* Minor dependency updates ([#3078](https://github.com/dasch-swiss/dsp-api/issues/3078)) ([f60d937](https://github.com/dasch-swiss/dsp-api/commit/f60d93790f82ec338e5ba2697614a546713d6356))
* Move caching from UserResponder to UserService ([#3064](https://github.com/dasch-swiss/dsp-api/issues/3064)) ([0484717](https://github.com/dasch-swiss/dsp-api/commit/0484717a4fbbfdec786c7ebe56d0830c7f38f2db))
* Move code from UsersResponder to UserService and UserRestService ([#3067](https://github.com/dasch-swiss/dsp-api/issues/3067)) ([5345350](https://github.com/dasch-swiss/dsp-api/commit/5345350a0b79f48694edf90bd808e15899ec6806))
* Move code from UsersResponder to UserService and UserRestService ([#3069](https://github.com/dasch-swiss/dsp-api/issues/3069)) ([e78a106](https://github.com/dasch-swiss/dsp-api/commit/e78a10618e2489a52e61d3a955637e5e6fd5b7ce))
* Move remaining methods from UsersResponder to UserService and UserRestService ([#3072](https://github.com/dasch-swiss/dsp-api/issues/3072)) ([320a4a8](https://github.com/dasch-swiss/dsp-api/commit/320a4a803f2675b1631d3f3d04428d9288667557))
* Patch dependency updates ([#3077](https://github.com/dasch-swiss/dsp-api/issues/3077)) ([3decf23](https://github.com/dasch-swiss/dsp-api/commit/3decf23ff369ad6c5416578343fb0dbc31a17a95))
* Prevent illegal updates with `KnoraUserService` ([#3098](https://github.com/dasch-swiss/dsp-api/issues/3098)) ([4111312](https://github.com/dasch-swiss/dsp-api/commit/4111312c8a57d6c3344cdac6772c8f4b7e4e8047))
* Refactor Group value objects ([#3058](https://github.com/dasch-swiss/dsp-api/issues/3058)) ([f7ab488](https://github.com/dasch-swiss/dsp-api/commit/f7ab488663c2de54d630e9d1e23e31527c8d3902))
* Remove Codecov annotations ([#3070](https://github.com/dasch-swiss/dsp-api/issues/3070)) ([e9e3d5c](https://github.com/dasch-swiss/dsp-api/commit/e9e3d5cebeb243bb7ba792755f341fa967fd54bb))
* Remove invalid test data ([#3088](https://github.com/dasch-swiss/dsp-api/issues/3088)) ([22a7333](https://github.com/dasch-swiss/dsp-api/commit/22a733356ec06cd6a978be7ae58a002b6199afc4))
* Remove ontology from KnoraProject entity ([#3063](https://github.com/dasch-swiss/dsp-api/issues/3063)) ([376f536](https://github.com/dasch-swiss/dsp-api/commit/376f536d0089c1f4e6a960d9fd219ea724f323e4))
* Remove unused `RejectingRoute` (DEV-3289) ([#3079](https://github.com/dasch-swiss/dsp-api/issues/3079)) ([fc8e7d0](https://github.com/dasch-swiss/dsp-api/commit/fc8e7d0e97921b28d0a65d0877ec1f0a602e7cac))
* Remove unused code from standoff responder (DEV-3264) ([#3085](https://github.com/dasch-swiss/dsp-api/issues/3085)) ([56815a3](https://github.com/dasch-swiss/dsp-api/commit/56815a38febac3d3acd990b3b10b1045463644b3))
* Rename GroupADM and move to domain model package (DEV-3292) ([#3094](https://github.com/dasch-swiss/dsp-api/issues/3094)) ([2202bd9](https://github.com/dasch-swiss/dsp-api/commit/2202bd95884b0c2350173e4d00fb64c9a9f25285))
* Rename test data folder to align with PermissionsResponderADMSpec ([#3092](https://github.com/dasch-swiss/dsp-api/issues/3092)) ([b56580d](https://github.com/dasch-swiss/dsp-api/commit/b56580de70b785e5bdeaa04db2714879ac0ee7cd))
* Replace default watermark for Sipi with new version ([#3066](https://github.com/dasch-swiss/dsp-api/issues/3066)) ([19caebd](https://github.com/dasch-swiss/dsp-api/commit/19caebd108d5adb57963af70476a58251f68708d))
* Replace Spray JSON with ZIO-JSON in some projects endpoints (DEV-3375) ([#3095](https://github.com/dasch-swiss/dsp-api/issues/3095)) ([70ea9ba](https://github.com/dasch-swiss/dsp-api/commit/70ea9ba5ab41d7a1c275ce8dac36b3b2b143be7c))
* Update dsp-app to v11.6.4 ([#3061](https://github.com/dasch-swiss/dsp-api/issues/3061)) ([97db659](https://github.com/dasch-swiss/dsp-api/commit/97db659486aec842de9410a51969273e62742c74))
* Use RDF model in all methods of project repo ([#3032](https://github.com/dasch-swiss/dsp-api/issues/3032)) ([689bbbf](https://github.com/dasch-swiss/dsp-api/commit/689bbbf313cb4995c1612899186e18931d091445))


### Documentation

* Fix dead links in docs ([#3076](https://github.com/dasch-swiss/dsp-api/issues/3076)) ([068ec84](https://github.com/dasch-swiss/dsp-api/commit/068ec841633ba0df1c32be3a4fbdea3f4700980a))
* Update documentation on restricted view settings ([#3101](https://github.com/dasch-swiss/dsp-api/issues/3101)) ([12db892](https://github.com/dasch-swiss/dsp-api/commit/12db892a5b4b04b42c2de6cce14e141527a46a7a))


### Enhancements

* Allow project admins to create users which are not a system admin (DEV-3266) ([#3099](https://github.com/dasch-swiss/dsp-api/issues/3099)) ([79e1963](https://github.com/dasch-swiss/dsp-api/commit/79e1963c442dc86004bc12ace4d5c6cdc095e37a))
* Make GET /admin/users faster by caching projects (DEV-3311) ([#3062](https://github.com/dasch-swiss/dsp-api/issues/3062)) ([793f118](https://github.com/dasch-swiss/dsp-api/commit/793f11833c9cf9884d7c0681bf3c21ab7c2dfb08))
* The restricted view must be either restricted with a watermark or by a particular size (DEV-3356) ([#3080](https://github.com/dasch-swiss/dsp-api/issues/3080)) ([75f5363](https://github.com/dasch-swiss/dsp-api/commit/75f53637ae71f3dba123a6fdecbe2fceaaed7cee))


### Bug Fixes

* **docs:** DSP-API docs root route gives a 404 error (DEV-3345) ([#3073](https://github.com/dasch-swiss/dsp-api/issues/3073)) ([0370e13](https://github.com/dasch-swiss/dsp-api/commit/0370e13d777e0f2b69c7560b6efbd7d26057537d))

## [30.8.2](https://github.com/dasch-swiss/dsp-api/compare/v30.8.1...v30.8.2) (2024-02-22)


### Maintenance

* Add -Xfatal-warnings ([#3042](https://github.com/dasch-swiss/dsp-api/issues/3042)) ([8e51a94](https://github.com/dasch-swiss/dsp-api/commit/8e51a94404d0ee34b9182d564e338e79dc2a6545))
* Add option to make warnings non-fatal locally. ([#3044](https://github.com/dasch-swiss/dsp-api/issues/3044)) ([0d2367e](https://github.com/dasch-swiss/dsp-api/commit/0d2367e67a3d7c94e43313d9779ffaa7b311d1d6))
* Bump Sipi to 3.8.12 ([#3051](https://github.com/dasch-swiss/dsp-api/issues/3051)) ([cbddd97](https://github.com/dasch-swiss/dsp-api/commit/cbddd9778be9fb07db900ed3680d204626a7cd4b))
* Dependency updates ([#3049](https://github.com/dasch-swiss/dsp-api/issues/3049)) ([bf60688](https://github.com/dasch-swiss/dsp-api/commit/bf6068865db7b3ca7e075c2e7a2ecd88e94d8c06))
* Enable Default Union Graph in TriplestoreServiceInMemory (DEV-3295) ([#3052](https://github.com/dasch-swiss/dsp-api/issues/3052)) ([8b59d54](https://github.com/dasch-swiss/dsp-api/commit/8b59d543383581bf4ddb18d2fb70a6877a87b378))
* Introduce repository and service for user and write queries with rdf4j's `SparqlBuilder` (DEV-3273) ([#3038](https://github.com/dasch-swiss/dsp-api/issues/3038)) ([f3df298](https://github.com/dasch-swiss/dsp-api/commit/f3df298e9c9a4b2574ed351c5871644203e40f36))
* Migrate user group endpoints to Tapir and remove UserRouteADM ([#3046](https://github.com/dasch-swiss/dsp-api/issues/3046)) ([52c798d](https://github.com/dasch-swiss/dsp-api/commit/52c798d4a2760e079d0a4380baae9d6e27fa2689))
* Migrate users endpoints to tapir (BasicInformation, Status, Password)  ([#3043](https://github.com/dasch-swiss/dsp-api/issues/3043)) ([6e16782](https://github.com/dasch-swiss/dsp-api/commit/6e16782f26385455a764fba6d00fdc4b457f4a18))
* Minor dependency updates ([#3048](https://github.com/dasch-swiss/dsp-api/issues/3048)) ([aa9b718](https://github.com/dasch-swiss/dsp-api/commit/aa9b718085b36ef27f7c3cff91c98cfa76615fc3))
* Patch dependency updates ([#3047](https://github.com/dasch-swiss/dsp-api/issues/3047)) ([5949e85](https://github.com/dasch-swiss/dsp-api/commit/5949e85aefa4448d0a9450368245a86709065bc8))
* Remove `knora-ontologies` symlink (DEV-3236) ([#3035](https://github.com/dasch-swiss/dsp-api/issues/3035)) ([df28afc](https://github.com/dasch-swiss/dsp-api/commit/df28afc23f90ec067ef3e9e20ff1603e8bb4d501))
* Remove token property from user which is always None ([#3041](https://github.com/dasch-swiss/dsp-api/issues/3041)) ([1b7f88b](https://github.com/dasch-swiss/dsp-api/commit/1b7f88b58eb8263481c819d7f81fd25d59db5ee0))
* Remove TriplestoreService dependency from UsersResponder ([#3054](https://github.com/dasch-swiss/dsp-api/issues/3054)) ([61f04e0](https://github.com/dasch-swiss/dsp-api/commit/61f04e00ca73c80042fc3e307f9f7fb17803e5fd))
* Replace watermark (DEV-3297) ([#3056](https://github.com/dasch-swiss/dsp-api/issues/3056)) ([60c804f](https://github.com/dasch-swiss/dsp-api/commit/60c804f5afcde0c24056f585f0d2499f51e0559f))
* Update APP to v11.5.1 ([#3039](https://github.com/dasch-swiss/dsp-api/issues/3039)) ([8290921](https://github.com/dasch-swiss/dsp-api/commit/82909218503e1371a807e34441277139bb6ed425))
* Use UserService to update a user ([#3053](https://github.com/dasch-swiss/dsp-api/issues/3053)) ([25ef280](https://github.com/dasch-swiss/dsp-api/commit/25ef2809653fb5150034f834d764cdb472ce6db6))


### Bug Fixes

* Allow hyphens in usernames (DEV-3306) ([#3055](https://github.com/dasch-swiss/dsp-api/issues/3055)) ([9398d21](https://github.com/dasch-swiss/dsp-api/commit/9398d21bfcb144df369ffee170f54eb55e7a94ee))
* OWL property type of knora-api:valueAsString ([#3036](https://github.com/dasch-swiss/dsp-api/issues/3036)) ([686c8fb](https://github.com/dasch-swiss/dsp-api/commit/686c8fb8862124381c308e2d49eec340e65b39cf))
* Remove UUID version check from IRI value objects (DEV-3310) ([#3033](https://github.com/dasch-swiss/dsp-api/issues/3033)) ([6de0374](https://github.com/dasch-swiss/dsp-api/commit/6de0374ab973661a17213a6cde656c88645a577e))

## [30.8.1](https://github.com/dasch-swiss/dsp-api/compare/v30.8.0...v30.8.1) (2024-02-08)


### Maintenance

* Change RDF handling for querying Projects from DB (DEV-3175) ([#2989](https://github.com/dasch-swiss/dsp-api/issues/2989)) ([884e3a8](https://github.com/dasch-swiss/dsp-api/commit/884e3a894adde26c7b83531694049763c90e8c07))
* **CI:** Run integration tests on actuated again ([#3027](https://github.com/dasch-swiss/dsp-api/issues/3027)) ([ca8319c](https://github.com/dasch-swiss/dsp-api/commit/ca8319c695f20ff55cc0bdd289085eae42a442dc))
* Migrate `GET /admin/users/&lt;iri|email|name&gt;` to Tapir ([#3020](https://github.com/dasch-swiss/dsp-api/issues/3020)) ([4b25387](https://github.com/dasch-swiss/dsp-api/commit/4b253875189c4f6b4b3247b007e0631614238cab))
* Migrate `GET /admin/users/iri/&lt;userIri&gt;/*memberships` and `POST /admin/users` to tapir ([#3021](https://github.com/dasch-swiss/dsp-api/issues/3021)) ([cafbc16](https://github.com/dasch-swiss/dsp-api/commit/cafbc1623829e967ff81732396af0ee7a2a1a3df))
* Remove chill, scallop dependency (DEV-3263) (DEV-3262) ([#3029](https://github.com/dasch-swiss/dsp-api/issues/3029)) ([a1e5db1](https://github.com/dasch-swiss/dsp-api/commit/a1e5db182db8a38eadb690cf1f2cd6a4f1fd7dc7))
* Remove jodd dependency (DEV-3069) ([#3024](https://github.com/dasch-swiss/dsp-api/issues/3024)) ([35ff4ed](https://github.com/dasch-swiss/dsp-api/commit/35ff4ed3148439be719a0c7bc2812e0a95e78a20))
* Remove jodd dependency leftovers ([#3028](https://github.com/dasch-swiss/dsp-api/issues/3028)) ([21a4c83](https://github.com/dasch-swiss/dsp-api/commit/21a4c83373ca64a3f6aed27d6352462d7c5c6ed5))
* Remove kamon (DEV-3261) ([#3030](https://github.com/dasch-swiss/dsp-api/issues/3030)) ([f27f118](https://github.com/dasch-swiss/dsp-api/commit/f27f1185670549c54d3f65c8c8a23a8c7cb1397c))
* Update dsp-app image in docker-compose.yml to v11.4.1 ([#3034](https://github.com/dasch-swiss/dsp-api/issues/3034)) ([71677a2](https://github.com/dasch-swiss/dsp-api/commit/71677a2d92a85c34ce5bd03e1677b8c5a6781c9a))
* Update fuseki to latest release 2.1.5 ([#3023](https://github.com/dasch-swiss/dsp-api/issues/3023)) ([6acbe4b](https://github.com/dasch-swiss/dsp-api/commit/6acbe4bfd0b691a5fc5378e9dd6edfae92ea2006))
* Update scala-graph to 2.0.1 (DEV-3072) ([#3031](https://github.com/dasch-swiss/dsp-api/issues/3031)) ([9b04b8f](https://github.com/dasch-swiss/dsp-api/commit/9b04b8f7d9588eab7c17081e9a66b0f5d46fd7bf))


### Bug Fixes

* Remove cardinality restriction for comments on regions (DEV-3179) ([#3026](https://github.com/dasch-swiss/dsp-api/issues/3026)) ([9ea8d6e](https://github.com/dasch-swiss/dsp-api/commit/9ea8d6e03b6c4606377dee2aced38a426248c6ec))

## [30.8.0](https://github.com/dasch-swiss/dsp-api/compare/v30.7.0...v30.8.0) (2024-02-05)


### Maintenance

* Admin groups cleanup ([#3011](https://github.com/dasch-swiss/dsp-api/issues/3011)) ([34fd51a](https://github.com/dasch-swiss/dsp-api/commit/34fd51a6417d5f593edb681e5ae9b714bcd30b96))
* Bump sipi to 3.8.10 ([#3007](https://github.com/dasch-swiss/dsp-api/issues/3007)) ([be8ce70](https://github.com/dasch-swiss/dsp-api/commit/be8ce705f3407dec2b59f16bdcb2eeb70349baec))
* Bump Sipi to 3.8.11 ([#3014](https://github.com/dasch-swiss/dsp-api/issues/3014)) ([67e6762](https://github.com/dasch-swiss/dsp-api/commit/67e676220537a7dd9312518cdce7a7d03fb37a56))
* Dependency updates ([#3017](https://github.com/dasch-swiss/dsp-api/issues/3017)) ([4918f5c](https://github.com/dasch-swiss/dsp-api/commit/4918f5c0b7310b12752c181b6f8852fdf8509f20))
* Migrate `GET /admin/lists?projectIri` route to tapir ([#3006](https://github.com/dasch-swiss/dsp-api/issues/3006)) ([a931357](https://github.com/dasch-swiss/dsp-api/commit/a931357160bc00ae54f285badb716f6ecb7d8a05))
* Migrate `POST /admin/lists` and `POST /admin/lists/&lt;parentListIri&gt;` to Tapir (DEV-1589) ([#3018](https://github.com/dasch-swiss/dsp-api/issues/3018)) ([63fe560](https://github.com/dasch-swiss/dsp-api/commit/63fe5600aec489a68c9868e56315feb1b14278f6))
* Migrate GET /admin/users/iri/&lt;userIri&gt; to tapir ([#3010](https://github.com/dasch-swiss/dsp-api/issues/3010)) ([34d2d7a](https://github.com/dasch-swiss/dsp-api/commit/34d2d7a226ead4e8dd4b1dd966e89376d58914f5))
* Migrate some `GET /admin/lists/*` endpoints to Tapir ([#3012](https://github.com/dasch-swiss/dsp-api/issues/3012)) ([785b573](https://github.com/dasch-swiss/dsp-api/commit/785b57332f8b96969db020184ec8acfdbd2abceb))
* Migrate update and delete of lists to Tapir ([#3013](https://github.com/dasch-swiss/dsp-api/issues/3013)) ([dafaffb](https://github.com/dasch-swiss/dsp-api/commit/dafaffba8dde87d9867ecf1dd82bf62e7f19b6c4))
* Minor dependency updates ([#3016](https://github.com/dasch-swiss/dsp-api/issues/3016)) ([d59246f](https://github.com/dasch-swiss/dsp-api/commit/d59246fc479d5462d56b6972bd21cdfea3b75c69))
* Patch dependency updates ([#3015](https://github.com/dasch-swiss/dsp-api/issues/3015)) ([b46f8f7](https://github.com/dasch-swiss/dsp-api/commit/b46f8f7fc39adc1aaad84a1af7cf31be56f4a600))
* **readme:** Update installation instruction ([#2999](https://github.com/dasch-swiss/dsp-api/issues/2999)) ([296815c](https://github.com/dasch-swiss/dsp-api/commit/296815cb1a296d356524f6708db76b39b50e9a46))
* **readme:** Use just targets instead of make targets ([#3000](https://github.com/dasch-swiss/dsp-api/issues/3000)) ([71e6119](https://github.com/dasch-swiss/dsp-api/commit/71e61198636df9a1a2a6271786db4ec41a91e677))
* Remove needless `ProjectsResponderRequestADM` classes ([#3008](https://github.com/dasch-swiss/dsp-api/issues/3008)) ([1468d2c](https://github.com/dasch-swiss/dsp-api/commit/1468d2cfa58df3fb2abdce9e1acaeb75726939fa))


### Enhancements

* Enable `UpgradePlugin`s to restrict to a specific graph and update the knora base version to 26 ([#3005](https://github.com/dasch-swiss/dsp-api/issues/3005)) ([e54aa0b](https://github.com/dasch-swiss/dsp-api/commit/e54aa0bbc49b581215f9a655666823951be406ae))


### Bug Fixes

* Less information in auth error messages (DEV-3260) ([#3019](https://github.com/dasch-swiss/dsp-api/issues/3019)) ([569a6c1](https://github.com/dasch-swiss/dsp-api/commit/569a6c19512a3eb6273259cba8debff34630ed5a))
* Only allow System Administrators to create users ([#3022](https://github.com/dasch-swiss/dsp-api/issues/3022)) ([5ab6e35](https://github.com/dasch-swiss/dsp-api/commit/5ab6e35187a4c334e85aea15e902fe3fb56edc6f))

## [30.7.0](https://github.com/dasch-swiss/dsp-api/compare/v30.6.0...v30.7.0) (2024-01-25)


### Maintenance

* Add just targets for simple stack handling ([#2985](https://github.com/dasch-swiss/dsp-api/issues/2985)) ([f5f135e](https://github.com/dasch-swiss/dsp-api/commit/f5f135ee85133035701c1854200b6c36c7aad57b))
* **admin/projects:** Add missing internal to external response formatting ([#2993](https://github.com/dasch-swiss/dsp-api/issues/2993)) ([f0312dc](https://github.com/dasch-swiss/dsp-api/commit/f0312dc1112365bb53c44c61edfb6f5861f0e974))
* Bump Sipi to 3.8.8 ([#2972](https://github.com/dasch-swiss/dsp-api/issues/2972)) ([9a00c3f](https://github.com/dasch-swiss/dsp-api/commit/9a00c3f61d205e1245e3e1c4fa3c6ce3e2ab9f10))
* Dependency updates ([#2967](https://github.com/dasch-swiss/dsp-api/issues/2967)) ([95ad2f9](https://github.com/dasch-swiss/dsp-api/commit/95ad2f9c24996513aeea1767b679b8e6ee03a953))
* Introduce `Value[A]` and extract tapir and zio-json codecs ([#2996](https://github.com/dasch-swiss/dsp-api/issues/2996)) ([9744f7b](https://github.com/dasch-swiss/dsp-api/commit/9744f7b2dc525bcae75c6562cec56acaf75a195c))
* Major dependency updates ([#2977](https://github.com/dasch-swiss/dsp-api/issues/2977)) ([6ee0111](https://github.com/dasch-swiss/dsp-api/commit/6ee01112869506c69b925230d4e03cc627c8f654))
* Migrate /admin/files to tapir (DEV-3189) ([#2995](https://github.com/dasch-swiss/dsp-api/issues/2995)) ([78ff954](https://github.com/dasch-swiss/dsp-api/commit/78ff9543c0e631512307fcb611f750de4e48ff47))
* Migrate `admin/permissions` endpoints to tapir (DEV-1590) ([#2975](https://github.com/dasch-swiss/dsp-api/issues/2975)) ([cf2c6fb](https://github.com/dasch-swiss/dsp-api/commit/cf2c6fba86e1f9adbfcd3a3ee4bf936b147958f0))
* Migrate get all users route to tapir (DEV-3142) ([#2971](https://github.com/dasch-swiss/dsp-api/issues/2971)) ([3684b91](https://github.com/dasch-swiss/dsp-api/commit/3684b9194d170d3085a0d920ca02e0362b437880))
* Migrate getAllGroups route to tapir (DEV-1588) ([#2984](https://github.com/dasch-swiss/dsp-api/issues/2984)) ([e5285ea](https://github.com/dasch-swiss/dsp-api/commit/e5285eacabb00ec5b079d0e546cf3629199a0641))
* Migrate getGroup and getGroupMembers to tapir (DEV-1588) ([#2987](https://github.com/dasch-swiss/dsp-api/issues/2987)) ([98820b0](https://github.com/dasch-swiss/dsp-api/commit/98820b05075d12f6c05af44cc07fe1d671394791))
* Migrate to Java 21 and remove usage of Java 17 (DEV-3146) ([#2974](https://github.com/dasch-swiss/dsp-api/issues/2974)) ([779fddb](https://github.com/dasch-swiss/dsp-api/commit/779fddb1978949676e3d1a8cd7944afef109db54))
* Minor dependency updates ([#2966](https://github.com/dasch-swiss/dsp-api/issues/2966)) ([0cc64d1](https://github.com/dasch-swiss/dsp-api/commit/0cc64d101b19057131ddd44645912819359d1fa1))
* Move and rename UserADM ([#2978](https://github.com/dasch-swiss/dsp-api/issues/2978)) ([56c1feb](https://github.com/dasch-swiss/dsp-api/commit/56c1febfe6c50cdb97a91a0499f973abf2839688))
* Patch dependency updates ([#2965](https://github.com/dasch-swiss/dsp-api/issues/2965)) ([d0d252d](https://github.com/dasch-swiss/dsp-api/commit/d0d252de45d987f1fb167718de832aaaef4091e2))
* Patch dependency updates ([#2976](https://github.com/dasch-swiss/dsp-api/issues/2976)) ([b150911](https://github.com/dasch-swiss/dsp-api/commit/b1509113433fc6d70daab56a823aade9d100642d))
* Patch dependency updates ([#2992](https://github.com/dasch-swiss/dsp-api/issues/2992)) ([9b57f16](https://github.com/dasch-swiss/dsp-api/commit/9b57f16cc5aefd241f13c4f66cc68e984b7c85fc))
* Rename staging servers to stage in Makefile ([#2961](https://github.com/dasch-swiss/dsp-api/issues/2961)) ([bad8bbe](https://github.com/dasch-swiss/dsp-api/commit/bad8bbefcb49dbce0a8bddfc870f43be2a8c7af3))
* Set Sipi max_post_size = '2G' in all configuration ([#2962](https://github.com/dasch-swiss/dsp-api/issues/2962)) ([9fc109c](https://github.com/dasch-swiss/dsp-api/commit/9fc109cc9ec91e7e8245ec4a438cdf775b32f48d))
* Streamline user identifier objects (DEV-3155) ([#2991](https://github.com/dasch-swiss/dsp-api/issues/2991)) ([0d07b6f](https://github.com/dasch-swiss/dsp-api/commit/0d07b6f039a919472119029d06ce1602b141c8f2))
* Unify user value objects and validation (DEV-3155) ([#2980](https://github.com/dasch-swiss/dsp-api/issues/2980)) ([83f777d](https://github.com/dasch-swiss/dsp-api/commit/83f777d5aa76f5693ea77b9f531e46cf03f34b6e))
* Update license header to 2024 ([#2981](https://github.com/dasch-swiss/dsp-api/issues/2981)) ([560dec4](https://github.com/dasch-swiss/dsp-api/commit/560dec4bd10572f72777412660d0f92cf1439738))
* Update years in the copyright header ([#2982](https://github.com/dasch-swiss/dsp-api/issues/2982)) ([700f3c5](https://github.com/dasch-swiss/dsp-api/commit/700f3c54cc729dc7e4e89afb8017bb2fe2b420a2))
* Use RestrictedViewSize in ProjectSetRestrictedViewSizeRequest ([#3001](https://github.com/dasch-swiss/dsp-api/issues/3001)) ([239c4fc](https://github.com/dasch-swiss/dsp-api/commit/239c4fce0c95bab77afea02d1d9256b28355d055))
* Use sttpbackend and reuse access token if not expired ([#2968](https://github.com/dasch-swiss/dsp-api/issues/2968)) ([eac470a](https://github.com/dasch-swiss/dsp-api/commit/eac470a4a95787002041c38b7f3f4418ff4b8525))
* Use the same custom scalac option for webapi and integration ([#2990](https://github.com/dasch-swiss/dsp-api/issues/2990)) ([b8a45ad](https://github.com/dasch-swiss/dsp-api/commit/b8a45adc61eeaa152c1a73656813f8e6427f706c))


### Documentation

* Add documentation on creating FileValue resources together with ingest (DEV -3134) ([#2969](https://github.com/dasch-swiss/dsp-api/issues/2969)) ([eac5751](https://github.com/dasch-swiss/dsp-api/commit/eac5751a2c50e402f99aeb2b57e6526eb8f410fb))
* Remove remaining API V1 documentation (DEV-3073) ([#2970](https://github.com/dasch-swiss/dsp-api/issues/2970)) ([2d3d4c4](https://github.com/dasch-swiss/dsp-api/commit/2d3d4c4272af636b43c8cd1a21cc5db8077fe358))


### Enhancements

* Add dsp ingest asset info resolution (DEV-3147) ([#2973](https://github.com/dasch-swiss/dsp-api/issues/2973)) ([c081ba8](https://github.com/dasch-swiss/dsp-api/commit/c081ba8442365903d1eb69b32a645f8b6c14c065))
* Add generating OpenApi yamls for the admin api ([#2983](https://github.com/dasch-swiss/dsp-api/issues/2983)) ([503b742](https://github.com/dasch-swiss/dsp-api/commit/503b7422248ca2638df0eface3aa569f409612d4))
* Add support to load knora-ontologies in the docker-compose stack ([#3002](https://github.com/dasch-swiss/dsp-api/issues/3002)) ([32bf7cc](https://github.com/dasch-swiss/dsp-api/commit/32bf7cc991880aa113385376c8385689f30183c5))
* Add watermark support (DEV-2993) (DEV-2991) ([#3003](https://github.com/dasch-swiss/dsp-api/issues/3003)) ([7637cb3](https://github.com/dasch-swiss/dsp-api/commit/7637cb31aa61df4d1ccb3d58632430a983a47fec))
* Make Sipi handling optional during FileValue creation (Dev-2945) ([#2960](https://github.com/dasch-swiss/dsp-api/issues/2960)) ([82ebce4](https://github.com/dasch-swiss/dsp-api/commit/82ebce45bd0679b799dece735b13588447e8d54c))


### Bug Fixes

* Allow all mime types in API that are returned by Ingest (DEV-3163) ([#2994](https://github.com/dasch-swiss/dsp-api/issues/2994)) ([1ea69a4](https://github.com/dasch-swiss/dsp-api/commit/1ea69a472db1a78c318a517f4c410c313e285632))
* Fix UserIri and allow existing values (DEV-3194) ([#2997](https://github.com/dasch-swiss/dsp-api/issues/2997)) ([ecf9c0a](https://github.com/dasch-swiss/dsp-api/commit/ecf9c0af91cb06c72df1da0a93212eae8fe9f47b))
* Let DspIngestClientLive get a fresh jwt for each request ([#2988](https://github.com/dasch-swiss/dsp-api/issues/2988)) ([31df947](https://github.com/dasch-swiss/dsp-api/commit/31df9473b385a0c2ed702ef28bfba348938d45fb))

## [30.6.0](https://github.com/dasch-swiss/dsp-api/compare/v30.5.2...v30.6.0) (2023-12-06)


### Maintenance

* Bump Sipi version to 3.8.7 ([#2951](https://github.com/dasch-swiss/dsp-api/issues/2951)) ([5b3436b](https://github.com/dasch-swiss/dsp-api/commit/5b3436bb3ba0531b97a11d98eba865fb5f51ec55))
* Dependency updates ([#2956](https://github.com/dasch-swiss/dsp-api/issues/2956)) ([1e14a88](https://github.com/dasch-swiss/dsp-api/commit/1e14a880b882ed696ec77c9ecf5e56fbf90cb21c))
* Minor dependency updates ([#2955](https://github.com/dasch-swiss/dsp-api/issues/2955)) ([8c2afcd](https://github.com/dasch-swiss/dsp-api/commit/8c2afcdbb20ca6250528844190577d57c809ef0c))
* Patch dependency updates ([#2954](https://github.com/dasch-swiss/dsp-api/issues/2954)) ([1c37768](https://github.com/dasch-swiss/dsp-api/commit/1c37768ec020d927bad328958c6cbb586e969e23))
* Simplify RDF handling ([#2952](https://github.com/dasch-swiss/dsp-api/issues/2952)) ([25099ef](https://github.com/dasch-swiss/dsp-api/commit/25099ef5aeaf412fd9b9f3baa7d3f9cbae1e9f8f))
* Update apache-jena-fuseki to v2.1.4 ([#2959](https://github.com/dasch-swiss/dsp-api/issues/2959)) ([a8a7c61](https://github.com/dasch-swiss/dsp-api/commit/a8a7c61fb685817504798898205de83ef5eecc3c))


### Documentation

* Clarify setting of restricted view of images (DEV-2961) ([#2950](https://github.com/dasch-swiss/dsp-api/issues/2950)) ([e4ca484](https://github.com/dasch-swiss/dsp-api/commit/e4ca484194a98a2b540ab0490c81fe4630456974))


### Enhancements

* Add http metrics for gravsearch endpoints (DEV-2936) ([#2946](https://github.com/dasch-swiss/dsp-api/issues/2946)) ([7ca5946](https://github.com/dasch-swiss/dsp-api/commit/7ca5946cac968123bd11df328997b210d1ace0c7))
* Add http metrics to all search endpoints by migrating to tapir DEV-2936 ([#2958](https://github.com/dasch-swiss/dsp-api/issues/2958)) ([20f8d5c](https://github.com/dasch-swiss/dsp-api/commit/20f8d5ce77b80e1d9303f730cd545b17fa471089))

## [30.5.2](https://github.com/dasch-swiss/dsp-api/compare/v30.5.1...v30.5.2) (2023-11-22)


### Maintenance

* Bump Sipi version to 3.8.5 ([#2942](https://github.com/dasch-swiss/dsp-api/issues/2942)) ([8f35d81](https://github.com/dasch-swiss/dsp-api/commit/8f35d8133715ccf2d2f53ec26df3cc2b44ff4371))
* Bump Sipi version to 3.8.6 ([#2947](https://github.com/dasch-swiss/dsp-api/issues/2947)) ([34b74bf](https://github.com/dasch-swiss/dsp-api/commit/34b74bfb682e9ad6961da913f108d24e0c70eacb))
* **docker-compose:** Bump app version to 11.1.0 ([#2926](https://github.com/dasch-swiss/dsp-api/issues/2926)) ([b39e9f3](https://github.com/dasch-swiss/dsp-api/commit/b39e9f3a230ecbcce8a72a3dcd6f04c6d3034025))
* Inline some UuidUtil functions and reduce deprecation warnings ([#2934](https://github.com/dasch-swiss/dsp-api/issues/2934)) ([52d1efa](https://github.com/dasch-swiss/dsp-api/commit/52d1efa3a4540cdb113875279205e36ccb9c9d46))
* **knora-sipi:** Remove cron and custom entrypoint ([#2940](https://github.com/dasch-swiss/dsp-api/issues/2940)) ([ef714c1](https://github.com/dasch-swiss/dsp-api/commit/ef714c1918d290189a0f96453d9622ff34e5148b))
* Major dependency updates ([#2932](https://github.com/dasch-swiss/dsp-api/issues/2932)) ([0624380](https://github.com/dasch-swiss/dsp-api/commit/06243800c8059917a6f22de3e1559c4c06daf251))
* Minor dependency updates ([#2931](https://github.com/dasch-swiss/dsp-api/issues/2931)) ([86c926a](https://github.com/dasch-swiss/dsp-api/commit/86c926a95ab3c1342fda65e445fb82462d3c8b93))
* Move project related value objects to admin.domain.model package and cleanup code ([#2923](https://github.com/dasch-swiss/dsp-api/issues/2923)) ([57c6ac2](https://github.com/dasch-swiss/dsp-api/commit/57c6ac2d719f65efcc8f716b40c18dd18e763db5))
* Move ProjectIri to KnoraProject ([#2944](https://github.com/dasch-swiss/dsp-api/issues/2944)) ([af95516](https://github.com/dasch-swiss/dsp-api/commit/af95516d5183979df623d688ccf322a6129b8325))
* Patch dependency updates ([#2930](https://github.com/dasch-swiss/dsp-api/issues/2930)) ([d8e13b7](https://github.com/dasch-swiss/dsp-api/commit/d8e13b74ef5bcfa81e46e39fb17df0bb9e0ffdb3))
* Prepare Scala 3 compatibility by adding -Xsource:3 compiler flag ([#2924](https://github.com/dasch-swiss/dsp-api/issues/2924)) ([ff9df5f](https://github.com/dasch-swiss/dsp-api/commit/ff9df5faae805928ff156b9b20ddb4e4ac5c42a3))
* Remove [@deprecation](https://github.com/deprecation) annotations ([#2937](https://github.com/dasch-swiss/dsp-api/issues/2937)) ([224eb3d](https://github.com/dasch-swiss/dsp-api/commit/224eb3dda3d0604d3075ec231eb25ae426fb5c16))
* Remove duplicate 'gravsearch' metrics ([#2936](https://github.com/dasch-swiss/dsp-api/issues/2936)) ([f11dfef](https://github.com/dasch-swiss/dsp-api/commit/f11dfef39bd7134a799f5895fd8eed9350d4ddb6))
* Remove MessageHandler from SearchResponder and call responder directly ([#2943](https://github.com/dasch-swiss/dsp-api/issues/2943)) ([ee8d09d](https://github.com/dasch-swiss/dsp-api/commit/ee8d09d8ff88fe3728565cce8d530f23b64dfc06))
* Remove redundancies in search by label queries ([#2933](https://github.com/dasch-swiss/dsp-api/issues/2933)) ([a333e34](https://github.com/dasch-swiss/dsp-api/commit/a333e346411feef176d5f2c3637ae2b7ab71947e))
* Replace spray json with zio-json for FileMetadataSipiResponse ([#2941](https://github.com/dasch-swiss/dsp-api/issues/2941)) ([20090dc](https://github.com/dasch-swiss/dsp-api/commit/20090dc460e766fef0b80002ebf6500e631a7925))
* Replace StringFormatter.validateProjectShortcode methods wi… ([#2935](https://github.com/dasch-swiss/dsp-api/issues/2935)) ([80561af](https://github.com/dasch-swiss/dsp-api/commit/80561af0521908af37b18ca08e4d243a9dea998e))
* Simplify and rename SipiService ([#2929](https://github.com/dasch-swiss/dsp-api/issues/2929)) ([0835301](https://github.com/dasch-swiss/dsp-api/commit/08353017d68590b0d1a871fdd8d96655ab449b98))
* Use KnoraRepo instead of MessageRelay in ResourcesResponderV2 ([#2927](https://github.com/dasch-swiss/dsp-api/issues/2927)) ([2358f23](https://github.com/dasch-swiss/dsp-api/commit/2358f23e5b9e51c9fac31a7a9fd488b32fa5c0da))


### Documentation

* Adjust Gravsearch documentation according to current state of code (DEV-2153) ([#2938](https://github.com/dasch-swiss/dsp-api/issues/2938)) ([6aa1990](https://github.com/dasch-swiss/dsp-api/commit/6aa1990d8e56bc0d8bdc008a04a6c6ed2f409133))


### Bug Fixes

* BEOL timeouts ([#2945](https://github.com/dasch-swiss/dsp-api/issues/2945)) ([f4a781b](https://github.com/dasch-swiss/dsp-api/commit/f4a781b14c6bb08c393585b67591f3ef02d7b7d4))
* Invalidate cached project information when adding an ontology to the project (DEV-2926) ([#2949](https://github.com/dasch-swiss/dsp-api/issues/2949)) ([d0700a2](https://github.com/dasch-swiss/dsp-api/commit/d0700a24706c97a883385570bfc8e8c326528c5c))

## [30.5.1](https://github.com/dasch-swiss/dsp-api/compare/v30.5.0...v30.5.1) (2023-11-09)


### Maintenance

* Bump sipi version ([#2913](https://github.com/dasch-swiss/dsp-api/issues/2913)) ([303ca0f](https://github.com/dasch-swiss/dsp-api/commit/303ca0f78dfdc1158e5980fc4ba09346f6e9e349))
* Bump Sipi version to 3.8.3 ([#2917](https://github.com/dasch-swiss/dsp-api/issues/2917)) ([e4c587a](https://github.com/dasch-swiss/dsp-api/commit/e4c587a6ba453a8973b09fc9c208d2f16e538bab))
* Group dependency updates ([#2906](https://github.com/dasch-swiss/dsp-api/issues/2906)) ([8b08c74](https://github.com/dasch-swiss/dsp-api/commit/8b08c741ee6503a21aaf88f12760ee01a6515c78))
* Load ontologies when querying for KnoraProjects ([#2916](https://github.com/dasch-swiss/dsp-api/issues/2916)) ([21550ce](https://github.com/dasch-swiss/dsp-api/commit/21550ce88302ebbe7e718bf2fd79d23482d7a9ce))
* Patch updates ([#2910](https://github.com/dasch-swiss/dsp-api/issues/2910)) ([3e95d71](https://github.com/dasch-swiss/dsp-api/commit/3e95d71c29467ebd3bc981ffaab9dcafb6875970))
* **PRs:** Add 'perf' as allowed prefix for PR titles ([#2915](https://github.com/dasch-swiss/dsp-api/issues/2915)) ([8d08e35](https://github.com/dasch-swiss/dsp-api/commit/8d08e35edeb647ae8aea55900c602fe93cb19d4f))
* **release-please:** Add missing PR title prefixes to the workflow ([#2918](https://github.com/dasch-swiss/dsp-api/issues/2918)) ([142fee0](https://github.com/dasch-swiss/dsp-api/commit/142fee02777bf8990a0ed92cfe2a681b23b231f6))
* Remove GravsearchQueryOptimisationFeature and simplify ([#2909](https://github.com/dasch-swiss/dsp-api/issues/2909)) ([a91f6f6](https://github.com/dasch-swiss/dsp-api/commit/a91f6f669d2517cc2667f0c59aa88e01de00116c))
* Replace MessageRelay with KnoraProjectRepo in OntologyResponderV2 ([#2920](https://github.com/dasch-swiss/dsp-api/issues/2920)) ([b093357](https://github.com/dasch-swiss/dsp-api/commit/b09335766c8d0d7da0e1524614d1755308cb574a))
* **scala-steward:** Improve dependency grouping ([#2919](https://github.com/dasch-swiss/dsp-api/issues/2919)) ([dbda1cc](https://github.com/dasch-swiss/dsp-api/commit/dbda1ccc6839d5da4fb3b6978c4a125c11c26a88))
* Update icu4j to v74.1 ([#2912](https://github.com/dasch-swiss/dsp-api/issues/2912)) ([77e144b](https://github.com/dasch-swiss/dsp-api/commit/77e144bda4150ac4c2e990255b054028f01036bb))
* Update sbt-javaagent to v0.1.8 ([#2911](https://github.com/dasch-swiss/dsp-api/issues/2911)) ([e17cda2](https://github.com/dasch-swiss/dsp-api/commit/e17cda29d60acb536cc62429375f786379b530f9))
* Update tapir to v1.8.4 ([#2922](https://github.com/dasch-swiss/dsp-api/issues/2922)) ([354662c](https://github.com/dasch-swiss/dsp-api/commit/354662c7fce0aea944412c0967b217bda4ddfb98))


### Bug Fixes

* Fix project name, description and keywords value objects (2892) ([#2908](https://github.com/dasch-swiss/dsp-api/issues/2908)) ([d1388bc](https://github.com/dasch-swiss/dsp-api/commit/d1388bce55523e6c4395d4c7f7d5f714ed256214))
* **performance:** Reverse order of topological sorting in Gravsearch queries ([#2914](https://github.com/dasch-swiss/dsp-api/issues/2914)) ([d81a88e](https://github.com/dasch-swiss/dsp-api/commit/d81a88e5045437445365d9faefd9c65408ccc21b))

## [30.5.0](https://github.com/dasch-swiss/dsp-api/compare/v30.4.2...v30.5.0) (2023-10-27)


### Enhancements

* **Gravsearch:** Enable ORDER BY external link (DEV-2704) ([#2902](https://github.com/dasch-swiss/dsp-api/issues/2902)) ([1b7e02a](https://github.com/dasch-swiss/dsp-api/commit/1b7e02a9ba30b917da596466dca42a45640b52d5))


### Bug Fixes

* Startup and keep instrumentation server running ([#2901](https://github.com/dasch-swiss/dsp-api/issues/2901)) ([a11af40](https://github.com/dasch-swiss/dsp-api/commit/a11af40805d8e90ae4ab1e13c6fcacdc6b305ce9))


### Maintenance

* Remove GitHub action which tests zio-http routes  ([#2903](https://github.com/dasch-swiss/dsp-api/issues/2903)) ([1999b22](https://github.com/dasch-swiss/dsp-api/commit/1999b221632a8094a74e55155831183987754eca))
* Remove/Fix some compiler warnings ([#2899](https://github.com/dasch-swiss/dsp-api/issues/2899)) ([cb5dec6](https://github.com/dasch-swiss/dsp-api/commit/cb5dec6b19049d239b2ab989bd19ac8151be64ec))
* Update dependencies ([#2905](https://github.com/dasch-swiss/dsp-api/issues/2905)) ([855eb02](https://github.com/dasch-swiss/dsp-api/commit/855eb02d13e9fd37af948780ffb9b0a4521bdc1e))

## [30.4.2](https://github.com/dasch-swiss/dsp-api/compare/v30.4.1...v30.4.2) (2023-10-23)


### Maintenance

* Add missing component to release please GitHub action ([#2896](https://github.com/dasch-swiss/dsp-api/issues/2896)) ([8751d52](https://github.com/dasch-swiss/dsp-api/commit/8751d5212329efa1a008e97ef6d55aa7f2982926))
* Update PR title template ([#2897](https://github.com/dasch-swiss/dsp-api/issues/2897)) ([3c3c45f](https://github.com/dasch-swiss/dsp-api/commit/3c3c45f63c54d565ac022231cba18b08a4527fe8))
* Update tapir-refined from 1.2.10 to 1.2.13 ([#2886](https://github.com/dasch-swiss/dsp-api/issues/2886)) ([05d519a](https://github.com/dasch-swiss/dsp-api/commit/05d519a15752364c46d8592534783d3bfd535bb5))
* Use correct syntax in release please configuration ([#2895](https://github.com/dasch-swiss/dsp-api/issues/2895)) ([e8eced4](https://github.com/dasch-swiss/dsp-api/commit/e8eced45f022059056afa1a1761f4bd2ac191014))

## [30.4.1](https://github.com/dasch-swiss/dsp-api/compare/v30.4.0...v30.4.1) (2023-10-17)


### Maintenance

* **docker:** Set container memory limits in local stack (DEV-2718) ([#2874](https://github.com/dasch-swiss/dsp-api/issues/2874)) ([783d4fd](https://github.com/dasch-swiss/dsp-api/commit/783d4fdd33110a3885d0ae2ebff5754c5412d3c8))
* Improve PR title check ([#2882](https://github.com/dasch-swiss/dsp-api/issues/2882)) ([6f4b962](https://github.com/dasch-swiss/dsp-api/commit/6f4b96248dd9876906c54b419f75c9178ba2e387))


### Documentation

* Remove invalid ProjectAdminOntologyAllPermission (DEV-2814) ([#2881](https://github.com/dasch-swiss/dsp-api/issues/2881)) ([1796d52](https://github.com/dasch-swiss/dsp-api/commit/1796d52a68085d47f351f405db680905f5ff8360))

## [30.4.0](https://github.com/dasch-swiss/dsp-api/compare/v30.3.0...v30.4.0) (2023-10-12)


### Enhancements

* add default value of projectRestrictedViewSize (DEV-2626) ([#2873](https://github.com/dasch-swiss/dsp-api/issues/2873)) ([ff4d3a1](https://github.com/dasch-swiss/dsp-api/commit/ff4d3a14f9c803e56b7b4d294e9f314e512ecf6f))
* Add maintenance service for fixing top-left dimension values DEV-2803 ([#2876](https://github.com/dasch-swiss/dsp-api/issues/2876)) ([82b715a](https://github.com/dasch-swiss/dsp-api/commit/82b715a3791ec295de49be168347c59557e04a5b))
* Add route that sets projectRestrictedViewSetting size (DEV-2304) ([#2794](https://github.com/dasch-swiss/dsp-api/issues/2794)) ([738ab1c](https://github.com/dasch-swiss/dsp-api/commit/738ab1ccd524d6cc52e6674676ecb74e4e875723))
* Introduce /admin/maintenance and expose fix top left maintenance action DEV-2805 ([#2877](https://github.com/dasch-swiss/dsp-api/issues/2877)) ([a6b8c2f](https://github.com/dasch-swiss/dsp-api/commit/a6b8c2f8210ea106e1cdfdb1804a46145b7ba1a2))


### Bug Fixes

* Improve performance for Gravsearch queries ([#2857](https://github.com/dasch-swiss/dsp-api/issues/2857)) ([86cc4f2](https://github.com/dasch-swiss/dsp-api/commit/86cc4f26f0ce93f7a0b51121e26a15890b7f815c))


### Maintenance

* Configure Scala Steward to produce PR with compatible title ([#2867](https://github.com/dasch-swiss/dsp-api/issues/2867)) ([fbbe5ec](https://github.com/dasch-swiss/dsp-api/commit/fbbe5ec792d0cfeb4581ad93d6fbcc85174d44c5))
* Do not log warn message for 405 and 404 status code responses ([#2854](https://github.com/dasch-swiss/dsp-api/issues/2854)) ([d9fd81c](https://github.com/dasch-swiss/dsp-api/commit/d9fd81ce523a80c15129392eb7b535ec1d4b6c01))
* Introduce tapir on Pekko ([#2870](https://github.com/dasch-swiss/dsp-api/issues/2870)) ([08accab](https://github.com/dasch-swiss/dsp-api/commit/08accabf8b0c5d02fe7147ffe44023878c39d047))
* Migrate secure admin/projects endpoints to Tapir ([#2872](https://github.com/dasch-swiss/dsp-api/issues/2872)) ([9f98f7e](https://github.com/dasch-swiss/dsp-api/commit/9f98f7e1d3872b2ff273f783b4374adbfeae2b79))
* Update dependencies DEV-2742 ([#2868](https://github.com/dasch-swiss/dsp-api/issues/2868)) ([8ba3bb5](https://github.com/dasch-swiss/dsp-api/commit/8ba3bb5c089a97249cf92b06101e2cf042c0b3ab))
* Update dependencies fuseki and app ([#2856](https://github.com/dasch-swiss/dsp-api/issues/2856)) ([8123dbd](https://github.com/dasch-swiss/dsp-api/commit/8123dbd4667abd6191a87efcf0b5f595cdeaa69e))
* Update Fuseki DEV-2743 ([#2869](https://github.com/dasch-swiss/dsp-api/issues/2869)) ([14f1911](https://github.com/dasch-swiss/dsp-api/commit/14f1911287ccbb819d5799b94f82d6b1242f07f9))
* update PR template ([#2878](https://github.com/dasch-swiss/dsp-api/issues/2878)) ([6c04101](https://github.com/dasch-swiss/dsp-api/commit/6c04101c3c4e9a9b39460c9e0748eadb04ef0468))
* Update spring-security-core to 6.1.4 ([#2865](https://github.com/dasch-swiss/dsp-api/issues/2865)) ([b75edaf](https://github.com/dasch-swiss/dsp-api/commit/b75edaf724c880ef1a4333523300468dcfa26308))


### Documentation

* remove inexisting pages from navigation bar ([#2871](https://github.com/dasch-swiss/dsp-api/issues/2871)) ([dd2dfe6](https://github.com/dasch-swiss/dsp-api/commit/dd2dfe61810bd3b1ad38731893e244ad15e30441))

## [30.3.0](https://github.com/dasch-swiss/dsp-api/compare/v30.2.1...v30.3.0) (2023-09-28)


### Enhancements

* update Shortname value object ([#2851](https://github.com/dasch-swiss/dsp-api/issues/2851)) ([35187ca](https://github.com/dasch-swiss/dsp-api/commit/35187ca32cb4c0314683009457fe32f3a1a091a3))


### Maintenance

* add GH action that checks PR title ([#2849](https://github.com/dasch-swiss/dsp-api/issues/2849)) ([e6c4b90](https://github.com/dasch-swiss/dsp-api/commit/e6c4b907c9ba5cd966a5940a4cc704f55aa2871c))
* Replace Akka with Pekko ([#2848](https://github.com/dasch-swiss/dsp-api/issues/2848)) ([d343d8e](https://github.com/dasch-swiss/dsp-api/commit/d343d8e87a3db8092e402f446429bedc6d21fdb6))
* Update and rename check-pr-title workflow ([#2852](https://github.com/dasch-swiss/dsp-api/issues/2852)) ([fde1faf](https://github.com/dasch-swiss/dsp-api/commit/fde1faf5b4722275d6910e0f06174fcbbaaaa2ae))

## [30.2.1](https://github.com/dasch-swiss/dsp-api/compare/v30.2.0...v30.2.1) (2023-09-15)


### Maintenance

* update dependencies ([#2836](https://github.com/dasch-swiss/dsp-api/issues/2836)) ([6642192](https://github.com/dasch-swiss/dsp-api/commit/6642192c616799bcca782b662993ee03fe246ca4))

## [30.2.0](https://github.com/dasch-swiss/dsp-api/compare/v30.1.2...v30.2.0) (2023-09-14)


### Enhancements

* SystemAdmins receive a token which is valid on the dps-ingest api ([#2835](https://github.com/dasch-swiss/dsp-api/issues/2835)) ([469f228](https://github.com/dasch-swiss/dsp-api/commit/469f228d85ee1f02246e09de32bddfc1f44854c2))


### Maintenance

* Remove deprecated methods on JsonLDObject ([#2832](https://github.com/dasch-swiss/dsp-api/issues/2832)) ([c8cf990](https://github.com/dasch-swiss/dsp-api/commit/c8cf990837ce51245fd616b9f677108dbf161152))
* Update db image in docker-compose and Dependencies ([#2833](https://github.com/dasch-swiss/dsp-api/issues/2833)) ([b0d48d0](https://github.com/dasch-swiss/dsp-api/commit/b0d48d010b194282c6cfb8dae47d32eb3de5192f))

## [30.1.2](https://github.com/dasch-swiss/dsp-api/compare/v30.1.1...v30.1.2) (2023-09-08)


### Maintenance

* Remove compiler warning stemming from -Wvalue-discard ([#2831](https://github.com/dasch-swiss/dsp-api/issues/2831)) ([9071204](https://github.com/dasch-swiss/dsp-api/commit/90712044d3bc1c1e32df73baf00f9ce898d36510))
* Remove type annotations which produce compiler warnings ([#2829](https://github.com/dasch-swiss/dsp-api/issues/2829)) ([c754042](https://github.com/dasch-swiss/dsp-api/commit/c75404288b4b048554fd4d8ccc374723930d92e3))
* Remove unused or dead code ([#2827](https://github.com/dasch-swiss/dsp-api/issues/2827)) ([36b835e](https://github.com/dasch-swiss/dsp-api/commit/36b835e82a76ecf01687b54bbdae410197ca4731))
* Update 'fuseki_request_duration' histogram bucket boundaries ([#2830](https://github.com/dasch-swiss/dsp-api/issues/2830)) ([b7bef4e](https://github.com/dasch-swiss/dsp-api/commit/b7bef4ef1cdd768693ab544db0a2874ede9e95aa))

## [30.1.1](https://github.com/dasch-swiss/dsp-api/compare/v30.1.0...v30.1.1) (2023-09-06)


### Maintenance

* Remove -HEAD from published docker tag when building a git tag ([#2825](https://github.com/dasch-swiss/dsp-api/issues/2825)) ([108c480](https://github.com/dasch-swiss/dsp-api/commit/108c48048624508c493a8acec0322bb1f484a883))

## [30.1.0](https://github.com/dasch-swiss/dsp-api/compare/v30.0.0...v30.1.0) (2023-09-06)


### Enhancements

* Add metrics to TriplestoreService SparqlQuery execution DEV-2627 ([#2823](https://github.com/dasch-swiss/dsp-api/issues/2823)) ([8ce554b](https://github.com/dasch-swiss/dsp-api/commit/8ce554bc3051799134ac86d23ae25f2ee241ef1c))


### Maintenance

* Filter "HEAD" as branch name when building a release on a chec… ([#2817](https://github.com/dasch-swiss/dsp-api/issues/2817)) ([818fb41](https://github.com/dasch-swiss/dsp-api/commit/818fb41ea81bda03669fff86353cbdcd3a521de2))
* Introduce typed queries Ask, Select, Construct, Update and remove TriplestoreMessageHandler ([#2816](https://github.com/dasch-swiss/dsp-api/issues/2816)) ([96c330d](https://github.com/dasch-swiss/dsp-api/commit/96c330d21d5e5bd74d7e8f50ba3515b039afa773))
* Speedup /projects endpoint ([#2824](https://github.com/dasch-swiss/dsp-api/issues/2824)) ([22d5146](https://github.com/dasch-swiss/dsp-api/commit/22d514677e8b073e3e6f13c0cb4c4516ed9c7e99))
* Update app in docker-compose ([#2821](https://github.com/dasch-swiss/dsp-api/issues/2821)) ([f124f85](https://github.com/dasch-swiss/dsp-api/commit/f124f855043443d355a6d07b6fb384361c7b4e24))
* Update Dependencies ([#2822](https://github.com/dasch-swiss/dsp-api/issues/2822)) ([198d570](https://github.com/dasch-swiss/dsp-api/commit/198d57058468be1037750c493eba1c2c00a43bb2))

## [30.0.0](https://github.com/dasch-swiss/dsp-api/compare/v29.1.3...v30.0.0) (2023-08-31)


### ⚠ BREAKING CHANGES

* remove experimental standoff route (DEV-2549) ([#2795](https://github.com/dasch-swiss/dsp-api/issues/2795))

### Bug Fixes

* allow ordering by label in Gravsearch (DEV-2546) ([#2798](https://github.com/dasch-swiss/dsp-api/issues/2798)) ([ca4553e](https://github.com/dasch-swiss/dsp-api/commit/ca4553e097e30cabdd250021ee14aa789d44ae47))


### Maintenance

* Cleanup TriplestoreService code ([#2804](https://github.com/dasch-swiss/dsp-api/issues/2804)) ([6278fba](https://github.com/dasch-swiss/dsp-api/commit/6278fba558fd03d4c44df374777230159ffa4a8b))
* Derive version from git tag DEV-2575 ([#2800](https://github.com/dasch-swiss/dsp-api/issues/2800)) ([5f612a8](https://github.com/dasch-swiss/dsp-api/commit/5f612a89cea59edec74e68bc7c9992c28ad4a5ee))
* remove experimental standoff route (DEV-2549) ([#2795](https://github.com/dasch-swiss/dsp-api/issues/2795)) ([246f1da](https://github.com/dasch-swiss/dsp-api/commit/246f1da04f372a2a1d7798ad3bc67c651ca270fd))
* Remove throws from OntologyResponderV2 DEV-2579  ([#2801](https://github.com/dasch-swiss/dsp-api/issues/2801)) ([b0694ad](https://github.com/dasch-swiss/dsp-api/commit/b0694ad082fd82bcd6e05c236f07cb999186891e))
* Remove throws from ResourceResponderV2 DEV-2580 ([#2802](https://github.com/dasch-swiss/dsp-api/issues/2802)) ([55d362d](https://github.com/dasch-swiss/dsp-api/commit/55d362d9f2900fb8dd9c16eaf1d678592e47df85))
* remove throws from values responder DEV-2568 ([#2799](https://github.com/dasch-swiss/dsp-api/issues/2799)) ([42ee838](https://github.com/dasch-swiss/dsp-api/commit/42ee83816eaae731bc8adfcbd444e4edb61f6206))

## [29.1.3](https://github.com/dasch-swiss/dsp-api/compare/v29.1.2...v29.1.3) (2023-08-16)


### Bug Fixes

* query-patterns-should-not-be-separated DEV-2473 ([#2786](https://github.com/dasch-swiss/dsp-api/issues/2786)) ([0aae817](https://github.com/dasch-swiss/dsp-api/commit/0aae8173ae6c2cf4a9cfa24728bab11e8c687ec4))


### Maintenance

* improve compiler warnings (DEV-1611) ([#2784](https://github.com/dasch-swiss/dsp-api/issues/2784)) ([00379ce](https://github.com/dasch-swiss/dsp-api/commit/00379ce69372dd9a482870ac96d6f242b4e22bc8))
* improve file path resolving logic for generated test data files ([#2783](https://github.com/dasch-swiss/dsp-api/issues/2783)) ([c2d6451](https://github.com/dasch-swiss/dsp-api/commit/c2d6451d9731cf55a6c81d49d56058b91798cfae))
* move integration tests to separate SBT project ([#2772](https://github.com/dasch-swiss/dsp-api/issues/2772)) ([321e2dc](https://github.com/dasch-swiss/dsp-api/commit/321e2dc3fe86bde7aebf5d35c53a1bdd84615534))
* **ProjectImportService:** improve logging ([#2781](https://github.com/dasch-swiss/dsp-api/issues/2781)) ([35ce421](https://github.com/dasch-swiss/dsp-api/commit/35ce42101eb44cffddc410adb87ae06c257a6b1a))
* Remove ValueUpdateRequestV2 and DeleteValueRequestV2 NO-TICKET ([#2779](https://github.com/dasch-swiss/dsp-api/issues/2779)) ([577e4ec](https://github.com/dasch-swiss/dsp-api/commit/577e4eccc66a9fb1e11d738b4126ba2e3a515e99))
* Speed up value creation and update DEV-2473 ([#2778](https://github.com/dasch-swiss/dsp-api/issues/2778)) ([5e5a3ea](https://github.com/dasch-swiss/dsp-api/commit/5e5a3ea4739d29a5cd5a30b36dad98d13dd95d57))
* update dependencies ([#2793](https://github.com/dasch-swiss/dsp-api/issues/2793)) ([eb85ba5](https://github.com/dasch-swiss/dsp-api/commit/eb85ba5ab02fd04398a455897dd52a31cd95d4ed))
* Use sttp client for dsp-ingest download ([#2777](https://github.com/dasch-swiss/dsp-api/issues/2777)) ([c431ccf](https://github.com/dasch-swiss/dsp-api/commit/c431ccf122b5dc4e740c7dc3721730ceebad8b71))


### Documentation

* minor Gravsearch docs fixes ([#2782](https://github.com/dasch-swiss/dsp-api/issues/2782)) ([35c87f4](https://github.com/dasch-swiss/dsp-api/commit/35c87f45d5c4638466606e97abf4b86ed8790e3d))

## [29.1.2](https://github.com/dasch-swiss/dsp-api/compare/v29.1.1...v29.1.2) (2023-08-02)


### Maintenance

* update dependencies ([#2770](https://github.com/dasch-swiss/dsp-api/issues/2770)) ([6e3857e](https://github.com/dasch-swiss/dsp-api/commit/6e3857e93f9f01e729b108603449f75c26382c0f))

## [29.1.1](https://github.com/dasch-swiss/dsp-api/compare/v29.1.0...v29.1.1) (2023-07-24)


### Maintenance

* add some OCI docker image labels to DSP-API for DataDog source code linking (INFRA-328) ([#2750](https://github.com/dasch-swiss/dsp-api/issues/2750)) ([b3296dc](https://github.com/dasch-swiss/dsp-api/commit/b3296dcf4fb2f6c6fa6920086f83c1a2ed9cee1b))
* bump Jena Fuseki version to 4.9.0 ([#2761](https://github.com/dasch-swiss/dsp-api/issues/2761)) ([92b3ca3](https://github.com/dasch-swiss/dsp-api/commit/92b3ca3fc5ffd9ba65399660f80751ea5d445ae5))
* fix expired token in SIPI test ([#2755](https://github.com/dasch-swiss/dsp-api/issues/2755)) ([fd1ce2b](https://github.com/dasch-swiss/dsp-api/commit/fd1ce2b650c2c2c67998bfb20989f1facd4d233f))
* reorganize test data ([#2757](https://github.com/dasch-swiss/dsp-api/issues/2757)) ([9b68d1c](https://github.com/dasch-swiss/dsp-api/commit/9b68d1cca4646c4ab6fe522ad73a5b87316665d1))
* Simplify GroupStatus model ([#2741](https://github.com/dasch-swiss/dsp-api/issues/2741)) ([0b6d102](https://github.com/dasch-swiss/dsp-api/commit/0b6d10222cb43d8ddec41fe0e55818871f3f9b1d))
* simplify test data in repository ([#2753](https://github.com/dasch-swiss/dsp-api/issues/2753)) ([a2a7fb7](https://github.com/dasch-swiss/dsp-api/commit/a2a7fb7ee671d9e93b3bfeb140db200739dbbe2a))
* tidy up scripts ([#2754](https://github.com/dasch-swiss/dsp-api/issues/2754)) ([8bb261f](https://github.com/dasch-swiss/dsp-api/commit/8bb261fc5bf2351fb6dbd96e8d3e1c20450155a9))
* Use JwtService to generate a jwt in SipiIT ([#2756](https://github.com/dasch-swiss/dsp-api/issues/2756)) ([70037b0](https://github.com/dasch-swiss/dsp-api/commit/70037b0801582f1c2ee4977736f9e4d8440362a2))

## [29.1.0](https://github.com/dasch-swiss/dsp-api/compare/v29.0.1...v29.1.0) (2023-07-06)


### Bug Fixes

* Align path variable for export and import routes to use Shortcode ([#2734](https://github.com/dasch-swiss/dsp-api/issues/2734)) ([8cc2c62](https://github.com/dasch-swiss/dsp-api/commit/8cc2c6220d63cf82fbebc805cf068fa266a406e5))
* Remove file path from temporaryUrl in upload response of Sipi ([#2737](https://github.com/dasch-swiss/dsp-api/issues/2737)) ([00e3b39](https://github.com/dasch-swiss/dsp-api/commit/00e3b39c67d36b0a1be8959a242006fd19b69f0f))
* Update dsp-ingest endpoint that moved to `projects` in DspIngestClient ([#2735](https://github.com/dasch-swiss/dsp-api/issues/2735)) ([592bfc8](https://github.com/dasch-swiss/dsp-api/commit/592bfc82ab1e6e45c5ca11fc287b74cb932a1784))


### Enhancements

* Dsp-ingest integration for import/export of projects DEV-2297 ([#2722](https://github.com/dasch-swiss/dsp-api/issues/2722)) ([12402f3](https://github.com/dasch-swiss/dsp-api/commit/12402f3ec2e3ceee845110d5c588568e28066afd))


### Maintenance

* Add docker compose configuration for api accessing ingest ([#2736](https://github.com/dasch-swiss/dsp-api/issues/2736)) ([6ef3d17](https://github.com/dasch-swiss/dsp-api/commit/6ef3d17cd172e6f71bc15da249da24bb4be4becc))
* Introduce JwtConfig and expose as layer ([#2719](https://github.com/dasch-swiss/dsp-api/issues/2719)) ([5737e18](https://github.com/dasch-swiss/dsp-api/commit/5737e18487e498daa25e50961e5c0a56d5aa5c26))
* optimize isEntityInUse queries, use ASK ([#2739](https://github.com/dasch-swiss/dsp-api/issues/2739)) ([0cfa3d4](https://github.com/dasch-swiss/dsp-api/commit/0cfa3d4cb20cb17b5e3456a7457d3cc090d6cdd0))
* Remove ontology responsibility from AppRouter ([#2740](https://github.com/dasch-swiss/dsp-api/issues/2740)) ([69f0d67](https://github.com/dasch-swiss/dsp-api/commit/69f0d67dc2b48314bbdafd5079be2e9edc6950a8))
* Rename Shortcode everywhere and use its type in KnoraProject property (NO-TICKET) ([#2724](https://github.com/dasch-swiss/dsp-api/issues/2724)) ([f01c319](https://github.com/dasch-swiss/dsp-api/commit/f01c319ec9d77af3d333a405f1b7b10b72657818))
* standarise shortname naming (NO-TICKET) ([#2733](https://github.com/dasch-swiss/dsp-api/issues/2733)) ([990030d](https://github.com/dasch-swiss/dsp-api/commit/990030d13343de8920990430e1fa7a0fa49c5392))
* **TriplestoreServiceLive:** minor improvements ([#2721](https://github.com/dasch-swiss/dsp-api/issues/2721)) ([d31ba01](https://github.com/dasch-swiss/dsp-api/commit/d31ba01931a3592a71992aaf817bcc658d805b27))
* update dependencies ([#2717](https://github.com/dasch-swiss/dsp-api/issues/2717)) ([ff712bf](https://github.com/dasch-swiss/dsp-api/commit/ff712bf0b5da47ede1920230a79140458dc559f0))
* Use Duration type for AppConfig properties (NO-TICKET) ([#2720](https://github.com/dasch-swiss/dsp-api/issues/2720)) ([2b2b551](https://github.com/dasch-swiss/dsp-api/commit/2b2b551c728961d7cebee185e82e68e94844c778))

## [29.0.1](https://github.com/dasch-swiss/dsp-api/compare/v29.0.0...v29.0.1) (2023-06-22)


### Bug Fixes

* Make Sipi handle multiple `KnoraAuthentication*` cookies correctly (DEV-2271) ([#2713](https://github.com/dasch-swiss/dsp-api/issues/2713)) ([1330d2b](https://github.com/dasch-swiss/dsp-api/commit/1330d2ba96b654e08314b42b5a9dbc668aba3d45))
* Multiple cookies are separated by `; ` not by `,` ([#2715](https://github.com/dasch-swiss/dsp-api/issues/2715)) ([c317efc](https://github.com/dasch-swiss/dsp-api/commit/c317efce23f97df384ab7999037c517cc270592c))


### Maintenance

* Add dsp-ingest to docker-compose.yml  ([#2712](https://github.com/dasch-swiss/dsp-api/issues/2712)) ([64e72be](https://github.com/dasch-swiss/dsp-api/commit/64e72be3a11d13500b86e39afb559f8b6150122b))
* Add sipi auth tests for knora.json (NO-Ticket) ([#2711](https://github.com/dasch-swiss/dsp-api/issues/2711)) ([85a30a5](https://github.com/dasch-swiss/dsp-api/commit/85a30a55a1d442fe64a9063bbf8171ebd4f8d98c))
* fix invalid links in CHANGELOG (DEV-2251) ([#2699](https://github.com/dasch-swiss/dsp-api/issues/2699)) ([2419f58](https://github.com/dasch-swiss/dsp-api/commit/2419f58458e3edef32424597e9aa1b4a92f06d96))

## [29.0.0](https://github.com/dasch-swiss/dsp-api/compare/v28.3.0...v29.0.0) (2023-06-08)


### ⚠ BREAKING CHANGES

* deactivate API V1 routes (DEV-2223) ([#2685](https://github.com/dasch-swiss/dsp-api/issues/2685))

### Bug Fixes

* gravsearch and count queries include deleted resources for some queries with negation patterns (DEV-2189) ([#2682](https://github.com/dasch-swiss/dsp-api/issues/2682)) ([668a734](https://github.com/dasch-swiss/dsp-api/commit/668a734cd563f1bbe34fe2fa9dd4b4acabcf2332))


### Maintenance

* bump fuseki version to 4.8.0 ([#2696](https://github.com/dasch-swiss/dsp-api/issues/2696)) ([135da99](https://github.com/dasch-swiss/dsp-api/commit/135da99291161724142c1aedabee1887382ca238))
* merge or move UUID related methods (DEV-2192) ([#2697](https://github.com/dasch-swiss/dsp-api/issues/2697)) ([93f7088](https://github.com/dasch-swiss/dsp-api/commit/93f7088f29a9831c51e7d310601cc2b9ca895981))
* remove remove SIPI file migration route (DEV-2180) ([#2683](https://github.com/dasch-swiss/dsp-api/issues/2683)) ([b2f0dff](https://github.com/dasch-swiss/dsp-api/commit/b2f0dff82d1aa5befe5e60193dd43b77b95825a5))


### Enhancements

* add export of assets (DEV-2106) ([#2668](https://github.com/dasch-swiss/dsp-api/issues/2668)) ([0be6991](https://github.com/dasch-swiss/dsp-api/commit/0be6991648699e2513aa8e107382b786ba632472))
* add import of trig file of export (DEV-2249) ([#2680](https://github.com/dasch-swiss/dsp-api/issues/2680)) ([6fb0e8c](https://github.com/dasch-swiss/dsp-api/commit/6fb0e8c9584dde9a914a19b6c6f2284b15fd64d8))
* deactivate API V1 routes (DEV-2223) ([#2685](https://github.com/dasch-swiss/dsp-api/issues/2685)) ([ee3cddc](https://github.com/dasch-swiss/dsp-api/commit/ee3cddc394eaaecf7dfe7c928b22dc22a7371d65))

## [28.3.0](https://github.com/dasch-swiss/dsp-api/compare/v28.2.0...v28.3.0) (2023-05-25)


### Bug Fixes

* add integration Test for Sipi and fix broken responses (NO-TICKET) ([#2675](https://github.com/dasch-swiss/dsp-api/issues/2675)) ([f8c3aa0](https://github.com/dasch-swiss/dsp-api/commit/f8c3aa0ddd8bf258196e053a9f34f59f882cb01f))
* Fix Sipi get file response if dsp-api responds with Not Found during permission check (NO-TICKET) ([#2677](https://github.com/dasch-swiss/dsp-api/issues/2677)) ([4e1c6c2](https://github.com/dasch-swiss/dsp-api/commit/4e1c6c2dbf9064cd20f1ac8ded43a7d8048b99d8))


### Enhancements

* Align authorization for all endpoints of knora-sipi (DEV-2175) ([#2669](https://github.com/dasch-swiss/dsp-api/issues/2669)) ([ff59664](https://github.com/dasch-swiss/dsp-api/commit/ff596644e42b1b28fc78ba57acabae26f2e2d4e2))
* **sipi:** add clean temp dir cronjob (DEV-2090) ([#2656](https://github.com/dasch-swiss/dsp-api/issues/2656)) ([7af1c27](https://github.com/dasch-swiss/dsp-api/commit/7af1c2734b1a5d5c28ce0bfd45dddcedb4e3661c))


### Maintenance

* add more gravsearch metrics ([#2666](https://github.com/dasch-swiss/dsp-api/issues/2666)) ([873eb46](https://github.com/dasch-swiss/dsp-api/commit/873eb46a5cea66b626618495efd8aea15ad99efb))
* add more tests for Sipi responses (NO-TICKET) ([#2679](https://github.com/dasch-swiss/dsp-api/issues/2679)) ([74f49ca](https://github.com/dasch-swiss/dsp-api/commit/74f49caa1d6da23e9ab83283c648c1822e5a03cd))
* Create license headers for all it files (NO-TICKET) ([#2676](https://github.com/dasch-swiss/dsp-api/issues/2676)) ([a97ec6b](https://github.com/dasch-swiss/dsp-api/commit/a97ec6b07636850c57923cd710b4c8ba20e8cd27))
* improve lucene handling in gravsearch (DEV-2148) ([#2667](https://github.com/dasch-swiss/dsp-api/issues/2667)) ([bf5d4be](https://github.com/dasch-swiss/dsp-api/commit/bf5d4be4578eb156ad44abd2d2d2962c98f9b1ba))
* merge duplicated StringFormatter and value objects methods part 1 (DEV-2046) ([#2672](https://github.com/dasch-swiss/dsp-api/issues/2672)) ([655590a](https://github.com/dasch-swiss/dsp-api/commit/655590ad2483633f69524311e5eb1b548bcc152b))
* remove deprecated StringFormatter methods ([#2658](https://github.com/dasch-swiss/dsp-api/issues/2658)) ([62eb63c](https://github.com/dasch-swiss/dsp-api/commit/62eb63cb48013aafece982a815aeaaa678ac8668))
* Remove GroupShortADM (NO-TICKET) ([#2674](https://github.com/dasch-swiss/dsp-api/issues/2674)) ([3676fbf](https://github.com/dasch-swiss/dsp-api/commit/3676fbf89614800191d9ad60107fc5f13d4416ca))
* Resolve compiler warning toObjsWithLang (NO-TICKET) ([#2671](https://github.com/dasch-swiss/dsp-api/issues/2671)) ([dc7e2c2](https://github.com/dasch-swiss/dsp-api/commit/dc7e2c21464d7157c7f239f2b8ec3edef5b0782f))
* **sipi:** increase healthcheck timeout and retries ([#2670](https://github.com/dasch-swiss/dsp-api/issues/2670)) ([5dd994d](https://github.com/dasch-swiss/dsp-api/commit/5dd994dcc4dd50468d0f2d045d0ce2d909bafacc))

## [28.2.0](https://github.com/dasch-swiss/dsp-api/compare/v28.1.2...v28.2.0) (2023-05-15)


### Bug Fixes

* Fix bad substitution in export-moving-image-frames.sh ([#2659](https://github.com/dasch-swiss/dsp-api/issues/2659)) ([9ee412c](https://github.com/dasch-swiss/dsp-api/commit/9ee412c1ec5a9ef7bfd5a013d0670d25621530b2))
* Use copy instead of move for Sipi file migration ([#2655](https://github.com/dasch-swiss/dsp-api/issues/2655)) ([1b8ffff](https://github.com/dasch-swiss/dsp-api/commit/1b8ffffbff0c55d54788a8b836d2cf0b710f6264))
* Use move for Sipi's file migration ([#2657](https://github.com/dasch-swiss/dsp-api/issues/2657)) ([da0a9b8](https://github.com/dasch-swiss/dsp-api/commit/da0a9b80b579611d4c3d77c66f94c46b0eef0e5d))


### Enhancements

* add dedicated export route and zip trig file DEV-2129 ([#2650](https://github.com/dasch-swiss/dsp-api/issues/2650)) ([12379aa](https://github.com/dasch-swiss/dsp-api/commit/12379aadcc161abfcd4e9ff862005b7a6214d5ce))
* Add new route to migrate files (DEV-1721) ([#2647](https://github.com/dasch-swiss/dsp-api/issues/2647)) ([971136f](https://github.com/dasch-swiss/dsp-api/commit/971136fdf1a9ee23315909dae22533e412baf420))
* Add support for new file structure within Sipi folders and move Sipi tmp directory cleanup to separate route (DEV-1715) ([#2621](https://github.com/dasch-swiss/dsp-api/issues/2621)) ([13e2a6f](https://github.com/dasch-swiss/dsp-api/commit/13e2a6f8581aa01ca2a50b452abd11767ad1202d))


### Maintenance

* add basic metrics to gravsearch routes (DEV-2150) ([#2661](https://github.com/dasch-swiss/dsp-api/issues/2661)) ([0a3cc8b](https://github.com/dasch-swiss/dsp-api/commit/0a3cc8bd78239506e89ed739201358c723977c6c))
* Add integration test for upload_without_processing route ([#2651](https://github.com/dasch-swiss/dsp-api/issues/2651)) ([24910a4](https://github.com/dasch-swiss/dsp-api/commit/24910a4b417f4a22f81cda70044370d44d3b900f))
* **ci:** add actuated ([#2652](https://github.com/dasch-swiss/dsp-api/issues/2652)) ([ab023bb](https://github.com/dasch-swiss/dsp-api/commit/ab023bb57be3ea939df1a9228b6c2c948d3569e2))
* decouple ConstructToConstructTransformer from WhereTransformer ([#2632](https://github.com/dasch-swiss/dsp-api/issues/2632)) ([e351592](https://github.com/dasch-swiss/dsp-api/commit/e3515925da45e2c10cf357ce25d1bcdd03fb39b7))
* extract rest and permission code from ProjectsResponderADM DEV-2106 ([#2644](https://github.com/dasch-swiss/dsp-api/issues/2644)) ([1ba0007](https://github.com/dasch-swiss/dsp-api/commit/1ba00070e088ca3c4e222e65ddea427942ae1b0a))
* Improve KnoraProject model  NO-TICKET ([#2648](https://github.com/dasch-swiss/dsp-api/issues/2648)) ([8d08abd](https://github.com/dasch-swiss/dsp-api/commit/8d08abd7a084a81e84f09a69141d9043a650411f))
* Increase max post size (DEV-2125) ([#2649](https://github.com/dasch-swiss/dsp-api/issues/2649)) ([bc6ffea](https://github.com/dasch-swiss/dsp-api/commit/bc6ffea79fa379fa526b8f30b947fa9bb04967d5))
* make construct transformer a service and move transformers to right package ([#2645](https://github.com/dasch-swiss/dsp-api/issues/2645)) ([4129856](https://github.com/dasch-swiss/dsp-api/commit/41298560ec20ba2fc7d4e7b2a7a37d9e39d71160))
* organize packages, cleanup and minor code improvements  DEV-2124 ([#2646](https://github.com/dasch-swiss/dsp-api/issues/2646)) ([a5b030f](https://github.com/dasch-swiss/dsp-api/commit/a5b030fec4c5c46cfe24a3f8f3895f8d21fef017))
* remove throws from ADM routes (DEV-2137) ([#2654](https://github.com/dasch-swiss/dsp-api/issues/2654)) ([7e24663](https://github.com/dasch-swiss/dsp-api/commit/7e2466358b28429690a2fa1a6c6164aec516f85f))
* remove throws from ListsResponderV1 & OntologyResponderV1 & ProjectsResponderV1 (DEV-1908) ([#2627](https://github.com/dasch-swiss/dsp-api/issues/2627)) ([5eaf212](https://github.com/dasch-swiss/dsp-api/commit/5eaf212095c51029646e4de1efbc3d8807a3f021))
* remove throws from ResourcesResponderV1 (DEV-2101) ([#2635](https://github.com/dasch-swiss/dsp-api/issues/2635)) ([5e72661](https://github.com/dasch-swiss/dsp-api/commit/5e726619d975a333591cbf9b30f857a333d17619))
* streamline db initialization make commands ([#2660](https://github.com/dasch-swiss/dsp-api/issues/2660)) ([82e53ee](https://github.com/dasch-swiss/dsp-api/commit/82e53ee532effc995835ae61aa6249c782f38e44))
* update dependencies ([#2642](https://github.com/dasch-swiss/dsp-api/issues/2642)) ([f4a1809](https://github.com/dasch-swiss/dsp-api/commit/f4a18095826224ae4682ed4d1229b37d2d262b74))
* ziofy UsersRouteADM DEV-2105 ([#2633](https://github.com/dasch-swiss/dsp-api/issues/2633)) ([4bb1770](https://github.com/dasch-swiss/dsp-api/commit/4bb1770e674916d0a8161f96f2deb6bfc5c2ee98))

## [28.1.2](https://github.com/dasch-swiss/dsp-api/compare/v28.1.1...v28.1.2) (2023-04-28)


### Bug Fixes

* querying all projects don't return system projects ([#2607](https://github.com/dasch-swiss/dsp-api/issues/2607)) ([68e88d9](https://github.com/dasch-swiss/dsp-api/commit/68e88d9775a0441d91102e03f687906d30b69fb9))
* search by label produces timeouts if too many resources match (DEV-2043) ([#2591](https://github.com/dasch-swiss/dsp-api/issues/2591)) ([78479d3](https://github.com/dasch-swiss/dsp-api/commit/78479d3dffd233e0e0478c1bfcae9e8006c85171))


### Documentation

* Add subproperty documentation to the cardinalities check (DEV-2039) ([#2603](https://github.com/dasch-swiss/dsp-api/issues/2603)) ([e39cc85](https://github.com/dasch-swiss/dsp-api/commit/e39cc85b237c703286cc4f074d5f645ca9c74fd0))


### Maintenance

* Add make command to init db from dev server (DEV-1989) ([#2574](https://github.com/dasch-swiss/dsp-api/issues/2574)) ([ab6dcce](https://github.com/dasch-swiss/dsp-api/commit/ab6dcce7d7de182d11ee8c4293e381d0d3b82846))
* Extract methods from UpdateResourceMetadataRequestV2#fromJsonLD (NO-TICKET) ([#2626](https://github.com/dasch-swiss/dsp-api/issues/2626)) ([11fc9db](https://github.com/dasch-swiss/dsp-api/commit/11fc9db3f595a5c53ca7cf7212e420995654966c))
* Extract standoff related functions from StringFormatter (NO-TICKET) ([#2609](https://github.com/dasch-swiss/dsp-api/issues/2609)) ([dbfb86e](https://github.com/dasch-swiss/dsp-api/commit/dbfb86e4e90a9245d56a256a69035c5dfaae7259))
* improve export-moving-images-frames ([#2615](https://github.com/dasch-swiss/dsp-api/issues/2615)) ([680f286](https://github.com/dasch-swiss/dsp-api/commit/680f286c7de9df2cc95277ec40d0f0a9e7fd7a43))
* move Gravsearch SPARQL transformers to separate package ([#2614](https://github.com/dasch-swiss/dsp-api/issues/2614)) ([3846c38](https://github.com/dasch-swiss/dsp-api/commit/3846c38174ad1af674de2976314e8aa2a89a157b))
* remove dsp-shared project (DEV-2045) ([#2619](https://github.com/dasch-swiss/dsp-api/issues/2619)) ([772e77c](https://github.com/dasch-swiss/dsp-api/commit/772e77c6ea3a3794269050108bc8b37ce3dbe067))
* remove errorFun from IRI related methods (DEV-1996) ([#2585](https://github.com/dasch-swiss/dsp-api/issues/2585)) ([12670fb](https://github.com/dasch-swiss/dsp-api/commit/12670fb9c4849bd74a200c8c2c42fa522957f3dd))
* remove intermediate interfaces from query transformers (DEV-2077) ([#2610](https://github.com/dasch-swiss/dsp-api/issues/2610)) ([954677b](https://github.com/dasch-swiss/dsp-api/commit/954677b0855f2b96a48d04c8781854b004b17620))
* remove knora explicit graph ([#2623](https://github.com/dasch-swiss/dsp-api/issues/2623)) ([95c55e7](https://github.com/dasch-swiss/dsp-api/commit/95c55e75de52c844b9437a3913b282b8eee2ed28))
* remove more intermediate interfaces (DEV-2078) ([#2612](https://github.com/dasch-swiss/dsp-api/issues/2612)) ([2f33878](https://github.com/dasch-swiss/dsp-api/commit/2f338780085119065fc9595f1aeac743199a8811))
* remove throws from health route and route utils ([#2584](https://github.com/dasch-swiss/dsp-api/issues/2584)) ([fba1b2f](https://github.com/dasch-swiss/dsp-api/commit/fba1b2f530faae482e9d8cba1b4ced57fe110380))
* remove throws from UsersResponderV1 (DEV-2102) ([#2629](https://github.com/dasch-swiss/dsp-api/issues/2629)) ([e31f846](https://github.com/dasch-swiss/dsp-api/commit/e31f8467ac43b40d3b7933b3eccffba03eb24c60))
* remove upload_for_processing route ([#2616](https://github.com/dasch-swiss/dsp-api/issues/2616)) ([4562a4a](https://github.com/dasch-swiss/dsp-api/commit/4562a4a054b99149c4fca3f0722c36f1fe3b8178))
* Remove warnings by using non deprecated zio.logging functions ([#2606](https://github.com/dasch-swiss/dsp-api/issues/2606)) ([04ad6bb](https://github.com/dasch-swiss/dsp-api/commit/04ad6bbd906d2ad2003c9f9eec41b07add4ca29b))
* rename gravsearch related variables ([#2618](https://github.com/dasch-swiss/dsp-api/issues/2618)) ([b975d01](https://github.com/dasch-swiss/dsp-api/commit/b975d01d79e3f17f3e46515f44175edac5af791f))
* rename gravsearch utils (DEV-2074) ([#2608](https://github.com/dasch-swiss/dsp-api/issues/2608)) ([99acc8c](https://github.com/dasch-swiss/dsp-api/commit/99acc8c8ba375ca429997ca0b5bda89e21992ed9))
* rework removing of type annotations (DEV-2079) ([#2611](https://github.com/dasch-swiss/dsp-api/issues/2611)) ([461b0b6](https://github.com/dasch-swiss/dsp-api/commit/461b0b6b89737e2565e70581878de932755c0137))
* streamline gravsearch type inspection ([#2605](https://github.com/dasch-swiss/dsp-api/issues/2605)) ([f6e9eda](https://github.com/dasch-swiss/dsp-api/commit/f6e9eda83a2da86a93f39ce761d629e4dd579aa3))
* streamline SPARQL transformation ([#2624](https://github.com/dasch-swiss/dsp-api/issues/2624)) ([c616581](https://github.com/dasch-swiss/dsp-api/commit/c616581ee0ad51c4a8bd4e1c84b667676deebb9d))
* update dependencies ([#2602](https://github.com/dasch-swiss/dsp-api/issues/2602)) ([474b23c](https://github.com/dasch-swiss/dsp-api/commit/474b23c5faa37e9aaeea6eda679e537d0866ef1f))
* ziofy AuthenticationRouteV2 * ListsRouteV2 * StandoffRouteV2 (DEV-2050) ([#2589](https://github.com/dasch-swiss/dsp-api/issues/2589)) ([cc4b7ba](https://github.com/dasch-swiss/dsp-api/commit/cc4b7ba4100bff70d0b21f40f407af369c494cb2))
* ziofy JsonLDObject accessor methods (DEV-2084) ([#2613](https://github.com/dasch-swiss/dsp-api/issues/2613)) ([666ddcf](https://github.com/dasch-swiss/dsp-api/commit/666ddcf112fb4c6c56afbe9ee46f44dc7f9698b0))
* ziofy OntologiesRouteV2 (DEV-2052) ([#2593](https://github.com/dasch-swiss/dsp-api/issues/2593)) ([c472ff7](https://github.com/dasch-swiss/dsp-api/commit/c472ff73bcdaf77bb2b575474f967c72bdfcd813))
* ziofy ProjectRouteADM (DEV-2104) ([#2630](https://github.com/dasch-swiss/dsp-api/issues/2630)) ([edfb127](https://github.com/dasch-swiss/dsp-api/commit/edfb127ab214552b4e2ffcddf9ce26e60793158c))
* ziofy ResourcesRouteV1 (DEV-2035) ([#2587](https://github.com/dasch-swiss/dsp-api/issues/2587)) ([e36605d](https://github.com/dasch-swiss/dsp-api/commit/e36605d217f3be0e8bdc22d0ab996f92c8cc72ad))
* ziofy ResourcesRouteV2 (DEV-2091) ([#2625](https://github.com/dasch-swiss/dsp-api/issues/2625)) ([7285b3c](https://github.com/dasch-swiss/dsp-api/commit/7285b3c24823707b3f3df3adab9d8c89fe545971))
* ziofy RouteUtilV1 and change deprecated code (DEV-2079) ([#2628](https://github.com/dasch-swiss/dsp-api/issues/2628)) ([3dacbf4](https://github.com/dasch-swiss/dsp-api/commit/3dacbf44052921b1cf5b523716cc2b88ce511900))
* ziofy SearchRouteV2 DEV-2088 ([#2622](https://github.com/dasch-swiss/dsp-api/issues/2622)) ([3105996](https://github.com/dasch-swiss/dsp-api/commit/3105996961c833177d3b385478d66505a2210fe0))
* ziofy ValuesRouteV2 (DEV-2051) ([#2604](https://github.com/dasch-swiss/dsp-api/issues/2604)) ([b3f15db](https://github.com/dasch-swiss/dsp-api/commit/b3f15db66518395b01f5877e510f6d86e9a23547))

## [28.1.1](https://github.com/dasch-swiss/dsp-api/compare/v28.1.0...v28.1.1) (2023-04-13)


### Bug Fixes

* cardinality issues on subclasses after adding cardinalities to superclass (DEV-2026) ([#2572](https://github.com/dasch-swiss/dsp-api/issues/2572)) ([20a243b](https://github.com/dasch-swiss/dsp-api/commit/20a243b699ca99a8815689bf27a10f391297b21f))
* Gravsearch wrongly finds no results, if a query can be optimized down to only negations (DEV-1980) ([#2576](https://github.com/dasch-swiss/dsp-api/issues/2576)) ([0612b9a](https://github.com/dasch-swiss/dsp-api/commit/0612b9a6b76e7959407436eaad50f3761d9d1bec))
* use 400 instead of 500 for invalid input in fulltext search (DEV-1829) ([#2557](https://github.com/dasch-swiss/dsp-api/issues/2557)) ([51acceb](https://github.com/dasch-swiss/dsp-api/commit/51accebd256935fb8fa7df419dc3159155b6dbda))


### Maintenance

* add webhook trigger for DEV deployment to workflow (INFRA-240) ([#2577](https://github.com/dasch-swiss/dsp-api/issues/2577)) ([58304f6](https://github.com/dasch-swiss/dsp-api/commit/58304f68258a3ca2fea29cc2ea1997d163bceb07))
* distribute V2 value functions to proper objects ([#2582](https://github.com/dasch-swiss/dsp-api/issues/2582)) ([fb6cbbc](https://github.com/dasch-swiss/dsp-api/commit/fb6cbbc3930d8e080e49961e1d90e82e2ba801b3))
* fix docker-publish workflow output step failing ([#2590](https://github.com/dasch-swiss/dsp-api/issues/2590)) ([7e29290](https://github.com/dasch-swiss/dsp-api/commit/7e292905e3d676c9e3f816fabb4e15b6c085cc04))
* fix string returned by docker-image-tag containing special control characters ([#2592](https://github.com/dasch-swiss/dsp-api/issues/2592)) ([ebee7d6](https://github.com/dasch-swiss/dsp-api/commit/ebee7d6e04bb38a8c597a4e5be64b00b7045595b))
* pulish sipi images on each commit on main (DEV-1997) ([#2588](https://github.com/dasch-swiss/dsp-api/issues/2588)) ([727911d](https://github.com/dasch-swiss/dsp-api/commit/727911d5b145fb040db6844102b9eddaa7f123b5))
* remove CORS restriction from ZIO-HTTP routes (DEV-2015) ([#2570](https://github.com/dasch-swiss/dsp-api/issues/2570)) ([f94bb82](https://github.com/dasch-swiss/dsp-api/commit/f94bb82ee1446eaccb579b89ef2d3ba22db8b72d))
* remove errorFun from value conversion and extract to ValuesValidator (DEV-1993) ([#2558](https://github.com/dasch-swiss/dsp-api/issues/2558)) ([6cd58e4](https://github.com/dasch-swiss/dsp-api/commit/6cd58e4ff8dbf3fd0d9dd5512aca4e6b1e816e9c))
* remove throwing from V1 Routes Assets, Authentication, Ckan ([#2580](https://github.com/dasch-swiss/dsp-api/issues/2580)) ([7cb0d33](https://github.com/dasch-swiss/dsp-api/commit/7cb0d3312cda771b5f1907840d05904156f090b9))
* remove throwing in V1 Lists Route (DEV-2033) ([#2579](https://github.com/dasch-swiss/dsp-api/issues/2579)) ([c64a41f](https://github.com/dasch-swiss/dsp-api/commit/c64a41fac8e06ebd3776c1b2a63eb945fb9f3150))
* remove throwing in V1 Projects Route (DEV-2037) ([#2581](https://github.com/dasch-swiss/dsp-api/issues/2581)) ([6b8bfc0](https://github.com/dasch-swiss/dsp-api/commit/6b8bfc06f86547f88f499484691d49e0ce4915b1))
* remove throws from V1 Search Responder(DEV-2038) ([#2583](https://github.com/dasch-swiss/dsp-api/issues/2583)) ([2e6361f](https://github.com/dasch-swiss/dsp-api/commit/2e6361f48a135f5b87e573dda5365407e61a2364))
* Return BadRequest in v1 values API if an expected parameter… ([#2561](https://github.com/dasch-swiss/dsp-api/issues/2561)) ([4a5a838](https://github.com/dasch-swiss/dsp-api/commit/4a5a838a94dcc51ddac458322bab93df70f60e38))
* update dependencies ([#2569](https://github.com/dasch-swiss/dsp-api/issues/2569)) ([644e085](https://github.com/dasch-swiss/dsp-api/commit/644e0856ffdd7a63cef1e5ad755b1cc00db418ab))
* upgrade Sipi base image to 2.8.1 DEV-2019 ([#2586](https://github.com/dasch-swiss/dsp-api/issues/2586)) ([8d88659](https://github.com/dasch-swiss/dsp-api/commit/8d886599d8179aedfa2b19ab6b14a765d114e8a4))
* ziofy ResourceTypesRouteV1 (DEV-2034) ([#2578](https://github.com/dasch-swiss/dsp-api/issues/2578)) ([31a8206](https://github.com/dasch-swiss/dsp-api/commit/31a8206a63217542707a23e673418b88ab882d36))
* ziofy StandoffRouteV1 (DEV-2031) ([#2575](https://github.com/dasch-swiss/dsp-api/issues/2575)) ([d739dfe](https://github.com/dasch-swiss/dsp-api/commit/d739dfedc8f4cae31daf36823c5740d2d0698bf3))
* ziofy UserRouteV1 (DEV-2028) ([#2573](https://github.com/dasch-swiss/dsp-api/issues/2573)) ([222f757](https://github.com/dasch-swiss/dsp-api/commit/222f757782af7dd8d00cb04486af305d30e0bd28))
* ziofy ValuesRouteV1 (DEV-2012) ([#2559](https://github.com/dasch-swiss/dsp-api/issues/2559)) ([0798fef](https://github.com/dasch-swiss/dsp-api/commit/0798fef04618781540140785bb58eabb52e5e624))

## [28.1.0](https://github.com/dasch-swiss/dsp-api/compare/v28.0.0...v28.1.0) (2023-03-29)


### Bug Fixes

* Allow setting a cardinality in a three tier class hierarchy (DEV-1927) ([#2542](https://github.com/dasch-swiss/dsp-api/issues/2542)) ([0dc76f0](https://github.com/dasch-swiss/dsp-api/commit/0dc76f00c0fd0003f387004114d1e8cc75a2d840))
* Apply correct DAOP after changeing them (remove its caching) DEV-1965 ([#2551](https://github.com/dasch-swiss/dsp-api/issues/2551)) ([72e5f92](https://github.com/dasch-swiss/dsp-api/commit/72e5f92ef007675158262d4a949f89f6be7111f7))


### Enhancements

* add separate route for local processing of files through sipi ([#2519](https://github.com/dasch-swiss/dsp-api/issues/2519)) ([042f806](https://github.com/dasch-swiss/dsp-api/commit/042f8063b7f7ad5b0b04c5052bf70ea91dc8b689))


### Documentation

* update Project IRI documentation (DEV-1786) ([#2539](https://github.com/dasch-swiss/dsp-api/issues/2539)) ([230fca5](https://github.com/dasch-swiss/dsp-api/commit/230fca50736668e5324c7e671e1d57ff4ca750ce))


### Maintenance

* add a make target for parametrised database dump and upload ([#2541](https://github.com/dasch-swiss/dsp-api/issues/2541)) ([94c38f2](https://github.com/dasch-swiss/dsp-api/commit/94c38f211122532320d3e0d9a5d7b723d9fa36e3))
* add docker container healthcheck for dsp-api (INFRA-93) ([#2549](https://github.com/dasch-swiss/dsp-api/issues/2549)) ([8bdc160](https://github.com/dasch-swiss/dsp-api/commit/8bdc160d268df38ead2c6c9c174839551ebed6ff))
* **CI:** pull all tags when checking out ([#2532](https://github.com/dasch-swiss/dsp-api/issues/2532)) ([fc3a891](https://github.com/dasch-swiss/dsp-api/commit/fc3a89161fcd8be4dd6ace823a42af8b13212caa))
* Publish a docker container for webapi and sipi on each commit t… ([#2553](https://github.com/dasch-swiss/dsp-api/issues/2553)) ([b6f2590](https://github.com/dasch-swiss/dsp-api/commit/b6f259055249705194cc50fda871786ed257b41e))
* Remove dead ZIOs from ProjectResponderADM and introduce project services (DEV-1998) ([#2534](https://github.com/dasch-swiss/dsp-api/issues/2534)) ([b5619c2](https://github.com/dasch-swiss/dsp-api/commit/b5619c2a17a3bf43115360c656b76fc8f81e7fc0))
* remove deprecated fuseki config ([#2552](https://github.com/dasch-swiss/dsp-api/issues/2552)) ([f6826fb](https://github.com/dasch-swiss/dsp-api/commit/f6826fb7c0832af8ad5a36eb6f4796e7448177da))
* Remove publishing sipi docker image because this build is broken (DEV-1848) ([#2556](https://github.com/dasch-swiss/dsp-api/issues/2556)) ([71f75fa](https://github.com/dasch-swiss/dsp-api/commit/71f75fa4ab669bad79f7125e974beb1cba3a4313))
* Remove unused code related to ziofied Responders (DEV-1958) ([#2543](https://github.com/dasch-swiss/dsp-api/issues/2543)) ([f40d6cb](https://github.com/dasch-swiss/dsp-api/commit/f40d6cbebc9c8d9f15be5db623df93349c64b173))
* run integration tests against akka and zio routes (DEV-1585) ([#2545](https://github.com/dasch-swiss/dsp-api/issues/2545)) ([e730ba4](https://github.com/dasch-swiss/dsp-api/commit/e730ba464e0530674c0b11dd5b11679d43fd0797))
* **StringFormatter:** move values related methods to separate file removing errorFun (DEV-1905) ([#2550](https://github.com/dasch-swiss/dsp-api/issues/2550)) ([ddcf912](https://github.com/dasch-swiss/dsp-api/commit/ddcf912ac03670b66ce236d94d768421dfe32b2b))
* ziofy Authenticator (DEV-1926) ([#2540](https://github.com/dasch-swiss/dsp-api/issues/2540)) ([9878529](https://github.com/dasch-swiss/dsp-api/commit/9878529653ad9f235e70264d149c10e9d4361c8f))
* ziofy ResourceUtilV1 (DEV-1967) ([#2546](https://github.com/dasch-swiss/dsp-api/issues/2546)) ([c6e0805](https://github.com/dasch-swiss/dsp-api/commit/c6e080538782fb7aa0b7c36fb18757bf7082acd8))
* ziofy ResourceUtilV2 (DEV-1963) ([#2544](https://github.com/dasch-swiss/dsp-api/issues/2544)) ([89eb033](https://github.com/dasch-swiss/dsp-api/commit/89eb0334f0f651dccadd700fb86d99bdfaa73fd0))
* ziofy RouteUtilADM (DEV-1968) ([#2547](https://github.com/dasch-swiss/dsp-api/issues/2547)) ([00b7070](https://github.com/dasch-swiss/dsp-api/commit/00b7070d5be417764e0db611a9bd509971de83ad))
* ziofy SearchResponderV2 and Gravsearch (DEV-1755) ([#2538](https://github.com/dasch-swiss/dsp-api/issues/2538)) ([e708fc3](https://github.com/dasch-swiss/dsp-api/commit/e708fc3179c2dd071a64c3d423ecf88ccdbadf69))
* ziofy ValuesResponderV2 (DEV-1757) ([#2536](https://github.com/dasch-swiss/dsp-api/issues/2536)) ([ca6f97a](https://github.com/dasch-swiss/dsp-api/commit/ca6f97af7feded2b50c23ddef0a000b5dc6936b7))

## [28.0.0](https://github.com/dasch-swiss/dsp-api/compare/v27.1.0...v28.0.0) (2023-03-17)


### ⚠ BREAKING CHANGES

* Allow special characters in full-text search (DEV-1712) ([#2441](https://github.com/dasch-swiss/dsp-api/issues/2441))

### Bug Fixes

* Allow special characters in full-text search (DEV-1712) ([#2441](https://github.com/dasch-swiss/dsp-api/issues/2441)) ([b3148a0](https://github.com/dasch-swiss/dsp-api/commit/b3148a0d47e467d244e51ead35a160721035687d))
* deleted properties in cardinalities should be included in the count query (DEV-1878) ([#2530](https://github.com/dasch-swiss/dsp-api/issues/2530)) ([9ae80fd](https://github.com/dasch-swiss/dsp-api/commit/9ae80fd83f9833256a43f1ecb84c78d1632b68c3))
* doSipiPostUpdate to evaluate provided task only once (NO-TICKET) ([#2521](https://github.com/dasch-swiss/dsp-api/issues/2521)) ([a3639c1](https://github.com/dasch-swiss/dsp-api/commit/a3639c129b1ca08701776b5a6ee4ff9b6082c31d))
* Invalidate the cache when changing the password (DEV-1814) ([#2511](https://github.com/dasch-swiss/dsp-api/issues/2511)) ([1cf4727](https://github.com/dasch-swiss/dsp-api/commit/1cf4727495fb6088addf6ae32c2d9eae1e602cf1))
* Replace invalid character in usernames ([#2510](https://github.com/dasch-swiss/dsp-api/issues/2510)) ([031c744](https://github.com/dasch-swiss/dsp-api/commit/031c7449220f0c7374167927af042d1c454066b7))
* Slow Gravsearch Queries are not being logged (DEV-1838) ([#2522](https://github.com/dasch-swiss/dsp-api/issues/2522)) ([fb254ad](https://github.com/dasch-swiss/dsp-api/commit/fb254ad400ceaa9513b05f1b556dd403ff8104d7))


### Documentation

* minor improvements to permissions documentation ([#2520](https://github.com/dasch-swiss/dsp-api/issues/2520)) ([e434f2c](https://github.com/dasch-swiss/dsp-api/commit/e434f2cdd95505d1eb2c4e51c8bfe89c71e13d90))


### Enhancements

* Add all instances which violate the new cardinality to response (DEV-1861) ([#2523](https://github.com/dasch-swiss/dsp-api/issues/2523)) ([5f22100](https://github.com/dasch-swiss/dsp-api/commit/5f22100eeaca5a9d7042533ddcd296af043e938a))
* Add new upload route to Sipi without processing (DEV-1700) ([#2457](https://github.com/dasch-swiss/dsp-api/issues/2457)) ([3cacc76](https://github.com/dasch-swiss/dsp-api/commit/3cacc76b84d199bc6f54e4b8e726fa5d0c4be2b5))
* **sipi:** add support for ODD and RNG file formats (DEV-1271) ([#2197](https://github.com/dasch-swiss/dsp-api/issues/2197)) ([4441035](https://github.com/dasch-swiss/dsp-api/commit/44410357838f0ab6248f286829313c88ebbdf397))


### Maintenance

* add JSON logging (DEV-931) ([#2506](https://github.com/dasch-swiss/dsp-api/issues/2506)) ([f3bbce3](https://github.com/dasch-swiss/dsp-api/commit/f3bbce3661d8cb9fbbef1729129743e6b9656ea9))
* add logger name to text logging (DEV-1826) ([#2514](https://github.com/dasch-swiss/dsp-api/issues/2514)) ([bcf83d2](https://github.com/dasch-swiss/dsp-api/commit/bcf83d2dfc714a794a15935abc9c675bb68bfe34))
* bump SIPI version (DEV-1797) ([#2507](https://github.com/dasch-swiss/dsp-api/issues/2507)) ([7e2dcd3](https://github.com/dasch-swiss/dsp-api/commit/7e2dcd3e29bd26375770284b78f8e463080aea0d))
* cleanup StringFormatter (NO-TICKET) ([#2517](https://github.com/dasch-swiss/dsp-api/issues/2517)) ([ca418aa](https://github.com/dasch-swiss/dsp-api/commit/ca418aa152c1f32bd2db9feded1ea07fa512050e))
* CORS fails if allowed origins contain upper case letter ([#2505](https://github.com/dasch-swiss/dsp-api/issues/2505)) ([de7337a](https://github.com/dasch-swiss/dsp-api/commit/de7337a71db353612dfe334261f2437bc98b6f95))
* remove dsp-main (NO-TICKET) ([#2513](https://github.com/dasch-swiss/dsp-api/issues/2513)) ([d7f2f19](https://github.com/dasch-swiss/dsp-api/commit/d7f2f196d5ed27227c6e955e3fa7a994a1dc7c27))
* Remove needless logging of all requests to /admin/projects (NO-TICKET) ([#2529](https://github.com/dasch-swiss/dsp-api/issues/2529)) ([3a01d87](https://github.com/dasch-swiss/dsp-api/commit/3a01d87c47158623fe007c5f18515713fc3acf9c))
* remove unused subprojects (NO-TICKET) ([#2531](https://github.com/dasch-swiss/dsp-api/issues/2531)) ([45b7632](https://github.com/dasch-swiss/dsp-api/commit/45b7632dea3b8c7ccc37b64ab9762c4a22f3d163))
* remove zio die from triplestore (NO-TICKET) ([#2509](https://github.com/dasch-swiss/dsp-api/issues/2509)) ([d5e0076](https://github.com/dasch-swiss/dsp-api/commit/d5e007699e833a1e1594e4673aa26bd4d3cd61b7))
* Replace CacheServiceManager (DEV-1798) ([#2503](https://github.com/dasch-swiss/dsp-api/issues/2503)) ([843d31d](https://github.com/dasch-swiss/dsp-api/commit/843d31d088b39731ac2fa32c8d03153cbc9480a7))
* Replace IIIFServiceManager (DEV-1799) ([#2502](https://github.com/dasch-swiss/dsp-api/issues/2502)) ([22e456b](https://github.com/dasch-swiss/dsp-api/commit/22e456b715e5bdb1f01c6427eff011196c12d93e))
* Replace TriplestoreServiceManager (DEV-1800) ([#2501](https://github.com/dasch-swiss/dsp-api/issues/2501)) ([211b601](https://github.com/dasch-swiss/dsp-api/commit/211b6019a6caf58448608d354b6232fba727eccc))
* ziofy Cache (DEV-1824) ([#2512](https://github.com/dasch-swiss/dsp-api/issues/2512)) ([1daeb55](https://github.com/dasch-swiss/dsp-api/commit/1daeb555a7eec8e30a7b5b06df11e1b98f3eaca9))
* ziofy CardinalityHandler and OntologyHelpers (NO-TICKET) ([#2500](https://github.com/dasch-swiss/dsp-api/issues/2500)) ([d6fa2c0](https://github.com/dasch-swiss/dsp-api/commit/d6fa2c03f98f0c38dfeea1c9f279581a43012e82))
* ziofy ontology responder v2 (DEV-1753) ([#2515](https://github.com/dasch-swiss/dsp-api/issues/2515)) ([3ac2379](https://github.com/dasch-swiss/dsp-api/commit/3ac2379951ac48a1508cae00854f8f39bdec255c))
* ziofy ResourcesResponderV1 (DEV-1747) ([#2486](https://github.com/dasch-swiss/dsp-api/issues/2486)) ([00ddc62](https://github.com/dasch-swiss/dsp-api/commit/00ddc624df0cc3c60cdf3d265a444bafc72b038a))
* ziofy ResourcesResponderV2 (DEV-1754) ([#2518](https://github.com/dasch-swiss/dsp-api/issues/2518)) ([e25284d](https://github.com/dasch-swiss/dsp-api/commit/e25284d48ff33135bc2fb1846c70eddbeba262c9))
* ziofy StandoffResponderV2 (DEV-1756) ([#2498](https://github.com/dasch-swiss/dsp-api/issues/2498)) ([402ea3f](https://github.com/dasch-swiss/dsp-api/commit/402ea3f984c3287f3b3281de5d65adbe4e87b278))

## [27.1.0](https://github.com/dasch-swiss/dsp-api/compare/v27.0.0...v27.1.0) (2023-03-03)


### Bug Fixes

* Filter out deleted subjects and objects when counting for cardinalities (DEV-1795) ([#2499](https://github.com/dasch-swiss/dsp-api/issues/2499)) ([60e1833](https://github.com/dasch-swiss/dsp-api/commit/60e183323762cf02e556d6257b05315e2682e90f))


### Documentation

* add high level overview of the current domain entities to the documentation (DEV-1416) ([#2431](https://github.com/dasch-swiss/dsp-api/issues/2431)) ([02db5ce](https://github.com/dasch-swiss/dsp-api/commit/02db5ce908b4ea516b302b1a9bd69062d94197b2))
* add missing documentation (DEV-1422) ([#2482](https://github.com/dasch-swiss/dsp-api/issues/2482)) ([9667f7a](https://github.com/dasch-swiss/dsp-api/commit/9667f7a9d59234ec9bdedc96d91df220dfa8f93f))


### Enhancements

* Add context for can set cardinalities failed responses (DEV-1768) ([#2471](https://github.com/dasch-swiss/dsp-api/issues/2471)) ([16c137e](https://github.com/dasch-swiss/dsp-api/commit/16c137e7065e5958620f4a1bf47152829701aad7))


### Maintenance

* Add MessageRelay as a preparation for enabling us to migrate responders to ZIO DEV-1728 ([#2453](https://github.com/dasch-swiss/dsp-api/issues/2453)) ([c61368a](https://github.com/dasch-swiss/dsp-api/commit/c61368a311992e05b9addc9040f4ebaf89db231f))
* add missing github-actions workflow ([#2463](https://github.com/dasch-swiss/dsp-api/issues/2463)) ([8983c95](https://github.com/dasch-swiss/dsp-api/commit/8983c954f73994a94231c6e9709633f47a011b26))
* improve finding all graphs ([#2470](https://github.com/dasch-swiss/dsp-api/issues/2470)) ([6de3c5d](https://github.com/dasch-swiss/dsp-api/commit/6de3c5d10312ae5b8e0de45f3bf392f078dfcbdd))
* Integrate zio-fied project responder in zio route DEV-1728 ([#2460](https://github.com/dasch-swiss/dsp-api/issues/2460)) ([1ed0175](https://github.com/dasch-swiss/dsp-api/commit/1ed0175a74319cb482271cc81903def854aca292))
* log request and user ID as log annotations (DEV-1233) ([#2466](https://github.com/dasch-swiss/dsp-api/issues/2466)) ([bec6fe6](https://github.com/dasch-swiss/dsp-api/commit/bec6fe68d4490d9110b0f68807ce065fe4ea1ef6))
* Move key frames extraction from store.lua to upload.lua (DEV-1716) ([#2454](https://github.com/dasch-swiss/dsp-api/issues/2454)) ([34924e2](https://github.com/dasch-swiss/dsp-api/commit/34924e213e83dac99d7ab023d5f5c0f8b591a8fc))
* update dependencies ([#2497](https://github.com/dasch-swiss/dsp-api/issues/2497)) ([d20b9ff](https://github.com/dasch-swiss/dsp-api/commit/d20b9ff7c1e5a1c95a1cffbfd1bb1c02d14d98c7))
* update SIPI version and adjust Lua scripts (DEV-1727) ([#2462](https://github.com/dasch-swiss/dsp-api/issues/2462)) ([9884539](https://github.com/dasch-swiss/dsp-api/commit/98845390c0bebb6727d83ed5fef228cc813ba3e7))
* ziofy CkanResponderV1  DEV-1743 ([#2473](https://github.com/dasch-swiss/dsp-api/issues/2473)) ([5a2d16c](https://github.com/dasch-swiss/dsp-api/commit/5a2d16c5dc3fab045f6332aae0d6c60adf974449))
* ziofy CkanResponderV1 & ListsResponderV1 (DEV-1743 DEV-1744) ([#2474](https://github.com/dasch-swiss/dsp-api/issues/2474)) ([34c2d95](https://github.com/dasch-swiss/dsp-api/commit/34c2d95a795edd8e164dc4a8120a2d630698959a))
* ziofy GroupsResponderADM DEV-1737 ([#2461](https://github.com/dasch-swiss/dsp-api/issues/2461)) ([44c5d46](https://github.com/dasch-swiss/dsp-api/commit/44c5d46792c72ffe539c7bf41189fbd3be209c48))
* ziofy ListsResponderADM (DEV-1738) ([#2467](https://github.com/dasch-swiss/dsp-api/issues/2467)) ([b5bd82b](https://github.com/dasch-swiss/dsp-api/commit/b5bd82bece212737db7c1e2c022ac50e0e32e710))
* ziofy ListsResponderV2 (DEV-1752) ([#2479](https://github.com/dasch-swiss/dsp-api/issues/2479)) ([28d5b48](https://github.com/dasch-swiss/dsp-api/commit/28d5b48a795380045397e045e5ebc498febbb4da))
* ziofy OntologyResponderV1 (DEV-1745) ([#2475](https://github.com/dasch-swiss/dsp-api/issues/2475)) ([f8344ff](https://github.com/dasch-swiss/dsp-api/commit/f8344fff6fbe4f602a9f3250a22c8ba4572a6673))
* ziofy PermissionsResponderADM  DEV-1739 ([#2468](https://github.com/dasch-swiss/dsp-api/issues/2468)) ([ba85b94](https://github.com/dasch-swiss/dsp-api/commit/ba85b94f787e529598bd929d043ec45bff0d3c73))
* Ziofy ProjectsResponderADM DEV-1728 ([#2459](https://github.com/dasch-swiss/dsp-api/issues/2459)) ([69c0640](https://github.com/dasch-swiss/dsp-api/commit/69c06403de863528822267bfadfbf4a5b29196cf))
* ziofy ProjectsResponderV1 (DEV-1746) ([#2476](https://github.com/dasch-swiss/dsp-api/issues/2476)) ([715c117](https://github.com/dasch-swiss/dsp-api/commit/715c117258668165a8e67b62576b4310d5c322ca))
* ziofy ResourceUtilV2 & PermissionUtilADM (NO-TICKET) ([#2485](https://github.com/dasch-swiss/dsp-api/issues/2485)) ([e2dcd43](https://github.com/dasch-swiss/dsp-api/commit/e2dcd43742d8e233394214608a5fee50c71a526e))
* ziofy SearchResponderV1 (DEV-1748) ([#2480](https://github.com/dasch-swiss/dsp-api/issues/2480)) ([7711a47](https://github.com/dasch-swiss/dsp-api/commit/7711a473d9c12aec58e6868445f1edbf44b35145))
* ziofy SipiResponderADM DEV-1740 ([#2469](https://github.com/dasch-swiss/dsp-api/issues/2469)) ([fcfdc39](https://github.com/dasch-swiss/dsp-api/commit/fcfdc39751928063804c1621160e17c9f0789141))
* ziofy StandoffResponderV1 (DEV-1749) ([#2495](https://github.com/dasch-swiss/dsp-api/issues/2495)) ([6a13eb0](https://github.com/dasch-swiss/dsp-api/commit/6a13eb0f27f19755964d16fe02711c7f7890cb5f))
* ziofy StandoffTagUtilV2 (NO-TICKET) ([#2481](https://github.com/dasch-swiss/dsp-api/issues/2481)) ([1daf98b](https://github.com/dasch-swiss/dsp-api/commit/1daf98bbc7840e992ac67b2dd5666bd815067553))
* ziofy StoresResponderADM DEV-1741 ([#2472](https://github.com/dasch-swiss/dsp-api/issues/2472)) ([991d5b9](https://github.com/dasch-swiss/dsp-api/commit/991d5b92e2d685b0a0432bcea5ea265f75728521))
* ziofy UsersResponderADM DEV-1742 ([#2465](https://github.com/dasch-swiss/dsp-api/issues/2465)) ([208087e](https://github.com/dasch-swiss/dsp-api/commit/208087ed48d427d6a9d04e31ef05e67b4decc9c3))
* ziofy UsersResponderV1 (DEV-1750) ([#2478](https://github.com/dasch-swiss/dsp-api/issues/2478)) ([e8112f8](https://github.com/dasch-swiss/dsp-api/commit/e8112f860c8d564bff939e932d92abc3658d4733))
* ziofy ValuesResponderV1 (DEV-1751) ([#2496](https://github.com/dasch-swiss/dsp-api/issues/2496)) ([9b68e89](https://github.com/dasch-swiss/dsp-api/commit/9b68e891bc62395eefbe5fef84bd7b69a51fbdb5))
* ziofy ValueUtilV1 (NO-TICKET) ([#2484](https://github.com/dasch-swiss/dsp-api/issues/2484)) ([4f38ac2](https://github.com/dasch-swiss/dsp-api/commit/4f38ac269fa3259cf0c4266aba662db4071067c8))

## [27.0.0](https://github.com/dasch-swiss/dsp-api/compare/v26.2.0...v27.0.0) (2023-02-16)


### ⚠ BREAKING CHANGES

* return empty list instead of an error on GET /admin/groups route (DEV-1599) ([#2439](https://github.com/dasch-swiss/dsp-api/issues/2439))

### Bug Fixes

* **CORS:** explicitly assign allowed CORS methods ([#2443](https://github.com/dasch-swiss/dsp-api/issues/2443)) ([99fe6fa](https://github.com/dasch-swiss/dsp-api/commit/99fe6facd7bfa3146b719a62c53aaadc80794e90))
* fix JVM metrics and logging DEV-1639 ([#2426](https://github.com/dasch-swiss/dsp-api/issues/2426)) ([97eb0fc](https://github.com/dasch-swiss/dsp-api/commit/97eb0fcf142be7b3c8427bfe2dfd8e7c580391b1))
* return empty list instead of an error on GET /admin/groups route (DEV-1599) ([#2439](https://github.com/dasch-swiss/dsp-api/issues/2439)) ([f966f7c](https://github.com/dasch-swiss/dsp-api/commit/f966f7c9b98ad4d04ad127dea84a8650ac2a4d6c))


### Enhancements

* expose GET /admin/projects/[ iri | shortname | shortcode ]/{iri | shortname | shortcode }/admin-members as ZIO HTTP route (DEV-1587) ([#2423](https://github.com/dasch-swiss/dsp-api/issues/2423)) ([d7c2cd6](https://github.com/dasch-swiss/dsp-api/commit/d7c2cd66615d7f657dcfea2145646fe0b61dd7c5))
* expose GET /admin/projects/[ iri | shortname | shortcode ]/{iri | shortname | shortcode }/members as ZIO HTTP route (DEV-1587)  ([#2422](https://github.com/dasch-swiss/dsp-api/issues/2422)) ([b5300b5](https://github.com/dasch-swiss/dsp-api/commit/b5300b5cf25eea28f873a13de49228ee177383e0))
* expose GET /admin/projects/[iri | shortname | shortcode]/{projectIri | shortname | shortcode}/RestrictedViewSettings as ZIO HTTP route (DEV-1587)  ([#2428](https://github.com/dasch-swiss/dsp-api/issues/2428)) ([8080951](https://github.com/dasch-swiss/dsp-api/commit/8080951d7f5a38cdea780d68b009ef0e96cca462))
* expose GET /admin/projects/iri/{projectIri}/Keywords as ZIO HTTP route (DEV-1587)  ([#2425](https://github.com/dasch-swiss/dsp-api/issues/2425)) ([3b86834](https://github.com/dasch-swiss/dsp-api/commit/3b86834471af5d1e73cc75446962dc2f02bf9f28))
* expose GET /admin/projects/Keywords as ZIO HTTP route (DEV-1587)  ([#2424](https://github.com/dasch-swiss/dsp-api/issues/2424)) ([39607a2](https://github.com/dasch-swiss/dsp-api/commit/39607a2bc52ed2de38706d3f2cbd6a0a5292a83f))


### Documentation

* fix broken links in docs and remove unused files ([#2433](https://github.com/dasch-swiss/dsp-api/issues/2433)) ([34df59d](https://github.com/dasch-swiss/dsp-api/commit/34df59dc290921fa570534e3d0ecc9e58566fec9))
* replace/canset cardinality documentation (DEV-1564 & DEV-1563) ([#2420](https://github.com/dasch-swiss/dsp-api/issues/2420)) ([adf1a34](https://github.com/dasch-swiss/dsp-api/commit/adf1a34b99f5b6abf9a85fb840e65343fb49b79d))


### Maintenance

* add 0.0.0.0 to allowed origins in config ([#2430](https://github.com/dasch-swiss/dsp-api/issues/2430)) ([9afd7a0](https://github.com/dasch-swiss/dsp-api/commit/9afd7a0d55360feea368bbf64048231ca6ac8ac9))
* add complete in-memory triple store implementation (DEV-628) ([#2432](https://github.com/dasch-swiss/dsp-api/issues/2432)) ([708c217](https://github.com/dasch-swiss/dsp-api/commit/708c21796a76cafab7ffe3f328af8c860fddf70b))
* Add more tests for the ZIO HTTP routes (DEV-1695) ([#2419](https://github.com/dasch-swiss/dsp-api/issues/2419)) ([84e2ead](https://github.com/dasch-swiss/dsp-api/commit/84e2ead596e57844e0647e0a1e0d881d7fe324a2))
* Clean-up ZIO HTTP routes and related code ([#2429](https://github.com/dasch-swiss/dsp-api/issues/2429)) ([1684718](https://github.com/dasch-swiss/dsp-api/commit/1684718149b48b889ee2e0498feb8990a664c5fd))
* cleanup remove unused shacl and redundant StringFormatter setup ([#2438](https://github.com/dasch-swiss/dsp-api/issues/2438)) ([293f6a3](https://github.com/dasch-swiss/dsp-api/commit/293f6a368c8993a8e7ebd51141eb5af16ded9485))
* **instrumentation:** expose ZIO-HTTP metrics (DEV-1714) ([#2452](https://github.com/dasch-swiss/dsp-api/issues/2452)) ([a76b6f9](https://github.com/dasch-swiss/dsp-api/commit/a76b6f9cd53be1b26019f35b66c1529ed54106e2))
* Rename ITTestDataFactory ([#2440](https://github.com/dasch-swiss/dsp-api/issues/2440)) ([dc8b4b5](https://github.com/dasch-swiss/dsp-api/commit/dc8b4b5aa15177795c73870b430f99c82b4677c3))
* update PR template and GH release action ([#2427](https://github.com/dasch-swiss/dsp-api/issues/2427)) ([65180ef](https://github.com/dasch-swiss/dsp-api/commit/65180eff2794af7101a21684fb2b67e4d168c158))

## [26.2.0](https://github.com/dasch-swiss/dsp-api/compare/v26.1.0...v26.2.0) (2023-02-02)


### Bug Fixes

* Search by label returns an Error when searching with a slash (DEV-1656) ([#2406](https://github.com/dasch-swiss/dsp-api/issues/2406)) ([bb02464](https://github.com/dasch-swiss/dsp-api/commit/bb0246468b43e2d42d33a95cd3d7405610aa9b33))
* Test file issue ([#2418](https://github.com/dasch-swiss/dsp-api/issues/2418)) ([78612e0](https://github.com/dasch-swiss/dsp-api/commit/78612e05fa2f43d007eb23f50a08bfb8a2502a3c))


### Maintenance

* cleanup Cache class, ie. scaladoc, renaming, code improvements ([#2411](https://github.com/dasch-swiss/dsp-api/issues/2411)) ([5efa7ac](https://github.com/dasch-swiss/dsp-api/commit/5efa7ac23747385ffbe159e34e3d127bec16be96))
* **deps:** change schedule of dependency updates check ([#2414](https://github.com/dasch-swiss/dsp-api/issues/2414)) ([a5c7a38](https://github.com/dasch-swiss/dsp-api/commit/a5c7a38449e3814a93281c93dd754bcddc87a2f2))
* **deps:** update scalafmt-core, kamon-core, kamon-scala-future ([#2412](https://github.com/dasch-swiss/dsp-api/issues/2412)) ([a02408a](https://github.com/dasch-swiss/dsp-api/commit/a02408a5a6eb45ae5dd353c7d6b9be48e176f331))
* enable publishing docker image in both arm64 and amd64 architectures (DEV-1684) ([#2410](https://github.com/dasch-swiss/dsp-api/issues/2410)) ([f224b24](https://github.com/dasch-swiss/dsp-api/commit/f224b24c768fb82cd050457085ffc798e513e276))
* rename ReplaceCardinalitiesRequestV2, remove old code, simplify and extract methods in OntologyResponder ([#2389](https://github.com/dasch-swiss/dsp-api/issues/2389)) ([5a4f4b6](https://github.com/dasch-swiss/dsp-api/commit/5a4f4b66e73d7655af52a269b341ea394cb6cfca))
* Replace Cardinality isStricterThan with isIncludedIn ([#2405](https://github.com/dasch-swiss/dsp-api/issues/2405)) ([229b362](https://github.com/dasch-swiss/dsp-api/commit/229b362f021c477a66cd6c7e63074195e4853ac0))
* update Scala to 2.13.10 ([#2415](https://github.com/dasch-swiss/dsp-api/issues/2415)) ([d501f59](https://github.com/dasch-swiss/dsp-api/commit/d501f59b59c62bee5016f0da7e1136e76b163959))
* upgrade dependencies ([#2404](https://github.com/dasch-swiss/dsp-api/issues/2404)) ([0d78030](https://github.com/dasch-swiss/dsp-api/commit/0d780304904e00cb4eeaf711f5872daf464a8eef))


### Enhancements

* add CORS to ZIO-HTTP routes (DEV-1619) ([#2390](https://github.com/dasch-swiss/dsp-api/issues/2390)) ([8dad4b2](https://github.com/dasch-swiss/dsp-api/commit/8dad4b2dd1dac41dd8a2c14e1ba609ac707f6e4e))
* allow setting a cardinality given the count in the persisted data is compatible  DEV-1563 ([#2416](https://github.com/dasch-swiss/dsp-api/issues/2416)) ([789bdd1](https://github.com/dasch-swiss/dsp-api/commit/789bdd1ffe0996feb0a3f6023173e9bb29c689ac))
* Allow setting new Cardinalities if they are more restrictive than the respective Cardinalities of a possibly existing super class ([#2397](https://github.com/dasch-swiss/dsp-api/issues/2397)) ([dbde740](https://github.com/dasch-swiss/dsp-api/commit/dbde740314dfedfc4fa7e6d52d02d31a80ecdc35))
* expose GET /admin/projects/iri/{project_iri}/allData as ZIO HTTP route (DEV-1587)  ([#2413](https://github.com/dasch-swiss/dsp-api/issues/2413)) ([eefaf62](https://github.com/dasch-swiss/dsp-api/commit/eefaf62eeb32aff4632bb7395f0a4b25f8fbf2b2))
* expose PUT /admin/projects/iri/{project_iri} as ZIO HTTP route (DEV-1587) ([#2394](https://github.com/dasch-swiss/dsp-api/issues/2394)) ([a832868](https://github.com/dasch-swiss/dsp-api/commit/a832868ef6d142878baa9f3848594235fed399c4))

## [26.1.0](https://github.com/dasch-swiss/dsp-api/compare/v26.0.0...v26.1.0) (2023-01-19)


### Bug Fixes

* API starts up and reports healthy despite failing to load ontologies ([#2363](https://github.com/dasch-swiss/dsp-api/issues/2363)) ([1696f7d](https://github.com/dasch-swiss/dsp-api/commit/1696f7dd51c0cb5f904cb37f0d68cd234fc60374))


### Enhancements

* Add check for can a cardinality be set for specific class and property ([#2382](https://github.com/dasch-swiss/dsp-api/issues/2382)) ([17e7064](https://github.com/dasch-swiss/dsp-api/commit/17e7064987194dd3232a389b05c785ec77eec423))
* Add mimetype image/jpx as accepted ([#2378](https://github.com/dasch-swiss/dsp-api/issues/2378)) ([d590e38](https://github.com/dasch-swiss/dsp-api/commit/d590e38e80bfa88c1c0c5493b2bc93d2d19ee071))
* expose DELETE /admin/projects as ZIO HTTP route (DEV-1587)  ([#2386](https://github.com/dasch-swiss/dsp-api/issues/2386)) ([6059012](https://github.com/dasch-swiss/dsp-api/commit/60590129cb21a9e7aef72cc22913b5ed51136f65))
* expose POST /admin/projects as ZIO HTTP route (DEV-1587) ([#2376](https://github.com/dasch-swiss/dsp-api/issues/2376)) ([983bec7](https://github.com/dasch-swiss/dsp-api/commit/983bec7e9c3624799ad4c69d3db003bf07a98121))


### Documentation

* clean up ADRs and add new one for ZIO HTTP ([#2380](https://github.com/dasch-swiss/dsp-api/issues/2380)) ([3a03733](https://github.com/dasch-swiss/dsp-api/commit/3a0373364c2136e75f9b15c7b0b8046b2f760661))
* Fix broken links in docs ([#2392](https://github.com/dasch-swiss/dsp-api/issues/2392)) ([85d25e3](https://github.com/dasch-swiss/dsp-api/commit/85d25e3e71c86f312319db261549b37e465d4604))


### Maintenance

* add authentication middleware ([#2370](https://github.com/dasch-swiss/dsp-api/issues/2370)) ([73a18ff](https://github.com/dasch-swiss/dsp-api/commit/73a18ff9c2a3d32ad55f3764b28b92575bc29799))
* Add tests for ZIO HTTP project routes ([#2377](https://github.com/dasch-swiss/dsp-api/issues/2377)) ([88e067b](https://github.com/dasch-swiss/dsp-api/commit/88e067bbff6d0f43f586d6f26ffc1de513bc493f))
* Cleanup and remove unused code ([#2383](https://github.com/dasch-swiss/dsp-api/issues/2383)) ([6aaf1bf](https://github.com/dasch-swiss/dsp-api/commit/6aaf1bffe6da6b7b52ded59d0f859601e9f20616))
* Expose the zio-http port in docker-compose.yml for the frontend (DEV-1482) ([#2381](https://github.com/dasch-swiss/dsp-api/issues/2381)) ([b11d493](https://github.com/dasch-swiss/dsp-api/commit/b11d49312c7960a756ca87b73aa61cef10458232))
* fix manual release form branch (DEV-1519) ([#2393](https://github.com/dasch-swiss/dsp-api/issues/2393)) ([97d7399](https://github.com/dasch-swiss/dsp-api/commit/97d73994bf97ca257e3f04aa785efac5b5371264))
* Remove deprecated Cardinality model ([#2387](https://github.com/dasch-swiss/dsp-api/issues/2387)) ([3c13e3a](https://github.com/dasch-swiss/dsp-api/commit/3c13e3a0d3e0134434ca0b806bcef3070268ee58))
* Suppress compiler warnings ([#2368](https://github.com/dasch-swiss/dsp-api/issues/2368)) ([62e1193](https://github.com/dasch-swiss/dsp-api/commit/62e11938afe4f8ee77b8730d40c1214e54d5ea10))
* switch zio http implementation from d11 to dev.zio ([#2395](https://github.com/dasch-swiss/dsp-api/issues/2395)) ([0ef6d2f](https://github.com/dasch-swiss/dsp-api/commit/0ef6d2f2c0cb173ed4c2f6fad449924c3e9c0498))
* update create-release.yml ([#2371](https://github.com/dasch-swiss/dsp-api/issues/2371)) ([f97f1bd](https://github.com/dasch-swiss/dsp-api/commit/f97f1bdcd007b135d31f7287afb599dc1efa9cb9))
* update year in the copyright header ([#2391](https://github.com/dasch-swiss/dsp-api/issues/2391)) ([d3740f8](https://github.com/dasch-swiss/dsp-api/commit/d3740f8ea1e3424ace3f7f3bb0eaaddddc7a518c))

## [26.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v25.0.0...v26.0.0) (2023-01-05)


### ⚠ BREAKING CHANGES

* return external representation of ontology IRIs in admin routes (#2330)

### Bug Fixes

* return external representation of ontology IRIs in admin routes ([#2330](https://www.github.com/dasch-swiss/dsp-api/issues/2330)) ([b58828a](https://www.github.com/dasch-swiss/dsp-api/commit/b58828a7557c24aac3cfa88101f4b12ed8d474ff))


### Documentation

* update admin documentation ([#2328](https://www.github.com/dasch-swiss/dsp-api/issues/2328)) ([cedb603](https://www.github.com/dasch-swiss/dsp-api/commit/cedb603f57ceb31acf4ca88052b12c9641e7c954))


### Maintenance

* Add BEOL exception to UUID validation (DEV-1570) ([#2349](https://www.github.com/dasch-swiss/dsp-api/issues/2349)) ([ed34df1](https://www.github.com/dasch-swiss/dsp-api/commit/ed34df105f29d7c64e468d17f7c6066e7650c631))
* add docker healthcheck to SIPI image (INFRA-130) ([#2359](https://www.github.com/dasch-swiss/dsp-api/issues/2359)) ([8554e3b](https://www.github.com/dasch-swiss/dsp-api/commit/8554e3b06c5a87267a8d61b9f732477484e119c0))
* Add dorny/test-reporter for webapi test results DEV-1544 ([#2322](https://www.github.com/dasch-swiss/dsp-api/issues/2322)) ([5c76338](https://www.github.com/dasch-swiss/dsp-api/commit/5c76338f8b3799c6c3c9dc10807fabd10d4f8c6e))
* add metrics endpoint (DEV-1555) ([#2331](https://www.github.com/dasch-swiss/dsp-api/issues/2331)) ([b06f5b4](https://www.github.com/dasch-swiss/dsp-api/commit/b06f5b4631c5c748d87adb94655dd4cc3db11e22))
* Add sbt-header plugin to webapi project and add missing headers ([#2317](https://www.github.com/dasch-swiss/dsp-api/issues/2317)) ([afec4a7](https://www.github.com/dasch-swiss/dsp-api/commit/afec4a74f14355599f00058c01fcd4a86a53258d))
* add stack-without-app target ([#2324](https://www.github.com/dasch-swiss/dsp-api/issues/2324)) ([5ec3223](https://www.github.com/dasch-swiss/dsp-api/commit/5ec32236505daab1ed22941854ae00ee6f8c0065))
* Add test report generation for integration tests (DEV-1544) ([#2325](https://www.github.com/dasch-swiss/dsp-api/issues/2325)) ([a61f227](https://www.github.com/dasch-swiss/dsp-api/commit/a61f227ad86a38db3122430edfe8377864ae5eef))
* Extract common code from responders into EntityAndClassIriS… ([#2348](https://www.github.com/dasch-swiss/dsp-api/issues/2348)) ([238ed71](https://www.github.com/dasch-swiss/dsp-api/commit/238ed712567be4c86ad0f0885a5d1e5d53c8e3bf))
* make it possible to debug integration tests with sbt or IDE ([#2327](https://www.github.com/dasch-swiss/dsp-api/issues/2327)) ([3a222bb](https://www.github.com/dasch-swiss/dsp-api/commit/3a222bb265e2fc064bef1b6404c2bff4a9097c8b))
* refactor project route for ZIO HTTP ([#2338](https://www.github.com/dasch-swiss/dsp-api/issues/2338)) ([e5be1db](https://www.github.com/dasch-swiss/dsp-api/commit/e5be1db9ad85647872b6d93b47a298ee85997a1f))
* remove methods that gets project and members by UUID ([#2346](https://www.github.com/dasch-swiss/dsp-api/issues/2346)) ([2c8da6c](https://www.github.com/dasch-swiss/dsp-api/commit/2c8da6c6ce1c16dda209a248912cc15de36adffb))
* remove PR2255 plugin and revert project IRIs (DEV-1571) ([#2350](https://www.github.com/dasch-swiss/dsp-api/issues/2350)) ([86a19ab](https://www.github.com/dasch-swiss/dsp-api/commit/86a19ab19c625289a757a2a0555c67e8582532c6))
* remove Redis cache implementation leftovers (DEV-1503) ([#2290](https://www.github.com/dasch-swiss/dsp-api/issues/2290)) ([a678dc5](https://www.github.com/dasch-swiss/dsp-api/commit/a678dc5ceaf84a4a60e8a2eb4d3ba299d20cdcc7))
* Remove unused dependency to gatling ([#2361](https://www.github.com/dasch-swiss/dsp-api/issues/2361)) ([baca8a8](https://www.github.com/dasch-swiss/dsp-api/commit/baca8a8d009d500a59c633608960d3e6a11ae4c9))
* remove unused route GET /admin/stores ([#2329](https://www.github.com/dasch-swiss/dsp-api/issues/2329)) ([1e11655](https://www.github.com/dasch-swiss/dsp-api/commit/1e116559df4013dec346f681e7d6acf1ccd63dc2))
* replace Spray-JSON with ZIO-JSON in health route ([#2360](https://www.github.com/dasch-swiss/dsp-api/issues/2360)) ([1b8e74b](https://www.github.com/dasch-swiss/dsp-api/commit/1b8e74b3d81105ca726f315d059c3b424c65ae0c))
* simplify health route setup ([#2337](https://www.github.com/dasch-swiss/dsp-api/issues/2337)) ([26e9596](https://www.github.com/dasch-swiss/dsp-api/commit/26e95960e37bd34c1c56152d5c1fc08672c11dcf))
* Simplify layer setup for integration-tests and reduce to two layers ([#2339](https://www.github.com/dasch-swiss/dsp-api/issues/2339)) ([94836e8](https://www.github.com/dasch-swiss/dsp-api/commit/94836e88b90193a0cdde8fc96a174ead3398be12))
* Split long running integration tests and fast unit tests (DEV-1537) ([#2315](https://www.github.com/dasch-swiss/dsp-api/issues/2315)) ([5b4d601](https://www.github.com/dasch-swiss/dsp-api/commit/5b4d60146a98008132f28da1ea63c40023b239d2))
* update dependencies ([#2347](https://www.github.com/dasch-swiss/dsp-api/issues/2347)) ([560b84f](https://www.github.com/dasch-swiss/dsp-api/commit/560b84f8c65d98dccfc2545c6b8d5fcfc5c4efe9))
* update dependencies ([#2358](https://www.github.com/dasch-swiss/dsp-api/issues/2358)) ([6007266](https://www.github.com/dasch-swiss/dsp-api/commit/6007266dc35423cd257825568b8bb1cb00774fce))
* upgrade Apache Jena Fuseki docker image to v2.0.11 (DEV-1299) ([#2362](https://www.github.com/dasch-swiss/dsp-api/issues/2362)) ([c91d284](https://www.github.com/dasch-swiss/dsp-api/commit/c91d28403e879c183505bd1490a60cbdab79cb43))


### Enhancements

* Add resources/info endpoint (DEV-792) ([#2309](https://www.github.com/dasch-swiss/dsp-api/issues/2309)) ([c3f96a9](https://www.github.com/dasch-swiss/dsp-api/commit/c3f96a92651d5b32dee4fdebc11e871c212a88e9))
* expose GET /admin/projects as ZIO HTTP route ([#2366](https://www.github.com/dasch-swiss/dsp-api/issues/2366)) ([b19f81c](https://www.github.com/dasch-swiss/dsp-api/commit/b19f81c2ac896b26f18e53ec236881877ba721ea))
* expose GET /admin/projects/[shortname | shortcode]/{shortname | shortcode} as ZIO HTTP routes ([#2365](https://www.github.com/dasch-swiss/dsp-api/issues/2365)) ([9907cdf](https://www.github.com/dasch-swiss/dsp-api/commit/9907cdf6d2ec18bfffb206ae93200c4e781c9885))
* Expose GET /admin/projects/iri/{iriUrlEncoded} as zio-http route ([#2355](https://www.github.com/dasch-swiss/dsp-api/issues/2355)) ([2f42906](https://www.github.com/dasch-swiss/dsp-api/commit/2f42906387f3f0775b3c4e797e8a3959d34dcc84))

## [25.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.8...v25.0.0) (2022-12-02)


### ⚠ BREAKING CHANGES

* partOf and sequenceOf properties are not marked as isEditable (#2268)
* change all project IRIs to contain UUID instead of shortcode (DEV-1400) (#2255)

### Bug Fixes

* Allow warn logging for requests/responses which are failures ([#2273](https://www.github.com/dasch-swiss/dsp-api/issues/2273)) ([92531ce](https://www.github.com/dasch-swiss/dsp-api/commit/92531ceb0c38f0c6117bc08633bb66a89dbcabc7))
* Ask timeouts with GetUserADM (DEV-1443) ([#2267](https://www.github.com/dasch-swiss/dsp-api/issues/2267)) ([3f77b6e](https://www.github.com/dasch-swiss/dsp-api/commit/3f77b6e5708e94f7531c5df09164cbfce20b9fc1))
* Deprecation warnings for SCryptPasswordEncoder ([#2308](https://www.github.com/dasch-swiss/dsp-api/issues/2308)) ([86dc389](https://www.github.com/dasch-swiss/dsp-api/commit/86dc389fb2a45d99e82c601f27bcd813dcf8be12))
* Don't log hashes (DEV-1442) ([#2265](https://www.github.com/dasch-swiss/dsp-api/issues/2265)) ([adaf4b0](https://www.github.com/dasch-swiss/dsp-api/commit/adaf4b0ca982464e38672b2cc3aaac04c802b49f))
* Exclude characters with special meaning in Lucene Query Parser syntax from searchbylabel search (DEV-1446) ([#2269](https://www.github.com/dasch-swiss/dsp-api/issues/2269)) ([b359916](https://www.github.com/dasch-swiss/dsp-api/commit/b3599163f3e74375da657e14e47387d598f8485e))
* fix RepositoryUpdater that is not timing out during repository upgrade (DEV-1534) ([#2313](https://www.github.com/dasch-swiss/dsp-api/issues/2313)) ([213a5f0](https://www.github.com/dasch-swiss/dsp-api/commit/213a5f0fd6cd48ffba8128e33b6a964059ed3309))
* Increase timeout when emptying repository (DEV-1506) ([#2289](https://www.github.com/dasch-swiss/dsp-api/issues/2289)) ([39771ed](https://www.github.com/dasch-swiss/dsp-api/commit/39771ed743cd485679a05e5679a25352a8891aea))
* key frame extraction (DEV-1513) ([#2300](https://www.github.com/dasch-swiss/dsp-api/issues/2300)) ([729f071](https://www.github.com/dasch-swiss/dsp-api/commit/729f0718ff6ac831e91279b77046e4f6b9438b65))
* partOf and sequenceOf properties are not marked as isEditable ([#2268](https://www.github.com/dasch-swiss/dsp-api/issues/2268)) ([68f19c3](https://www.github.com/dasch-swiss/dsp-api/commit/68f19c3e570c470563d831ab7f8483e1f392de6f))


### Enhancements

* **projectsADM:** add possibility to get project and members by UUID (DEV-1408) ([#2272](https://www.github.com/dasch-swiss/dsp-api/issues/2272)) ([4b66682](https://www.github.com/dasch-swiss/dsp-api/commit/4b666829ff81a447cd1c1fae304bb5ca518fbab9))


### Documentation

* improve permissions documentation ([#2314](https://www.github.com/dasch-swiss/dsp-api/issues/2314)) ([f4004b2](https://www.github.com/dasch-swiss/dsp-api/commit/f4004b2525821285ac798b251a0a11ba42cc5daf))
* publish architectural decision records ([#2301](https://www.github.com/dasch-swiss/dsp-api/issues/2301)) ([be6bcd0](https://www.github.com/dasch-swiss/dsp-api/commit/be6bcd0b1b6b8d321e2105f25431a43d5814d5c6))
* Remove warning which considers v2 as not production ready ([#2282](https://www.github.com/dasch-swiss/dsp-api/issues/2282)) ([0246522](https://www.github.com/dasch-swiss/dsp-api/commit/024652259ada84f49c4615f91aa3f585da14d254))


### Maintenance

* add GH workflow to publish manually from branches ([#2316](https://www.github.com/dasch-swiss/dsp-api/issues/2316)) ([6f5020e](https://www.github.com/dasch-swiss/dsp-api/commit/6f5020ea54cd0df9a96d132043992f612ffb09cd))
* change all project IRIs to contain UUID instead of shortcode (DEV-1400) ([#2255](https://www.github.com/dasch-swiss/dsp-api/issues/2255)) ([f2b2584](https://www.github.com/dasch-swiss/dsp-api/commit/f2b2584489748bf8a9d56044744c7821582bdce8))
* Decrease timeout for emptying repository (DEV-1518) ([#2310](https://www.github.com/dasch-swiss/dsp-api/issues/2310)) ([a83000b](https://www.github.com/dasch-swiss/dsp-api/commit/a83000ba0b234b294c80931c5930fe15b26c9fdc))
* Introduce ZIO HTTP (DEV-1425) ([#2256](https://www.github.com/dasch-swiss/dsp-api/issues/2256)) ([7ae6d24](https://www.github.com/dasch-swiss/dsp-api/commit/7ae6d24604cda006b85aceda689ed45089025f5a))
* make possible to run Publish GH Action manually (DEV-1519) ([#2297](https://www.github.com/dasch-swiss/dsp-api/issues/2297)) ([bfe578a](https://www.github.com/dasch-swiss/dsp-api/commit/bfe578a37f7609cfac0b2c38e318c98641eebb49))
* **SIPI:** add timestamp to some SIPI Lua logs ([#2311](https://www.github.com/dasch-swiss/dsp-api/issues/2311)) ([8f3f19f](https://www.github.com/dasch-swiss/dsp-api/commit/8f3f19f235a3787afa43b010afedac86bd320958))
* slight improvements to PR template ([#2312](https://www.github.com/dasch-swiss/dsp-api/issues/2312)) ([ca3a8d0](https://www.github.com/dasch-swiss/dsp-api/commit/ca3a8d0def066e8b3b949080bd8d4e163948c73f))
* update dependencies ([#2264](https://www.github.com/dasch-swiss/dsp-api/issues/2264)) ([41d5315](https://www.github.com/dasch-swiss/dsp-api/commit/41d53150e3fee7d91d6c67fda3511e02644e38e3))
* update dependencies ([#2281](https://www.github.com/dasch-swiss/dsp-api/issues/2281)) ([725bc0f](https://www.github.com/dasch-swiss/dsp-api/commit/725bc0f750ec9503ea7902d54a549d848983c358))

### [24.0.8](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.7...v24.0.8) (2022-10-18)


### Bug Fixes

* User can be project admin without being project member (DEV-1383) ([#2248](https://www.github.com/dasch-swiss/dsp-api/issues/2248)) ([c1aa8f0](https://www.github.com/dasch-swiss/dsp-api/commit/c1aa8f0bd4e8f7141d25c0134aec65a8ff0e0546))


### Maintenance

* automatically clean sipi image files (DEV-1395) ([#2237](https://www.github.com/dasch-swiss/dsp-api/issues/2237)) ([eddb34d](https://www.github.com/dasch-swiss/dsp-api/commit/eddb34d80b8fc41a1b219051159ed2eea4f2fd3e))
* fix project name ([#2239](https://www.github.com/dasch-swiss/dsp-api/issues/2239)) ([5af65eb](https://www.github.com/dasch-swiss/dsp-api/commit/5af65eba2edecdf13bbda837cd5a529791dcefe0))
* update dependencies ([#2247](https://www.github.com/dasch-swiss/dsp-api/issues/2247)) ([2eefcbc](https://www.github.com/dasch-swiss/dsp-api/commit/2eefcbcee1734fb46b8086e22dc63f36e5146d3c))

### [24.0.7](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.6...v24.0.7) (2022-10-07)


### Bug Fixes

* DSP-API project IRI validation fails for BEOL project IRI ([#2240](https://www.github.com/dasch-swiss/dsp-api/issues/2240)) ([4b63a72](https://www.github.com/dasch-swiss/dsp-api/commit/4b63a729ab260b123979f5c2ba4a3aaff27197f8))

### [24.0.6](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.5...v24.0.6) (2022-10-06)


### Bug Fixes

* Ask timeouts when requesting projects (DEV-1386) ([#2235](https://www.github.com/dasch-swiss/dsp-api/issues/2235)) ([1820367](https://www.github.com/dasch-swiss/dsp-api/commit/1820367f4e7b9d844f4abd7d98ae75e41133161b))
* User can't be edited by project admin (DEV-1373) ([#2232](https://www.github.com/dasch-swiss/dsp-api/issues/2232)) ([e0b1433](https://www.github.com/dasch-swiss/dsp-api/commit/e0b143382fbf1c55146f775a720562d6ea7444d0))

### [24.0.5](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.4...v24.0.5) (2022-10-05)


### Bug Fixes

* Timeout for multiple Gravsearch queries (DEV-1379) ([#2234](https://www.github.com/dasch-swiss/dsp-api/issues/2234)) ([c63567b](https://www.github.com/dasch-swiss/dsp-api/commit/c63567b5ea4b96c8b6b88600f351c3016d5aec23))


### Maintenance

* app actor cleanup ([#2230](https://www.github.com/dasch-swiss/dsp-api/issues/2230)) ([a67c98f](https://www.github.com/dasch-swiss/dsp-api/commit/a67c98f67793066ab46fe607f24f046b0abcfc44))

### [24.0.4](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.3...v24.0.4) (2022-09-29)


### Bug Fixes

* API returns invalid file URLs, due to including the port ([#2223](https://www.github.com/dasch-swiss/dsp-api/issues/2223)) ([1a0b09c](https://www.github.com/dasch-swiss/dsp-api/commit/1a0b09c4244e65a55d45072ab69bd699c6cc5aa8))
* Value update or deletion doesn't work for properties of other ontology (DEV-1367) ([#2222](https://www.github.com/dasch-swiss/dsp-api/issues/2222)) ([472b375](https://www.github.com/dasch-swiss/dsp-api/commit/472b375e2950264ddd12b09f0497d3b720e80267))

### [24.0.3](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.2...v24.0.3) (2022-09-21)


### Maintenance

* application actor (DEV-956) ([#2166](https://www.github.com/dasch-swiss/dsp-api/issues/2166)) ([4852425](https://www.github.com/dasch-swiss/dsp-api/commit/48524250c73d5adc6965f5b8a2e2c587a82efdc3))
* remove swagger route and docs annotations (DEV-1335) ([#2203](https://www.github.com/dasch-swiss/dsp-api/issues/2203)) ([bec5b8a](https://www.github.com/dasch-swiss/dsp-api/commit/bec5b8aafb3e32cbbb1fe9613f33e1d2b85a3bc1))
* Replace Settings with AppConfig (DEV-1312) ([#2202](https://www.github.com/dasch-swiss/dsp-api/issues/2202)) ([9b76417](https://www.github.com/dasch-swiss/dsp-api/commit/9b7641750214df4a6164adb6d34889e15b16dff3))
* update dependencies ([#2214](https://www.github.com/dasch-swiss/dsp-api/issues/2214)) ([3706acd](https://www.github.com/dasch-swiss/dsp-api/commit/3706acd4585fb1f90e53de7821baf49572147237))

### [24.0.2](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.1...v24.0.2) (2022-09-08)


### Bug Fixes

* **sipi:** remove support for audio/mp4 file format (DEV-1300) ([#2195](https://www.github.com/dasch-swiss/dsp-api/issues/2195)) ([122bf52](https://www.github.com/dasch-swiss/dsp-api/commit/122bf52f97142961f147f3a5b13142c660b555da))


### Maintenance

* Adjust GitHub template (DEV-1313) ([#2183](https://www.github.com/dasch-swiss/dsp-api/issues/2183)) ([5782494](https://www.github.com/dasch-swiss/dsp-api/commit/57824944ae580d10ff7267121d5b77333b452126))
* bump dependencies ([#2196](https://www.github.com/dasch-swiss/dsp-api/issues/2196)) ([2fbf664](https://www.github.com/dasch-swiss/dsp-api/commit/2fbf664c65b647038154580f777a344cc5be992f))
* Ignore push on certain branches from tests (DEV-1112) ([#2187](https://www.github.com/dasch-swiss/dsp-api/issues/2187)) ([e0a0fbb](https://www.github.com/dasch-swiss/dsp-api/commit/e0a0fbb19d5df7fa7978291401bbae6bba6f556f))
* Improve GitHub actions (DEV-1112) ([#2182](https://www.github.com/dasch-swiss/dsp-api/issues/2182)) ([71c772f](https://www.github.com/dasch-swiss/dsp-api/commit/71c772f64f3108250abe37d756ecc93453396c3f))
* Skip tests with success (DEV-1112) ([#2188](https://www.github.com/dasch-swiss/dsp-api/issues/2188)) ([82703d7](https://www.github.com/dasch-swiss/dsp-api/commit/82703d7687b8f4947fba384d2b6d26f75b62c1ec))
* **v3:** add project slice (DEV-1009) ([#2076](https://www.github.com/dasch-swiss/dsp-api/issues/2076)) ([bd2d31e](https://www.github.com/dasch-swiss/dsp-api/commit/bd2d31e3dbcd4898a699a220bd70f88660a4ed02))

### [24.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v24.0.0...v24.0.1) (2022-08-26)


### Bug Fixes

* **cardinality:** Check cardinality with multiple inherited classes (DEV-1189) ([#2164](https://www.github.com/dasch-swiss/dsp-api/issues/2164)) ([f183d7d](https://www.github.com/dasch-swiss/dsp-api/commit/f183d7d5d7c8bc5387a671dc9c589b55f42a44fb))
* Fuseki doesn't stop after client's timeout (DEV-1190) ([#2175](https://www.github.com/dasch-swiss/dsp-api/issues/2175)) ([90f86b5](https://www.github.com/dasch-swiss/dsp-api/commit/90f86b58f1af10025f98a45b1631cd18a13c0493))
* **v2 test:** fix test data collection ([#2174](https://www.github.com/dasch-swiss/dsp-api/issues/2174)) ([468df8f](https://www.github.com/dasch-swiss/dsp-api/commit/468df8ff5502d7054f9641dd7a88cb9801a228d6))


### Documentation

* update file formats (DEV-1185) ([#2158](https://www.github.com/dasch-swiss/dsp-api/issues/2158)) ([4fab193](https://www.github.com/dasch-swiss/dsp-api/commit/4fab193cf5db6c24a07428f19b396596d39053fe))


### Maintenance

* add codacy coverage reporter ([#2177](https://www.github.com/dasch-swiss/dsp-api/issues/2177)) ([c30390f](https://www.github.com/dasch-swiss/dsp-api/commit/c30390f5baa269aa3541b5ea58f7668a58aa2d1c))
* add code coverage ([#2135](https://www.github.com/dasch-swiss/dsp-api/issues/2135)) ([1a02f49](https://www.github.com/dasch-swiss/dsp-api/commit/1a02f49dba83e7a1eb8c5feed19b464e007a4b87))
* add code coverage ([#2163](https://www.github.com/dasch-swiss/dsp-api/issues/2163)) ([b026442](https://www.github.com/dasch-swiss/dsp-api/commit/b026442de716ba3399147e071b941f2b331823a6))
* add coverage upload to codecov ([#2179](https://www.github.com/dasch-swiss/dsp-api/issues/2179)) ([5d4e57e](https://www.github.com/dasch-swiss/dsp-api/commit/5d4e57ebed8a97aee396705766668b59024ae728))
* **feature-toggles:** remove remnants of feature toggles (DEV-217) ([#2176](https://www.github.com/dasch-swiss/dsp-api/issues/2176)) ([ed1cbd0](https://www.github.com/dasch-swiss/dsp-api/commit/ed1cbd05cbc2753655a16b21d57bc64cfde74f03))
* remove github action for deploying docs (DEV-824) ([#2155](https://www.github.com/dasch-swiss/dsp-api/issues/2155)) ([a55eef4](https://www.github.com/dasch-swiss/dsp-api/commit/a55eef417c167fdace1545c5e9f10c5de4e15c94))
* update dependencies ([#2173](https://www.github.com/dasch-swiss/dsp-api/issues/2173)) ([79b88d2](https://www.github.com/dasch-swiss/dsp-api/commit/79b88d2696cc94ee52111f27722a92d896fac26e))

## [24.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v23.0.3...v24.0.0) (2022-08-08)


### ⚠ BREAKING CHANGES

* add isSequenceOf to knora-base ontology (DEV-745) (#2061)

### Bug Fixes

* **sipi:** SIPI returns 404 instead of images if cookie is invalid (DEV-1135) ([#2142](https://www.github.com/dasch-swiss/dsp-api/issues/2142)) ([eb797f0](https://www.github.com/dasch-swiss/dsp-api/commit/eb797f06809bc8e4da386ca2498de3e5676cbbbf))


### Enhancements

* add isSequenceOf to knora-base ontology (DEV-745) ([#2061](https://www.github.com/dasch-swiss/dsp-api/issues/2061)) ([74366d4](https://www.github.com/dasch-swiss/dsp-api/commit/74366d42c88fd05e129abba1ec77d438c7a5b189))


### Maintenance

* **dependencies:** bulk upgrade dependencies ([#2144](https://www.github.com/dasch-swiss/dsp-api/issues/2144)) ([4602150](https://www.github.com/dasch-swiss/dsp-api/commit/4602150013d8a0ad018a85cad7ee337cecf9023e))
* update dependencies ([4cd9812](https://www.github.com/dasch-swiss/dsp-api/commit/4cd98123dd02adcc2fa6ffa7617d33b7440a5ea7))

### [23.0.3](https://www.github.com/dasch-swiss/dsp-api/compare/v23.0.2...v23.0.3) (2022-08-02)


### Bug Fixes

* **triplestore-connector:** stack crashes on invalid search (DEV-1154) ([#2140](https://www.github.com/dasch-swiss/dsp-api/issues/2140)) ([e5426dc](https://www.github.com/dasch-swiss/dsp-api/commit/e5426dca0888ca7ab064cbe292904217dbe8b17f))


### Maintenance

* **dependencies:** update akka-http-cors to 1.1.3 ([#2103](https://www.github.com/dasch-swiss/dsp-api/issues/2103)) ([5d0d522](https://www.github.com/dasch-swiss/dsp-api/commit/5d0d52267ce9e702215c7b2a6527df7e0eb822ef))
* **dependencies:** update jwt-spray-json to 9.0.2 ([#2111](https://www.github.com/dasch-swiss/dsp-api/issues/2111)) ([6e54443](https://www.github.com/dasch-swiss/dsp-api/commit/6e54443e4c2234e432d54ca25b7ecbd3a8656a2f))
* **dependencies:** update Saxon-HE to 11.4 ([#2137](https://www.github.com/dasch-swiss/dsp-api/issues/2137)) ([08c9f68](https://www.github.com/dasch-swiss/dsp-api/commit/08c9f68d427b230327aea149192c907cc3387092))
* **dependencies:** update scalatest to 3.2.13 ([#2138](https://www.github.com/dasch-swiss/dsp-api/issues/2138)) ([a345079](https://www.github.com/dasch-swiss/dsp-api/commit/a345079999de24cabed8235f055bab46cdf6094b))
* **dependencies:** update spring-security-core to 5.6.6 ([#2130](https://www.github.com/dasch-swiss/dsp-api/issues/2130)) ([c83645d](https://www.github.com/dasch-swiss/dsp-api/commit/c83645d6c4a4d949d40c0ec1507106c6071434c2))
* **dependencies:** update spring-security-core to 5.7.2 ([#2139](https://www.github.com/dasch-swiss/dsp-api/issues/2139)) ([3a12562](https://www.github.com/dasch-swiss/dsp-api/commit/3a1256216022770eca0515b21c8ea663ba6b3425))
* **dependencies:** update titanium-json-ld to 1.3.1 ([#2104](https://www.github.com/dasch-swiss/dsp-api/issues/2104)) ([4850525](https://www.github.com/dasch-swiss/dsp-api/commit/4850525494aa2f949b7dfaa2fb1be6f7ff14b684))

### [23.0.2](https://www.github.com/dasch-swiss/dsp-api/compare/v23.0.1...v23.0.2) (2022-07-29)


### Bug Fixes

* **ontology:** link value property is still not editable after updating the property metadata (DEV-1116) ([#2133](https://www.github.com/dasch-swiss/dsp-api/issues/2133)) ([d5b48db](https://www.github.com/dasch-swiss/dsp-api/commit/d5b48db1528d3a8f0c0d3f08bc97564d01e383ce))
* **sipi:** cookie parsing can cause an error which leads to 404 for images (DEV-1135) ([#2134](https://www.github.com/dasch-swiss/dsp-api/issues/2134)) ([bd023a5](https://www.github.com/dasch-swiss/dsp-api/commit/bd023a5836c1c741f094f9b6c974adc54a8d798d))


### Maintenance

* add dependency checking ([#2100](https://www.github.com/dasch-swiss/dsp-api/issues/2100)) ([8017b1f](https://www.github.com/dasch-swiss/dsp-api/commit/8017b1fe96e0728d8d3b112c25b121aa7af073c2))
* add dependency checking ([#2102](https://www.github.com/dasch-swiss/dsp-api/issues/2102)) ([856277b](https://www.github.com/dasch-swiss/dsp-api/commit/856277b0c7d843f4ff563bf8350e99c579073d14))
* Improve validation of GUI elements and GUI attributes (DEV-1082) ([#2098](https://www.github.com/dasch-swiss/dsp-api/issues/2098)) ([5cec8ba](https://www.github.com/dasch-swiss/dsp-api/commit/5cec8bac0b73237c85a72a33d2e3cd20a7cebee1))
* **v3:** add role slice (DEV-1010) ([#2099](https://www.github.com/dasch-swiss/dsp-api/issues/2099)) ([6920716](https://www.github.com/dasch-swiss/dsp-api/commit/6920716e72b9acb32f5444bc6848bfda2986fb93))

### [23.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v23.0.0...v23.0.1) (2022-07-19)


### Bug Fixes

* **ontology:** Don't accept list values without gui attribute (DEV-775) ([#2089](https://www.github.com/dasch-swiss/dsp-api/issues/2089)) ([74a14e1](https://www.github.com/dasch-swiss/dsp-api/commit/74a14e1eb5c475b307ee573042b5384f6c1f562a))

## [23.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v22.0.1...v23.0.0) (2022-07-14)


### ⚠ BREAKING CHANGES

* transform valueHasUri values from node to string type (DEV-1047) (#2094)

### Bug Fixes

* **authentication:** make cookie name unique between environments ([#2095](https://www.github.com/dasch-swiss/dsp-api/issues/2095)) ([7d420a4](https://www.github.com/dasch-swiss/dsp-api/commit/7d420a4e88f355b1465aa1403508c433deb0ae8d))
* **ontology:** existing cardinalities get duplicated in the triplestore when adding a new cardinality to a class (DEV-937) ([#2092](https://www.github.com/dasch-swiss/dsp-api/issues/2092)) ([9fa26db](https://www.github.com/dasch-swiss/dsp-api/commit/9fa26db3ceaa9678b2244863e2fdbc2797a2b57b))
* transform valueHasUri values from node to string type (DEV-1047) ([#2094](https://www.github.com/dasch-swiss/dsp-api/issues/2094)) ([e1d8d95](https://www.github.com/dasch-swiss/dsp-api/commit/e1d8d95504f5b6b0c35dd136f2a51e05576f2eac))

### [22.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v22.0.0...v22.0.1) (2022-07-08)


### Bug Fixes

* **authentication:** make cookie name unique between environments ([#2091](https://www.github.com/dasch-swiss/dsp-api/issues/2091)) ([680021e](https://www.github.com/dasch-swiss/dsp-api/commit/680021e8ffd477b162e36a05eeeb4a1c6389d127))
* **value:** make impossible to set list root node as a value (DEV-973) ([#2088](https://www.github.com/dasch-swiss/dsp-api/issues/2088)) ([94d2b46](https://www.github.com/dasch-swiss/dsp-api/commit/94d2b46387376c78531a45465827e52f5aaaf490))


### Maintenance

* **triplestore:** ZIO-fying triplestore service (DSP-904) ([#2059](https://www.github.com/dasch-swiss/dsp-api/issues/2059)) ([9e038ec](https://www.github.com/dasch-swiss/dsp-api/commit/9e038ec8ef21179f4c2f31ccb00fcf02b21311b7))
* **v3:** finish user slice (DEV-671) ([#2078](https://www.github.com/dasch-swiss/dsp-api/issues/2078)) ([48592ad](https://www.github.com/dasch-swiss/dsp-api/commit/48592ade1694bb26db8e7876e7f4b880e854dc67))

## [22.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v21.0.1...v22.0.0) (2022-06-30)


### ⚠ BREAKING CHANGES

* add upgrade plugin that fixes invalid date serialisations (#2081)

### Bug Fixes

* add upgrade plugin that fixes invalid date serialisations ([#2081](https://www.github.com/dasch-swiss/dsp-api/issues/2081)) ([3a0902e](https://www.github.com/dasch-swiss/dsp-api/commit/3a0902ea9b44860bcb73e4ef25888f5ac8a5dc21))
* **ontology:** link value property is not editable after editing the property metadata (DEV-1037) ([#2084](https://www.github.com/dasch-swiss/dsp-api/issues/2084)) ([09688f5](https://www.github.com/dasch-swiss/dsp-api/commit/09688f5d4bd85e9544807328ee51b222d8b4732b))


### Maintenance

* temporarily ignore KnoraSipiIntegrationV2ITSpec ([#2085](https://www.github.com/dasch-swiss/dsp-api/issues/2085)) ([59f93b3](https://www.github.com/dasch-swiss/dsp-api/commit/59f93b318115c204ec3a20da23c97251575084bd))

### [21.0.1](https://www.github.com/dasch-swiss/dsp-api/compare/v21.0.0...v21.0.1) (2022-06-23)


### Bug Fixes

* fix RepositoryUpdater by removing old way of adding plugins ([#2082](https://www.github.com/dasch-swiss/dsp-api/issues/2082)) ([6599b68](https://www.github.com/dasch-swiss/dsp-api/commit/6599b684da0e6d6506a311441a0214f50e5fb3d6))

## [21.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v20.4.1...v21.0.0) (2022-06-23)


### ⚠ BREAKING CHANGES

* fix valueHasUri bad values and missing types (DEV-1036) (#2079)

### Bug Fixes

* fix valueHasUri bad values and missing types (DEV-1036) ([#2079](https://www.github.com/dasch-swiss/dsp-api/issues/2079)) ([de1e5a4](https://www.github.com/dasch-swiss/dsp-api/commit/de1e5a4e2ce83ad395a76b33b6e2044db6d3003d))

### [20.4.1](https://www.github.com/dasch-swiss/dsp-api/compare/v20.4.0...v20.4.1) (2022-06-16)


### Bug Fixes

* **admin:** return list labels and comments sorted by language ([#2074](https://www.github.com/dasch-swiss/dsp-api/issues/2074)) ([f3a66cb](https://www.github.com/dasch-swiss/dsp-api/commit/f3a66cb2ada71177ce5c256b36001b4b33072e2d))


### Maintenance

* add missing client test data (DEV-979) ([#2072](https://www.github.com/dasch-swiss/dsp-api/issues/2072)) ([54446bc](https://www.github.com/dasch-swiss/dsp-api/commit/54446bcae2e920581d3d4a6848bdcfa8706d6b22))
* **audio:** remove not required properties ([#2070](https://www.github.com/dasch-swiss/dsp-api/issues/2070)) ([96362f4](https://www.github.com/dasch-swiss/dsp-api/commit/96362f448d0b2ef93cc840685028a79e59026881))
* **exceptions:** Create sbt project "shared" and move exceptions (DEV-990) ([#2075](https://www.github.com/dasch-swiss/dsp-api/issues/2075)) ([c09392d](https://www.github.com/dasch-swiss/dsp-api/commit/c09392d7741caecfb21c8fe63e9922ccc912e7de))
* move value objects to separate project (DEV-615) ([#2069](https://www.github.com/dasch-swiss/dsp-api/issues/2069)) ([b55eb12](https://www.github.com/dasch-swiss/dsp-api/commit/b55eb1228414729dc5b53c6cfc952a5305c7dbcd))
* responder manager as plain case class ([#2073](https://www.github.com/dasch-swiss/dsp-api/issues/2073)) ([7f55697](https://www.github.com/dasch-swiss/dsp-api/commit/7f55697923cfdb6dbcfc6aa35673e3936648572c))
* **user:** add user project (DEV-586) ([#2063](https://www.github.com/dasch-swiss/dsp-api/issues/2063)) ([0c5ec03](https://www.github.com/dasch-swiss/dsp-api/commit/0c5ec03239b28ed2075622825695c7475d248e6b))

## [20.4.0](https://www.github.com/dasch-swiss/dsp-api/compare/v20.3.1...v20.4.0) (2022-05-25)


### Bug Fixes

* **cache:** cache does not update correctly when an ontology is modified (DEV-939) ([#2068](https://www.github.com/dasch-swiss/dsp-api/issues/2068)) ([8541519](https://www.github.com/dasch-swiss/dsp-api/commit/8541519c9a4120b4cfa96b3fea4e071956a2e0c4))


### Enhancements

* **admin:** add list child node deletion route (DEV-729) ([#2064](https://www.github.com/dasch-swiss/dsp-api/issues/2064)) ([179ad19](https://www.github.com/dasch-swiss/dsp-api/commit/179ad19bc25637e73d3e850299877600638fd57f))

### [20.3.1](https://www.github.com/dasch-swiss/dsp-api/compare/v20.3.0...v20.3.1) (2022-05-12)


### Bug Fixes

* **authentication:** Add bouncyCastle dependency (DEV-922) ([#2065](https://www.github.com/dasch-swiss/dsp-api/issues/2065)) ([4ac799d](https://www.github.com/dasch-swiss/dsp-api/commit/4ac799dd62ae93eb43ffce319070d238d47b0fee))

## [20.3.0](https://www.github.com/dasch-swiss/dsp-api/compare/v20.2.1...v20.3.0) (2022-05-12)


### Bug Fixes

* Problem with updating cache after deleting comments (DEV-508) ([#2060](https://www.github.com/dasch-swiss/dsp-api/issues/2060)) ([a9fda7e](https://www.github.com/dasch-swiss/dsp-api/commit/a9fda7e4449f52e61350863e6302cce79202fa92))


### Maintenance

* check that the expected Fuseki version is present (DEV-331) ([#2057](https://www.github.com/dasch-swiss/dsp-api/issues/2057)) ([2a695ec](https://www.github.com/dasch-swiss/dsp-api/commit/2a695ec5d917d02e70ec3d3ddcf1ee6ebe265f05))
* **deps:** bump ZIO version (DEV-893) ([#2056](https://www.github.com/dasch-swiss/dsp-api/issues/2056)) ([933f91e](https://www.github.com/dasch-swiss/dsp-api/commit/933f91e049f4a6392015e11ccce0b9b3caa26657))


### Enhancements

* add Romansh as supported language (DEV-557) ([#2053](https://www.github.com/dasch-swiss/dsp-api/issues/2053)) ([58971c8](https://www.github.com/dasch-swiss/dsp-api/commit/58971c8e5dcdc83d0dcf0456887b42b7dedff0e9))
* **gravsearch:** improve gravsearch performance by using unions in prequery (DEV-492) ([#2045](https://www.github.com/dasch-swiss/dsp-api/issues/2045)) ([40354a7](https://www.github.com/dasch-swiss/dsp-api/commit/40354a7d0ee7bc4954adb87e8b16ba4d9fc45784))

### [20.2.1](https://www.github.com/dasch-swiss/dsp-api/compare/v20.2.0...v20.2.1) (2022-05-05)


### Bug Fixes

* **projectsADM:** fix cache issue in getSingleProjectADM ([#2054](https://www.github.com/dasch-swiss/dsp-api/issues/2054)) ([77bfadc](https://www.github.com/dasch-swiss/dsp-api/commit/77bfadc8b3cc9e92bc5bde10155104043cd979e2))


### Maintenance

* **IIIFService:** zio-fying iiif service (DEV-801) ([#2044](https://www.github.com/dasch-swiss/dsp-api/issues/2044)) ([224b664](https://www.github.com/dasch-swiss/dsp-api/commit/224b6649aa68379bca7531679f5d1b824500b02c))

## [20.2.0](https://www.github.com/dasch-swiss/dsp-api/compare/v20.1.1...v20.2.0) (2022-04-28)


### Bug Fixes

* Cleaning sipi tmp folder results in an error when there are lots of files (DEV-316) ([#2052](https://www.github.com/dasch-swiss/dsp-api/issues/2052)) ([33e6896](https://www.github.com/dasch-swiss/dsp-api/commit/33e689645a899e39b7f9d6e3a0c56fd1d99cc1f2))


### Enhancements

* **error-handling:** return status 504 instead of 500 for triplestore timeout exception (DEV-749) ([#2046](https://www.github.com/dasch-swiss/dsp-api/issues/2046)) ([a47096e](https://www.github.com/dasch-swiss/dsp-api/commit/a47096ebebcc458e01063fac6a01827eeb5312c1))
* **ontology:** allow deleting comments of classes (DEV-804) ([#2048](https://www.github.com/dasch-swiss/dsp-api/issues/2048)) ([eca9206](https://www.github.com/dasch-swiss/dsp-api/commit/eca92066bdb6d48033f9f4590f45dba774bc4be2))
* **ontology:** allow deleting comments of properties (DEV-696) ([#2042](https://www.github.com/dasch-swiss/dsp-api/issues/2042)) ([985c5fd](https://www.github.com/dasch-swiss/dsp-api/commit/985c5fd45dcf2b1c9d4a6d47040165a56b7dfe33))


### Maintenance

* **formatting-logging:** reformat scala code and change logging policy (DEV-839) ([#2051](https://www.github.com/dasch-swiss/dsp-api/issues/2051)) ([5e4e914](https://www.github.com/dasch-swiss/dsp-api/commit/5e4e91499347f4b118b84fa06cb7b0499e4cfecf))
* **formatting:** reformat turtle files (DEV-430) ([#2050](https://www.github.com/dasch-swiss/dsp-api/issues/2050)) ([0389e52](https://www.github.com/dasch-swiss/dsp-api/commit/0389e52fa120d1ee46e40509e5e29c91d0fb17f5))
* **triplestore:** remove embedded-jena-tdb related code ([#2043](https://www.github.com/dasch-swiss/dsp-api/issues/2043)) ([a5ea62e](https://www.github.com/dasch-swiss/dsp-api/commit/a5ea62eeaaadb97caa456e7dc9e6946c44abcf5b))

### [20.1.1](https://www.github.com/dasch-swiss/dsp-api/compare/v20.1.0...v20.1.1) (2022-04-14)


### Bug Fixes

* **sipi:** extract frames from video even without aspect ratio (DEV-802) ([#2041](https://www.github.com/dasch-swiss/dsp-api/issues/2041)) ([57d40f7](https://www.github.com/dasch-swiss/dsp-api/commit/57d40f79beed8035ee461562b54abc347746237b))


### Documentation

* **ingest:** Add accepted file formats to documentation (DEV-677) ([#2038](https://www.github.com/dasch-swiss/dsp-api/issues/2038)) ([f72e7a0](https://www.github.com/dasch-swiss/dsp-api/commit/f72e7a01e9396430e88c75c1ab3ec2743e6cf053))


### Maintenance

* **cacheservice:** use ZIO (DEV-546) ([#2022](https://www.github.com/dasch-swiss/dsp-api/issues/2022)) ([521150f](https://www.github.com/dasch-swiss/dsp-api/commit/521150fbf3dac5ce4c075e6b3ef3c537004a750d))
* **triplestore:** remove graphDB support ([#2037](https://www.github.com/dasch-swiss/dsp-api/issues/2037)) ([bf17bca](https://www.github.com/dasch-swiss/dsp-api/commit/bf17bcad5fd87313b797cdb6187ef7e42d6e2911))

## [20.1.0](https://www.github.com/dasch-swiss/dsp-api/compare/v20.0.0...v20.1.0) (2022-04-07)


### Bug Fixes

* docs/requirements.txt to reduce vulnerabilities ([#2034](https://www.github.com/dasch-swiss/dsp-api/issues/2034)) ([b07600d](https://www.github.com/dasch-swiss/dsp-api/commit/b07600dbb45195549248f493401178c2ff1da763))


### Maintenance

* distinguish between compile, runtime and test dependencies ([#2028](https://www.github.com/dasch-swiss/dsp-api/issues/2028)) ([7cb326f](https://www.github.com/dasch-swiss/dsp-api/commit/7cb326ff5804b46995d25ce301a17c153af93036))
* inventory and upgrade of dependencies (DEV-478) ([#2033](https://www.github.com/dasch-swiss/dsp-api/issues/2033)) ([470b77f](https://www.github.com/dasch-swiss/dsp-api/commit/470b77fb8b6e7b0c37392414fe96f4864231e3d0))


### Documentation

* replace Bazel and Intellij documentation with SBT and VSCode (DEV-607) ([#2035](https://www.github.com/dasch-swiss/dsp-api/issues/2035)) ([603efef](https://www.github.com/dasch-swiss/dsp-api/commit/603efeffa35204dcbe59554f4ba33cc58c1e66ae))


### Enhancements

* **ontology:** Add support for additional ontologies (DEV-512) ([#2029](https://www.github.com/dasch-swiss/dsp-api/issues/2029)) ([50e3186](https://www.github.com/dasch-swiss/dsp-api/commit/50e318628bf7b6e30e4dfd2472e69c3b8ccd65e2))
* **sipi:** upload video support (DEV-771 / DEV-207) ([#1952](https://www.github.com/dasch-swiss/dsp-api/issues/1952)) ([47f2e28](https://www.github.com/dasch-swiss/dsp-api/commit/47f2e28817fc98af9998a19d56248b638d8ceace))

## [20.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v19.0.0...v20.0.0) (2022-03-31)


### ⚠ BREAKING CHANGES

* **ontology:** make knora-base:lastModificationDate required property (#2018)

### Maintenance

* fix docker containers timezone ([#2027](https://www.github.com/dasch-swiss/dsp-api/issues/2027)) ([6bbb3fe](https://www.github.com/dasch-swiss/dsp-api/commit/6bbb3fe474fe6a558b0b99f0a833c1a0ed1e1454))


### Enhancements

* **ontology:** make knora-base:lastModificationDate required property ([#2018](https://www.github.com/dasch-swiss/dsp-api/issues/2018)) ([64cdce9](https://www.github.com/dasch-swiss/dsp-api/commit/64cdce9dfa305f1e514ada184777e4c0942ef0e2))

## [19.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v18.0.0...v19.0.0) (2022-03-24)


### ⚠ BREAKING CHANGES

* **authentication:** add server specific issuer to JWT token (DEV-555) (#2024)

### Bug Fixes

* **authentication:** add server specific issuer to JWT token (DEV-555) ([#2024](https://www.github.com/dasch-swiss/dsp-api/issues/2024)) ([4bd5b2f](https://www.github.com/dasch-swiss/dsp-api/commit/4bd5b2fccd8ef34c5ff8240269e3c290ab106850))
* **version:** fix displayed versions ([#2026](https://www.github.com/dasch-swiss/dsp-api/issues/2026)) ([566285c](https://www.github.com/dasch-swiss/dsp-api/commit/566285c227a59f2a6efc93ded171ab548efde3a2))


### Maintenance

* improve logging (DEV-634) ([#2021](https://www.github.com/dasch-swiss/dsp-api/issues/2021)) ([85d1057](https://www.github.com/dasch-swiss/dsp-api/commit/85d1057062cb3d76d083b684d21ece8dcb46a1a4))
* remove warnings (DEV-621) ([#2015](https://www.github.com/dasch-swiss/dsp-api/issues/2015)) ([70630f1](https://www.github.com/dasch-swiss/dsp-api/commit/70630f17de1d684b15726a9e28cf8e58193104cf))
* **test:** get tests to run in vs code (DEV-601) ([#2020](https://www.github.com/dasch-swiss/dsp-api/issues/2020)) ([747d13d](https://www.github.com/dasch-swiss/dsp-api/commit/747d13de9a0eee479decd48b7d3a186b49f81a2b))

## [18.0.0](https://www.github.com/dasch-swiss/dsp-api/compare/v17.5.3...v18.0.0) (2022-03-08)


### ⚠ BREAKING CHANGES

* **standoff:** return XML alongside HTML for textValue with custom standoff mapping and default XSL transformation (DEV-201) (#1991)

### Bug Fixes

* Use correct docker image tag after publishing (DEV-614) ([#2016](https://www.github.com/dasch-swiss/dsp-api/issues/2016)) ([7649515](https://www.github.com/dasch-swiss/dsp-api/commit/7649515230fa0048b12a1e6cb113e8cce7e73c54))


### Maintenance

* improve code structure (DEV-612) ([#2012](https://www.github.com/dasch-swiss/dsp-api/issues/2012)) ([eac0049](https://www.github.com/dasch-swiss/dsp-api/commit/eac0049773c288d0b2f05c626662585a913cbaef))


### Enhancements

* **standoff:** return XML alongside HTML for textValue with custom standoff mapping and default XSL transformation (DEV-201) ([#1991](https://www.github.com/dasch-swiss/dsp-api/issues/1991)) ([2548b8f](https://www.github.com/dasch-swiss/dsp-api/commit/2548b8f2cc75e8350091aefd70f62d66d8605428))

### [17.5.3](https://www.github.com/dasch-swiss/dsp-api/compare/v17.5.2...v17.5.3) (2022-03-04)


### Bug Fixes

* **RepositoryUpdater:** make sure temp directories are deleted ([#2010](https://www.github.com/dasch-swiss/dsp-api/issues/2010)) ([9c9a1bd](https://www.github.com/dasch-swiss/dsp-api/commit/9c9a1bddf32c80ffd1eea30806a311bd3b71831b))


### Documentation

* fix permissions design documentation (DEV-495) ([#1997](https://www.github.com/dasch-swiss/dsp-api/issues/1997)) ([5154adc](https://www.github.com/dasch-swiss/dsp-api/commit/5154adc8620f5b966419bea187030dbd92b236fb))


### Maintenance

* fix docker image name (DEV-574) ([#2007](https://www.github.com/dasch-swiss/dsp-api/issues/2007)) ([7a186ba](https://www.github.com/dasch-swiss/dsp-api/commit/7a186bafa579b57d838db2bfcb31098aac115a02))
* remove fuseki image creation and change sipi image creation to sbt (DEV-544) ([#2011](https://www.github.com/dasch-swiss/dsp-api/issues/2011)) ([eed2767](https://www.github.com/dasch-swiss/dsp-api/commit/eed2767103b552a0f895aeb18252deecb7cc4497))
* start on a functional domain design implementation for ontologies (DEV-227) ([#2009](https://www.github.com/dasch-swiss/dsp-api/issues/2009)) ([54cee7a](https://www.github.com/dasch-swiss/dsp-api/commit/54cee7a6711d38d7d11a2c95d95c16e71e200d64))

### [17.5.2](https://www.github.com/dasch-swiss/dsp-api/compare/v17.5.1...v17.5.2) (2022-02-23)


### Bug Fixes

* **permissions:** Update default object access permissions (DEV-514) ([#2004](https://www.github.com/dasch-swiss/dsp-api/issues/2004)) ([04a8d3d](https://www.github.com/dasch-swiss/dsp-api/commit/04a8d3d544fab8e4bc883831d20eaa638f8ac9aa))
* **timeout:** Increase timeouts (DEV-536) ([#2005](https://www.github.com/dasch-swiss/dsp-api/issues/2005)) ([f1f8005](https://www.github.com/dasch-swiss/dsp-api/commit/f1f8005244a346f64761cad8d7cb5a5144d407f4))


### Maintenance

* BAZEL to SBT migration (DEV-508) ([#2002](https://www.github.com/dasch-swiss/dsp-api/issues/2002)) ([38faa9e](https://www.github.com/dasch-swiss/dsp-api/commit/38faa9eb38b77d082d2f17a7cf220408ca409b3b))

### [17.5.1](https://www.github.com/dasch-swiss/dsp-api/compare/v17.5.0...v17.5.1) (2022-02-16)


### Maintenance

* **deps:** upgrade Jena Fuseki docker image to v2.0.8 ([#2001](https://www.github.com/dasch-swiss/dsp-api/issues/2001)) ([3e2eccc](https://www.github.com/dasch-swiss/dsp-api/commit/3e2eccc6a7a38b0e03796a2db987cb97b886c662))
* **deps:** upgrate Jena API to v4.4.0 ([#1999](https://www.github.com/dasch-swiss/dsp-api/issues/1999)) ([3eecc69](https://www.github.com/dasch-swiss/dsp-api/commit/3eecc69f0394afde32eb0698005c4d9840ab9a3d))


### Documentation

* fix markdown issues in documentation (DEV-504) ([#2003](https://www.github.com/dasch-swiss/dsp-api/issues/2003)) ([ff6b4cf](https://www.github.com/dasch-swiss/dsp-api/commit/ff6b4cf4ae610399bc01c04304f8f9fa94e3e0b7))

## [17.5.0](https://www.github.com/dasch-swiss/dsp-api/compare/v17.4.1...v17.5.0) (2022-02-11)


### Enhancements

* **ontologies:** make comments optional for property and class creation (DEV-342) ([#1996](https://www.github.com/dasch-swiss/dsp-api/issues/1996)) ([a3c286c](https://www.github.com/dasch-swiss/dsp-api/commit/a3c286c3ab90e713c5a7929d69517d1dbb848d40))

### [17.4.1](https://www.github.com/dasch-swiss/dsp-api/compare/v17.4.0...v17.4.1) (2022-02-07)


### Maintenance

* **deps:** upgrade Jena to v4.3.2 (DEV-473) ([#1995](https://www.github.com/dasch-swiss/dsp-api/issues/1995)) ([216dcb4](https://www.github.com/dasch-swiss/dsp-api/commit/216dcb432b79812551d50f4a05375cee8f379852))
* **deps:** upgrade titanium-json-ld to v1.2.0 & jakarta-json to v2.0.1 (DEV-335) ([#1993](https://www.github.com/dasch-swiss/dsp-api/issues/1993)) ([ad01bf9](https://www.github.com/dasch-swiss/dsp-api/commit/ad01bf996de77d72934671de2479a206ca80e365))

## [17.4.0](https://www.github.com/dasch-swiss/dsp-api/compare/v17.3.1...v17.4.0) (2022-02-04)


### Bug Fixes

* **version-upgrade:** add upgrade plugin for ArchiveRepresentation and DeletedResource (DEV-467) ([#1992](https://www.github.com/dasch-swiss/dsp-api/issues/1992)) ([e1566e9](https://www.github.com/dasch-swiss/dsp-api/commit/e1566e999d67fab57218e9ec2a5f209b44d82af9))


### Maintenance

* add support for building native API and Fuseki Docker images on Apple M1 (DEV-435) ([#1987](https://www.github.com/dasch-swiss/dsp-api/issues/1987)) ([ab80e72](https://www.github.com/dasch-swiss/dsp-api/commit/ab80e72047dd0ce7fc18e874a2712074908c2d7b))
* refactor test models (DEV-264) ([#1975](https://www.github.com/dasch-swiss/dsp-api/issues/1975)) ([65952f9](https://www.github.com/dasch-swiss/dsp-api/commit/65952f963417d0a7f546d04b172ff768382feae9))


### Enhancements

* **resource:** add ArchiveRepresentation to API V1 (DEV-393) (DEV-394) ([#1984](https://www.github.com/dasch-swiss/dsp-api/issues/1984)) ([65b88a2](https://www.github.com/dasch-swiss/dsp-api/commit/65b88a207dd5a68413f4fd365a3ee7bd566cb1a2))
* **UUID:** add IRI validation that allows only to create IRIs using UUID version 4 and 5 (DEV-402) ([#1990](https://www.github.com/dasch-swiss/dsp-api/issues/1990)) ([74d4344](https://www.github.com/dasch-swiss/dsp-api/commit/74d43441e6960acd6e86ea2a67ebdf3f6fdf4125))

### [17.3.1](https://www.github.com/dasch-swiss/dsp-api/compare/v17.3.0...v17.3.1) (2022-01-28)


### Bug Fixes

* **ontology:** Sub-properties of link values aren't created correctly (DEV-426) ([#1985](https://www.github.com/dasch-swiss/dsp-api/issues/1985)) ([70a8b08](https://www.github.com/dasch-swiss/dsp-api/commit/70a8b08e0bd18a8affc89c6136f166ca21a5f27f))


### Maintenance

* **deps:** bump fuseki image to 2.0.7 (DEV-389) ([#1983](https://www.github.com/dasch-swiss/dsp-api/issues/1983)) ([fcbfb1d](https://www.github.com/dasch-swiss/dsp-api/commit/fcbfb1dcdf3c8273618bc3288ecb7bf236104c65))
* **license:** update the license (DEV-374) ([#1981](https://www.github.com/dasch-swiss/dsp-api/issues/1981)) ([044fdc5](https://www.github.com/dasch-swiss/dsp-api/commit/044fdc56cec038ed3fea1c00cb39563976005b9b))

## [17.3.0](https://www.github.com/dasch-swiss/dsp-api/compare/v17.2.0...v17.3.0) (2022-01-17)


### Bug Fixes

* **ontology:** DSP-API creates wrong partOfValue property (DEV-216) ([#1978](https://www.github.com/dasch-swiss/dsp-api/issues/1978)) ([27b5c86](https://www.github.com/dasch-swiss/dsp-api/commit/27b5c866f29795f49dbf584ca20245e90d210d13))
* **resource:** return sensible CreationDate for DeletedResource ([#1979](https://www.github.com/dasch-swiss/dsp-api/issues/1979)) ([1658103](https://www.github.com/dasch-swiss/dsp-api/commit/1658103d27051b5920b68b01f6b863be7c55e5ab))


### Enhancements

* **resource:** add support for 7z files in ArchiveRepresentation (DEV-322) ([#1977](https://www.github.com/dasch-swiss/dsp-api/issues/1977)) ([729689c](https://www.github.com/dasch-swiss/dsp-api/commit/729689c7111bc907f1b108242475ae2fe342b2a7))


### Maintenance

* **admin:** refactor projects & users value objects (DEV-240) ([#1976](https://www.github.com/dasch-swiss/dsp-api/issues/1976)) ([563d252](https://www.github.com/dasch-swiss/dsp-api/commit/563d25255d3621f8b2d803e5133a9974eb000c0a))
* **CI:** add disk cache and other cleanup (DEV-388) ([#1982](https://www.github.com/dasch-swiss/dsp-api/issues/1982)) ([e590d12](https://www.github.com/dasch-swiss/dsp-api/commit/e590d12c58fafffe3428f99f17a952c6700fb4fd))

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
  <https://docs.knora.org/paradox/03-endpoints/api-v2/knora-iris.html#iris-for-data>
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

* a `knora-base:ListNode` must have at least one `rdfs:label`. ([#991](https://github.com/dasch-swiss/dsp-api/issues/990))

### New features

* add developer-centric docker-compose.yml for starting the Knora / GraphDB / Sipi / Salsah1 ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* configure `webapi` and `salsah1` thorough environment variables ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* update for Java 10 ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* comment out the generation of fat jars from `KnoraBuild.sbt` (for now) ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* update ehcache ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* update sbt to 1.2.1 ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* remove Kamon monitoring (for now) since we don't see anything meaningful there. We probably will have to instrument Knora by hand and then use Kamon for access. ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* update Dockerfiles for `webapi` and `salsah1` ([#979](https://github.com/dasch-swiss/dsp-api/issues/979))
* follow subClassOf when including ontologies in XML import schemas ([#991](https://github.com/dasch-swiss/dsp-api/issues/991))
* add support for adding list child nodes ([#991](https://github.com/dasch-swiss/dsp-api/issues/990))
* add support for shared ontologies ([#987](https://github.com/dasch-swiss/dsp-api/issues/987))

### Bugfixes

* trouble with xml-checker and/or consistency-checker during bulk import ([#978](https://github.com/dasch-swiss/dsp-api/issues/978))
* ontology API error with link values ([#988](https://github.com/dasch-swiss/dsp-api/issues/988))

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

* Accept queries in POST requests ([#650](https://github.com/dasch-swiss/dsp-api/issues/650)).
* Allow a Gravsearch query to specify the IRI of the main resource ([#871](https://github.com/dasch-swiss/dsp-api/issues/871)) (by allowing `BIND`).
* Allow `lang` to be used with `!=`.
* A `UNION` or `OPTIONAL` can now be nested in an `OPTIONAL` ([#882](https://github.com/dasch-swiss/dsp-api/issues/882)).
* Gravsearch now does type inference ([#884](https://github.com/dasch-swiss/dsp-api/issues/884)).
* The Knora API v2 complex schema can now be used in Gravsearch, making it possible to search
  for list nodes ([#899](https://github.com/dasch-swiss/dsp-api/issues/899)).

Admin API:

* Make project description required ([#875](https://github.com/dasch-swiss/dsp-api/issues/875)).

Conversion to TEI:

* Conversion of standard standoff entities to TEI
* Custom conversion of project specific standoff entities and metadata to TEI

Sipi integration:

* The Knora specific Sipi configuration and scripts can now be found under the `sipi/` directory ([#404](https://github.com/dasch-swiss/dsp-api/issues/404)).
* Documentation on how Sipi can be started changed ([#404](https://github.com/dasch-swiss/dsp-api/issues/404)).

Bugfixes
---------

* Allow a class or property definition to have more than one object for `rdf:type` ([#885](https://github.com/dasch-swiss/dsp-api/issues/885)).
* Exclude list values from v2 fulltext search ([#906](https://github.com/dasch-swiss/dsp-api/issues/906)).

Gravsearch fixes:

* Allow the `lang` function to be used in a comparison inside AND/OR ([#846](https://github.com/dasch-swiss/dsp-api/issues/846)).
* Fix the processing of resources with multiple incoming links that use the same property ([#878](https://github.com/dasch-swiss/dsp-api/issues/878)).
* Fix the parsing of a FILTER inside an OPTIONAL ([#879](https://github.com/dasch-swiss/dsp-api/issues/879)).
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
