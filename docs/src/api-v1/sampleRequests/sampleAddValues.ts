/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import { addValueFormats } from "../addValueFormats"
import { basicMessageComponents} from "../basicMessageComponents"

let addIntervalValueRequest: addValueFormats.addIntervalValueRequest = {"interval_value":[0,0],"res_id":"http://rdfh.ch/a-thing","prop":"http://www.knora.org/ontology /anything#hasInterval","project_id":"http://rdfh.ch/projects/anything"};

let addIntervalValueResponse: addValueFormats.addValueResponse = {"id":"http://rdfh.ch/a-thing/values/7iX8sKUGQMiInDP9PF_bOw","status":0,"comment":null,"rights":8,"value":{"timeval1":0,"timeval2":0}};

let addSimpletextValueRequest: addValueFormats.addRichtextValueRequest = {"richtext_value":{"utf8str":"test"},"res_id":"http://rdfh.ch/a-thing","prop":"http://www.knora.org/ontology/0001/anything#hasText","project_id":"http://rdfh.ch/projects/anything"};

let addSimpletextValueResponse: addValueFormats.addValueResponse = {"id":"http://rdfh.ch/a-thing/values/lfwoLx9LT7-JnA_wKjeZYg","status":0,"comment":null,"rights":8,"value":{"utf8str":"test"}};

let addRichtextValueRequest: addValueFormats.addRichtextValueRequest = {"richtext_value":{"xml":"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><strong>Test</strong><br/>text</text>", "mapping_id": "http://rdfh.ch/standoff/mappings/StandardMapping"},"res_id":"http://rdfh.ch/a-thing","prop":"http://www.knora.org/ontology/0001/anything#hasText","project_id":"http://rdfh.ch/projects/anything"};

let addRichtextValueResponse: addValueFormats.addValueResponse = {"id":"http://rdfh.ch/a-thing/values/lfwoLx9LT7-JnA_wKjeZYg","status":0,"comment":null,"rights":8,"value":{"xml":"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><strong>Test</strong><br/>text</text>", "mapping_id": "http://rdfh.ch/standoff/mappings/StandardMapping"}};
