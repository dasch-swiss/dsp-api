#!/bin/bash

# get the tag from volatile-status.txt
EXTRACTED_TAG=$(awk '/BUILD_SCM_TAG/ {print $2}' "$1")

# exchange the placeholder 'BUILD_TAG' with the 'EXTRACTED_TAG' extracted in the previous step
sed "s/{BUILD_TAG}/$EXTRACTED_TAG/" "$2"
