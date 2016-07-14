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

import { changeValueFormats } from "../changeValueFormats"

let changeIntervalValue: changeValueFormats.changeIntervalValueRequest = {"interval_value":[0,36000],"project_id":"http://data.knora.org/projects/anything"};

let changeIntervalValueResponse: changeValueFormats.changeValueResponse = {"userdata":{"email":"test@test.ch","username":"root","firstname":"Administrator","projects_info":[{"basepath":null,"shortname":"incunabula","description":null,"logo":"incunabula_logo.png","id":"http://data.knora.org/projects/77275339","keywords":null,"rights":null,"longname":"Bilderfolgen Basler Frühdrucke"},{"basepath":null,"shortname":"images","description":null,"logo":null,"id":"http://data.knora.org/projects/images","keywords":null,"rights":null,"longname":"Images Collection Demo"},{"basepath":null,"shortname":"anything","description":null,"logo":null,"id":"http://data.knora.org/projects/anything","keywords":null,"rights":null,"longname":"Anything Project"}],"user_id":"http://data.knora.org/users/91e19f1e01","lastname":"Admin","token":null,"active_project":null,"projects":["http://data.knora.org/projects/77275339","http://data.knora.org/projects/images","http://data.knora.org/projects/anything"],"lang":"de","password":null},"id":"http://data.knora.org/a-thing/values/G58MBZ5ES7yxmKX2l5QTPg","status":0,"comment":null,"rights":8,"value":{"timeval1":0,"timeval2":36000}};

let changeFileValueRequest: changeValueFormats.changeFileValueRequest = {
    'file': {
        'originalFilename' : "myfile.jpg",
        'originalMimeType' : "image/jpeg",
        'filename' : "tmpname.jpg"
    }
};

let changeFileValueResponse: changeValueFormats.changeFileValueResponse = {"locations":[{"duration":0,"nx":128,"path":"http://localhost:1024/knora/5XTEI1z10A2-D8ojQHrMiUz.jpg/full/full/0/default.jpg","ny":72,"fps":0,"format_name":"JPEG","origname":"2016-06-26+12.26.45.jpg","protocol":"file"},{"duration":0,"nx":3264,"path":"http://localhost:1024/knora/5XTEI1z10A2-D8ojQHrMiUz.jpx/full/3264,1836/0/default.jpg","ny":1836,"fps":0,"format_name":"JPEG2000","origname":"2016-06-26+12.26.45.jpg","protocol":"file"}],"userdata":{"email":"test@test.ch","username":"root","firstname":"Administrator","projects_info":[{"basepath":null,"shortname":"incunabula","description":null,"logo":"incunabula_logo.png","id":"http://data.knora.org/projects/77275339","keywords":null,"rights":null,"longname":"Bilderfolgen Basler Frühdrucke"},{"basepath":null,"shortname":"images","description":null,"logo":null,"id":"http://data.knora.org/projects/images","keywords":null,"rights":null,"longname":"Images Collection Demo"},{"basepath":null,"shortname":"anything","description":null,"logo":null,"id":"http://data.knora.org/projects/anything","keywords":null,"rights":null,"longname":"Anything Project"}],"user_id":"http://data.knora.org/users/91e19f1e01","lastname":"Admin","token":null,"active_project":null,"projects":["http://data.knora.org/projects/77275339","http://data.knora.org/projects/images","http://data.knora.org/projects/anything"],"lang":"de","password":null},"status":0};