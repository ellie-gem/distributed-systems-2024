#!/bin/bash

# Define the root directory
ROOT_DIR="ds-ass-1"

# GEt current directory (where script is being executed)
CURRENT_DIR=$(pwd)

# Create the directory structure
mkdir -p $ROOT_DIR/src/main/java/org/calculator/
mkdir -p $ROOT_DIR/src/test/java/org/calculator/

# Move Java files to the appropriate directories
mv $CURRENT_DIR/*Test.java $ROOT_DIR/src/test/java/org/calculator/
mv $CURRENT_DIR/Calculator*.java $ROOT_DIR/src/main/java/org/calculator/

mv $CURRENT_DIR/pom.xml $ROOT_DIR/
mv $CURRENT_DIR/README.md $ROOT_DIR/
