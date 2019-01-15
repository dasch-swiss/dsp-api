/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import { createResourceFormats } from "../createResourceFormats"
import changeResourceLabelResponse = createResourceFormats.changeResourceLabelResponse;

let thing: createResourceFormats.createResourceWithoutRepresentationRequest = {
    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
    "label": "A thing",
    "project_id": "http://data.knora.org/projects/anything",
    "properties": {
        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"utf8str":"Test text"}}],
        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}, {"int_value":1}],
        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}, {"decimal_value":5.7}],
        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
    }
};

let thingWithFile: createResourceFormats.createResourceWithRepresentationRequest = {
    "restype_id": "http://www.knora.org/ontology/0001/anything#Thing",
    "label": "A thing",
    "project_id": "http://data.knora.org/projects/anything",
    "properties": {
        "http://www.knora.org/ontology/0001/anything#hasText": [{"richtext_value":{"utf8str":"Test text"}}],
        "http://www.knora.org/ontology/0001/anything#hasInteger": [{"int_value":12345}],
        "http://www.knora.org/ontology/0001/anything#hasDecimal": [{"decimal_value":5.6}, {"decimal_value":5.7}],
        "http://www.knora.org/ontology/0001/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
        "http://www.knora.org/ontology/0001/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
        "http://www.knora.org/ontology/0001/anything#hasColor": [{"color_value":"#4169E1"}],
        "http://www.knora.org/ontology/0001/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
        "http://www.knora.org/ontology/0001/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
    },
    "file": {
        'originalFilename' : "myfile.jpg",
        'originalMimeType' : "image/jpeg",
        'filename' : "tmp.jpg"
    }
};

let createResourceResponse: createResourceFormats.createResourceResponse = {"res_id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw","results":{"http://www.knora.org/ontology/0001/anything#hasDecimal":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"3.3"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasDecimal"},"calendar":null,"timeval2":null,"dval":{"decimal":3.3},"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw/values/1WQOgjJWS86laX0BYKoyGw"}],"http://www.knora.org/ontology/0001/anything#hasColor":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"#ff3333"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasColor"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw/values/x5EBVPVHTReZsz-UCnZR0g"}],"http://www.knora.org/ontology/0001/anything#hasInteger":[{"value":{"dateval1":null,"ival":{"integer":1},"dateprecision1":null,"textval":{"string":"1"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasInteger"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw/values/6mbnuiGeSIWaB_sXw3-cJA"}],"http://www.knora.org/ontology/0001/anything#hasInterval":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"IntervalValueV1(0,0)"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasInterval"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw/values/VRlXZ-taQoKonY4y6_Y9wQ"}],"http://www.knora.org/ontology/0001/anything#hasDate":[{"value":{"dateval1":{"string":"2016-07-14"},"ival":null,"dateprecision1":{"string":"DAY"},"textval":{"string":"2016-07-14"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasDate"},"calendar":{"string":"GREGORIAN"},"timeval2":null,"dval":null,"dateval2":{"string":"2016-07-14 CE"},"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw"},"timeval1":null,"dateprecision2":{"string":"DAY"}},"id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw/values/M15JNh0rRjGvYrL7G257EQ"}],"http://www.knora.org/ontology/0001/anything#hasListItem":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"http://data.knora.org/anything/treeList01"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasListItem"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Bc7yXd3ETJ6SjNuq86eBdw/values/FuZ9sVvzQgywFhkf5SuE8Q"}]},"status":0}

let createResourceResponse2: createResourceFormats.createResourceResponse = {"res_id":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA","results":{"http://www.knora.org/ontology/0001/anything#hasColor":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"#ff3333"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasColor"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA/values/tBqjfhARTQm71yw962Ml6Q"}],"http://www.knora.org/ontology/0001/anything#hasInteger":[{"value":{"dateval1":null,"ival":{"integer":1},"dateprecision1":null,"textval":{"string":"1"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasInteger"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA/values/07D3Kz26TZiKPb0eo7hoZQ"}],"http://www.knora.org/ontology/0001/anything#hasInterval":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"IntervalValueV1(36000,72000)"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasInterval"},"calendar":null,"timeval2":{"decimal":72000},"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA"},"timeval1":{"decimal":36000},"dateprecision2":null},"id":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA/values/Qj4h2nT4Qy2NyCZPGhQdPA"}],"http://www.knora.org/ontology/0001/anything#hasDate":[{"value":{"dateval1":{"string":"2016-07-14"},"ival":null,"dateprecision1":{"string":"DAY"},"textval":{"string":"2016-07-14"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasDate"},"calendar":{"string":"GREGORIAN"},"timeval2":null,"dval":null,"dateval2":{"string":"2016-07-14 CE"},"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA"},"timeval1":null,"dateprecision2":{"string":"DAY"}},"id":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA/values/0HYPzuKaRpGuG4SeNr7ENQ"}],"http://www.knora.org/ontology/0001/anything#hasListItem":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"http://data.knora.org/anything/treeList01"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/0001/anything#hasListItem"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/aWaF2v-nR3ytO1s_bb0ILA/values/9Vya9AbaROu5eG4q6dVC0g"}]},"status":0};

let createResourceWithRepresentationResponse: createResourceFormats.createResourceResponse = {"res_id":"http://data.knora.org/Ox5oO8cLQiq6uSW7xjf2Ug","results":{"http://www.knora.org/ontology/knora-base#hasStillImageFileValue":[{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"schweizer.jpg"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/knora-base#hasStillImageFileValue"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Ox5oO8cLQiq6uSW7xjf2Ug"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Ox5oO8cLQiq6uSW7xjf2Ug/values/k7YTAUxXS9WIHlWjZxT6mA"},{"value":{"dateval1":null,"ival":null,"dateprecision1":null,"textval":{"string":"schweizer.jpg"},"person_id":{"string":"http://data.knora.org/users/91e19f1e01"},"property_id":{"string":"http://www.knora.org/ontology/knora-base#hasStillImageFileValue"},"calendar":null,"timeval2":null,"dval":null,"dateval2":null,"order":{"integer":1},"resource_id":{"string":"http://data.knora.org/Ox5oO8cLQiq6uSW7xjf2Ug"},"timeval1":null,"dateprecision2":null},"id":"http://data.knora.org/Ox5oO8cLQiq6uSW7xjf2Ug/values/9Y_IIgPLRT-yGDUhbH433w"}]},"status":0};

let changeResourceLabelResponse: changeResourceLabelResponse = {"res_id":"http://data.knora.org/c5058f3a","label":"Mein Zeitglöckleinnnn","status":0};