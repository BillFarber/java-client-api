/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.example.cookbook;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.admin.MethodType;
import com.marklogic.client.admin.ResourceExtensionsManager;
import com.marklogic.client.admin.ResourceExtensionsManager.MethodParameters;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.example.cookbook.Util.ExampleProperties;
import com.marklogic.client.extensions.ResourceManager;
import com.marklogic.client.extensions.ResourceServices.ServiceResult;
import com.marklogic.client.extensions.ResourceServices.ServiceResultIterator;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.util.RequestParameters;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * JavascriptResourceExtension installs an extension for managing spelling dictionary resources.
 */
public class JavascriptResourceExtension {
  public static void main(String[] args)
    throws IOException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
    run(Util.loadProperties());
  }

  // install and then use the resource extension
  public static void run(ExampleProperties props)
    throws IOException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
    System.out.println("example: "+JavascriptResourceExtension.class.getName());
    installResourceExtension(props);
    useResource(props);
    tearDownExample(props);
  }

  /**
   * HelloWorld provides an example of a class that implements
   * a resource extension client, exposing a method for each service.
   * Typically, this class would be a top-level class.
   */
  static public class HelloWorld extends ResourceManager {
    static final public String NAME = "helloWorld";
    static final public ExtensionMetadata.ScriptLanguage scriptLanguage
      = ExtensionMetadata.JAVASCRIPT;
    private XMLDocumentManager docMgr;

    public HelloWorld(DatabaseClient client) {
      super();

      // a Resource Manager must be initialized by a Database Client
      client.init(NAME, this);

      // the Dictionary Manager delegates some services to a document manager
      docMgr = client.newXMLDocumentManager();
    }

    public String sayHello() {
      RequestParameters params = new RequestParameters();
      params.add("service", "hello");
      params.add("planet", "Earth");

      // call the service
      ServiceResultIterator resultItr = getServices().get(params);

      // iterate over the results
      List<String> responses = new ArrayList<>();
      StringHandle readHandle = new StringHandle();
      while (resultItr.hasNext()) {
        ServiceResult result = resultItr.next();

        // get the result content
        result.getContent(readHandle);
        responses.add(readHandle.get());
      }

      // release the iterator resources
      resultItr.close();

      return responses.get(0);
    }

  }

  // install the resource extension on the server
  public static void installResourceExtension(ExampleProperties props) throws IOException {
	  DatabaseClient client = Util.newAdminClient(props);

    // use either shortcut or strong typed IO
    installResourceExtension(client);

    // release the client
    client.release();
  }

  public static void installResourceExtension(DatabaseClient client) throws IOException {
    // create a manager for resource extensions
    ResourceExtensionsManager resourceMgr = client.newServerConfigManager().newResourceExtensionsManager();

    // specify metadata about the resource extension
    ExtensionMetadata metadata = new ExtensionMetadata();
    metadata.setTitle("Hello World Resource Services");
    metadata.setDescription("This resource extension is written in javascript");
    metadata.setProvider("MarkLogic");
    metadata.setVersion("0.1");
    metadata.setScriptLanguage(HelloWorld.scriptLanguage);

    // acquire the resource extension source code
    InputStream sourceStream = Util.openStream(
      "scripts"+File.separator+HelloWorld.NAME+".sjs");
    if (sourceStream == null)
      throw new IOException("Could not read example resource extension");

    // write the resource extension to the database
    resourceMgr.writeServicesAs(HelloWorld.NAME, sourceStream, metadata,
      new MethodParameters(MethodType.GET));

    System.out.println("(Shortcut) Installed the resource extension on the server");
  }

  // use the resource manager
  public static void useResource(ExampleProperties props)
    throws ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
	  DatabaseClient client = Util.newClient(props);

    // create the resource extension client
    HelloWorld hello = new HelloWorld(client);

    String response = hello.sayHello();
    System.out.println("Called hello worlds service, got response:["+ response + "]");

    // release the client
    client.release();
  }

  // clean up by deleting the example resource extension
  public static void tearDownExample(ExampleProperties props) {
	  DatabaseClient client = Util.newAdminClient(props);

    ResourceExtensionsManager resourceMgr = client.newServerConfigManager().newResourceExtensionsManager();

    resourceMgr.deleteServices(HelloWorld.NAME);

    client.release();
  }
}
