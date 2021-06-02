#! /usr/bin/bash

argc=$#
version="2.0"
#if (( argc > 0 ))
#then
#	version=$1
#fi

#echo "Opened peers running version ${version}"

xfce4-terminal -T "rmiregistry" -e "bash -c 'cd src/build/; echo Loading RMI; rmiregistry; bash'" \
	--tab -T "ap1" -e "bash -c 'cd src/build; sleep 1; java main.g24.Peer 1 ap1 225.0.0.0:4444; bash'" \
	--tab -T "ap2" -e "bash -c 'cd src/build; sleep 2; java main.g24.Peer 2 ap2 226.0.0.0:4445; bash'" \
	--tab -T "ap3" -e "bash -c 'cd src/build; sleep 2; java main.g24.Peer 3 ap3 227.0.0.0:4446; bash'" \
	--tab -T "ap4" -e "bash -c 'cd src/build; sleep 2; java main.g24.Peer 4 ap4 227.0.0.0:4447; bash'" \
	--tab -T "ap5" -e "bash -c 'cd src/build; sleep 2; java main.g24.Peer 5 ap5 227.0.0.0:4448; bash'" &
