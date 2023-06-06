#!/bin/sh

echo "***********************************************"
echo "**       Starting Bened forging system       **"
echo "***********************************************"
sleep 1

#//cd /Ems/bened/
if [ -e ./bnd.pid ]; then
    PID=`cat ./bnd.pid`
    ps -p $PID > /dev/null
    STATUS=$?
    if [ $STATUS -eq 0 ]; then
        echo "Bened server already running"
        exit 1
    fi
fi

if [ -x jdk/bin/java ]; then
    JAVA=./jdk/bin/java
else
    JAVA=java
fi
nohup ${JAVA} -Xms256m -Xmx6000m -XX:+UseG1GC -XX:+CrashOnOutOfMemoryError -cp BenedMG-1.jar:conf bened.Bened > /dev/null 2>&1 &
echo $! > ./bnd.pid
cd - > /dev/null
