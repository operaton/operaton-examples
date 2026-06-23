FROM operaton/operaton:2.1.1
ARG PLUGIN_VERSION=2.1.0

# Download the Keycloak identity provider plugin (fat jar with all transitive deps).
ADD https://repo1.maven.org/maven2/org/operaton/bpm/extension/operaton-keycloak-run/${PLUGIN_VERSION}/operaton-keycloak-run-${PLUGIN_VERSION}.jar \
    /operaton/configuration/userlib/operaton-keycloak-run-${PLUGIN_VERSION}.jar

# Inject the plugin into BOOT-INF/lib/ of the Spring Boot executable jar and
# update the classpath index so that Spring Boot's AutoConfiguration.imports
# discovery works alongside the PropertiesLauncher classpath.
# zip is installed temporarily to manipulate the JAR (which is a ZIP archive).
USER root
RUN apk update && apk add --no-cache zip && \
    PLUGIN_JAR="/operaton/configuration/userlib/operaton-keycloak-run-${PLUGIN_VERSION}.jar" && \
    PLUGIN_NAME="operaton-keycloak-run-${PLUGIN_VERSION}.jar" && \
    MAIN_JAR="/operaton/internal/operaton-bpm.jar" && \
    mkdir -p /tmp/boot-patch/BOOT-INF/lib && \
    cd /tmp/boot-patch && \
    unzip -p "${MAIN_JAR}" BOOT-INF/classpath.idx > BOOT-INF/classpath.idx && \
    echo "- \"BOOT-INF/lib/${PLUGIN_NAME}\"" >> BOOT-INF/classpath.idx && \
    cp "${PLUGIN_JAR}" "BOOT-INF/lib/${PLUGIN_NAME}" && \
    zip -u "${MAIN_JAR}" "BOOT-INF/lib/${PLUGIN_NAME}" BOOT-INF/classpath.idx && \
    cd / && rm -rf /tmp/boot-patch && \
    apk del zip
USER operaton

COPY configuration/default.yml /operaton/configuration/default.yml
