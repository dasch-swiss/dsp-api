-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "get_knora_session"

-------------------------------------------------------------------------------
-- This function is being called from sipi before the file is served
-- Knora is called to ask for the user's permissions on the file
-- Parameters:
--    prefix: This is the prefix that is given on the IIIF url
--    identifier: the identifier for the image
--    cookie: The cookie that may be present
--
-- Returns:
--    permission:
--       'allow' : the view is allowed with the given IIIF parameters
--       'restrict:watermark=<path-to-watermark>' : Add a watermark
--       'restrict:size=<iiif-size-string>' : reduce size/resolution
--       'deny' : no access!
--    filepath: server-path where the master file is located
-------------------------------------------------------------------------------
function pre_flight(prefix,identifier,cookie)
    server.log("pre_flight called in sipi.init-knora-no-auth.lua", server.loglevel.LOG_DEBUG)

    --
    -- Allways allows access to images. No authorization from Knora is retrieved
    -- allowing to also access images that are not inside Knora
    --

    filepath = config.imgroot .. '/knora/Leaves.jp2'
    
    server.log("Returning test file " .. filepath, server.loglevel.LOG_INFO)
    
    return 'allow', filepath

end
-------------------------------------------------------------------------------
