#! /usr/bin/bash

# Script for running the test app
# To be run at the root of the compiled tree
# No jar files used
# Assumes that TestApp is the main class
#  and that it belongs to the test package
# Modify as appropriate, so that it can be run
#  from the root of the compiled tree

# Check number input arguments
argc=$#

if (( argc < 1 ))
then
  pap=1
#	echo "Usage: $0 <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]"
#	exit 1
else
  pap=$1
fi

# Assign input arguments to nicely named variables



# Validate remaining arguments

# Execute the program
# Should not need to change anything but the class and its package, unless you use any jar file

# echo "java test.TestApp ${pap} ${oper} ${opernd_1} ${rep_deg}"

java -cp src/build main.g24.TestApp "${pap}" BACKUP asd.txt 2
