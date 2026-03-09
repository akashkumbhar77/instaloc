#!/bin/sh

echo "DEBUG DB_URL=$DB_URL"
echo "DEBUG DB_HOST=$DB_HOST"
echo "DEBUG DB_PORT=$DB_PORT"
echo "DEBUG DB_NAME=$DB_NAME"
echo "DEBUG SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"

if [ -n "$DB_HOST" ] && [ -n "$DB_PORT" ] && [ -n "$DB_NAME" ] && [ -z "$SPRING_DATASOURCE_URL" ]; then
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
fi

echo "DEBUG FINAL SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"

exec java $JAVA_OPTS -jar app.jar