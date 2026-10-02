#!/usr/bin/env bash
# Compile, test (with coverage), run the experiments, or launch the app.
#   ./build.sh test | experiments | screens | run
set -euo pipefail
cd "$(dirname "$0")"
CP="lib/sqlite-jdbc-3.46.1.3.jar:lib/slf4j-api-1.7.36.jar:lib/slf4j-nop-1.7.36.jar:lib/flatlaf-3.5.2.jar"
JUNIT="$PWD/lib/junit-platform-console-standalone-1.11.3.jar"
rm -rf out && mkdir -p out/main out/test
javac -Xlint:none -d out/main -cp "$CP" $(find src/main/java -name '*.java')
case "${1:-run}" in
  test)
    javac -d out/test -cp "out/main:$CP:$JUNIT" $(find src/test/java -name '*.java')
    java -javaagent:"$PWD/lib/org.jacoco.agent-0.8.12-runtime.jar=destfile=out/jacoco.exec" \
         -jar "$JUNIT" execute -cp "out/main:out/test:$CP" --scan-classpath --disable-banner
    java -jar lib/org.jacoco.cli-0.8.12-nodeps.jar report out/jacoco.exec --classfiles out/main \
         --sourcefiles src/main/java --html out/coverage --quiet && echo "coverage report: out/coverage/index.html" ;;
  experiments) java -cp "out/main:$CP" shelfwise.app.Experiments ;;
  screens)     java -cp "out/main:$CP" shelfwise.ui.Screens ;;
  run)         java -cp "out/main:$CP" shelfwise.app.Main ;;
esac
