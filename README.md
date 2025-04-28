![GitHub release](https://img.shields.io/github/release/marklogic/java-client-api.svg)
![GitHub last commit](https://img.shields.io/github/last-commit/marklogic/java-client-api.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# The MarkLogic Java Client

The MarkLogic Java Client makes it easy to write, read, delete, and find documents
in a [MarkLogic](http://developer.marklogic.com/) database. The client requires connecting to a 
[MarkLogic REST API app server](https://docs.marklogic.com/guide/rest-dev) and is ideal for applications wishing to 
build upon the MarkLogic REST API. 

The client supports the following core features of the MarkLogic database:

*  Write and read binary, JSON, text, and XML documents.
*  Query data structure trees, marked-up text, and all the hybrids in between those extremes.
*  Project values, tuples, and triples from hierarchical documents and aggregate over them.
*  Patch documents with partial updates.
*  Use Optimistic Locking to detect contention without creating locks on the server.
*  Execute ACID modifications so the change either succeeds or throws an exception.
*  Execute multi-statement transactions so changes to multiple documents succeed or fail together.
*  Call Data Services via a Java interface on the client for data functionality implemented by an endpoint on the server.

The client is tested on Java 8, 11, 17, and 21 and can safely be used on each of those major Java versions. The client
may work on more recent major versions of Java but has not been thoroughly tested on those yet.

If you are using Java 11 or higher and intend to use [JAXB](https://docs.oracle.com/javase/tutorial/jaxb/intro/), please see the section below for ensuring that the
necessary dependencies are available in your application's classpath.

## QuickStart

To use the client in your [Maven](https://maven.apache.org/) project, include the following in your `pom.xml` file:

    <dependency>
        <groupId>com.marklogic</groupId>
        <artifactId>marklogic-client-api</artifactId>
        <version>7.1.0</version>
    </dependency>

To use the client in your [Gradle](https://gradle.org/) project, include the following in your `build.gradle` file:

    dependencies {
        implementation "com.marklogic:marklogic-client-api:7.1.0"
    }

Next, read [The Java API in Five Minutes](http://developer.marklogic.com/try/java/index) to get started.

Full documentation is available at:

* [Java Application Developer's Guide](http://docs.marklogic.com/guide/java)
* [JavaDoc](http://docs.marklogic.com/javadoc/client/index.html)

## Including JAXB support

As of the 7.0.0 release, the client now depends on the [Jakarta XML Binding](https://eclipse-ee4j.github.io/jaxb-ri/)
API instead of the older [JAXB API](https://docs.oracle.com/javase/tutorial/jaxb/intro/). If you are using Java 11 or
higher, you no longer need to declare additional dependencies in order to use Jakarta XML Binding. If you wish to use
the older JAXB APIs - i.e. those in the `javax.xml.bind` package instead of `jakarta.xml.bind` - you are free to 
include those as dependencies in your application; they will not conflict with the 7.0.0 release of the Java Client.

### JAXB support in 6.x releases and older

If you are using Java Client 6.x or older and also Java 11 or higher, and you wish to use JAXB with the Java client, 
you will need to include JAXB API and implementation dependencies as those are no longer included in Java 11 and higher.

For Maven, include the following in your pom.xml file:

    <dependency>
        <groupId>javax.xml.bind</groupId>
        <artifactId>jaxb-api</artifactId>
        <version>2.3.1</version>
    </dependency>
    <dependency>
        <groupId>org.glassfish.jaxb</groupId>
        <artifactId>jaxb-runtime</artifactId>
        <version>2.3.2</version>
    </dependency>
    <dependency>
        <groupId>org.glassfish.jaxb</groupId>
        <artifactId>jaxb-core</artifactId>
        <version>2.3.0.1</version>
    </dependency>

For Gradle, include the following in your build.gradle file (this can be included in the same `dependencies` block
as the one that includes the marklogic-client-api dependency):

    dependencies {
        implementation "javax.xml.bind:jaxb-api:2.3.1"
        implementation "org.glassfish.jaxb:jaxb-runtime:2.3.2"
        implementation "org.glassfish.jaxb:jaxb-core:2.3.0.1"
    }

You are free to use any implementation of JAXB that you wish, but you need to ensure that you're using a JAXB
implementation that corresponds to the `javax.xml.bind` interfaces.

## Support

The MarkLogic Java Client is maintained by [MarkLogic](https://www.marklogic.com/) Engineering and is made available under 
the [Apache 2.0 license](https://github.com/marklogic/java-client-api/blob/master/LICENSE). It is designed for use in production applications with MarkLogic Server. 
Everyone is encouraged to file bug reports, feature requests, and pull requests through [GitHub](https://github.com/marklogic/java-client-api/issues). 
This input is critical and will be carefully considered. However, we can’t promise a specific resolution or timeframe 
for any request. In addition, MarkLogic provides technical support for [release tags](https://github.com/marklogic/java-client-api/releases) of the Java Client to 
licensed customers. Please visit [our support guide](https://community.progress.com/s/products/marklogic/support-guide) 
for more information on technical support. 
