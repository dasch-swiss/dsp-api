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

import { addValueFormats } from "../addValueFormats"

let addIntervalValueRequest: addValueFormats.addintervalValueRequest = {"interval_value":[0,0],"res_id":"http://data.knora.org/a-thing","prop":"http://www.knora.org/ontology /anything#hasInterval","project_id":"http://data.knora.org/projects/anything"};

let addIntervalValueResponse: addValueFormats.addValueResponse = {"userdata":{"email":"test@test.ch","username":"root","firstname":"Administrator","projects_info":[{"basepath":null,"shortname":"incunabula","description":null,"logo":"incunabula_logo.png","id":"http://data.knora.org/projects/77275339","keywords":null,"rights":null,"longname":"Bilderfolgen Basler Frühdrucke"},{"basepath":null,"shortname":"images","description":null,"logo":null,"id":"http://data.knora.org/projects/images","keywords":null,"rights":null,"longname":"Images Collection Demo"},{"basepath":null,"shortname":"anything","description":null,"logo":null,"id":"http://data.knora.org/projects/anything","keywords":null,"rights":null,"longname":"Anything Project"}],"user_id":"http://data.knora.org/users/91e19f1e01","lastname":"Admin","token":null,"active_project":null,"projects":["http://data.knora.org/projects/77275339","http://data.knora.org/projects/images","http://data.knora.org/projects/anything"],"lang":"de","password":null},"id":"http://data.knora.org/a-thing/values/7iX8sKUGQMiInDP9PF_bOw","status":0,"comment":null,"rights":8,"value":{"timeval1":0,"timeval2":0}};

let addRichtextValueRequest: addValueFormats.addRichtextValueRequest = {"richtext_value":{"utf8str":"test","textattr":"{}","resource_reference":[]},"res_id":"http://data.knora.org/a-thing","prop":"http://www.knora.org/ontology/anything#hasText","project_id":"http://data.knora.org/projects/anything"};

let addRichtextValueResponse: addValueFormats.addValueResponse = {"userdata":{"email":"test@test.ch","username":"root","firstname":"Administrator","projects_info":[{"basepath":null,"shortname":"incunabula","description":null,"logo":"incunabula_logo.png","id":"http://data.knora.org/projects/77275339","keywords":null,"rights":null,"longname":"Bilderfolgen Basler Frühdrucke"},{"basepath":null,"shortname":"images","description":null,"logo":null,"id":"http://data.knora.org/projects/images","keywords":null,"rights":null,"longname":"Images Collection Demo"},{"basepath":null,"shortname":"anything","description":null,"logo":null,"id":"http://data.knora.org/projects/anything","keywords":null,"rights":null,"longname":"Anything Project"}],"user_id":"http://data.knora.org/users/91e19f1e01","lastname":"Admin","token":null,"active_project":null,"projects":["http://data.knora.org/projects/77275339","http://data.knora.org/projects/images","http://data.knora.org/projects/anything"],"lang":"de","password":null},"id":"http://data.knora.org/a-thing/values/lfwoLx9LT7-JnA_wKjeZYg","status":0,"comment":null,"rights":8,"value":{"utf8str":"test","textattr":"{}","resource_reference":[]}};