#!/bin/bash

# Export moving image frames for preview in search results and on timeline
# Extract frames from mp4 file:
# - 6 frames per minute
# Create matrix file:
# - max. 36 frames per matrix
# - one matrix file corresponds to 6 minutes of film

if [ "${DEBUG:false}" == true ]; then
  set -ex
else
  set -e
fi

function printSeparator() {
  echo "---------------------------------"
}

function die() {
  echo >&2 "$@"
  exit 1
}

function usage() {
  echo "usage: <command> -i [infile]>"
  printSeparator
  echo "-i = Inpute file"
  printSeparator
}

# default variables
# current directory
dir=$(pwd)
# name of the input file for which the keyframes should be extracted
infile=''

# alternative to get some information from the source file:
# ffprobe -v quiet -print_format json -show_format -show_streams ~/sourceFile.mov

while getopts ':i:' OPTION; do
  case ${OPTION} in
  i) infile=$OPTARG ;;
  *) echo 'Unknown Parameter' ;;
  esac
done

if [ $# -eq 0 ]; then
  usage
  die ''
fi

# infile must be given and has to be an mp4 by checking if mime-type is video/mp4
if [ -z "${infile}" ]; then
  # must be given
  usage
  die "ERROR: The Input File (-i) is missing"
fi
if [ "$(file -b --mime-type "${infile}")" != "video/mp4" ]; then
  die "ERROR: The Input File (-i) is not mp4"
fi

# get the right output path, the file and the filename (without extension)
dir=$(dirname "${infile}")
file=$(basename "${infile}")
name="${file%.*}"

cd "${dir}"

# store the frames and matrix files in specific folder;
# folder has same unique name as the uploaded file
mkdir -p "${name}"
cd "${name}"

# read frame rate from input file
frame_rate=$(ffprobe -v 0 -of csv=p=0 -select_streams v:0 -show_entries stream=r_frame_rate ../"${file}")

IFS="/" read -ra array <<<"$frame_rate"
numerator="${array[0]}"
denumerator="${array[1]}"
fps=$(bc <<< "scale=2; $numerator/$denumerator")

# --
# read aspect ratio from input file
aspect=$(ffprobe -v error -select_streams v:0 -show_entries stream=display_aspect_ratio -of csv=s=x:p=0 ../"${file}")
# if aspect ratio does not exist in video file metadata
if [ -n "$aspect" ]; then
  # get aspect ratio from video dimension (width and height)
  aspectW=$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=s=x:p=0 ../"${file}")
  aspectH=$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of csv=s=x:p=0 ../"${file}")
else
  IFS=':' read -a -r array <<<"$aspect"
  aspectW="${array[0]}"
  aspectH="${array[1]}"
fi

# --
# calculate frame width, height and size
framewidth='256'
frameheight=$((framewidth * aspectH / aspectW))
framesize=${framewidth}'x'${frameheight}

# --
# check the outpath for the frames;
# if exists, delete it and create new
if [ -d "frames" ]; then
  rm -rf ./frames
fi
mkdir -p 'frames'

ffmpeg -i ../"${file}" -hide_banner -loglevel error -an -ss 0 -f image2 -s ${framesize} -vf framestep="${fps}" frames/"${name}"'_f_%d.jpg' 2>&1

# --
# create the matrix files
# change directory frames
cd frames
# Get the number of files: one image = one second of movie
# number_of_frame_files is equivalent to the movie duration (in seconds)
number_of_frame_files=$(find . -type f | wc -l)
readonly number_of_frame_files

image="${name}_f_1.jpg"

# check if file exists
if [[ ! -f "${image}" ]]; then
  die "File not found: ${image}"
fi

# grab the identify string, make sure it succeeded
IMG_CHARS=$(identify "${image}" 2> /dev/null) || die "${image} is not a proper image"
# grab width and height
IMG_CHARS=$(echo "${IMG_CHARS}" | sed -n 's/\(^.*\)\ \([0-9]*\)x\([0-9]*\)\ \(.*$\)/\2 \3/p')

width=$(echo "${IMG_CHARS}" | awk '{print $1}')
height=$(echo "${IMG_CHARS}" | awk '{print $2}')

ar=$(echo "scale=6; ${width}/${height}" | bc)
# width for the whole output matrix image: 960px
# so one preview image has a width of 160px
# calculate the height for the output matrix image form the aspect ratio
matrix_width="960"
matrix_height=$(echo "scale=0; ${matrix_width}/${ar}" | bc)
matrix_size="${matrix_width}x${matrix_height}"

frame_width="160"
frame_height=$(echo "scale=0; ${matrix_height}/6" | bc)
frame_size="${frame_width}x${frame_height}"
# get every 10th image; start with the image number 5;
# how many matrixes will it produce?
calcmatrix=$(echo "scale=4; (${number_of_frame_files}/10)/36" | bc)
# Round the exact fraction to the to the next highest integer
# for the number of matrix files to  create 'nummatrix'
interim=$(echo "${calcmatrix}+1"|bc)
readonly interim
nummatrix=${interim%.*}
readonly nummatrix

t=0
while [ ${t} -lt "${nummatrix}" ]; do
  firstframe=$(echo "scale=0; ${t}*360+1" | bc)
  MATRIX=$(find -- *_"${firstframe}.jpg")' '

  c=1
  while [ ${c} -lt 36 ]; do
    sec=$(echo "scale=0; ${firstframe}+(${c}*10)" | bc)
    if [ "${sec}" -lt "${number_of_frame_files}" ]; then
      img="${name}_f_${sec}.jpg"
      if [ -f "${img}" ]; then
        MATRIX+=${img}' '
      fi
    fi

    (( c=c+1 ))
  done

  # here we make the montage of every matrix file
  # montage -size 320x180 DB_4_5.jpg DB_4_15.jpg DB_4_25.jpg DB_4_35.jpg -geometry +0+0 montage1.jpg
  output_file="../${name}_m_${t}.jpg"
  # shellcheck disable=SC2086
  montage -size "${matrix_size}" ${MATRIX} -tile 6x6 -geometry +0+0 -resize "${frame_size}" "${output_file}" 2>&1

  (( t=t+1 ))

done
