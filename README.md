# SDIS Project

SDIS Project for group T4G24.

Group members:

1. Ivo Saavedra (up201707093@edu.fe.up.pt)
2. Telmo Batista (up201806554@edu.fe.up.pt)
3. Tiago Duarte da Silva (up201806516@edu.fe.up.pt)

## COMPILATION
For compiling and executing this project, please use JDK 15.
From the project's root run:
rootscripts/compile.sh


## EXECUTION
All commands should be run in the project's root.

To start the peers and the rmiregistry:
rootscripts/xfce_peer_commands.sh 	(for xfce terminals)
rootscripts/gnome_peer_commands.sh 	(for gnome terminals)


To start one of the protocols run:
rootscripts/test.sh <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]
<peer_ap> - peer's access point
BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]] - protocol name and respective arguments


## TEST CASES
All test cases are run from the project's root
All test files should be placed under the ./src/build/ folder.

To test one of the protocols:
rootscripts/test.sh <peer_ap> BACKUP|RESTORE|DELETE|RECLAIM|STATE [<opnd_1> [<optnd_2]]


## EXAMPLES
To backup file.txt with a replication degree of 2:
rootscripts/test.sh ap1 BACKUP file.txt 2

To delete file.txt:
rootscripts/test.sh ap1 DELETE file.txt

To restore file.txt:
rootscripts/test.sh ap1 RESTORE file.txt

To reclaim all disk space in a peer:
rootscripts/test.sh ap1 RECLAIM 0


## STORAGE
```
PeerID/
├─ stored/
│  ├─ fileHash
├─ restored/
│  ├─ restoredFile
```

Each peer's information is stored in a folder with its ID in src/build/peers/peerID.
Inside each peer's folder are the stored, restored directories.
In the stored folder are all the sotred files for the peer.
The restored files are kept in the restored folder.
