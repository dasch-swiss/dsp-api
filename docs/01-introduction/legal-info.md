<!---
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Storing Legal Information

Each file representation in the project data may have legal metadata associated with it.
This metadata is stored in the [FileValue](../02-dsp-ontologies/knora-base.md#filevalue) that represents the file.

!!! warning "Legal Information Will Become Mandatory"

    Currently, the legal information on the FileValue is optional. This will change in the future when License, Authorship, and Copyright Holder will become mandatory.

## License

The license under which the file is published.
A license has the following properties:

- `uri` - this is a URI that identifies the license.
- `label-en` - this is a human-readable label for the license in English.
- `id` - this is a unique identifier as an IRI for the license.

Each project [restricts the licenses that can be used](/03-endpoints/api-admin/#get-adminprojectsshortcodeprojectshortcodelegal-infolicenses).

Currently, the set of allowed licenses is fixed to [our predefined licenses](#predefined-licenses) and cannot be changed by the project.

## Copyright Holder

The copyright holder of the file.
A copyright holder is a person or organization that holds the copyright to a file.

Each project [references an allowed list of copyright holders](/03-endpoints/api-admin/#get-adminprojectsshortcodeprojectshortcodelegal-infocopyright-holders) that can be used.  
System project administrators can [add new copyright holders](/03-endpoints/api-admin/#post-adminprojectsshortcodeprojectshortcodelegal-infocopyright-holders) to the list.

## Authorship

The authorship of the file.
This can be a person or an organization who was involved in creating the asset, also known as moral rights to the asset.

# Predefined Licenses

We encourage the use of Creative Commons licenses for the publication of data.  
Notable **CC BY 4.0** (Attribution 4.0 International) and **CC BY-SA 4.0** (Attribution-ShareAlike 4.0 International) licenses are well suited for [FAIR content](https://www.go-fair.org/fair-principles/).

- **[Creative Commons](https://creativecommons.org/) Licenses**
    - **CC BY 4.0** (Attribution 4.0 International)
        - Id: `http://rdfh.ch/licenses/cc-by-4.0`
        - URI: [https://creativecommons.org/licenses/by/4.0/](https://creativecommons.org/licenses/by/4.0/)
        - Label-En: CC BY 4.0
    - **CC BY-SA 4.0** (Attribution-ShareAlike 4.0 International)
        - Id: `http://rdfh.ch/licenses/cc-by-sa-4.0`
        - URI: [https://creativecommons.org/licenses/by-sa/4.0/](https://creativecommons.org/licenses/by-sa/4.0/)
        - Label-En: CC BY-SA 4.0
    - **CC BY-NC 4.0** (Attribution-NonCommercial 4.0 International)
        - Id: `http://rdfh.ch/licenses/cc-by-nc-4.0`
        - URI: [https://creativecommons.org/licenses/by-nc/4.0/](https://creativecommons.org/licenses/by-nc/4.0/)
        - Label-En: CC BY-NC 4.0
    - **CC BY-NC-SA 4.0** (Attribution-NonCommercial-ShareAlike 4.0 International)
        - Id: `http://rdfh.ch/licenses/cc-by-nc-sa-4.0`
        - URI: [https://creativecommons.org/licenses/by-nc-sa/4.0/](https://creativecommons.org/licenses/by-nc-sa/4.0/)
        - Label-En: CC BY-NC-SA 4.0
    - **CC BY-ND 4.0** (Attribution-NoDerivatives 4.0 International)
        - Id: `http://rdfh.ch/licenses/cc-by-nd-4.0`
        - URI: [https://creativecommons.org/licenses/by-nd/4.0/](https://creativecommons.org/licenses/by-nd/4.0/)
        - Label-En: CC BY-ND 4.0
    - **CC BY-NC-ND 4.0** (Attribution-NonCommercial-NoDerivatives 4.0 International)
        - Id: `http://rdfh.ch/licenses/cc-by-nc-nd-4.0`
        - URI: [https://creativecommons.org/licenses/by-nc-nd/4.0/](https://creativecommons.org/licenses/by-nc-nd/4.0/)
        - Label-En: CC BY-NC-ND 4.0
- **Special licenses**
    - **AI-Generated Content - Not Protected by Copyright**
        - Id: `http://rdfh.ch/licenses/ai-generated`
        - URI: [http://rdfh.ch/licenses/ai-generated](http://rdfh.ch/licenses/ai-generated)
        - Label-En: AI-Generated Content - Not Protected by Copyright
    - **Unknown License - Ask Copyright Holder for Permission**
        - Id: `http://rdfh.ch/licenses/unknown`
        - URI: [http://rdfh.ch/licenses/unknown](http://rdfh.ch/licenses/unknown)
        - Label-En: Unknown License - Ask Copyright Holder for Permission
    - **Public Domain - Not Protected by Copyright**
        - Id: `http://rdfh.ch/licenses/public-domain`
        - URI: [http://rdfh.ch/licenses/public-domain](http://rdfh.ch/licenses/public-domain)
        - Label-En: Public Domain - Not Protected by Copyright
