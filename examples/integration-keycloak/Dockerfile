FROM operaton/operaton:2.1.1
ARG PLUGIN_VERSION=2.1.0

# ADD from a URL creates the file as root:root 600, so switch to root first,
# download, fix permissions, then switch back to the container's operaton user.
USER root
ADD https://repo1.maven.org/maven2/org/operaton/bpm/extension/operaton-keycloak-run/${PLUGIN_VERSION}/operaton-keycloak-run-${PLUGIN_VERSION}.jar \
    /operaton/configuration/userlib/operaton-keycloak-run-${PLUGIN_VERSION}.jar
RUN chmod 644 /operaton/configuration/userlib/operaton-keycloak-run-${PLUGIN_VERSION}.jar
USER operaton

COPY configuration/default.yml /operaton/configuration/default.yml
