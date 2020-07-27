#!/bin/bash

REPO=http://onto.fel.cvut.cz/rdf4j-server/repositories/ufo-current
QUERY=`cat language.rq`
LANGS=(en cs)
FILES=""
for l in "${LANGS[@]}"
do
  echo "Running query for language '$l'"
  QUERY2=${QUERY//\?lang/\"$l\"}
  # echo $QUERY2
  curl -H "Accept: text/turtle" -H "Content-Type: application/x-www-form-urlencoded; charset=utf-8" --data-urlencode "query=$QUERY2" $REPO -o language-$l.ttl
  FILES="$FILES language-$l.ttl"
done;

echo "Joining files ..."
rdfpipe -i text/turtle -o text/turtle $FILES > language.ttl
mv language.ttl ../../resources/
