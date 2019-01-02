#!/bin/bash
# create temp dir (no, done by caller)
# git clone or git pull
echo RUNNING $0 $*
for i in $(seq 5); do
    echo $i
    sleep 3
done
exit 1
