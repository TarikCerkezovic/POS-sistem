#!/bin/sh
set -e
MAVEN="https://repo1.maven.org/maven2"
mkdir -p lib

preuzmi() {
    naziv=$(basename "$1")
    if [ -f "lib/$naziv" ]; then
        echo "Postoji:   $naziv"
    else
        echo "Preuzimam: $naziv"
        curl -fsSL -o "lib/$naziv" "$MAVEN/$1"
    fi
}

preuzmi org/hibernate/orm/hibernate-core/6.6.3.Final/hibernate-core-6.6.3.Final.jar
preuzmi org/hibernate/orm/hibernate-community-dialects/6.6.3.Final/hibernate-community-dialects-6.6.3.Final.jar

preuzmi jakarta/persistence/jakarta.persistence-api/3.1.0/jakarta.persistence-api-3.1.0.jar
preuzmi jakarta/transaction/jakarta.transaction-api/2.0.1/jakarta.transaction-api-2.0.1.jar
preuzmi jakarta/inject/jakarta.inject-api/2.0.1/jakarta.inject-api-2.0.1.jar

preuzmi org/jboss/logging/jboss-logging/3.5.3.Final/jboss-logging-3.5.3.Final.jar
preuzmi org/hibernate/common/hibernate-commons-annotations/7.0.3.Final/hibernate-commons-annotations-7.0.3.Final.jar
preuzmi io/smallrye/jandex/3.2.0/jandex-3.2.0.jar
preuzmi com/fasterxml/classmate/1.5.1/classmate-1.5.1.jar
preuzmi net/bytebuddy/byte-buddy/1.15.11/byte-buddy-1.15.11.jar
preuzmi org/antlr/antlr4-runtime/4.13.0/antlr4-runtime-4.13.0.jar

preuzmi jakarta/xml/bind/jakarta.xml.bind-api/4.0.2/jakarta.xml.bind-api-4.0.2.jar
preuzmi org/glassfish/jaxb/jaxb-runtime/4.0.5/jaxb-runtime-4.0.5.jar
preuzmi org/glassfish/jaxb/jaxb-core/4.0.5/jaxb-core-4.0.5.jar
preuzmi org/glassfish/jaxb/txw2/4.0.5/txw2-4.0.5.jar
preuzmi com/sun/istack/istack-commons-runtime/4.1.2/istack-commons-runtime-4.1.2.jar
preuzmi org/eclipse/angus/angus-activation/2.0.2/angus-activation-2.0.2.jar
preuzmi jakarta/activation/jakarta.activation-api/2.1.3/jakarta.activation-api-2.1.3.jar

echo "Sve biblioteke su preuzete u lib/."
