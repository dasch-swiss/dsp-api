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

import { valueResponseFormats } from "../valueResponseFormats"

let getDecimalValue: valueResponseFormats.valueResponse = {"valuetype":"http://www.knora.org/ontology/knora-base#DecimalValue","valuecreatorname":"Administrator-alt Admin-alt","valuecreationdate":"2016-03-02T15:05:10Z","valuecreator":"root-alt@example.com","status":0,"comment":null,"rights":8,"value":4.4};

let getTextValue: valueResponseFormats.valueResponse = {"valuetype":"http://www.knora.org/ontology/knora-base#TextValue","valuecreatorname":"Administrator-alt Admin-alt","valuecreationdate":"2016-03-02T15:05:10Z","valuecreator":"root-alt@example.com","status":0,"comment":null,"rights":8,"value":{"utf8str":"test"}};

let getDateValue: valueResponseFormats.valueResponse = {"valuetype":"http://www.knora.org/ontology/knora-base#DateValue","valuecreatorname":"Administrator-alt Admin-alt","valuecreationdate":"2016-03-02T15:05:10Z","valuecreator":"root-alt@example.com","status":0,"comment":null,"rights":2,"value":{"dateval1":"1492","calendar":"JULIAN","era1":"CE","dateval2":"1492","era2":"CE"}};

let getIntervalValue: valueResponseFormats.valueResponse = {"valuetype":"http://www.knora.org/ontology/knora-base#IntervalValue","valuecreatorname":"Administrator-alt Admin-alt","valuecreationdate":"2016-03-02T15:05:10Z","valuecreator":"root-alt@example.com","status":0,"comment":null,"rights":8,"value":{"timeval1":0,"timeval2":0}};

let valueVersionResponse: valueResponseFormats.valueVersionsResponse = {"valueVersions":[{"valueObjectIri":"http://data.knora.org/v_3QOWcDTeu2mgPW8DRzUg/values/ox6hH8abS7u3l50hMzoZlQ","valueCreationDate":"2016-07-15T10:15:08.131+02:00","previousValue":"http://data.knora.org/v_3QOWcDTeu2mgPW8DRzUg/values/2nEUilUVTROmuEoTkOhfNg"},{"valueObjectIri":"http://data.knora.org/v_3QOWcDTeu2mgPW8DRzUg/values/2nEUilUVTROmuEoTkOhfNg","valueCreationDate":"2016-07-15T09:19:19.304+02:00","previousValue":null}],"status":0};

let linkResponse: valueResponseFormats.linkResponse = {"valuetype":"http://www.knora.org/ontology/knora-base#LinkValue","valuecreatorname":"Administrator-alt Admin-alt","valuecreationdate":"2016-03-02T15:05:10Z","valuecreator":"root-alt@example.com","status":0,"comment":null,"rights":2,"value":{"subjectIri":"http://data.knora.org/8a0b1e75","predicateIri":"http://www.knora.org/ontology/0803/incunabula#partOf","objectIri":"http://data.knora.org/c5058f3a","referenceCount":1}};