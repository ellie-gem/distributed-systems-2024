## Distributed Systems Assignment 2: Weather System Aggregation Server

Project Build Details:
- Built using Maven with JUnit dependencies in Intellij IDEA.
- It would help you run the tests so much easier with the IDE unless you know how to build in vscode using the pom.xml file that I included.
- Please do not rely on my design sketch as I have made significant changes to it.

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
                    └───WeatherSystemIT.java
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
