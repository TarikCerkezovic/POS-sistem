@echo off
setlocal
set MAVEN=https://repo1.maven.org/maven2
if not exist lib mkdir lib

call :preuzmi org/hibernate/orm/hibernate-core/6.6.3.Final hibernate-core-6.6.3.Final.jar
call :preuzmi org/hibernate/orm/hibernate-community-dialects/6.6.3.Final hibernate-community-dialects-6.6.3.Final.jar
call :preuzmi jakarta/persistence/jakarta.persistence-api/3.1.0 jakarta.persistence-api-3.1.0.jar
call :preuzmi jakarta/transaction/jakarta.transaction-api/2.0.1 jakarta.transaction-api-2.0.1.jar
call :preuzmi jakarta/inject/jakarta.inject-api/2.0.1 jakarta.inject-api-2.0.1.jar
call :preuzmi org/jboss/logging/jboss-logging/3.5.3.Final jboss-logging-3.5.3.Final.jar
call :preuzmi org/hibernate/common/hibernate-commons-annotations/7.0.3.Final hibernate-commons-annotations-7.0.3.Final.jar
call :preuzmi io/smallrye/jandex/3.2.0 jandex-3.2.0.jar
call :preuzmi com/fasterxml/classmate/1.5.1 classmate-1.5.1.jar
call :preuzmi net/bytebuddy/byte-buddy/1.15.11 byte-buddy-1.15.11.jar
call :preuzmi org/antlr/antlr4-runtime/4.13.0 antlr4-runtime-4.13.0.jar
call :preuzmi jakarta/xml/bind/jakarta.xml.bind-api/4.0.2 jakarta.xml.bind-api-4.0.2.jar
call :preuzmi org/glassfish/jaxb/jaxb-runtime/4.0.5 jaxb-runtime-4.0.5.jar
call :preuzmi org/glassfish/jaxb/jaxb-core/4.0.5 jaxb-core-4.0.5.jar
call :preuzmi org/glassfish/jaxb/txw2/4.0.5 txw2-4.0.5.jar
call :preuzmi com/sun/istack/istack-commons-runtime/4.1.2 istack-commons-runtime-4.1.2.jar
call :preuzmi org/eclipse/angus/angus-activation/2.0.2 angus-activation-2.0.2.jar
call :preuzmi jakarta/activation/jakarta.activation-api/2.1.3 jakarta.activation-api-2.1.3.jar

echo Sve biblioteke su preuzete u lib\.
pause
exit /b

:preuzmi
if exist "lib\%2" (
    echo Postoji:   %2
) else (
    echo Preuzimam: %2
    curl -fsSL -o "lib\%2" "%MAVEN%/%1/%2"
)
exit /b
