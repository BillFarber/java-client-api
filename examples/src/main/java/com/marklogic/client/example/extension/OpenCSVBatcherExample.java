/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.example.extension;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.ResourceNotFoundException;
import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.admin.MethodType;
import com.marklogic.client.admin.ResourceExtensionsManager;
import com.marklogic.client.admin.ResourceExtensionsManager.MethodParameters;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.example.cookbook.Util;
import com.marklogic.client.example.cookbook.Util.ExampleProperties;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.io.StringHandle;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * OpenCSVBatcherExample illustrates splitting a CSV stream
 * using the OpenCSVBatcher class and the DocumentSplitter example
 * of a Resource Extension.
 */
public class OpenCSVBatcherExample {
  public static void main(String[] args)
    throws IOException, ParserConfigurationException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
    run(Util.loadProperties());
  }

  // install and then use the resource extension
  public static void run(ExampleProperties props)
    throws IOException, ParserConfigurationException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
    System.out.println("example: "+OpenCSVBatcherExample.class.getName());
    installResourceExtension(props);
    useResource(props);
    tearDownExample(props);
  }

  // install the resource extension on the server
  public static void installResourceExtension(ExampleProperties props)
    throws IOException
  {
	  DatabaseClient client = Util.newAdminClient(props);

    // create a manager for resource extensions
    ResourceExtensionsManager resourceMgr = client.newServerConfigManager().newResourceExtensionsManager();

    // specify metadata about the resource extension
    ExtensionMetadata metadata = new ExtensionMetadata();
    metadata.setTitle("Document Splitter Resource Services");
    metadata.setDescription("This plugin supports splitting input into multiple documents");
    metadata.setProvider("MarkLogic");
    metadata.setVersion("0.1");

    // acquire the resource extension source code
    InputStream sourceStream = Util.openStream(
      "scripts"+File.separator+DocumentSplitter.NAME+".xqy");
    if (sourceStream == null)
      throw new RuntimeException("Could not read example resource extension");

    // create a handle on the extension source code
    InputStreamHandle handle = new InputStreamHandle(sourceStream);
    handle.set(sourceStream);

    // write the resource extension to the database
    resourceMgr.writeServices(DocumentSplitter.NAME, handle, metadata,
      new MethodParameters(MethodType.POST));

    System.out.println("Installed the resource extension on the server");

    // release the client
    client.release();
  }

  // use the resource manager
  public static void useResource(ExampleProperties props)
    throws IOException, ParserConfigurationException, ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
	  DatabaseClient client = Util.newClient(props);

    // create the CSV splitter
    OpenCSVBatcher splitter = new OpenCSVBatcher(client);
    splitter.setHasHeader(true);

    // acquire the CSV input
    InputStream listingStream = Util.openStream(
      "data"+File.separator+"listings.csv");
    if (listingStream == null)
      throw new RuntimeException("Could not read example listings");

    // write the CSV input to the database
    long docs = splitter.write(
      new InputStreamReader(listingStream), "/listings/", "listing"
    );

    System.out.println("split CSV file into "+docs+" documents");
    XMLDocumentManager docMgr = client.newXMLDocumentManager();
    StringHandle       handle = new StringHandle();
    for (int i=1; i <= docs; i++) {
      System.out.println(
        docMgr.read("/listings/listing"+i+".xml", handle).get()
      );
    }

    // release the client
    client.release();
  }

  // clean up by deleting the example resource extension
  public static void tearDownExample(ExampleProperties props)
    throws ResourceNotFoundException, ForbiddenUserException, FailedRequestException
  {
	  DatabaseClient client = Util.newAdminClient(props);

    XMLDocumentManager docMgr = client.newXMLDocumentManager();
    for (int i=1; i <= 4; i++) {
      docMgr.delete("/listings/listing"+i+".xml");
    }

    ResourceExtensionsManager resourceMgr = client.newServerConfigManager().newResourceExtensionsManager();

    resourceMgr.deleteServices(DocumentSplitter.NAME);

    client.release();
  }
}
