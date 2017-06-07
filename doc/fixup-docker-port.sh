#!/bin/sh
# Makes Docker on Ubuntu listen on tcp port 4243
DF=/lib/systemd/system/docker.service
if test ! -f $DF; then
	echo "$DF does not exist"
	exit 1
fi
if test ! -w $DF; then
	echo "$DF is not writable"
	exit 1
fi
sed 's@^ExecStart=/usr/bin/dockerd -H fd://$@ExecStart=/usr/bin/dockerd -H fd:// -H tcp://127.0.0.1:4243@' < $DF >x
if diff x $DF >/dev/null; then
	echo "$DF already up to date"
else
	cp x $DF
	echo "$DF updated"
	systemctl daemon-reload
	systemctl restart docker
fi


