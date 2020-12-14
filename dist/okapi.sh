#!/bin/bash
#
#  Configure and start Okapi.
#


[ -f /etc/default/okapi ] && . /etc/default/okapi

if [ -f "${CONF_DIR}/okapi.conf" ] ; then
   . "${CONF_DIR}/okapi.conf"
else
   echo "Cannot locate ${CONF_DIR}/okapi.conf"
   exit 2
fi


DATA_DIR="${DATA_DIR:-/var/lib/okapi}"
LIB_DIR="${LIB_DIR:-/usr/share/folio/okapi/lib}"
OKAPI_JAR="${LIB_DIR}/okapi-core-fat.jar"
# Copy from deprecated postgres_user into postgres_username
postgres_username="${postgres_username:-${postgres_user}}"

parse_okapi_conf()  {

   if [ "$role" == "dev" ] || [ "$role" == "cluster" ]; then

      # storage backend options
      if [ "$storage" == "postgres" ]; then
         OKAPI_JAVA_OPTS+=" -Dstorage=postgres"
         # Set defaults
         postgres_username="${postgres_username:-okapi}"
         postgres_host="${postgres_host:-localhost}"
         postgres_port="${postgres_port:-5432}"
         postgres_password="${postgres_password:-okapi25}"
         postgres_database="${postgres_database:-okapi}"
      else
         OKAPI_JAVA_OPTS+=" -Dstorage=inmemory"
      fi

      # Validate JSON of $docker_registries, undefined is valid
      echo "$docker_registries" | jq empty
      if [ $? -ne 0 ]; then
         echo "Exiting because \$docker_registries is invalid"
         exit 3
      fi

      # create runtime configuration file for sensitive information
      # Store auth here rather than exposing on command line
      if [ "$storage" == "postgres" ] || [ "$docker_registries" ]; then
         OKAPI_OPTIONS+=" -conf ${PID_DIR}/okapi-runtime.conf"
         rm -f "${PID_DIR}/okapi-runtime.conf" > /dev/null 2>&1
         touch "${PID_DIR}/okapi-runtime.conf"
         chmod 600 "${PID_DIR}/okapi-runtime.conf"
         # include postgres connection arguments only if defined
         jq -n --arg WARNING "AUTOMATICALLY CREATED FILE, DO NOT EDIT!"    \
               --argjson dockerRegistries  "${docker_registries:-[{\}]}"   \
               --arg postgres_host         "${postgres_host}"              \
               --arg postgres_port         "${postgres_port}"              \
               --arg postgres_username     "${postgres_username}"          \
               --arg postgres_password     "${postgres_password}"          \
               --arg postgres_database     "${postgres_database}"          \
               --arg postgres_server_pem   "${postgres_server_pem}"        \
               "{\$WARNING,
                 \$dockerRegistries
                 ${postgres_host+,\$postgres_host}
                 ${postgres_port+,\$postgres_port}
                 ${postgres_username+,\$postgres_username}
                 ${postgres_password+,\$postgres_password}
                 ${postgres_database+,\$postgres_database}
                 ${postgres_server_pem+,\$postgres_server_pem}
               }" > "${PID_DIR}/okapi-runtime.conf"
         if [ $? -ne 0 ]; then
            echo "Exiting because jq could not generate okapi-runtime.conf"
            exit 3
         fi
         echo "${PID_DIR}/okapi-runtime.conf = "
         cat "${PID_DIR}/okapi-runtime.conf" | sed 's/\(password":\|identitytoken":\).*[^,]/\1 .../g'
      fi

   fi

   # if role is not set to 'dev', get cluster options
   if [ "$role" != "dev" ]; then
      if [ "$nodename" ]; then
         OKAPI_JAVA_OPTS+=" -Dnodename=${nodename}"
      else
         NODENAME=`hostname`
         OKAPI_JAVA_OPTS+=" -Dnodename=${NODENAME}"
      fi

      if [ "$cluster_interface" ]; then
         CLUSTER_IP=`ip addr show dev $cluster_interface | grep ' inet ' \
                    | awk '{ print $2 }' | awk -F "/" '{ print $1 }'`
         CLUSTER_OPTIONS+=" -cluster-host $CLUSTER_IP"
      fi

      if [ "$cluster_port" ]; then
         CLUSTER_OPTIONS+=" -cluster-port $cluster_port"
      fi

      if [ "$cluster_config" ]; then
         CLUSTER_OPTIONS+=" $cluster_config"
      fi

      if [  -n "$CLUSTER_OPTIONS" ]; then
         OKAPI_OPTIONS+=" $CLUSTER_OPTIONS"
      fi

      OKAPI_JAVA_OPTS+=" --add-modules java.se"
      OKAPI_JAVA_OPTS+=" --add-exports java.base/jdk.internal.ref=ALL-UNNAMED"
      OKAPI_JAVA_OPTS+=" --add-opens java.base/java.lang=ALL-UNNAMED"
      OKAPI_JAVA_OPTS+=" --add-opens java.base/java.nio=ALL-UNNAMED"
      OKAPI_JAVA_OPTS+=" --add-opens java.base/sun.nio.ch=ALL-UNNAMED"
      OKAPI_JAVA_OPTS+=" --add-opens java.management/sun.management=ALL-UNNAMED"
      OKAPI_JAVA_OPTS+=" --add-opens jdk.management/com.ibm.lang.management.internal=ALL-UNNAMED"
      OKAPI_JAVA_OPTS+=" --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"
   fi

   # Set performance metric options
   if [ "$enable_metrics" == 1 ]; then
      OKAPI_OPTIONS+=" -enable-metrics"
   fi

   # configure log file if specified
   if [ "$log4j_config" ]; then
      OKAPI_JAVA_OPTS+=" -Dhazelcast.logging.type=log4j"
      OKAPI_JAVA_OPTS+=" -Dlog4j.configurationFile=${log4j_config}"
   fi

   # configure okapi host
   if [ "$host" ]; then
      OKAPI_JAVA_OPTS+=" -Dhost=${host}"
   fi

   # configure okapi http port
   # Environment variables with periods/dots in their names are deprecated
   # because a period is not POSIX compliant and therefore some shells, notably,
   # the BusyBox /bin/sh included in Alpine Linux, strip them:
   # https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html
   # https://github.com/docker-library/docs/tree/master/openjdk#environment-variables-with-periods-in-their-names
   # https://wiki.ubuntu.com/DashAsBinSh
   # https://wiki.ubuntu.com/DashAsBinSh/Spec
   # Java property variable names allow periods/dots.
   if [ "$http_port" ]; then
      OKAPI_JAVA_OPTS+=" -Dhttp.port=${http_port}"
   elif [ "$port" ]; then
      OKAPI_JAVA_OPTS+=" -Dhttp.port=${port}"
   fi

   # configure module port range
   if [ "$port_start" ] && [ "$port_end" ]; then
      OKAPI_JAVA_OPTS+=" -Dport_start=${port_start} -Dport_end=${port_end}"
   fi

   # configure dockerURL
   if [ "$dockerurl" ] ; then
      OKAPI_JAVA_OPTS+=" -DdockerUrl=${dockerurl}"
   fi

   # configure okapi URL
   if [ "$okapiurl" ]; then
      OKAPI_JAVA_OPTS+=" -Dokapiurl=${okapiurl}"
   fi

   # configure Vert.x cache dir
   if [ "$vertx_cache_dir_base" ]; then
      OKAPI_JAVA_OPTS+=" -Dvertx.cacheDirBase=${vertx_cache_dir_base}"
   fi

}   # end parse_okapi_conf



