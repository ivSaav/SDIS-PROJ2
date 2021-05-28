@echo off

start cmd /k "cd src/build && rmiregistry"
start cmd /k "cd src/build && java main.g24.Peer 1 ap1 225.0.0.0:9"
timeout /t 2
start cmd /k "cd src/build && java main.g24.Peer 2 ap2 225.0.0.0:18"
timeout /t 3
start cmd /k "cd src/build && java main.g24.Peer 3 ap3 225.0.0.0:19"
