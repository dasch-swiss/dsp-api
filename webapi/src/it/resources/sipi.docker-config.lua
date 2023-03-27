-- Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- SPDX-License-Identifier: Apache-2.0

--
-- ATTENTION: This configuration file should only be used for integration testing. It has additional routes defined!!!
--
sipi = {
    --
    -- The user under which the Sipi server should run. Use this only if Sipi should setuid to a particular user after
    -- starting. Otherwise, leave this commented out. If this setting is used, Sipi must be started as root.
    --
    -- userid = '_www',

    --
    -- Sipi's hostname as returned in the thumbnail response, default is "localhost".
    -- If sipi is run behind a proxy, then this external FQDN needs to be set here.
    --
    hostname = '0.0.0.0',

    --
    -- port number the server is listening to
    --
    port = 1024,

    --
    -- Number of threads to use
    --
    nthreads = 8,

    --
    -- SIPI is using libjpeg to generate the JPEG images. libjpeg requires a quality value which
    -- corresponds to the compression rate. 100 is (almost) no compression and best quality, 0
    -- would be full compression and no quality. Reasonable values are between 30 and 95...
    --
    jpeg_quality = 60,

    --
    -- For scaling images, SIPI offers two methods. The value "high" offers best quality using expensive
    -- algorithms: bilinear interpolation, if downscaling the image is first scaled up to an integer
    -- multiple of the requires size, and then downscaled using averaging. This results in the best
    -- image quality. "medium" uses bilinear interpolation but does not do upscaling before
    -- downscaling. If scaling quality is set to "low", then just a lookup table and nearest integer
    -- interpolation is being used to scale the images.
    -- Recognized values are: "high", "medium", "low".
    --
    scaling_quality = {
        jpeg = "medium",
        tiff = "high",
        png = "high",
        j2k = "high"
    },

    --
    -- Number of seconds a connection (socket) remains open
    --
    keep_alive = 5,

    --
    -- Maximal size of a post request
    --
    max_post_size = '250M',

    --
    -- indicates the path to the root of the image directory. Depending on the settings of the variable
    -- "prefix_as_path" the images are search at <imgroot>/<prefix>/<imageid> (prefix_as_path = TRUE)
    -- or <imgroot>/<imageid> (prefix_as_path = FALSE). Please note that "prefix" and "imageid" are
    -- expected to be urlencoded. Both will be decoded. That is, "/" will be recoignized and expanded
    -- in the final path the image file!
    --
    imgroot = '/sipi/images', -- make sure that this directory exists

    --
    -- If FALSE, the prefix is not used to build the path to the image files
    --
    prefix_as_path = true,

    --
    -- In order not to accumulate to many files into one diretory (which slows down file
    -- access considerabely), the images are stored in recursive subdirectories 'A'-'Z'.
    -- If subdir_levels is equal 0, no subdirectories are used. The maximum is 6.
    -- The recommandeation is that on average there should not me more than a few
    -- thousand files in a unix directory (your mileage may vay depending on the
    -- file system used).
    --
    subdir_levels = 0,

    --
    -- if subdir_levels is > 0 and if prefix_as_path is true, all prefixes will be
    -- regarded as directories under imgroot. Thus, the subdirs 'A'-'Z' will be
    -- created in these directories for the prefixes. However, it may make sense
    -- for certain prefixes *not* to use subdirs. A list of these prefix-directories
    -- can be given with this configuration parameter.
    --
    subdir_excludes = { "knora", "thumbs" },

    --
    -- Lua script which is executed on initialization of the Lua interpreter
    --
    initscript = '/sipi/scripts/sipi.init.lua',

    --
    -- path to the caching directory
    --
    cachedir = '/sipi/cache',

    --
    -- maximal size of the cache
    --
    cachesize = '100M',

    --
    -- if the cache becomes full, the given percentage of file space is marked for reuase
    --
    cache_hysteresis = 0.15,

    --
    -- Path to the directory where the scripts for the routes defined below are to be found
    --
    scriptdir = '/sipi/scripts',

    ---
    --- Size of the thumbnails (to be used within Lua)
    ---
    thumb_size = '!128,128',

    --
    -- Path to the temporary directory
    --
    tmpdir = '/tmp',

    --
    -- Maximum age of temporary files, in seconds (requires Knora's upload.lua).
    -- Defaults to 86400 seconds (1 day).
    --
    max_temp_file_age = 86400,

    --
    -- Path to Knora Application
    --
    knora_path = 'api',

    --
    -- Port of Knora Application
    --
    knora_port = '3333',

    --
    -- The secret for generating JWT's (JSON Web Tokens) (42 characters)
    --
    jwt_secret = 'UP 4888, nice 4-8-4 steam engine',
    --            12345678901234567890123456789012

    --
    -- Name of the logfile (a ".txt" is added...)
    --
    -- logfile = "sipi.log",


    --
    -- loglevel, one of "DEBUG", "INFO", "NOTICE", "WARNING", "ERR",
    -- "CRIT", "ALERT", "EMERG"
    --
    loglevel = "DEBUG"

}


fileserver = {
    --
    -- directory where the documents for the normal webserver are located
    --
    docroot = '/sipi/server',

    --
    -- route under which the normal webserver shouöd respond to requests
    --
    wwwroute = '/server'
}

--
-- Custom routes. Each route is an URL path associated with a Lua script.
--
routes = {
    {
        method = 'POST',
        route = '/upload',
        script = 'upload.lua'
    },
    {
        method = 'POST',
        route = '/store',
        script = 'store.lua'
    },
    {
        method = 'DELETE',
        route = '/delete_temp_file',
        script = 'delete_temp_file.lua'
    },
    {
        method = 'POST',
        route = '/upload_without_processing',
        script = 'upload_without_processing.lua'
    },
    {
        method = 'POST',
        route = '/upload_for_processing',
        script = 'upload_for_processing.lua'
    }

}

