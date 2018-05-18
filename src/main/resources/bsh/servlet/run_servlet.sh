#!/usr/bin/env bash

servlet="`pwd`/${0%/*}"
servlet="${servlet%/.}"
target="${servlet%/src/main*servlet}/target"
target="${target%/classes/bsh*servlet/target}"

printf "Locating servlet container - "
jetty_url="https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.9.v20180320/jetty-distribution-9.4.9.v20180320.tar.gz"
[[ ! -f "$target/jetty.tar.gz" ]] && echo "Fetching jetty" && curl -Lo "$target/jetty.tar.gz" "$jetty_url" 2>&1
grep -Iq \< "$target/jetty.tar.gz" 2>/dev/null && rm "$target/jetty.tar.gz" 2>/dev/null # Failed if file not binary and contains a <
[[ ! -f "$target/jetty.tar.gz" ]] && echo "Failed" && \
    echo "Failed to download servlet container" && \
    echo "Please fetch jetty from https://www.eclipse.org/jetty/download.html" && \
    echo "Save it as $target/jetty.tar.gz" && \
    echo "Then run the script again" && exit 1

jetty="$target/jetty-distribution-9.4.9.v20180320"
[[ ! -d "$jetty" ]] && tar -C "$target" -zxf "$target/jetty.tar.gz"

echo "Done"

printf "Creating flat war file structure - "
pwd="$jetty/webapps/servlet"
[[ ! -d "$pwd" ]] && cp -r "$servlet" "$pwd" 2>/dev/null
[[ ! -d "$pwd/WEB-INF" ]] && \
    mkdir -p "$pwd/WEB-INF" && \
    cp -r "$target/classes" "$pwd/WEB-INF/" && \
    cp "$pwd/example-web.xml" "$pwd/WEB-INF/web.xml"

echo "Done"
echo
echo "Servlet url: http://localhost:8080/servlet"
echo
echo "Starting servelt container"
export JETTY_HOME="$jetty"
cd "$jetty" && java -jar start.jar

