#!/bin/bash

# Export moving image frames for preview in search results and on timeline
# Extract frames from mp4 file:
# - 6 frames per minute
# Create matrix file:
# - max. 36 frames per matrix
# - one matrix file corresponds to 6 minutes of film

set -e

dir=$(pwd)
sep='---------------------------------'

die() {
	echo >&2 "$@"
	exit 1
}

usage() {
	echo "usage: <command> -i [infile]>"
	echo ${sep}
	echo "-i = Inpute file"
	echo ${sep}
}

collect() {
	film=''
	cnt=1
	#for b in `ls`; do
	for file in *.$1; do
		[ -f ${file} ]
		name=$2

		for c in ${cnt}; do
			if [ "$film" == '' ] || [ "$film" == 'VIDEO_TS.VOB' ]; then
				film=${file}
			else
				case $3 in
				true) film="$film|$file" ;;
				esac
			fi
		done
		cnt=$(expr ${cnt} + 1)
	done
	needle="|"
	num=$(grep -o "$needle" <<<"$film" | wc -l)
	num=$(expr ${num} + 1)
}

# default variables
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

# check the arguments: if they exist
num='^[0-9]+$'

# infile must be given and has to be an mp4
if [ -z "${infile}" ]; then
	# must be given
	usage
	die "ERROR: The Input File (-i) is missing"
fi

# check if mime-type is video/mp4
if file -b --mime-type "${infile}" -ne "video/mp4"; then
	die "ERROR: The Input File (-i) is not mp4"
fi

# get the right output path, the file and the filename (without extension)
dir=$(dirname "${infile}")
file=$(basename "${infile}")
name="${file%.*}"

cd $dir

# --
# store the frames and matrix files in specific folder;
# folder has same unique name as the uploaded file
mkdir -p $name
cd $name

# --
# read frame rate from input file
framerate=$(ffprobe -v 0 -of csv=p=0 -select_streams v:0 -show_entries stream=r_frame_rate ../"${file}")

IFS="/" read -a array <<<"$framerate"
numerator="${array[0]}"
denumerator="${array[1]}"

fps=$(expr "scale=2;${numerator}/${denumerator}" | bc)

# --
# read aspect ratio from input file
aspect=$(ffprobe -v error -select_streams v:0 -show_entries stream=display_aspect_ratio -of csv=s=x:p=0 ../"${file}")

# if aspect ratio does not exist in video file metadata
if [ ! -z {$aspect} ]; then
	# get aspect ratio from video dimension (width and height)
	aspectW=$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=s=x:p=0 ../"${file}")
	aspectH=$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of csv=s=x:p=0 ../"${file}")
else 
	IFS=':' read -a array <<<"$aspect"
	aspectW="${array[0]}"
	aspectH="${array[1]}"
fi

# --
# framesize
framewidth='256'
frameheight=$(($framewidth * $aspectH / $aspectW))
framesize=${framewidth}'x'${frameheight}

echo ${sep}
echo 'Start with frame export'
# check the outpath for the frames;
# if exists, delete it and create new
if [ -d "frames" ]; then
	rm -rf ./frames
fi
mkdir -p 'frames'

framestart=$(echo ${start} + 1 | bc)
ffmpeg -i ../${file} -an -ss 1 -f image2 -s ${framesize} -vf framestep=${fps} frames/${name}'_f_%d.jpg' 2>&1

echo 'Done'
echo ${sep}

# --
# create the matrix files
echo 'Start with creating matrix file'

# change directory frames
cd frames
# Get the number of files: one image = one second of movie
# numfiles is equivalent to the movie duration (in seconds)
numfiles=(*)
numfiles=${#numfiles[@]}

image="${name}_f_1.jpg"

# check if file exists
if [[ ! -f "${image}" ]]; then
	die "File not found: ${image}"
fi

# grab the identify string, make sure it succeeded
# IMG_CHARS=$(identify "${image}" 2> /dev/null) || die "${image} is not a proper image"
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
calcmatrix=$(echo "scale=4; (${numfiles}/10)/36" | bc)
echo '#Matrix (calculated) '${calcmatrix}
nummatrix=$(echo ${calcmatrix} | awk '{printf("%.0f\n",$1 + 0.75)}')
echo '#Matrix (rounded) '${nummatrix}

t=0
while [ ${t} -lt ${nummatrix} ]; do
	echo 'Matrix nr '${t}
	firstframe=$(echo "scale=0; ${t}*360+5" | bc)
	MATRIX=$(find *_${firstframe}.jpg)' '

	c=1
	while [ ${c} -lt 36 ]; do
		sec=$(echo "scale=0; ${firstframe}+(${c}*10)" | bc)
		if [ $sec -lt $numfiles ]; then
			img="${name}_f_${sec}.jpg"
			if [ -f ${img} ]; then
				MATRIX+=${img}' '
			fi
		fi

		let c=c+1
	done

	# here we make the montage of every matrix file
	# montage -size 320x180 DB_4_5.jpg DB_4_15.jpg DB_4_25.jpg DB_4_35.jpg -geometry +0+0 montage1.jpg
	# $(echo "montage -size ${matrix_size} ${MATRIX} -tile 6x6 -geometry +0+0 -resize ${frame_size} ../matrix/${name}'_m_'${t}'.jpg'")
	montage -size ${matrix_size} ${MATRIX} -tile 6x6 -geometry +0+0 -resize ${frame_size} ../${name}'_m_'${t}'.jpg' 2>&1

	let t=t+1

done

echo 'Done'
echo ${sep}
