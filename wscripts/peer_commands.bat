@echo off

start cmd /k "cd src/build && rmiregistry"
start cmd /k "cd src/build && java main.g24.Peer 1 ap1"
start cmd /k "cd src/build && java main.g24.Peer 3 ap3"
start cmd /k "cd src/build && java main.g24.Peer 2 ap2"
