# Revoc

Revoc is a fast code coverage engine for Java.

# Download

See https://github.com/kantega/revoc/downloads

# Usage

Revoc is run as a Java Agent. It needs to options, the pattern for packages to include and the port to use for the HTTP server:

    java -javaagent:revoc.jar=include=com.example.mypackage.,port=7071 com.example.MyMainClass

