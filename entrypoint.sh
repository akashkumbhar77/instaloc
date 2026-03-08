#!/bin/sh

# DB_URL from Render is already a JDBC URL when using jdbcConnectionString
if [ -n "$DB_URL" ]; then
    export SPRING_DATASOURCE_URL="$DB_URL"
fi

exec java $JAVA_OPTS -jar app.jar
