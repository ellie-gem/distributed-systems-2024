## Distributed Systems Assignment 2: Weather System Aggregation Server

Project Build Details:
- Built using Maven with JUnit dependencies in Intellij IDEA.
- It would help you run the tests so much easier with the IDE unless you know how to build in vscode using the pom.xml file that I included.

Folder Directory structure should look something like this:
```
└───ds-ass-2
    ├───pom.xml
    ├───README.md
    ├───backup-files
    │   └───weather_data_backup.json 
    │       (contains files generated from backing up data)
    │
    ├───content-server-files
    │   └───CS__.txt 
    │       (files used by content servers)
    │
    ├───runtime-files
    │   └───......
    │       (logging files & persistent storage for lamport clocks from aggregation server, content server, getclient)
    │
    └───src
        ├───main
        │   └───java
        │       └───org.aggregationServer
        │           │───AggregationServer.java
        │           ├───ContentServer.java
        │           │───GETClient.java
        │           │───LamportClock.java
        │           │───Request.java
        │           ├───RequestHandler.java
        │           ├───WeatherData.java
        │           └───weather_data.json
        └───test
            └───java
                └───org.aggregationServer
                    └───
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

### To run manually from terminal:

1. Make sure that you are in the project root folder `ds-ass-2`

2. Compile the entire project: 
```mvn clean compile```

3. Run the server (from project root)
```java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) org.aggregationServer.AggregationServer <optional port number (4 digits)>```

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
   - eg: ```java -cp target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) org.aggregationServer.ContentServer localhost:4567 content-server-files/CS01.txt 20000```

-----------------------

### How it works:

1. Start `AggregationServer`. 
   - If you specify a port number (4 digits), server socket will be bound to that port. Otherwise the default port is 4567
   - Upon starting, the following actions are performed:
      - Backup data from `backup-files/weather_data_backup.json` is loaded into the AggregationServer's `weatherData` ConcurrentHashMap. 
        - The `lastUpdateTimes` are also updated to reflect the current time that the data is being loaded back.
      - Periodic-save and expiration-checker functions are started. 
        - Please note that periodic-save is set to run every 60 seconds. Persistent saving to storage only occurs if the `isDirty` flag is `true`. (ie new updates (PUT requests) to `weatherData` has occurred)
        - Expiration-checker is set to run every 30 seconds. If this thread started at `x` time, if a new entry is made at `(x + 15 secs)`, at `(x + 30 secs)` the entry is only `15 secs` old. This means, the entry will only be removed at `(x + 60 secs)`. Not quite how it works but hey at least the data is only 15 secs old...
      - Other noteworthy things:
        - `RequestHandler`, `Request` classes are associated with `AggregationServer`.
        - a static `RequestProcessor` class exists within the `AggregationServer` class
2. Start `ContentServer`. 
   - Please specify 3 arguments: AggregationServer's address, the file it's supposed to read from, the interval between PUT requests (above 30 seconds only if u want to check for expiry).
   - I designed it so that the `ContentServer` will continuously send PUT requests every `x` intervals.
   - Once `ContentServer` is started, it will immediately send a PUT request to `AggregationServer`.
   - Press ctrl + c to stop the 
3. Start `GETClient`:
   - Please speify 1 argument: AggregationServer's address
   - Once `GETClient` is started, it will only send GET requests when you enter a valid station id. 
     - If it is invalid, you will be prompted to re-enter again.
     - A valid station id is : `IDSxxxxx` - where x is a digit.
   - After a valid station id is entered, you can expect a response to be printed out. (as required by the assignment)

-----------------------

### Other Noteworthy Things:

REST architecture
- I've implemented this whole structure based on the REST architecture.
- This means, connections are stateless, and a connection is only established when a request needs to be sent.

Tests
- JUnit
- Some of my test files have some integrated components. I wasn't sure where to put them.

I wanted to smash my laptop so many times while doing this. It doesnt help that my laptop is gets slow and glitchy bc I am running IDEA on WSL2