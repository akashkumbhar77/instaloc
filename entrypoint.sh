#!/bin/sh

echo "DEBUG DB_URL=$DB_URL"
echo "DEBUG SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"

if [ -n "$DB_URL" ] && [ -z "$SPRING_DATASOURCE_URL" ]; then
    # Extract host:port/dbname by removing the scheme and credentials
    HOST_PORT_DB=$(echo "$DB_URL" | sed -e 's|^postgres[a-z]*://||' -e 's|^[^@]*@||')
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${HOST_PORT_DB}"
fi

echo "DEBUG FINAL SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"

exec java $JAVA_OPTS -jar app.jar