#!/bin/sh

# Transform Render's postgres:// URL to jdbc:postgresql:// URL
if [ -n "$DB_URL" ]; then
    JDBC_URL="jdbc:postgresql://${DB_URL#postgres://}"
    export SPRING_DATASOURCE_URL="$JDBC_URL"
fi

exec java $JAVA_OPTS -jar app.jar
