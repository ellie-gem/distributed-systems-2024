# Makefile only for compilation!!! 
# Please read the README file and follow the instructions on how to run my project! Thanks!

# Define the directories
SRC_DIR = src
BIN_DIR = bin
SRC_FILES = $(wildcard $(SRC_DIR)/org/calculator/*.java)
CLASSES = $(patsubst $(SRC_DIR)/%, $(BIN_DIR)/%, $(SRC_FILES:.java=.class))

# Define the Java compiler
JAVAC = javac

# Create a rule for building all classes
all: $(CLASSES)

# Rule for compiling Java files
$(BIN_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(BIN_DIR)/$(dir $<)
	$(JAVAC) -d $(BIN_DIR) $<

# Clean the bin directory
clean:
	rm -rf $(BIN_DIR)

.PHONY: all clean
