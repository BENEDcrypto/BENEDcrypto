#!/bin/sh
#//cd /Ems/bened/

if [ -e ./bnd.pid ]; then
    PID=`cat ./bnd.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    echo "stopping"
    while [ $STATUS -eq 0 ]; do
        kill `cat ./bnd.pid` > /dev/null
        sleep 5
        ps -p $PID > /dev/null
        STATUS=$?
    done
    rm -f ./bnd.pid
    echo "Bened server stopped"
fi

