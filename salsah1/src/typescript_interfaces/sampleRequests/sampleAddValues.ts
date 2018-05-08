/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import { addValueFormats } from "../addValueFormats"
import { basicMessageComponents} from "../basicMessageComponents"

let addIntervalValueRequest: addValueFormats.addIntervalValueRequest = {"interval_value":[0,0],"res_id":"http://data.knora.org/a-thing","prop":"http://www.knora.org/ontology /anything#hasInterval","project_id":"http://data.knora.org/projects/anything"};

let addIntervalValueResponse: addValueFormats.addValueResponse = {"id":"http://data.knora.org/a-thing/values/7iX8sKUGQMiInDP9PF_bOw","status":0,"comment":null,"rights":8,"value":{"timeval1":0,"timeval2":0}};

let addSimpletextValueRequest: addValueFormats.addRichtextValueRequest = {"richtext_value":{"utf8str":"test"},"res_id":"http://data.knora.org/a-thing","prop":"http://www.knora.org/ontology/0001/anything#hasText","project_id":"http://data.knora.org/projects/anything"};

let addSimpletextValueResponse: addValueFormats.addValueResponse = {"id":"http://data.knora.org/a-thing/values/lfwoLx9LT7-JnA_wKjeZYg","status":0,"comment":null,"rights":8,"value":{"utf8str":"test"}};

let addRichtextValueRequest: addValueFormats.addRichtextValueRequest = {"richtext_value":{"xml":"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><strong>Test</strong><br/>text</text>", "mapping_id": "http://rdfh.ch/standoff/mappings/StandardMapping"},"res_id":"http://data.knora.org/a-thing","prop":"http://www.knora.org/ontology/0001/anything#hasText","project_id":"http://data.knora.org/projects/anything"};

let addRichtextValueResponse: addValueFormats.addValueResponse = {"id":"http://data.knora.org/a-thing/values/lfwoLx9LT7-JnA_wKjeZYg","status":0,"comment":null,"rights":8,"value":{"xml":"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><strong>Test</strong><br/>text</text>", "mapping_id": "http://rdfh.ch/standoff/mappings/StandardMapping"}};