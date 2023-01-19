-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0
--
local img = SipiImage.new(config.imgroot .. "/Leaves8.tif", {region="full", reduce=4})
local dim = SipiImage.dims(img)

server.sendHeader("Content-Type", "image/jpeg")
SipiImage.send(img, "jpg")
