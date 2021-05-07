#! /usr/bin/bash

argc=$#
version="2.0"
if (( argc > 0 ))
then
	version=$1
fi

echo "Opened peers running version ${version}"

xfce4-terminal -T "rmiregistry" -e "bash -c 'cd src/build/; echo Loading RMI; rmiregistry; bash'" \
	--tab -T "ap1" -e "bash -c 'cd src/build; java main.g24.Peer ${version} 1 ap1 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446; bash'" \
	--tab -T "ap2" -e "bash -c 'cd src/build; java main.g24.Peer ${version} 2 ap2 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446; bash'" \
	--tab -T "ap3" -e "bash -c 'cd src/build; java main.g24.Peer ${version} 3 ap3 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446; bash'" \
	--tab -T "ap4" -e "bash -c 'cd src/build; java main.g24.Peer ${version} 4 ap4 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446; bash'" &
