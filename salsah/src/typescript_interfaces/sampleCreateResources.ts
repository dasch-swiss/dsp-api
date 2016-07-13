/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

import { createResourceFormats } from "./createResourceFormats"

let thing: createResourceFormats.createResourceWithoutRepresentationRequest = {
    "restype_id": "http://www.knora.org/ontology/anything#Thing",
    "label": "A thing",
    "project_id": "http://data.knora.org/projects/anything",
    "properties": {
        "http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Test text"}}],
        "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
        "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}, {"decimal_value":5.7}],
        "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
        "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
        "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
        "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
        "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
    }
};