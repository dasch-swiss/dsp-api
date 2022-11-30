-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

function log(msg, level)
    local timestamp = os.date("!%Y-%m-%dT%H:%M:%S")
    local log_message = timestamp .. " - " .. msg
    server.log(log_message, level)
end
