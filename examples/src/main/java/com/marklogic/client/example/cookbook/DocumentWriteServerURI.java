/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.example.cookbook;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.document.DocumentDescriptor;
import com.marklogic.client.document.DocumentUriTemplate;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.example.cookbook.Util.ExampleProperties;
import com.marklogic.client.io.InputStreamHandle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * DocumentWriterServerURI illustrates how to write content, letting MarkLogic assign a server URI
 */
public class DocumentWriteServerURI {
  public static void main(String[] args) throws IOException {
    run(Util.loadProperties());
  }

  public static void run(ExampleProperties props) throws IOException {
    System.out.println("example: "+DocumentWriteServerURI.class.getName());

    String filename = "flipper.xml";

	  DatabaseClient client = Util.newClient(props);

    // acquire the content
    InputStream docStream = Util.openStream("data"+File.separator+filename);
    if (docStream == null)
      throw new IOException("Could not read document example");

    // create a manager for XML documents
    XMLDocumentManager docMgr = client.newXMLDocumentManager();

    // create a uri template.  This one says "use an XML extension to generate URIs"
    DocumentUriTemplate uriTemplate = docMgr.newDocumentUriTemplate("xml");

    // create a handle on the content
    InputStreamHandle handle = new InputStreamHandle();
    handle.set(docStream);

    // write the document content, returning a document descriptor.
    DocumentDescriptor documentDescriptor = docMgr.create(uriTemplate, handle);

    System.out.println("Wrote /example/"+filename+" " +
      "content, and got back descriptor with uri " + documentDescriptor.getUri() + ".");

    tearDownExample(docMgr, documentDescriptor.getUri());

    // release the client
    client.release();
  }

  // clean up by deleting the document that the example wrote
  public static void tearDownExample(XMLDocumentManager docMgr, String docId) {
    docMgr.delete(docId);
  }
}
