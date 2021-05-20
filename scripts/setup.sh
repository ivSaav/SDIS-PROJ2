#! /usr/bin/bash

# Placeholder for setup script
# To be executed on the root of the compiled tree
# Requires one argument: the peer id
# Sets up the directory tree for storing 
#  both the chunks and the restored files of
#  either a single peer, in which case you may or not use the argument
#    or for all peers, in which case you 

#mv ../../tfiles/shrug.png shrug.png
#mv ../../tfiles/jojo.json jojo.json
#mv ../../tfiles/sherlock.txt sherlock.txt
#mv ../../tfiles/8k.jpg 8k.jpg
#mv ../../tfiles/16k.jpg 16k.jpg

cd keys/

rm -f *.keys *.cer truststore

# Peer 1
keytool -genkey -alias peer1 \
        -keyalg RSA -validity 7 \
        -keystore peer1.keys \
        -dname "CN=Peer 1, OU=SDIS, O=FEUP, L=Porto, ST=Porto, C=PT" \
        -storepass 123456 -keypass 123456

keytool -export -alias peer1 \
        -keystore peer1.keys -rfc \
        -file peer1.cer \
        -storepass 123456 -keypass 123456

# Peer 2
keytool -genkey -alias peer2 \
        -keyalg RSA -validity 7 \
        -keystore peer2.keys \
        -dname "CN=Peer 2, OU=SDIS, O=FEUP, L=Porto, ST=Porto, C=PT" \
        -storepass 123456 -keypass 123456

keytool -export -alias peer2 \
        -keystore peer2.keys -rfc \
        -file peer2.cer \
        -storepass 123456 -keypass 123456

# Peer 3
keytool -genkey -alias peer3 \
        -keyalg RSA -validity 7 \
        -keystore peer3.keys \
        -dname "CN=Peer 3, OU=SDIS, O=FEUP, L=Porto, ST=Porto, C=PT" \
        -storepass 123456 -keypass 123456

keytool -export -alias peer3 \
        -keystore peer3.keys -rfc \
        -file peer3.cer \
        -storepass 123456 -keypass 123456

# Peer 4
keytool -genkey -alias peer4 \
        -keyalg RSA -validity 7 \
        -keystore peer4.keys \
        -dname "CN=Peer 4, OU=SDIS, O=FEUP, L=Porto, ST=Porto, C=PT" \
        -storepass 123456 -keypass 123456

keytool -export -alias peer4 \
        -keystore peer4.keys -rfc \
        -file peer4.cer \
        -storepass 123456 -keypass 123456

# Trust Store
keytool -importcert -alias peer1cert -file peer1.cer \
        -keystore truststore \
        -storepass 123456 -keypass 123456 \
        -noprompt

keytool -importcert -alias peer2cert -file peer2.cer \
        -keystore truststore \
        -storepass 123456 -keypass 123456 \
        -noprompt

keytool -importcert -alias peer3cert -file peer3.cer \
        -keystore truststore \
        -storepass 123456 -keypass 123456 \
        -noprompt

keytool -importcert -alias peer4cert -file peer4.cer \
        -keystore truststore \
        -storepass 123456 -keypass 123456 \
        -noprompt

# Check number input arguments
#argc=$#
#
#if ((argc == 1 ))
#then
#	peer_id=$1
#else
#	echo "Usage: $0 [<peer_id>]]"
#	exit 1
#fi

# Build the directory tree for storing files
# For a crash course on shell commands check for example:
# Command line basi commands from GitLab Docs':	https://docs.gitlab.com/ee/gitlab-basics/command-line-commands.html
# For shell scripting try out the following tutorials of the Linux Documentation Project
# Bash Guide for Beginners: https://tldp.org/LDP/Bash-Beginners-Guide/html/index.html
# Advanced Bash Scripting: https://tldp.org/LDP/abs/html/

