## Distributed Systems Assignment 1: Simple Calculator Server

Project Build Details:
- Built using Maven with JUnit dependencies in Intellij IDEA.
- Using the IDE was like a cheat code bc lots of things were automated.
- It would help you run the tests so much easier with the IDE unless you know how to build in vscode using the pom.xml file that I included.

Folder Directory structure should look something like this:
```
└───ds-ass-1
    ├───pom.xml
    ├───README.md
    └───src
        ├───main
        │   └───java
        │       └───org.calculator
        │           │───Calculator.java
        │           ├───CalculatorClient.java
        │           │───CalculatorFactory.java
        │           ├───CalculatorFactoryImplementation.java
        │           ├───CalculatorImplementation.java
        │           └───CalculatorServer.java
        └───test
            └───java
                └───org.calculator
                    ├───CalculatorFactoryImplementationTest.java
                    └───CalculatorImplementationTest.java
```

Make sure to open the terminal in a clean root folder where ONLY my submission files are located.

I have written a bash script that will create and move the folders accordingly.

**Ensure that once the new folder structure is created it is named as `ds-ass-1`.**

**DO NOT CHANGE THE ROOT FOLDER NAME as this will be conflicting with the `pom.xml` file**

If you prefer -> Run this bash script to build the above folder directory:
- `./organize.sh`

If the script can't run, make it executable by running in the terminal:
- `chmod +x organize.sh`

Requirements:
- Ensure you have Maven and JUnit installed

To Build Project with Maven:
- `mvn clean` - clear target directory, removes output files
- `mvn compile` - compiles project's source code

To run and test:
- `mvn test` - run JUnit tests

To package project into Jar files if needed
- `mvn package`


Would prefer you to use the above method than using Makefile