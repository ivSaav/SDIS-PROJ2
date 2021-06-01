#! /usr/bin/bash

# Script for running the backup protocol
# To be run at the root of the compiled tree
# No jar files used
# Assumes that TestApp is the main class
#  and that it belongs to the test package

# Check number input arguments
argc=$#

# fetching access point
if (( argc < 1 ))
then
  pap="ap1" # default is ap1
else
  pap=$1
fi

java -cp src/build main.g24.TestApp "${pap}" RESTORE shrug.png 