# Look for Java and check version
java_check() {
   if [ -n "$JAVA_HOME" -a -x "$JAVA_HOME/bin/java" ]; then
      JAVA="$JAVA_HOME/bin/java"
   else
      JAVA=`which java`
   fi

   if [ ! -x "$JAVA" ]; then
      echo "Could not find any executable java binary."
      echo "Install java in your PATH or set JAVA_HOME"
      exit 1
   fi
}  # end java_check

# initialize the Okapi database
init_db() {
   # Postgres instance check
   if command -v psql >/dev/null; then
      psql postgresql://${postgres_username}:${postgres_password}@${postgres_host}:${postgres_port}/${postgres_database}?connect_timeout=5 > /dev/null 2>&1 << EOF
\q
EOF
      POSTGRES_RETVAL=$?
      if [ "$POSTGRES_RETVAL" != 0 ]; then
         echo "Postgres check failed.  Verify that the Postgres instance"
         echo "configured in okapi.conf is available and the okapi database"
         echo "and user has been configured. Then re-run:"
         echo "    /usr/share/folio/okapi/bin/okapi.sh --initdb"
         echo "Postgres configuration:"
         echo "   postgres_username: $postgres_username"
         echo "   postgres_password: ${postgres_password+...}"
         echo "   postgres_host:     $postgres_host"
         echo "   postgres_port:     $postgres_port"
         echo "   postgres_database: $postgres_database"
         exit 2
      else
         echo -n "Initializing okapi database..."
         $JAVA -Dport=8600 -Dstorage=postgres -Dpostgres_host=${postgres_host} -Dpostgres_port=${postgres_port} -Dpostgres_username=${postgres_username} -Dpostgres_password=${postgres_password} -Dpostgres_database=${postgres_database} -jar ${OKAPI_JAR} initdatabase >/dev/null 2>&1
         INIT_RETVAL=$?
         if [ "$INIT_RETVAL" != 0 ]; then
            echo "Failed"
            exit 2
         else
            echo "OK"
            exit
         fi
      fi

   else
      echo "Postgres client not installed.  Unable to test connectivity to"
      echo "postgres instance. Please manually verify connectivity and init"
      echo "okapi database manually."
      exit 2
   fi
}  # end init_db

# Set 'java'
java_check

if [ "$1" = "--initdb" ] && [ "$storage" = "postgres" ]; then
   init_db
fi


# start Okapi as daemon
cd $DATA_DIR
if [ -f "${PID_DIR}/okapi.pid" ]; then
   PID=`cat ${PID_DIR}/okapi.pid`
   if ps -p $PID > /dev/null; then
      echo "Okapi already running with [${PID}]"
      exit 0
   else
      echo "Pid file exists, but the Pid does not exist."
      echo "Remove ${PID_DIR}/okapi.pid and retry."
      exit 1
   fi
else
   parse_okapi_conf

   echo ""
   echo "############################"
   echo "JAVA: $JAVA $VERSION"
   echo "JVM OPTIONS: $OKAPI_JAVA_OPTS"
   echo "OKAPI ROLE: $role"
   echo "OKAPI OPTIONS: $OKAPI_OPTIONS"
   echo "OKAPI JAR: $OKAPI_JAR"
   echo "############################"
   echo ""

   echo -n "Starting Okapi..."
   exec $JAVA $OKAPI_JAVA_OPTS -jar "$OKAPI_JAR" $role $OKAPI_OPTIONS <&- &

   RETVAL=$?
   PID=$!
   [ $RETVAL -eq 0 ] || exit $RETVAL
   sleep 3
   if ! ps -p $PID > /dev/null; then
      exit 1
   else
      echo "$PID" > ${PID_DIR}/okapi.pid
      echo "OK [$PID]"
      exit 0
   fi
fi

exit $?
