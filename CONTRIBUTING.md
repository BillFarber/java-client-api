To develop the Java Client and ultimately submit a pull request, you'll first need to be able to build the client and 
run the tests locally. 

Please see [this guide](.github/CONTRIBUTING.md) for information on creating and submitting a pull request.

To build the client locally, complete the following steps:

1. Clone this repository on your machine.
2. Choose the appropriate branch (usually develop)
3. Ensure you are using Java 8 or Java 11
4. Verify that you can build the client by running `./gradlew build -x test`

"Running the tests" in the context of developing and submitting a pull request refers to running the tests found
in the `marklogic-client-api` module. The tests for this module depend on a 
[ml-gradle](https://github.com/marklogic-community/ml-gradle) application being deployed from the `test-app` module.
This application contains a number of database and security resources that the tests depend on.
The `./gradle.properties` file defines the connection properties for this application; these default
to `localhost` and an admin password of `admin`. To override these, create the file `./gradle-local.properties`
and add the following (you can override additional properties as necessary):

    mlHost=changeme
    mlPassword=changeme

Note that additional properties are defined via `./tests-app/gradle.properties`, though it is not expected that these
properties will need to be changed.

The application is then deployed via the following command:

    ./gradlew mlDeploy -i

You can then run the tests, which will use the values of `mlHost` and `mlPassword` for connecting to MarkLogic as an
admin user:

    ./gradlew marklogic-client-api:test

Individual tests can be run in the following manner (replace `GraphsTest` with the name of the test class you wish to run):

    ./gradlew marklogic-client-api:test --tests GraphsTest

You can also undeploy the test application if you do not wish to keep it around on your MarkLogic instance:

    ./gradlew mlUndeploy -i -Pconfirm=true
