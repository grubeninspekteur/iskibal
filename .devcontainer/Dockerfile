FROM eclipse-temurin:25-jdk

# Install basic tools
RUN apt-get update && apt-get install -y git wget curl unzip tar && rm -rf /var/lib/apt/lists/*

# Install Maven 4
ARG MAVEN_VERSION=4.0.0-rc-5
ARG BASE_URL=https://archive.apache.org/dist/maven/maven-4/${MAVEN_VERSION}/binaries

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
    && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
    && rm -f /tmp/apache-maven.tar.gz \
    && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME/.m2"

CMD ["bash"]
