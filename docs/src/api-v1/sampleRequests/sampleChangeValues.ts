/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import { changeValueFormats } from "../changeValueFormats"

let changeIntervalValue: changeValueFormats.changeIntervalValueRequest = {"interval_value":[0,36000],"project_id":"http://rdfh.ch/projects/anything"};

let changeIntervalValueResponse: changeValueFormats.changeValueResponse = {"id":"http://rdfh.ch/a-thing/values/G58MBZ5ES7yxmKX2l5QTPg","status":0,"comment":null,"rights":8,"value":{"timeval1":0,"timeval2":36000}};

let changeFileValueRequest: changeValueFormats.changeFileValueRequest = {
    "file": "3UIsXH9bP0j-BV0D4sN51Xz.jp2"
};

let changeFileValueResponse: changeValueFormats.changeFileValueResponse = {"locations":[{"duration":0,"nx":128,"path":"http://localhost:1024/knora/5XTEI1z10A2-D8ojQHrMiUz.jpg/full/max/0/default.jpg","ny":72,"fps":0,"format_name":"JPEG","origname":"2016-06-26+12.26.45.jpg","protocol":"file"},{"duration":0,"nx":3264,"path":"http://localhost:1024/knora/5XTEI1z10A2-D8ojQHrMiUz.jpx/full/3264,1836/0/default.jpg","ny":1836,"fps":0,"format_name":"JPEG2000","origname":"2016-06-26+12.26.45.jpg","protocol":"file"}],"status":0};

let changeValueComment: changeValueFormats.changeValueCommentRequest = {"project_id":"http://rdfh.ch/projects/77275339","comment":"dumm"};
