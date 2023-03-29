# Changelog

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
