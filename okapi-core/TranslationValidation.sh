#!/bin/bash
# find all usages of getMessage() and check if there are corresponding translations in properties file.
okapi_dir=$(cd ../ && pwd)
translations=$okapi_dir'/okapi-core/src/main/resources/infra-messages/Messages_en.properties'

grep -horE 'getMessage\(\"[0-9]*\"' $okapi_dir --exclude="MessagesTest.java" | while read -r line ; do
	usages="$( echo "$line" | tr -dc '0-9' )"
	grep $translations -qe $usages || ( \
		echo "> There is no translation for $usages located in: " && \
		grep -rin "getMessage(\"$usages\"" $okapi_dir | cut -d: -f1-2 )
done
echo ">ok"
