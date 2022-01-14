-- * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

-- Knora GUI-case: create a thumbnail

require "send_response"

server.setBuffer()

for imgindex,imgparam in pairs(server.uploads) do

end

result = {
    me = "Lukas",
    you = "not lukas"
}

send_success(result)
