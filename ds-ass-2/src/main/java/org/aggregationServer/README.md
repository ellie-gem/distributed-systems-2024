## Distributed Systems Assignment 2: Weather System Aggregation Server

Project Build Details:
- Built using Maven with JUnit dependencies in Intellij IDEA.
- It would help you run the tests so much easier with the IDE unless you know how to build in vscode using the pom.xml file that I included.

Folder Directory structure should look something like this:
```
└───ds-ass-2
    ├───pom.xml
    ├───README.md
    └───src
        ├───main
        │   └───java
        │       └───org.aggregationServer
        │           │───AggregationServer.java
        │           ├───ContenServer.java
        │           │───GETClient.java
        │           ├───RequestHandler.java
        │           ├───WeatherData.java
        │           └───weather_data.json
        └───test
            └───java
                └───org.aggregationServer
                    └───......
```

Requirements:
- Ensure you have Maven and JUnit installed

To Build Project with Maven:
- `mvn clean` - clear target directory, removes output files
- `mvn compile` - compiles project's source code

To run and test:
- `mvn test` - run JUnit tests

To package project into Jar files if needed
- `mvn package`

-----------------------

To run manually from terminal:

1. Make sure that you are in the project root folder `ds-ass-2`

2. Compile the entire project: 
```mvn clean compile```

3. Run the server (from project root)
```java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) org.aggregationServer.AggregationServer```

4. In a different terminal, run the client
```java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) org.aggregationServer.GETClient <server url>```
   - default server url is "localhost:4567"
   - you can specify a different port: "localhost:9999"

5. In a different terminal, run the content server (default server url is "localhost:4567")
```java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) org.aggregationServer.ContentServer <server url> <content server file> <milliseconds>```
   - default server url is "localhost:4567"
   - you can specify a different port: "localhost:9999"
   - content server file path: `content-server-files/<text-file>`
   - milliseconds represent the interval between PUT requests for this particular content server
   - eg: `java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) org.aggregationServer.ContentServer localhost:4567 content-server-files/CS01.txt 20000`
    