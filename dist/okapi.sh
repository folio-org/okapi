#!/bin/bash
#
#  Configure and start Okapi.
#


[ -f /etc/default/okapi ] && . /etc/default/okapi

if [ -f "${CONF_DIR}/okapi.conf" ] ; then
   . "${CONF_DIR}/okapi.conf"
else
   echo "Cannot locate okapi.conf"
   exit 2
fi


DATA_DIR="${DATA_DIR:-/var/lib/okapi}"
LIB_DIR="${LIB_DIR:-/usr/share/folio/okapi/lib}"
OKAPI_JAR="${LIB_DIR}/okapi-core-fat.jar"

parse_okapi_conf()  {

   # storage backend options
   if [ "$role" == "dev" ] || [ $role == "cluster" ]; then

      if [ "$storage" == "postgres" ]; then
         OKAPI_JAVA_OPTS+=" -Dstorage=postgres"
         OKAPI_JAVA_OPTS+=" -Dpostgres_host=${postgres_host:-localhost}"
         OKAPI_JAVA_OPTS+=" -Dpostgres_port=${postgres_port:-5432}"
         OKAPI_JAVA_OPTS+=" -Dpostgres_user=${postgres_user:-okapi}"
         OKAPI_JAVA_OPTS+=" -Dpostgres_password=${postgres_password:-okapi25}"
         OKAPI_JAVA_OPTS+=" -Dpostgres_database=${postgres_database:-okapi}"
      else
         OKAPI_JAVA_OPTS+=" -Dstorage=inmemory"
      fi

   fi

   # if role is not set to 'dev', get cluster options
   if [ "$role" != "dev" ]; then

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

   fi

   # Set performance metric options
   if [ "$enable_metrics" == 1 ]; then
      OKAPI_OPTIONS+=" -enable-metrics"
      OKAPI_JAVA_OPTS+=" -DgraphiteHost=${carbon_host}"
      OKAPI_JAVA_OPTS+=" -DgraphitePort=${carbon_port}"
   fi

   # configure log file if specified
   if [ "$log4j_config" ]; then
      OKAPI_JAVA_OPTS+=" -Dhazelcast.logging.type=slf4j"
      OKAPI_JAVA_OPTS+=" -Dlog4j.configuration=file://${log4j_config}"
   fi

   # configure okapi host
   if [ "$host" ]; then
      OKAPI_JAVA_OPTS+=" -Dhost=${host}"
   fi

   # configure okapi port
   if [ "$port" ]; then
      OKAPI_JAVA_OPTS+=" -Dport=${port}"
   fi

   # configure module port range
   if [ "$port_start" ] && [ "$port_end" ]; then
      OKAPI_JAVA_OPTS+=" -Dport_start=${port_start} -Dport_end=${port_end}"
   fi

   # configure dockerURL
   if [ "$dockerurl" ] ; then
      OKAPI_JAVA_OPTS+=" -DdockerURL=${dockerurl}"
   fi

   # configure okapi URL
   if [ "$okapiurl" ]; then
      OKAPI_JAVA_OPTS+=" -Dokapiurl=${okapiurl}"
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
   else
      VERSION=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ { print $2 }' \
              | awk -F '.' '{ print $2 }')
      if [ "$VERSION" -lt "8" ];  then
         echo "Java version 8 or higher is required."
         exit 0
      fi
   fi
}  # end java_check

# initialize the Okapi database
init_db() {
   # Postgres instance check
   if command -v psql >/dev/null; then
      psql postgresql://${postgres_user}:${postgres_password}@${postgres_host}:${postgres_port}/${postgres_database}?connect_timeout=5 > /dev/null 2>&1 << EOF
\q
EOF
      POSTGRES_RETVAL=$?
      if [ "$POSTGRES_RETVAL" != 0 ]; then
         echo "Postgres check failed.  Verify that the Postgres instance"
         echo "configured in okapi.conf is available and the okapi database"
         echo "and user has been configured. Then re-run:"
         echo "    /usr/share/folio/okapi/bin/okapi.sh --initdb"
         exit 2
      else
         echo -n "Initializing okapi database..."
         $JAVA -Dport=8600 -Dstorage=postgres -Dpostgres_host=${postgres_host} -Dpostgres_port=${postgres_port} -Dpostgres_user=${postgres_user} -Dpostgres_password=${postgres_password} -Dpostgres_database=${postgres_database} -jar ${OKAPI_JAR} initdatabase >/dev/null 2>&1
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
