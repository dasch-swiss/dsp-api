# Copyright © 2016 Lukas Rosenthaler, Andrea Bianco, Benjamin Geer,
# Ivan Subotic, Tobias Schweizer, André Kilchenmann, and André Fatton.
# This file is part of Sipi.
# Sipi is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# Sipi is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# Additional permission under GNU AGPL version 3 section 7:
# If you modify this Program, or any covered work, by linking or combining
# it with Kakadu (or a modified version of that library) or Adobe ICC Color
# Profiles (or a modified version of that library) or both, containing parts
# covered by the terms of the Kakadu Software Licence or Adobe Software Licence,
# or both, the licensors of this Program grant you additional permission
# to convey the resulting work.
# See the GNU Affero General Public License for more details.
# You should have received a copy of the GNU Affero General Public
# License along with Sipi.  If not, see <http://www.gnu.org/licenses/>.

import pytest

# Tests basic functionality of the Sipi server.

class TestServer:
    component = "The Sipi server"

    def test_sipi_starts(self, manager):
        """start"""
        assert manager.sipi_is_running()

    def test_sipi_log_output(self, manager):
        """add routes"""
        assert "Added route" in manager.get_sipi_output()

    def test_lua_functions(self, manager):
        """call C++ functions from Lua scripts"""
        manager.expect_status_code("/test_functions", 200)

    def test_lua_scripts(self, manager):
        """call Lua functions for mediatype handling"""
        manager.expect_status_code("/test_mediatype", 200)

    def test_knora_session_parsing(self, manager):
        """call Lua function that gets the Knora session id from the cookie header sent to Sipi"""
        manager.expect_status_code("/test_knora_session_cookie", 200)

    def test_file_bytes(self, manager):
        """return an unmodified JPG file"""
        manager.compare_server_bytes("/knora/Leaves.jpg/full/full/0/default.jpg", manager.data_dir_path("knora/Leaves.jpg"))

    def test_restrict(self, manager):
        """return a restricted image in a smaller size"""
        image_info = manager.get_image_info("/knora/RestrictLeaves.jpg/full/full/0/default.jpg")
        page_geometry = [line.strip().split()[-1] for line in image_info.splitlines() if line.strip().startswith("Page geometry:")][0]
        assert page_geometry == "128x128+0+0"

    def test_deny(self, manager):
        """return 401 Unauthorized if the user does not have permission to see the image"""
        manager.expect_status_code("/knora/DenyLeaves.jpg/full/full/0/default.jpg", 401)

    def test_thumbnail(self, manager):
        """accept a POST request to create a thumbnail with Content-Type: multipart/form-data"""
        response_json = manager.post_file("/make_thumbnail", manager.data_dir_path("knora/Leaves.jpg"), "image/jpeg")
        filename = response_json["filename"]
        manager.expect_status_code("/thumbs/{}.jpg/full/full/0/default.jpg".format(filename), 200)

        # given the temporary filename, create the file
        params = {
            "filename": filename,
            "originalfilename": "Leaves.jpg",
            "originalmimetype": "image/jpeg"
        }

        response_json2 = manager.post_request("/convert_from_file", params)
        filename_full = response_json2["filename_full"]
        filename_thumb = response_json2["filename_thumb"]

        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_full), 200)
        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_thumb), 200)

    def test_image_conversion(self, manager):
        """ convert and store an image file"""

        params = {
            "originalfilename": "Leaves.jpg",
            "originalmimetype": "image/jpeg",
            "source": manager.data_dir_path("knora/Leaves.jpg")
        }

        response_json = manager.post_request("/convert_from_binaries", params)

        filename_full = response_json["filename_full"]
        filename_thumb = response_json["filename_thumb"]

        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_full), 200)
        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_thumb), 200)


    def test_lausanne_thumbnail_noalpha(self, manager):
        """create thumbnail with Lausanne file - no alpha"""
        response_json = manager.post_file("/make_thumbnail", manager.data_dir_path("knora/Leaves-small-no-alpha.tif"), "image/tiff")
        # print("\n==>>" + str(response_json))
        filename = response_json["filename"]
        manager.expect_status_code("/thumbs/{}.jpg/full/full/0/default.jpg".format(filename), 200)

        # given the temporary filename, create the file
        params = {
            "filename": filename,
            "originalfilename": "Leaves-small-no-alpha.tif",
            "originalmimetype": "image/tiff"
        }

        response_json2 = manager.post_request("/convert_from_file", params)
        filename_full = response_json2["filename_full"]
        filename_thumb = response_json2["filename_thumb"]

        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_full), 200)
        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_thumb), 200)

    def test_lausanne_thumbnail_alpha(self, manager):
        """create thumbnail with Lausanne file - with alpha"""
        response_json = manager.post_file("/make_thumbnail", manager.data_dir_path("knora/Leaves-small-alpha.tif"), "image/tiff")
        # print("\n==>>" + str(response_json))
        filename = response_json["filename"]
        manager.expect_status_code("/thumbs/{}.jpg/full/full/0/default.jpg".format(filename), 200)

        # given the temporary filename, create the file
        params = {
            "filename": filename,
            "originalfilename": "Leaves-small-alpha.tif",
            "originalmimetype": "image/tiff"
        }

        response_json2 = manager.post_request("/convert_from_file", params)
        filename_full = response_json2["filename_full"]
        filename_thumb = response_json2["filename_thumb"]

        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_full), 200)
        manager.expect_status_code("/knora/{}/full/full/0/default.jpg".format(filename_thumb), 200)

    def test_concurrency(self, manager):
        """handle many concurrent requests for different URLs (this may take a while, please be patient)"""

        # The command-line arguments we want to pass to ab for each process.
        
        filename = "load_test.jpx"

        ab_processes = [
            {
                "concurrent_requests": 5,
                "total_requests": 10,
                "url_path": "/knora/{}/full/full/0/default.jpg".format(filename)
            },
            {
                "concurrent_requests": 5,
                "total_requests": 10,
                "url_path": "/knora/{}/full/pct:50/0/default.jpg".format(filename)
            },
            {
                "concurrent_requests": 5,
                "total_requests": 10,
                "url_path": "/knora/{}/full/full/90/default.jpg".format(filename)
            },
            {
                "concurrent_requests": 25,
                "total_requests": 30,
                "url_path": "/knora/{}/pct:10,10,40,40/full/0/default.jpg".format(filename)
            },
            {
                "concurrent_requests": 25,
                "total_requests": 30,
                "url_path": "/knora/{}/pct:10,10,50,30/full/180/default.jpg".format(filename)
            }
        ]

        # Start all the ab processes.

        for process_info in ab_processes:
            process_info["process"] = manager.run_ab(process_info["concurrent_requests"], process_info["total_requests"], 300, process_info["url_path"])

        # Wait for all the processes to terminate, and get their return codes and output.

        for process_info in ab_processes:
            process = process_info["process"]

            # stderr has been redirected to stdout and is therefore None
            # do not use wait() because of the danger of a deadlock: https://docs.python.org/3.5/library/subprocess.html#subprocess.Popen.wait
            stdout, stderr = process.communicate(timeout=300)

            process_info["returncode"] = process.returncode
            process_info["stdout"] = stdout

        # Check whether they all succeeded.

        bad_result = False
        failure_results = "\n"

        for process_info in ab_processes:
            stdout_str = process_info["stdout"].decode("ascii", "ignore") # Strip out non-ASCII characters, because for some reason ab includes an invalid 0xff
            non_2xx_responses_lines = [line.strip().split()[-1] for line in stdout_str.splitlines() if line.strip().startswith("Non-2xx responses:")]

            if len(non_2xx_responses_lines) > 0:
                bad_result = True
                failure_results += "Sipi returned a non-2xx response for URL path {}, returncode {}, and output:\n{}".format(process_info["url_path"], process_info["returncode"], stdout_str)

            if process_info["returncode"] != 0:
                bad_result = True
                failure_results += "Failed ab command with URL path {}, returncode {}, and output:\n{}".format(process_info["url_path"], process_info["returncode"], stdout_str)

        if bad_result:
            failure_results += "\nWrote Sipi log file " + manager.sipi_log_file

        assert not bad_result, failure_results
