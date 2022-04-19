FROM adoptopenjdk/maven-openjdk11 AS builder
RUN mkdir /tmp/sauto
COPY . /tmp/sauto
WORKDIR /tmp/sauto

ARG MONGO_URI
ENV MONGO_URI $MONGO_URI

RUN mvn clean install -DskipTests

FROM adoptopenjdk:11-jdk-hotspot
RUN mkdir /app
WORKDIR /app
COPY --from=builder /tmp/sauto/target/dylen-sauto-0.0.1-SNAPSHOT.jar dylen-sauto.jar
CMD ["java", "-jar", "dylen-sauto.jar"]