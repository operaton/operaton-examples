FROM operaton/operaton:2.1.1
ARG PLUGIN_VERSION=2.1.0
ADD https://repo1.maven.org/maven2/org/operaton/bpm/extension/operaton-keycloak-run/${PLUGIN_VERSION}/operaton-keycloak-run-${PLUGIN_VERSION}.jar \
    /operaton/configuration/userlib/operaton-keycloak-run-${PLUGIN_VERSION}.jar
COPY configuration/default.yml /operaton/configuration/default.yml
