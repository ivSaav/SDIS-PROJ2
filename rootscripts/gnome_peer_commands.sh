#! /usr/bin/bash

argc=$#
version="2.0"
if (( argc > 0 ))
then
	version=$1
fi

gnome-terminal --tab -- bash -c "cd build && rmiregistry"
gnome-terminal --tab -- bash -c "cd src/build && java main.g24.Peer ${version} 1 ap 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446"
gnome-terminal --tab -- bash -c "cd src/build && java main.g24.Peer ${version} 2 ap2 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446"
gnome-terminal --tab -- bash -c "cd src/build && java main.g24.Peer ${version} 3 ap3 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446"
gnome-terminal --tab -- bash -c "cd src/build && java main.g24.Peer ${version} 4 ap4 225.0.0.0:4444 230.0.0.0:4445 235.0.0.0:4446"
