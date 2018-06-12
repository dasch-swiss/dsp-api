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
import tempfile
import os

# Tests file conversions.

class TestConversions:

    component = "The Sipi command-line program"

    reference_tif_tmpl = "iso-15444-4/reference_jp2/jp2_{}.tif"
    reference_jp2_tmpl = "iso-15444-4/testfiles_jp2/file{}.jp2"
    sipi_jp2_tmpl = "sipi_file{}.jp2"
    sipi_tif_tmpl = "sipi_jp2_{}.tif"
    sipi_round_trip_tmpl = "sipi_sipi_jp2_{}.tif"

    def test_iso_15444_4_decode_jp2(self, manager):
        """convert ISO/IEC 15444-4 reference JPEG2000 images to TIFF"""

        results = "\n"
        bad_result = False
        tempdir = tempfile.mkdtemp()

        # Skip:
        # - file 3 (https://github.com/dhlab-basel/Sipi/issues/152)
        # - files 2 and 5-9 (https://github.com/ImageMagick/ImageMagick/issues/409)

        for i in [1, 4]:
            reference_jp2 = manager.data_dir_path(self.reference_jp2_tmpl.format(i))
            reference_tif = manager.data_dir_path(self.reference_tif_tmpl.format(i))
            sipi_tif = os.path.join(tempdir, self.sipi_tif_tmpl.format(i))

            manager.sipi_convert(reference_jp2, sipi_tif, "tif")
            pae = manager.compare_images(sipi_tif, reference_tif, "PAE")

            results += "Image {}: Converted JP2 -> TIFF\n    Reference JP2: {}\n    Reference TIFF: {}\n    Sipi TIFF: {}\n    PAE (Sipi TIFF compared to reference TIFF): {}\n\n".format(i, reference_jp2, reference_tif, sipi_tif, pae)

            if pae > 0:
                bad_result = True

        assert not bad_result, results

    def test_iso_15444_4_round_trip(self, manager):
        """convert ISO/IEC 15444-4 reference TIFF images to JPEG2000 and back"""

        results = "\n"
        bad_result = False
        tempdir = tempfile.mkdtemp()

        for i in [1, 2, 3, 4, 5, 6, 7, 8, 9]:

            reference_tif = manager.data_dir_path(self.reference_tif_tmpl.format(i))
            sipi_jp2 = os.path.join(tempdir, self.sipi_jp2_tmpl.format(i))
            sipi_tif = os.path.join(tempdir, self.sipi_tif_tmpl.format(i))
            sipi_round_trip_tif = os.path.join(self.sipi_round_trip_tmpl.format(i))

            manager.sipi_convert(reference_tif, sipi_jp2, "jpx")
            manager.sipi_convert(sipi_jp2, sipi_tif, "tif")
            pae = manager.compare_images(sipi_tif, reference_tif, "PAE")

            results += "Image {}: Converted TIFF -> JP2 -> TIFF\n    Reference TIFF: {}\n    Sipi JP2: {}\n    Sipi TIFF: {}\n    PAE (Sipi TIFF compared to reference TIFF): {}\n\n".format(i, reference_tif, sipi_jp2, sipi_tif, pae)

            if pae > 0:
                bad_result = True

        assert not bad_result, results
