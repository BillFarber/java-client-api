/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */

package com.marklogic.client.functionaltest;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.Transaction;
import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.admin.TransformExtensionsManager;
import com.marklogic.client.bitemporal.TemporalDescriptor;
import com.marklogic.client.document.*;
import com.marklogic.client.document.DocumentManager.Metadata;
import com.marklogic.client.expression.CtsQueryBuilder;
import com.marklogic.client.io.*;
import com.marklogic.client.io.DocumentMetadataHandle.Capability;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentPermissions;
import com.marklogic.client.io.DocumentMetadataHandle.DocumentProperties;
import com.marklogic.client.query.*;
import com.marklogic.client.query.StructuredQueryBuilder.TemporalOperator;
import com.marklogic.client.type.*;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.DOMImplementationLS;

import jakarta.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestBiTemporal extends BasicJavaClientREST {

  private static String dbName = "TestBiTemporalJava";
  private static String[] fNames = { "TestBiTemporalJava-1" };
  private static String schemadbName = "TestBiTemporalJavaSchemaDB";
  private static String[] schemafNames = { "TestBiTemporalJavaSchemaDB-1" };

  private DatabaseClient adminClient = null;
  private DatabaseClient writerClient = null;
  private DatabaseClient readerClient = null;

  private final static String dateTimeDataTypeString = "dateTime";

  private final static String systemStartERIName = "javaSystemStartERI";
  private final static String systemEndERIName = "javaSystemEndERI";
  private final static String validStartERIName = "javaValidStartERI";
  private final static String validEndERIName = "javaValidEndERI";

  private final static String axisSystemName = "javaERISystemAxis";
  private final static String axisValidName = "javaERIValidAxis";

  private final static String temporalCollectionName = "javaERITemporalCollection";
  private final static String bulktemporalCollectionName = "bulkjavaERITemporalCollection";
  private final static String temporalLsqtCollectionName = "javaERILsqtTemporalCollection";

  private final static String systemNodeName = "System";
  private final static String validNodeName = "Valid";
  private final static String addressNodeName = "Address";
  private final static String uriNodeName = "uri";

  private final static String latestCollectionName = "latest";
  private final static String updateCollectionName = "updateCollection";
  private final static String insertCollectionName = "insertCollection";

  @BeforeAll
  public static void setUpBeforeClass() throws Exception {
    System.out.println("In setup");
    configureRESTServer(dbName, fNames);

    ConnectedRESTQA.addRangeElementIndex(dbName, dateTimeDataTypeString, "",
        systemStartERIName);
    ConnectedRESTQA.addRangeElementIndex(dbName, dateTimeDataTypeString, "",
        systemEndERIName);
    ConnectedRESTQA.addRangeElementIndex(dbName, dateTimeDataTypeString, "",
        validStartERIName);
    ConnectedRESTQA.addRangeElementIndex(dbName, dateTimeDataTypeString, "",
        validEndERIName);
    createDB(schemadbName);
    createForest(schemafNames[0], schemadbName);
    waitForPropertyPropagate();
    // Set the schemadbName database as the Schema database.
    setDatabaseProperties(dbName, "schema-database", schemadbName);
    waitForPropertyPropagate();

    // Temporal axis must be created before temporal collection associated with
    // those axes is created
    ConnectedRESTQA.addElementRangeIndexTemporalAxis(dbName, axisSystemName,
        "", systemStartERIName, "", systemEndERIName);
    ConnectedRESTQA.addElementRangeIndexTemporalAxis(dbName, axisValidName, "",
        validStartERIName, "", validEndERIName);
    ConnectedRESTQA.addElementRangeIndexTemporalCollection(dbName,
        temporalCollectionName, axisSystemName, axisValidName);
    ConnectedRESTQA.addElementRangeIndexTemporalCollection(dbName,
        bulktemporalCollectionName, axisSystemName, axisValidName);
    ConnectedRESTQA.addElementRangeIndexTemporalCollection(dbName,
        temporalLsqtCollectionName, axisSystemName, axisValidName);
    ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName,
        temporalLsqtCollectionName, true);
  }

  @AfterAll
  public static void tearDownAfterClass() throws Exception {
    System.out.println("In tear down");

    // Delete database first. Otherwise axis and collection cannot be deleted
    cleanupRESTServer(dbName, fNames);
    deleteRESTUser("eval-user");
    deleteUserRole("test-eval");

    // Temporal collection needs to be delete before temporal axis associated
    // with it can be deleted
    /*ConnectedRESTQA.deleteElementRangeIndexTemporalCollection("Documents",
        temporalLsqtCollectionName);
    ConnectedRESTQA.deleteElementRangeIndexTemporalCollection("Documents",
        temporalCollectionName);
    ConnectedRESTQA.deleteElementRangeIndexTemporalCollection("Documents",
        bulktemporalCollectionName);
    ConnectedRESTQA.deleteElementRangeIndexTemporalAxis("Documents",
        axisValidName);
    ConnectedRESTQA.deleteElementRangeIndexTemporalAxis("Documents",
        axisSystemName);*/
    deleteDB(schemadbName);
    deleteForest(schemafNames[0]);
  }

  @BeforeEach
  public void setUp() throws Exception {
    createUserRolesWithPrevilages("test-eval", "xdbc:eval", "xdbc:eval-in", "xdmp:eval-in", "any-uri",
        "xdbc:invoke", "temporal:statement-set-system-time");
    createRESTUser("eval-user", "x", "test-eval", "rest-admin", "rest-writer", "rest-reader", "temporal-admin");

    adminClient = getDatabaseClient("rest-admin", "x", getConnType());
    //adminClient = getDatabaseClientOnDatabase(appServerHostname, restPort, dbName, "rest-admin", "x", getConnType());
    //writerClient = getDatabaseClientOnDatabase(appServerHostname, restPort, dbName, "eval-user", "x", getConnType());
    writerClient = getDatabaseClient("eval-user", "x", getConnType());
    //readerClient = getDatabaseClientOnDatabase(appServerHostname, restPort, dbName, "rest-reader", "x", getConnType());
    readerClient = getDatabaseClient("rest-reader", "x", getConnType());
  }

  @AfterEach
  public void tearDown() throws Exception {
    clearDB();
    adminClient.release();
  }

  public DocumentMetadataHandle setMetadata(boolean update) {
    // create and initialize a handle on the meta-data
    DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();

    if (update) {
      metadataHandle.getCollections().addAll("updateCollection");
      metadataHandle.getProperties().put("published", true);

      metadataHandle.getPermissions().add("app-user", Capability.UPDATE,
          Capability.READ);

      metadataHandle.setQuality(99);
    } else {
      metadataHandle.getCollections().addAll("insertCollection");
      metadataHandle.getProperties().put("reviewed", true);

      metadataHandle.getPermissions().add("app-user", Capability.UPDATE,
          Capability.READ, Capability.EXECUTE);

      metadataHandle.setQuality(11);
    }

    metadataHandle.getProperties().put("myString", "foo");
    metadataHandle.getProperties().put("myInteger", 10);
    metadataHandle.getProperties().put("myDecimal", 34.56678);
    metadataHandle.getProperties().put("myCalendar",
        Calendar.getInstance().get(Calendar.YEAR));

    return metadataHandle;
  }

  public void validateMetadata(DocumentMetadataHandle mh) {
    // get metadata values
    DocumentProperties properties = mh.getProperties();
    DocumentPermissions permissions = mh.getPermissions();

    // Properties
    // String expectedProperties =
    // "size:5|reviewed:true|myInteger:10|myDecimal:34.56678|myCalendar:2014|myString:foo|";
    String actualProperties = getDocumentPropertiesString(properties);
    boolean result = actualProperties.contains("size:5|");
    assertTrue(result);

    // Permissions
    String actualPermissions = getDocumentPermissionsString(permissions);
    System.out.println(actualPermissions);
  }

  private void validateLSQTQueryData(DatabaseClient client) throws Exception {
    // Fetch documents associated with a search term (such as XML) in Address
    // element
    QueryManager queryMgr = client.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

    Calendar queryTime = DatatypeConverter.parseDateTime("2007-01-01T00:00:01");
    StructuredQueryDefinition periodQuery = sqb.temporalLsqtQuery(
        temporalLsqtCollectionName, queryTime, 0, new String[] {});

    long start = 1;
    JSONDocumentManager docMgr = client.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
    DocumentPage termQueryResults = docMgr.search(periodQuery, start);

    long count = 0;
    while (termQueryResults.hasNext()) {
      ++count;
      DocumentRecord record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);
      Iterator<String> resCollections = metadataHandle.getCollections()
          .iterator();
      while (resCollections.hasNext()) {
        System.out.println("Collection = " + resCollections.next());
      }

      if (record.getFormat() == Format.XML) {
        DOMHandle recordHandle = new DOMHandle();
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());
      } else {
        JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        Map<String, Object> docObject = mapper.readValue(
            recordHandle.toString(), typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> systemNode = (HashMap<String, Object>) (docObject
            .get(systemNodeName));

        String systemStartDate = (String) systemNode.get(systemStartERIName);
        String systemEndDate = (String) systemNode.get(systemEndERIName);
        System.out.println("systemStartDate = " + systemStartDate);
        System.out.println("systemEndDate = " + systemEndDate);

        @SuppressWarnings("unchecked")
        Map<String, Object> validNode = (HashMap<String, Object>) (docObject
            .get(validNodeName));

        String validStartDate = (String) validNode.get(validStartERIName);
        String validEndDate = (String) validNode.get(validEndERIName);
        System.out.println("validStartDate = " + validStartDate);
        System.out.println("validEndDate = " + validEndDate);

        assertTrue(
            (validStartDate.equals("2001-01-01T00:00:00") &&
                validEndDate.equals("2011-12-31T23:59:59") &&
                systemStartDate.equals("2005-01-01T00:00:01-08:00") &&
            systemEndDate.equals("2010-01-01T00:00:01-08:00")));
      }
    }

    System.out.println("Number of results using SQB = " + count);
    assertEquals(1, count);
  }

  // This covers passing transforms and descriptor
  private void insertXMLSingleDocument(String temporalCollection, String docId,
      String transformName) throws Exception {
    System.out.println("Inside insertXMLSingleDocument");

    DOMHandle handle = getXMLDocumentHandle("2001-01-01T00:00:00",
        "2011-12-31T23:59:59", "999 Skyway Park - XML", docId);

    XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentDescriptor desc = docMgr.newDescriptor(docId);
    DocumentMetadataHandle mh = setMetadata(false);

    if (transformName != null) {
      TransformExtensionsManager transMgr = adminClient.newServerConfigManager()
          .newTransformExtensionsManager();
      ExtensionMetadata metadata = new ExtensionMetadata();
      metadata.setTitle("Adding Element xquery Transform");
      metadata
          .setDescription("This plugin transforms an XML document by adding Element to root node");
      metadata.setProvider("MarkLogic");
      metadata.setVersion("0.1");
      // get the transform file
      File transformFile = new File(
          "src/test/java/com/marklogic/client/functionaltest/transforms/" + transformName
              + ".xqy");
      FileHandle transformHandle = new FileHandle(transformFile);
      transMgr.writeXQueryTransform(transformName, transformHandle, metadata);
      ServerTransform transformer = new ServerTransform(transformName);
      transformer.put("name", "Lang");
      transformer.put("value", "English");

      docMgr.write(desc, mh, handle, transformer, null, temporalCollection);
    }
    else {
      docMgr.write(desc, mh, handle, null, null, temporalCollection);
    }
  }

  // This covers passing transforms and descriptor
  private void updateXMLSingleDocument(String temporalCollection, String docId,
      String transformName) throws Exception {
    System.out.println("Inside updateXMLSingleDocument");

    // Update the document
    DOMHandle handle = getXMLDocumentHandle("2003-01-01T00:00:00",
        "2008-12-31T23:59:59", "1999 Skyway Park - Updated - XML", docId);

    XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    DocumentDescriptor desc = docMgr.newDescriptor(docId);
    DocumentMetadataHandle mh = setMetadata(true);
    if (transformName != null) {
      TransformExtensionsManager transMgr = adminClient.newServerConfigManager()
          .newTransformExtensionsManager();
      ExtensionMetadata metadata = new ExtensionMetadata();
      metadata.setTitle("Adding Element xquery Transform");
      metadata
          .setDescription("This plugin transforms an XML document by adding Element to root node");
      metadata.setProvider("MarkLogic");
      metadata.setVersion("0.1");
      // get the transform file
      File transformFile = new File(
          "src/test/java/com/marklogic/client/functionaltest/transforms/" + transformName
              + ".xqy");
      FileHandle transformHandle = new FileHandle(transformFile);
      transMgr.writeXQueryTransform(transformName, transformHandle, metadata);
      ServerTransform transformer = new ServerTransform(transformName);
      transformer.put("name", "Lang");
      transformer.put("value", "English");

      docMgr.write(desc, mh, handle, transformer, null, temporalCollection);
    } else {
      docMgr.write(desc, mh, handle, null, null, temporalCollection);
    }

  }

  // This covers passing descriptor
  public void deleteXMLSingleDocument(String temporalCollection, String docId)
      throws Exception {

    System.out.println("Inside deleteXMLSingleDocument");

    XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();

    DocumentDescriptor desc = docMgr.newDescriptor(docId);
    docMgr.delete(desc, null, temporalCollection, null);
  }

  private DOMHandle getXMLDocumentHandle(String startValidTime,
      String endValidTime, String address, String uri) throws Exception {

    Document domDocument = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().newDocument();
    Element root = domDocument.createElement("root");

    // System start and End time
    Node systemNode = root.appendChild(domDocument.createElement("system"));
    systemNode.appendChild(domDocument.createElement(systemStartERIName));
    systemNode.appendChild(domDocument.createElement(systemEndERIName));

    // Valid start and End time
    Node validNode = root.appendChild(domDocument.createElement("valid"));

    Node validStartNode = validNode.appendChild(domDocument
        .createElement(validStartERIName));
    validStartNode.appendChild(domDocument.createTextNode(startValidTime));
    validNode.appendChild(validStartNode);

    Node validEndNode = validNode.appendChild(domDocument
        .createElement(validEndERIName));
    validEndNode.appendChild(domDocument.createTextNode(endValidTime));
    validNode.appendChild(validEndNode);

    // Address
    Node addressNode = root.appendChild(domDocument.createElement("Address"));
    addressNode.appendChild(domDocument.createTextNode(address));

    // uri
    Node uriNode = root.appendChild(domDocument.createElement("uri"));
    uriNode.appendChild(domDocument.createTextNode(uri));
    domDocument.appendChild(root);

    String domString = ((DOMImplementationLS) DocumentBuilderFactory
        .newInstance().newDocumentBuilder().getDOMImplementation())
        .createLSSerializer().writeToString(domDocument);

    System.out.println(domString);

    DOMHandle handle = new DOMHandle().with(domDocument);

    return handle;
  }

  private Document getXMLAsDocumentObject(String startValidTime,
      String endValidTime, String address, String uri) throws Exception {

    Document domDocument = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().newDocument();
    Element root = domDocument.createElement("root");

    // System start and End time
    Node systemNode = root.appendChild(domDocument.createElement("system"));
    systemNode.appendChild(domDocument.createElement(systemStartERIName));
    systemNode.appendChild(domDocument.createElement(systemEndERIName));

    // Valid start and End time
    Node validNode = root.appendChild(domDocument.createElement("valid"));

    Node validStartNode = validNode.appendChild(domDocument
        .createElement(validStartERIName));
    validStartNode.appendChild(domDocument.createTextNode(startValidTime));
    validNode.appendChild(validStartNode);

    Node validEndNode = validNode.appendChild(domDocument
        .createElement(validEndERIName));
    validEndNode.appendChild(domDocument.createTextNode(endValidTime));
    validNode.appendChild(validEndNode);

    // Address
    Node addressNode = root.appendChild(domDocument.createElement("Address"));
    addressNode.appendChild(domDocument.createTextNode(address));

    // uri
    Node uriNode = root.appendChild(domDocument.createElement("uri"));
    uriNode.appendChild(domDocument.createTextNode(uri));
    domDocument.appendChild(root);

    return domDocument;
  }

  public void insertJSONSingleDocument(String temporalCollection, String docId,
      Transaction transaction, java.util.Calendar systemTime) throws Exception {

    insertJSONSingleDocument(temporalCollection, docId, null, transaction,
        systemTime);
  }

  public void insertJSONSingleDocument(String temporalCollection, String docId,
      String transformName) throws Exception {

    insertJSONSingleDocument(temporalCollection, docId, transformName, null,
        null);
  }

  public void insertJSONSingleDocument(String temporalCollection, String docId,
      String transformName, Transaction transaction,
      java.util.Calendar systemTime) throws Exception {

    System.out.println("Inside insertJSONSingleDocument");

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        docId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);

    if (transformName != null) {
      TransformExtensionsManager transMgr = writerClient.newServerConfigManager()
          .newTransformExtensionsManager();
      ExtensionMetadata metadata = new ExtensionMetadata();
      metadata.setTitle("Adding sjs Transform");
      metadata.setDescription("This plugin adds 2 properties to JSON document");
      metadata.setProvider("MarkLogic");
      metadata.setVersion("0.1");
      // get the transform file
      File transformFile = new File(
          "src/test/java/com/marklogic/client/functionaltest/transforms/" + transformName
              + ".js");
      FileHandle transformHandle = new FileHandle(transformFile);
      transMgr.writeJavascriptTransform(transformName, transformHandle,
          metadata);
      ServerTransform transformer = new ServerTransform(transformName);
      transformer.put("name", "Lang");
      transformer.put("value", "English");

      if (systemTime != null) {
        docMgr.write(docId, mh, handle, transformer, null, temporalCollection,
            systemTime);
      } else {
        docMgr.write(docId, mh, handle, transformer, null, temporalCollection);
      }
    } else {
      if (systemTime != null) {
        docMgr.write(docId, mh, handle, null, transaction, temporalCollection,
            systemTime);
      } else {
        docMgr.write(docId, mh, handle, null, transaction, temporalCollection);
      }
    }
  }

  public void insertJSONSingleDocumentAsEvalUser(String temporalCollection, String docId) throws Exception {

    System.out.println("Inside insertJSONSingleDocumentAsEvalUser");

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        docId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);
    docMgr.write(docId, mh, handle, null, null, temporalCollection);
  }

  public void updateJSONSingleDocument(String temporalCollection, String docId)
      throws Exception {

    updateJSONSingleDocument(temporalCollection, docId, null, null);
  }

  public void updateJSONSingleDocumentAsEvalUser(String temporalCollection, String docId) throws Exception {

    System.out.println("Inside updateJSONSingleDocumentString");

    // Update the temporal document
    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2003-01-01T00:00:00", "2008-12-31T23:59:59",
        "1999 Skyway Park - Updated - JSON", docId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);
    DocumentMetadataHandle mh = setMetadata(true);

    docMgr.write(docId, mh, handle, null, null, temporalCollection);
  }

  public void updateJSONSingleDocument(String temporalCollection, String docId,
      Transaction transaction, java.util.Calendar systemTime) throws Exception {

    System.out.println("Inside updateJSONSingleDocument");

    // Update the temporal document
    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2003-01-01T00:00:00", "2008-12-31T23:59:59",
        "1999 Skyway Park - Updated - JSON", docId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);
    DocumentMetadataHandle mh = setMetadata(true);

    docMgr.write(docId, mh, handle, null, transaction, temporalCollection,
        systemTime);
  }

  public void deleteJSONSingleDocument(String temporalCollection, String docId,
      Transaction transaction) throws Exception {

    deleteJSONSingleDocument(temporalCollection, docId, transaction, null);
  }

  public void deleteJSONSingleDocumentAsEvalUser(String temporalCollection, String docId) throws Exception {

    System.out.println("Inside deleteJSONSingleDocumentAsEvalUser");

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();

    // Doing the logic here to exercise the overloaded methods
    docMgr.delete(docId, null, temporalCollection);
  }

  public void deleteJSONSingleDocument(String temporalCollection, String docId,
      Transaction transaction, java.util.Calendar systemTime) throws Exception {

    System.out.println("Inside deleteJSONSingleDocument");

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();

    // Doing the logic here to exercise the overloaded methods
    if (systemTime != null) {
      docMgr.delete(docId, transaction, temporalCollection, systemTime);
    } else {
      docMgr.delete(docId, transaction, temporalCollection);
    }
  }

  private JacksonDatabindHandle<ObjectNode> getJSONDocumentHandle(
      String startValidTime, String endValidTime, String address, String uri)
      throws Exception {

    // Setup for JSON document
    /**
     *
     { "System": { systemStartERIName : "", systemEndERIName : "", }, "Valid":
     * { validStartERIName: "2001-01-01T00:00:00", validEndERIName:
     * "2011-12-31T23:59:59" }, "Address": "999 Skyway Park", "uri":
     * "javaSingleDoc1.json" }
     */

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode rootNode = mapper.createObjectNode();

    // Set system time values
    ObjectNode system = mapper.createObjectNode();

    system.put(systemStartERIName, "");
    system.put(systemEndERIName, "");
    rootNode.set(systemNodeName, system);

    // Set valid time values
    ObjectNode valid = mapper.createObjectNode();

    valid.put(validStartERIName, startValidTime);
    valid.put(validEndERIName, endValidTime);
    rootNode.set(validNodeName, valid);

    // Set Address
    rootNode.put(addressNodeName, address);

    // Set uri
    rootNode.put(uriNodeName, uri);

    System.out.println(rootNode.toString());

    JacksonDatabindHandle<ObjectNode> handle = new JacksonDatabindHandle<>(
        ObjectNode.class).withFormat(Format.JSON);
    handle.set(rootNode);

    return handle;
  }

  public void insertSimpleDocument(String docId, String transformName, Transaction transaction) throws Exception {

      System.out.println("Inside getDocumentDescriptor");

      JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
              "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
              docId);

      JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
      docMgr.setMetadataCategories(Metadata.ALL);

      // put meta-data
      DocumentMetadataHandle mh = setMetadata(false);

      if (transformName != null) {
          TransformExtensionsManager transMgr = writerClient.newServerConfigManager()
                  .newTransformExtensionsManager();
          ExtensionMetadata metadata = new ExtensionMetadata();
          metadata.setTitle("Adding sjs Transform");
          metadata.setDescription("This plugin adds 2 properties to JSON document");
          metadata.setProvider("MarkLogic");
          metadata.setVersion("0.1");
          // get the transform file
          File transformFile = new File(
                  "src/test/java/com/marklogic/client/functionaltest/transforms/" + transformName
                  + ".js");
          FileHandle transformHandle = new FileHandle(transformFile);
          transMgr.writeJavascriptTransform(transformName, transformHandle,
                  metadata);
          ServerTransform transformer = new ServerTransform(transformName);
          transformer.put("name", "Lang");
          transformer.put("value", "English");
          docMgr.write(docId, mh, handle, transformer, null);
      }
      else {
          docMgr.write(docId, mh, handle);
      }
}

  /*
   * Insert multiple temporal documents to test bulk write of temporal
   * documents.
   */
  @Test
  public void testBulkWritReadeWithTransaction() throws Exception {

    boolean tstatus = false;
    DocumentPage termQueryResults = null;

    Transaction tx = writerClient.openTransaction();
    try {
      XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();

      DocumentWriteSet writeset = docMgr.newWriteSet();
      String[] docId = new String[4];
      docId[0] = "1.xml";
      docId[1] = "2.xml";
      docId[2] = "3.xml";
      docId[3] = "4.xml";

      DOMHandle handle1 = getXMLDocumentHandle("2001-01-01T00:00:00",
          "2011-12-31T23:59:56", "999 Skyway Park - XML", docId[0]);
      DOMHandle handle2 = getXMLDocumentHandle("2001-01-02T00:00:00",
          "2011-12-31T23:59:57", "999 Skyway Park - XML", docId[1]);
      DOMHandle handle3 = getXMLDocumentHandle("2001-01-03T00:00:00",
          "2011-12-31T23:59:58", "999 Skyway Park - XML", docId[2]);
      DOMHandle handle4 = getXMLDocumentHandle("2001-01-04T00:00:00",
          "2011-12-31T23:59:59", "999 Skyway Park - XML", docId[3]);
      DocumentMetadataHandle mh = setMetadata(false);

      writeset.add(docId[0], mh, handle1);
      writeset.add(docId[1], mh, handle2);
      writeset.add(docId[2], mh, handle3);
      writeset.add(docId[3], mh, handle4);
      Map<String, DOMHandle> map = new TreeMap<String, DOMHandle>();
      map.put(docId[0], handle1);
      map.put(docId[1], handle2);
      map.put(docId[2], handle3);
      map.put(docId[3], handle4);

      docMgr.write(writeset, null, null, bulktemporalCollectionName);

      QueryManager queryMgr = readerClient.newQueryManager();
      StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

      String[] collections = { latestCollectionName, bulktemporalCollectionName, "insertCollection" };
      StructuredQueryDefinition termQuery = sqb.collection(collections);

      long start = 1;
      docMgr = readerClient.newXMLDocumentManager();
      docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
      termQueryResults = docMgr.search(termQuery, start);
      assertEquals(4, termQueryResults.size());
      // Verify the Document Record content with map contents for each record.
      while (termQueryResults.hasNext()) {

        DocumentRecord record = termQueryResults.next();

        DOMHandle recordHandle = new DOMHandle();
        record.getContent(recordHandle);

        String recordContent = recordHandle.toString();

        System.out.println("Record URI = " + record.getUri());
        System.out.println("Record content is = " + recordContent);

        DOMHandle readDOMHandle = map.get(record.getUri());
        String mapContent = readDOMHandle.evaluateXPath("/root/Address/text()", String.class);

        assertTrue(recordContent.contains(mapContent));

        readDOMHandle = null;
        mapContent = null;
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
      tstatus = true;
      throw e;
    } finally {
      if (tstatus) {
        if (termQueryResults != null)
          termQueryResults.close();
      }
      tx.rollback();
      tx = null;
    }
  }

  /*
   * Update multiple temporal documents to test bulk write of temporal documents
   * with logical version ids
   */
  @Test
  public void testAddWithLogicalVersionDocSet() throws Exception {

    boolean tstatus = false;
    DocumentPage termQueryResults = null;

    Transaction tx = writerClient.openTransaction();
    try {
      XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();

      DocumentWriteSet writeset = docMgr.newWriteSet();
      String[] docId = new String[4];
      docId[0] = "1.xml";
      docId[1] = "2.xml";
      docId[2] = "3.xml";
      docId[3] = "4.xml";

      String[] versiondocId = new String[4];
      versiondocId[0] = "/version2/1.xml";
      versiondocId[1] = "/version2/2.xml";
      versiondocId[2] = "/version2/3.xml";
      versiondocId[3] = "/version2/4.xml";

      DOMHandle handle1 = getXMLDocumentHandle("2001-01-01T00:00:00",
          "2011-12-31T23:59:56", "999 Skyway Park - XML", docId[0]);
      DOMHandle handle2 = getXMLDocumentHandle("2001-01-02T00:00:00",
          "2011-12-31T23:59:57", "999 Skyway Park - XML", docId[1]);
      DOMHandle handle3 = getXMLDocumentHandle("2001-01-03T00:00:00",
          "2011-12-31T23:59:58", "999 Skyway Park - XML", docId[2]);
      DOMHandle handle4 = getXMLDocumentHandle("2001-01-04T00:00:00",
          "2011-12-31T23:59:59", "999 Skyway Park - XML", docId[3]);
      DocumentMetadataHandle mh = setMetadata(false);

      writeset.add(docId[0], mh, handle1);
      writeset.add(docId[1], mh, handle2);
      writeset.add(docId[2], mh, handle3);
      writeset.add(docId[3], mh, handle4);
      Map<String, DOMHandle> map = new TreeMap<String, DOMHandle>();
      map.put(docId[0], handle1);
      map.put(docId[1], handle2);
      map.put(docId[2], handle3);
      map.put(docId[3], handle4);

      docMgr.write(writeset, null, null, bulktemporalCollectionName);

      // Update the docs.
      DocumentWriteSet writesetUpd = docMgr.newWriteSet();
      DOMHandle vTwohandle1 = getXMLDocumentHandle("2001-01-01T00:00:00",
          "2011-12-31T23:59:56", "999 Skyway Park - XML Updated", docId[0]);
      DOMHandle vTwohandle2 = getXMLDocumentHandle("2001-01-02T00:00:00",
          "2011-12-31T23:59:57", "999 Skyway Park - XML Updated", docId[1]);
      DOMHandle vTwohandle3 = getXMLDocumentHandle("2001-01-03T00:00:00",
          "2011-12-31T23:59:58", "999 Skyway Park - XML Updated", docId[2]);
      DOMHandle vTwohandle4 = getXMLDocumentHandle("2001-01-04T00:00:00",
          "2011-12-31T23:59:59", "999 Skyway Park - XML Updated", docId[3]);

      writesetUpd.add(versiondocId[0], null, vTwohandle1, docId[0]);
      writesetUpd.add(versiondocId[1], null, vTwohandle2, docId[1]);
      writesetUpd.add(versiondocId[2], null, vTwohandle3, docId[2]);
      writesetUpd.add(versiondocId[3], null, vTwohandle4, docId[3]);

      // Write updated version

      docMgr.write(writesetUpd, null, null, bulktemporalCollectionName);

      QueryManager queryMgr = readerClient.newQueryManager();
      StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

      String[] collections = { latestCollectionName, bulktemporalCollectionName, "insertCollection" };
      StructuredQueryDefinition termQuery = sqb.collection(collections);

      long start = 1;
      docMgr = readerClient.newXMLDocumentManager();
      docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
      termQueryResults = docMgr.search(termQuery, start);
      assertEquals(8, termQueryResults.size());
    } catch (Exception e) {
      System.out.println(e.getMessage());
      tstatus = true;
      throw e;
    } finally {
      if (tstatus) {
        if (termQueryResults != null)
          termQueryResults.close();
      }
      tx.rollback();
      tx = null;
    }
  }

  /*
   * Insert multiple temporal documents to test bulk write of temporal
   * documents. Verify that addAs does not blow up and contents can be added.
   */
  @Test
  public void testBulkWriteReadWithAddAs() throws Exception {

    boolean tstatus = false;
    DocumentPage termQueryResults = null;

    try {
      XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();

      DocumentWriteSet writeset = docMgr.newWriteSet();
      String[] docId = new String[4];
      docId[0] = "1.xml";
      docId[1] = "2.xml";
      docId[2] = "3.xml";
      docId[3] = "4.xml";

      Document doc1 = getXMLAsDocumentObject("2001-01-01T00:00:00",
          "2011-12-31T23:59:56", "999 Skyway Park - XML1", docId[0]);
      Document doc2 = getXMLAsDocumentObject("2001-01-02T00:00:00",
          "2011-12-31T23:59:57", "999 Skyway Park - XML2", docId[1]);
      Document doc3 = getXMLAsDocumentObject("2001-01-03T00:00:00",
          "2011-12-31T23:59:58", "999 Skyway Park - XML3", docId[2]);
      Document doc4 = getXMLAsDocumentObject("2001-01-04T00:00:00",
          "2011-12-31T23:59:59", "999 Skyway Park - XML4", docId[3]);
      DocumentMetadataHandle mh = setMetadata(false);

      writeset.addAs(docId[0], mh, doc1);
      writeset.addAs(docId[1], mh, doc2);
      writeset.addAs(docId[2], mh, doc3);
      writeset.addAs(docId[3], mh, doc4);

      docMgr.write(writeset, null, null, bulktemporalCollectionName);

      QueryManager queryMgr = readerClient.newQueryManager();
      StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

      String[] collections = { latestCollectionName, bulktemporalCollectionName, "insertCollection" };
      StructuredQueryDefinition termQuery = sqb.collection(collections);

      long start = 1;
      docMgr = readerClient.newXMLDocumentManager();
      docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
      termQueryResults = docMgr.search(termQuery, start);
      assertEquals(4, termQueryResults.size());

      // Read one document to make sure that addAs worked.
      DocumentPage page = docMgr.read(docId[2]);
      DOMHandle dh = new DOMHandle();
      while (page.hasNext()) {
        DocumentRecord rec = page.next();
        rec.getContent(dh);

        assertEquals(docId[2], rec.getUri());
        assertEquals(Format.XML, rec.getFormat());
        assertTrue(convertXMLDocumentToString(dh.get()).contains("XML3"));
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
      tstatus = true;
      throw e;
    } finally {
      if (tstatus) {
        if (termQueryResults != null)
          termQueryResults.close();
      }
    }
  }

  @Test
  // Insert a temporal document using DocumentUriTemplate
  public void testInsertXMLSingleDocumentUsingTemplate() throws Exception {
    System.out.println("Inside testInsertXMLSingleDocumentUsingTemplate");

    String docId = "javaSingleXMLDoc.xml";
    DOMHandle handle = getXMLDocumentHandle("2001-01-01T00:00:00",
        "2011-12-31T23:59:59", "777 Skyway Park - XML", docId);

    XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // Create document using using document template
    String dirName = "/java/bitemporal/";
    String fileSuffix = "xml";
    DocumentUriTemplate template = docMgr.newDocumentUriTemplate(fileSuffix);
    template.setDirectory(dirName);
    DocumentMetadataHandle mh = setMetadata(false);

    docMgr.create(template, mh, handle, null, null, temporalCollectionName);

    // Make sure there are no documents associated with "latest" collection
    QueryManager queryMgr = readerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

    String[] collections = { latestCollectionName, "insertCollection" };
    StructuredQueryDefinition termQuery = sqb.collection(collections);

    long start = 1;
    docMgr = readerClient.newXMLDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
    DocumentPage termQueryResults = docMgr.search(termQuery, start);

    long count = 0;
    while (termQueryResults.hasNext()) {
      ++count;
      DocumentRecord record = termQueryResults.next();

      String uri = record.getUri();
      System.out.println("URI = " + uri);

      if (!uri.contains(dirName) && !uri.contains(fileSuffix)) {
        fail("Uri name does not have the right prefix or suffix");
      }
    }

    System.out.println("Number of results = " + count);
    assertEquals(1, count);

    System.out.println("Done");
  }

  @Test
  // Insert a temporal document and update it using an invalid transform.
  // The transform in this case creates a duplicate element against which as
  // range index
  // has been setup
  public void testInsertAndUpdateXMLSingleDocumentUsingInvalidTransform()
      throws Exception {

    System.out.println("Inside testXMLWriteSingleDocument");
    String docId = "javaSingleXMLDoc.xml";

    boolean exceptionThrown = false;
    try {
      updateXMLSingleDocument(temporalCollectionName, docId,
          "add-element-xquery-invalid-bitemp-transform");
    } catch (com.marklogic.client.FailedRequestException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("XDMP-MULTIMATCH"));
      assertTrue((statusCode == 400));
    }

    assertTrue(exceptionThrown);
  }

  @Test
  // This test validates the following -
  // 1. Inserts, updates and delete and and also makes sure number of documents
  // in doc uri collection, latest collection are accurate after those
  // operations.
  // Do this for more than one document URI (we do this with JSON and XML)
  // 2. Make sure things are correct with transforms
  public void testConsolidated() throws Exception {

    System.out.println("Inside testXMLConsolidated");
    String xmlDocId = "javaSingleXMLDoc.xml";

    // =============================================================================
    // Check insert works
    // =============================================================================
    // Insert XML document
    insertXMLSingleDocument(temporalCollectionName, xmlDocId,
        "add-element-xquery-transform"); // Transforming during insert

    // Verify that the document was inserted
    XMLDocumentManager xmlDocMgr = readerClient.newXMLDocumentManager();
    DocumentPage readResults = xmlDocMgr.read(xmlDocId);
    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    // Now insert a JSON document
    String jsonDocId = "javaSingleJSONDoc.json";
    insertJSONSingleDocument(temporalCollectionName, jsonDocId, null);

    // Verify that the document was inserted
    JSONDocumentManager jsonDocMgr = readerClient.newJSONDocumentManager();
    readResults = jsonDocMgr.read(jsonDocId);
    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    // =============================================================================
    // Check update works
    // =============================================================================
    // Update XML document
    updateXMLSingleDocument(temporalCollectionName, xmlDocId, null);

    // Make sure there are 2 documents in latest collection
    QueryManager queryMgr = readerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();
    StructuredQueryDefinition termQuery = sqb.collection(latestCollectionName);

    long start = 1;
    DocumentPage termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(2, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in xmlDocId collection with term XML
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.and(sqb.and(sqb.term("XML"), sqb.collection(xmlDocId)));

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure transform on insert worked
    while (termQueryResults.hasNext()) {
      DocumentRecord record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      if (record.getFormat() != Format.XML) {
        fail("Format is not JSON: " + Format.JSON);
      } else {

        DOMHandle recordHandle = new DOMHandle();
        record.getContent(recordHandle);

        String content = recordHandle.toString();
        System.out.println("Content = " + content);

        // Check if transform worked. We did transform only with XML document
        if ((content.contains("2001-01-01T00:00:00")
            && content.contains("2011-12-31T23:59:59") && record.getFormat() != Format.XML)
            && (!content.contains("new-element") || !content
                .contains("2007-12-31T23:59:59"))) {
          fail("Transform did not work");
        } else {
          System.out.println("Transform Worked!");
        }
      }
    }

    // Update JSON document
    updateJSONSingleDocument(temporalCollectionName, jsonDocId);

    // Make sure there are still 2 documents in latest collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(latestCollectionName);

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(2, termQueryResults.getTotalSize());

    // Docu URIs in latest collection must be the same as the one as the
    // original document
    while (termQueryResults.hasNext()) {
      DocumentRecord record = termQueryResults.next();

      String uri = record.getUri();
      System.out.println("URI = " + uri);

      if (!uri.equals(xmlDocId) && !uri.equals(jsonDocId)) {
        fail("URIs are not what is expected");
      }
    }

    // Make sure there are 4 documents in jsonDocId collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(jsonDocId);

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there are 8 documents in temporal collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(temporalCollectionName);

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(8, termQueryResults.getTotalSize());

    // Make sure there are 8 documents in total. Use string search for this
    queryMgr = readerClient.newQueryManager();
    StringQueryDefinition stringQD = queryMgr.newStringDefinition();
    stringQD.setCriteria("Skyway Park");

    start = 1;
    termQueryResults = xmlDocMgr.search(stringQD, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(8, termQueryResults.getTotalSize());

    // =============================================================================
    // Check delete works
    // =============================================================================
    // Delete one of the document
    deleteXMLSingleDocument(temporalCollectionName, xmlDocId);

    // Make sure there are still 4 documents in xmlDocId collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(xmlDocId);

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there is one document with xmlDocId uri
    XMLDocumentManager docMgr = readerClient.newXMLDocumentManager();
    readResults = docMgr.read(xmlDocId);

    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    // Make sure there is only 1 document in latest collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(latestCollectionName);

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(1, termQueryResults.getTotalSize());

    // Docu URIs in latest collection must be the same as the one as the
    // original document
    while (termQueryResults.hasNext()) {
      DocumentRecord record = termQueryResults.next();

      String uri = record.getUri();
      System.out.println("URI = " + uri);

      if (!uri.equals(jsonDocId)) {
        fail("URIs are not what is expected");
      }
    }

    // Make sure there are 8 documents in temporal collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(temporalCollectionName);

    start = 1;
    termQueryResults = xmlDocMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(8, termQueryResults.getTotalSize());
  }

  @Test
  // Test bitemporal create, update and delete works with a JSON document
  // All database operations in this method are done using 'eval-user' against
  // port 8000
  public void testJSONConsolidated() throws Exception {

    System.out.println("Inside testJSONConsolidated");

    String docId = "javaSingleJSONDoc.json";
    insertJSONSingleDocumentAsEvalUser(temporalCollectionName, docId);

    // Verify that the document was inserted
    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    DocumentPage readResults = docMgr.read(docId);

    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    DocumentRecord latestDoc = readResults.next();
    System.out.println("URI after insert = " + latestDoc.getUri());
    assertEquals( docId, latestDoc.getUri());

    // Check if properties have been set. User XML DOcument Manager since
    // properties are written as XML
    JacksonDatabindHandle<ObjectNode> contentHandle = new JacksonDatabindHandle<>(
        ObjectNode.class);
    DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
    docMgr.read(docId, metadataHandle, contentHandle);

    validateMetadata(metadataHandle);

    // ================================================================
    // Update the document
    updateJSONSingleDocumentAsEvalUser(temporalCollectionName, docId);

    // Verify that the document was updated
    // Make sure there is 1 document in latest collection
    QueryManager queryMgr = writerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();
    StructuredQueryDefinition termQuery = sqb.collection(latestCollectionName);
    long start = 1;
    DocumentPage termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(1, termQueryResults.getTotalSize());

    // Docu URIs in latest collection must be the same as the one as the
    // original document
    while (termQueryResults.hasNext()) {
      DocumentRecord record = termQueryResults.next();

      String uri = record.getUri();
      System.out.println("URI = " + uri);

      if (!uri.equals(docId)) {
        fail("URIs are not what is expected");
      }
    }

    // Make sure there are 4 documents in jsonDocId collection
    queryMgr = writerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(docId);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in temporal collection
    queryMgr = writerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(temporalCollectionName);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in total. Use string search for this
    queryMgr = writerClient.newQueryManager();
    StringQueryDefinition stringQD = queryMgr.newStringDefinition();
    stringQD.setCriteria("Skyway Park");

    start = 1;
    docMgr.setMetadataCategories(Metadata.ALL);
    termQueryResults = docMgr.search(stringQD, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    while (termQueryResults.hasNext()) {
      DocumentRecord record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);
      Iterator<String> resCollections = metadataHandle.getCollections()
          .iterator();

      int count = 0;
      while (resCollections.hasNext()) {
        ++count;
        String collection = resCollections.next();
        System.out.println("Collection = " + collection);

        if (!collection.equals(docId) && !collection.equals(docId)
            && !collection.equals(latestCollectionName)
            && !collection.equals(updateCollectionName)
            && !collection.equals(insertCollectionName)
            && !collection.equals(temporalCollectionName)) {
          fail("Collection not what is expected: " + collection);
        }

        if (collection.equals(latestCollectionName)) {
          // If there is a latest collection, docId must match the URI
          assertTrue(record.getUri().equals(docId));
        }
      }

      if (record.getUri().equals(docId)) {
        // Must belong to latest collection as well. So, count must be 4
        assertTrue((count == 4));
      } else {
        assertTrue((count == 3));
      }

      if (record.getFormat() != Format.JSON) {
        fail("Format is not JSON: " + Format.JSON);
      } else {
        JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());
      }
    }

    // =============================================================================
    // Check delete works
    // =============================================================================
    // Delete one of the document
    deleteJSONSingleDocumentAsEvalUser(temporalCollectionName, docId);

    // Make sure there are still 4 documents in docId collection
    queryMgr = writerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(docId);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there is one document with docId uri
    docMgr = writerClient.newJSONDocumentManager();
    readResults = docMgr.read(docId);

    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    // Make sure there are no documents in latest collection
    queryMgr = writerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(latestCollectionName);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(0, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in temporal collection
    queryMgr = writerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(temporalCollectionName);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in total. Use string search for this
    queryMgr = writerClient.newQueryManager();

    start = 1;
    termQueryResults = docMgr.search(stringQD, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());
  }

  // Test LSQT advance manually. Should have automation set to false on the temporal collection used.
  @Test
  public void testAdvancingLSQT() throws Exception {
      try {
          System.out.println("Inside testAdvancingLSQT");
          ConnectedRESTQA.disableAutomationOnTemporalCollection(dbName, temporalLsqtCollectionName, true);

          String docId = "javaSingleJSONDoc.json";
          String afterLSQTAdvance = null;

          Calendar firstInsertTime = DatatypeConverter.parseDateTime("2010-01-01T00:00:01");
          JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();

          JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
                  "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
                  docId);
          TemporalDescriptor desc = docMgr.write(docId, null, handle, null, null, temporalLsqtCollectionName, firstInsertTime);
          // Verify permissions for LSQT advance
          String permnExceptMsg = "User is not allowed to advanceLsqt resource at temporal/collections/" + temporalLsqtCollectionName;
          String extMsg = null;
          try {
              JSONDocumentManager docMgr1 = readerClient.newJSONDocumentManager();
              docMgr1.advanceLsqt(temporalLsqtCollectionName);
          }
          catch (ForbiddenUserException ex) {
              extMsg = ex.getMessage();
              System.out.println("Permissions exception message for LSQT advance is " + extMsg);
          }
          assertTrue(extMsg.contains(permnExceptMsg));

          QueryManager queryMgrLSQT = adminClient.newQueryManager();
          StructuredQueryBuilder sqbLSQT = queryMgrLSQT.newStructuredQueryBuilder();

          Calendar queryTimeLSQT = DatatypeConverter.parseDateTime("2007-01-01T00:00:01");
          StructuredQueryDefinition periodQueryLSQT = sqbLSQT.temporalLsqtQuery(temporalLsqtCollectionName, queryTimeLSQT, 0, new String[] {});

          long startLSQT = 1;
          JSONDocumentManager docMgrQy = adminClient.newJSONDocumentManager();
          String WithoutAdvaceSetExceptMsg = "Timestamp 2007-01-01T00:00:01-08:00 provided is greater than LSQT 1601-01-01T00:00:00Z";
          String actualNoAdvanceMsg = null;
          DocumentPage termQueryResultsLSQT = null;
          try {
              termQueryResultsLSQT = docMgrQy.search(periodQueryLSQT, startLSQT);
          }
          catch(Exception ex) {
              actualNoAdvanceMsg = ex.getMessage();
              System.out.println("Exception message for LSQT without advance set is " + actualNoAdvanceMsg);
          }
          assertTrue(actualNoAdvanceMsg.contains(WithoutAdvaceSetExceptMsg));

          // Set the Advance manually.
          docMgr.advanceLsqt(temporalLsqtCollectionName);
          termQueryResultsLSQT = docMgrQy.search(periodQueryLSQT, startLSQT);

          assertTrue(termQueryResultsLSQT.getTotalPages() == 0);
          assertTrue(termQueryResultsLSQT.size() == 0);

          // After Advance of the LSQT, query again with new query time greater than LSQT

          afterLSQTAdvance = desc.getTemporalSystemTime();
          Calendar queryTimeLSQT2 = DatatypeConverter.parseDateTime(afterLSQTAdvance);
          queryTimeLSQT2.add(Calendar.YEAR, 10);
          docMgrQy = adminClient.newJSONDocumentManager();
          docMgrQy.setMetadataCategories(Metadata.ALL); // Get all meta-data
          StructuredQueryDefinition periodQueryLSQT2 = sqbLSQT.temporalLsqtQuery(temporalLsqtCollectionName, queryTimeLSQT2, 0, new String[] {});

          String excepMsgGrtr = "Timestamp 2020-01-01T00:00:01-08:00 provided is greater than LSQT 2010-01-01T08:00:01Z";
          String actGrMsg = null;
          DocumentPage termQueryResultsLSQT2 = null;
          try {
              termQueryResultsLSQT2 = docMgrQy.search(periodQueryLSQT2, startLSQT);
          }
          catch(Exception ex) {
              actGrMsg = ex.getMessage();
          }
          assertTrue(actGrMsg.contains(excepMsgGrtr));

          // Query again with query time less than LSQT. 10 minutes less than the LSQT
          Calendar lessTime = DatatypeConverter.parseDateTime("2009-01-01T00:00:01");

          periodQueryLSQT2 = sqbLSQT.temporalLsqtQuery(temporalLsqtCollectionName, lessTime, 0, new String[] {});
          termQueryResultsLSQT2 = docMgrQy.search(periodQueryLSQT2, startLSQT);

          System.out.println("LSQT Query results (Total Pages) after advance " + termQueryResultsLSQT2.getTotalPages());
          System.out.println("LSQT Query results (Size) after advance " + termQueryResultsLSQT2.size());
          assertTrue(termQueryResultsLSQT2.getTotalPages() == 0);
          assertTrue(termQueryResultsLSQT2.size() == 0);

          // Query again with query time equal to LSQT.
          queryTimeLSQT2 = DatatypeConverter.parseDateTime(afterLSQTAdvance);
          periodQueryLSQT2 = sqbLSQT.temporalLsqtQuery(temporalLsqtCollectionName, queryTimeLSQT2, 0, new String[] {});
          termQueryResultsLSQT2 = docMgrQy.search(periodQueryLSQT2, startLSQT);

          System.out.println("LSQT Query results (Total Pages) after advance " + termQueryResultsLSQT2.getTotalPages());
          System.out.println("LSQT Query results (Size) after advance " + termQueryResultsLSQT2.size());
          assertTrue(termQueryResultsLSQT2.getTotalPages() == 1);
          assertTrue(termQueryResultsLSQT2.size() == 1);

          while (termQueryResultsLSQT2.hasNext()) {
              DocumentRecord record = termQueryResultsLSQT2.next();
              System.out.println("URI = " + record.getUri());
              StringHandle resultHandleOfLSQT2 = new StringHandle();
              record.getContent(resultHandleOfLSQT2);
              String strResOfLSQT2 = resultHandleOfLSQT2.get();

              System.out.println("Result of LSQT Query 2 is " + strResOfLSQT2);
          }

          // Verify that the document was inserted
          JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(ObjectNode.class);
          DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
          docMgr.read(docId, metadataHandle, recordHandle);
          DocumentPage readResults = docMgr.read(docId);

          System.out.println("Number of results = " + readResults.size());
          assertEquals(1, readResults.size());

          DocumentRecord record = readResults.next();
          System.out.println("URI after insert = " + record.getUri());
          assertEquals( docId, record.getUri());
          System.out.println("Content = " + recordHandle.toString());

          // Make sure System start time was what was set ("2010-01-01T00:00:01")
          if (record.getFormat() != Format.JSON) {
              fail("Invalid document format: " + record.getFormat());
          } else {
              JsonFactory factory = new JsonFactory();
              ObjectMapper mapper = new ObjectMapper(factory);
              TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};

              Map<String, Object> docObject = mapper.readValue(recordHandle.toString(), typeRef);

              @SuppressWarnings("unchecked")
              Map<String, Object> validNode = (HashMap<String, Object>) (docObject.get(systemNodeName));

              String systemStartDate = (String) validNode.get(systemStartERIName);
              String systemEndDate = (String) validNode.get(systemEndERIName);
              System.out.println("systemStartDate = " + systemStartDate);
              System.out.println("systemEndDate = " + systemEndDate);

              assertTrue((systemStartDate.contains("2010-01-01T00:00:01")));
              assertTrue((systemEndDate.contains("9999-12-31T11:59:59")));

              // Validate collections
              Iterator<String> resCollections = metadataHandle.getCollections().iterator();
              while (resCollections.hasNext()) {
                  String collection = resCollections.next();
                  System.out.println("Collection = " + collection);

                  if (!collection.equals(docId)
                          && !collection.equals(insertCollectionName)
                          && !collection.equals(temporalLsqtCollectionName)
                          && !collection.equals(latestCollectionName)) {
                      fail("Collection not what is expected: " + collection);
                  }
              }

              // Validate permissions
              DocumentPermissions permissions = metadataHandle.getPermissions();
              System.out.println("Permissions: " + permissions);

              String actualPermissions = getDocumentPermissionsString(permissions);
              System.out.println("actualPermissions: " + actualPermissions);

              assertTrue(actualPermissions.contains("size:6"));
              assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
              assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));

              assertTrue(actualPermissions.contains("rest-reader:[READ]"));
              // Split up rest-writer:[READ, EXECUTE, UPDATE] string
              String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

              assertTrue(
                      writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
              assertTrue(
                      writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
              assertTrue(
                      writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));

              // Split up temporal-admin=[READ, UPDATE] string
              String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

              assertTrue(
                      temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
              assertTrue(
                      temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));
          }

          // =============================================================================
          // Check update works
          // =============================================================================
          Calendar updateTime = DatatypeConverter.parseDateTime(afterLSQTAdvance);
          // Advance the system time for update. To be greater than LSQT time.
          updateTime.add(Calendar.DAY_OF_MONTH, 5);
          JacksonDatabindHandle<ObjectNode> handleUpd = getJSONDocumentHandle("2003-01-01T00:00:00", "2008-12-31T23:59:59",
                  "1999 Skyway Park - Updated - JSON", docId);
          docMgr.setMetadataCategories(Metadata.ALL);
          DocumentMetadataHandle mh = setMetadata(true);

          desc = docMgr.write(docId, mh, handleUpd, null, null, temporalLsqtCollectionName, updateTime);
          // Validate the advance from desc
          docMgr.advanceLsqt(temporalLsqtCollectionName);
          afterLSQTAdvance = desc.getTemporalSystemTime();
          System.out.println("LSQT on collection after update and manual advance is " + afterLSQTAdvance);
          assertTrue(desc.getTemporalSystemTime().trim().contains("2010-01-06T00:00:01-08:00"));

          // Verify that the document was updated
          // Make sure there are 1 documents in latest collection
          QueryManager queryMgr = readerClient.newQueryManager();
          StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();
          StructuredQueryDefinition termQuery = sqb.collection(latestCollectionName);
          long start = 1;
          DocumentPage termQueryResults = docMgr.search(termQuery, start);
          System.out.println("Number of results = " + termQueryResults.getTotalSize());
          assertEquals(1, termQueryResults.getTotalSize());

          // Document URIs in latest collection must be the same as the one as the
          // original documents
          while (termQueryResults.hasNext()) {
              record = termQueryResults.next();
              String uri = record.getUri();
              System.out.println("URI = " + uri);
              assertTrue(uri.equals(docId));
          }

          // Make sure there are 4 documents in jsonDocId collection
          queryMgr = readerClient.newQueryManager();
          sqb = queryMgr.newStructuredQueryBuilder();
          termQuery = sqb.collection(docId);

          start = 1;
          termQueryResults = docMgr.search(termQuery, start);
          System.out.println("Number of results = " + termQueryResults.getTotalSize());
          assertEquals(4, termQueryResults.getTotalSize());

          // Make sure there are 4 documents in temporal collection
          queryMgr = readerClient.newQueryManager();
          sqb = queryMgr.newStructuredQueryBuilder();
          termQuery = sqb.collection(temporalLsqtCollectionName);

          start = 1;
          termQueryResults = docMgr.search(termQuery, start);
          System.out.println("Number of results = " + termQueryResults.getTotalSize());
          assertEquals(5, termQueryResults.getTotalSize());

          // Issue a period range search to make sure update went fine.
          StructuredQueryBuilder.Axis axis = sqbLSQT.axis(axisValidName);
          StructuredQueryBuilder.Period period = sqbLSQT.period("2003-01-01T00:00:00", "2009-12-31T23:59:59");

          periodQueryLSQT2 = sqbLSQT.temporalPeriodRange(axis, StructuredQueryBuilder.TemporalOperator.ALN_CONTAINS, period, new String[] {});
          termQueryResultsLSQT2 = docMgrQy.search(periodQueryLSQT2, startLSQT);
          assertTrue(termQueryResultsLSQT2.getTotalSize() == 1);

          while (termQueryResultsLSQT2.hasNext()) {
              DocumentRecord recordContains = termQueryResultsLSQT2.next();
              System.out.println("URI = " + recordContains.getUri());

              JacksonDatabindHandle<ObjectNode> recordContainsHandle = new JacksonDatabindHandle<>(
                      ObjectNode.class);
              recordContains.getContent(recordContainsHandle);
              String docContents = recordContainsHandle.toString();
              System.out.println("Content = " + docContents);
              assertTrue(docContents.contains("\"javaValidStartERI\":\"2001-01-01T00:00:00\",\"javaValidEndERI\":\"2011-12-31T23:59:59\""));
          }
      }
    catch (Exception ex) {
        System.out.println("Exception thrown from testAdvacingLSQT method " + ex.getMessage() );
    }
    finally {
        ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName, temporalLsqtCollectionName, true);
    }
  }

  @Test
  // Create a bitemporal document and update the document with a system time
  // that is less than
  // the one used during creation
  public void testSystemTimeUsingInvalidTime() throws Exception {

    System.out.println("Inside testSystemTimeUsingInvalidTime");
    ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName,
        temporalLsqtCollectionName, true);

    String docId = "javaSingleJSONDoc.json";

    Calendar firstInsertTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:01");
    insertJSONSingleDocument(temporalLsqtCollectionName, docId, null, null,
        firstInsertTime);

    // Sleep for 2 seconds for LSQT to be advanced
    Thread.sleep(2000);

    // Update by passing a system time that is less than previous one
    Calendar updateTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:00");

    boolean exceptionThrown = false;
    try {
      updateJSONSingleDocument(temporalLsqtCollectionName, docId, null,
          updateTime);
    } catch (com.marklogic.client.FailedRequestException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("TEMPORAL-OPNOTAFTERLSQT"));
      assertTrue((statusCode == 400));
    }

    assertTrue(
        exceptionThrown, "Exception not thrown during invalid update of system time");

    // Delete by passing invalid time
    Calendar deleteTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:00");

    exceptionThrown = false;
    try {
      deleteJSONSingleDocument(temporalLsqtCollectionName, docId, null,
          deleteTime);
    } catch (com.marklogic.client.FailedRequestException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("TEMPORAL-SYSTEMTIME-BACKWARDS"));
      assertTrue((statusCode == 400));
    }

    assertTrue(exceptionThrown);
  }

  @Test
  // Test transaction commit with bitemporal documents
  public void testTransactionCommit() throws Exception {

    System.out.println("Inside testTransactionCommit");

    String docId = "javaSingleJSONDoc.json";

    Transaction transaction = writerClient
        .openTransaction("Transaction for BiTemporal");
    try {
      insertJSONSingleDocument(temporalCollectionName, docId, null,
          transaction, null);

      // Verify that the document was inserted
      JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
      DocumentPage readResults = docMgr.read(transaction, docId);

      System.out.println("Number of results = " + readResults.size());
      if (readResults.size() != 1) {
        transaction.rollback();

        assertEquals(1, readResults.size());
      }

      DocumentRecord latestDoc = readResults.next();
      System.out.println("URI after insert = " + latestDoc.getUri());

      if (!docId.equals(latestDoc.getUri())) {
        transaction.rollback();

        assertEquals( docId,
            latestDoc.getUri());
      }

      // Make sure document is not visible to any other transaction
      boolean exceptionThrown = false;
      try {
        JacksonDatabindHandle<ObjectNode> contentHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
        docMgr.read(docId, metadataHandle, contentHandle);
      } catch (Exception ex) {
        exceptionThrown = true;
      }

      if (!exceptionThrown) {
        transaction.rollback();

        assertTrue(

            exceptionThrown, "Exception not thrown during read using no transaction handle");
      }

      updateJSONSingleDocument(temporalCollectionName, docId, transaction, null);

      QueryManager queryMgr = writerClient.newQueryManager();
      StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();
      StructuredQueryDefinition termQuery = sqb
          .collection(latestCollectionName);

      long start = 1;
      DocumentPage termQueryResults = docMgr.search(termQuery, start,
          transaction);
      System.out.println("Number of results = "
          + termQueryResults.getTotalSize());

      if (termQueryResults.getTotalSize() != 1) {
        transaction.rollback();

        assertEquals(1,
            termQueryResults.getTotalSize());
      }

      // There should be 4 documents in docId collection
      queryMgr = writerClient.newQueryManager();
      sqb = queryMgr.newStructuredQueryBuilder();
      termQuery = sqb.collection(docId);

      start = 1;
      termQueryResults = docMgr.search(termQuery, start, transaction);
      System.out.println("Number of results = "
          + termQueryResults.getTotalSize());

      if (termQueryResults.getTotalSize() != 4) {
        transaction.rollback();

        assertEquals(4,
            termQueryResults.getTotalSize());
      }

      // Search for documents using doc uri collection and no transaction object
      // passed.
      // There should be 0 documents in docId collection
      queryMgr = writerClient.newQueryManager();
      sqb = queryMgr.newStructuredQueryBuilder();
      termQuery = sqb.collection(docId);

      start = 1;
      termQueryResults = docMgr.search(termQuery, start);
      System.out.println("Number of results = "
          + termQueryResults.getTotalSize());

      if (termQueryResults.getTotalSize() != 0) {
        transaction.rollback();

        assertEquals(0,
            termQueryResults.getTotalSize());
      }

      deleteJSONSingleDocument(temporalCollectionName, docId, transaction);

      // There should be no documents in latest collection
      queryMgr = writerClient.newQueryManager();
      sqb = queryMgr.newStructuredQueryBuilder();
      termQuery = sqb.collection(latestCollectionName);

      start = 1;
      termQueryResults = docMgr.search(termQuery, start, transaction);
      System.out.println("Number of results = "
          + termQueryResults.getTotalSize());

      if (termQueryResults.getTotalSize() != 0) {
        transaction.rollback();

        assertEquals(0,
            termQueryResults.getTotalSize());
      }

      transaction.commit();
      transaction = null;

      // There should still be no documents in latest collection
      queryMgr = writerClient.newQueryManager();
      sqb = queryMgr.newStructuredQueryBuilder();
      termQuery = sqb.collection(latestCollectionName);

      start = 1;
      termQueryResults = docMgr.search(termQuery, start);
      System.out.println("Number of results = "
          + termQueryResults.getTotalSize());
      assertEquals(0,
          termQueryResults.getTotalSize());
    } catch (Exception ex) {
      transaction.rollback();
      transaction = null;

      assertTrue(false);
    } finally {
      if (transaction != null) {
        transaction.rollback();
        transaction = null;
      }
    }
  }

  @Test
  // Test transaction rollback with bitemporal documents
  public void testTransactionRollback() throws Exception {

    System.out.println("Inside testTransactionRollback");
    Transaction transaction = writerClient
        .openTransaction("Transaction for BiTemporal");

    try {
      String docId = "javaSingleJSONDoc.json";
      try {
        insertJSONSingleDocument(temporalCollectionName, docId, null,
            transaction, null);
      } catch (Exception ex) {
        transaction.rollback();
        transaction = null;

        fail("insertJSONSingleDocument failed in testTransactionRollback");
      }

      // Verify that the document was inserted
      JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
      DocumentPage readResults = docMgr.read(transaction, docId);

      System.out.println("Number of results = " + readResults.size());
      if (readResults.size() != 1) {
        transaction.rollback();

        assertEquals(1, readResults.size());
      }

      DocumentRecord latestDoc = readResults.next();
      System.out.println("URI after insert = " + latestDoc.getUri());
      if (!docId.equals(latestDoc.getUri())) {
        transaction.rollback();

        assertEquals( docId, latestDoc.getUri());
      }

      try {
        updateJSONSingleDocument(temporalCollectionName, docId, transaction, null);
      } catch (Exception ex) {
        transaction.rollback();
        transaction = null;

        fail("updateJSONSingleDocument failed in testTransactionRollback");
      }

      // Verify that the document is visible and count is 4
      // Fetch documents associated with a search term (such as XML) in Address
      // element
      QueryManager queryMgr = writerClient.newQueryManager();
      StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

      StructuredQueryDefinition termQuery = sqb.collection(docId);

      long start = 1;
      DocumentPage termQueryResults = docMgr
          .search(termQuery, start, transaction);
      System.out
          .println("Number of results = " + termQueryResults.getTotalSize());
      if (termQueryResults.getTotalSize() != 4) {
        transaction.rollback();

        assertEquals(4,
            termQueryResults.getTotalSize());
      }

      transaction.rollback();

      // Verify that the document is not there after rollback
      boolean exceptionThrown = false;
      try {
        JacksonDatabindHandle<ObjectNode> contentHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
        docMgr.read(docId, metadataHandle, contentHandle);
      } catch (Exception ex) {
        exceptionThrown = true;
      }

      if (!exceptionThrown) {
        transaction.rollback();

        assertTrue(exceptionThrown, "Exception not thrown during read on non-existing uri");
      }

      // =======================================================================
      // Now try rollback with delete
      System.out.println("Test Rollback after delete");
      docId = "javaSingleJSONDocForDelete.json";

      transaction = writerClient
          .openTransaction("Transaction Rollback for BiTemporal Delete");

      try {
        insertJSONSingleDocument(temporalCollectionName, docId, null,
            transaction, null);
      } catch (Exception ex) {
        transaction.rollback();
        transaction = null;

        fail("insertJSONSingleDocument failed in testTransactionRollback");
      }

      // Verify that the document was inserted
      docMgr = writerClient.newJSONDocumentManager();
      readResults = docMgr.read(transaction, docId);

      System.out.println("Number of results = " + readResults.size());
      if (readResults.size() != 1) {
        transaction.rollback();

        assertEquals(1, readResults.size());
      }

      latestDoc = readResults.next();
      System.out.println("URI after insert = " + latestDoc.getUri());
      if (!docId.equals(latestDoc.getUri())) {
        transaction.rollback();

        assertEquals( docId, latestDoc.getUri());
      }

      try {
        deleteJSONSingleDocument(temporalCollectionName, docId, transaction);
      } catch (Exception ex) {
        transaction.rollback();
        transaction = null;

        fail("deleteJSONSingleDocument failed in testTransactionRollback");
      }

      // Verify that the document is visible and count is 1
      // Fetch documents associated with a search term (such as XML) in Address
      // element
      queryMgr = writerClient.newQueryManager();
      sqb = queryMgr.newStructuredQueryBuilder();

      termQuery = sqb.collection(docId);

      start = 1;
      termQueryResults = docMgr.search(termQuery, start, transaction);
      System.out
          .println("Number of results = " + termQueryResults.getTotalSize());
      if (termQueryResults.getTotalSize() != 1) {
        transaction.rollback();

        assertEquals(1,
            termQueryResults.getTotalSize());
      }

      transaction.rollback();
      transaction = null;

      // Verify that the document was rolled back and count is 0
      exceptionThrown = false;
      try {
        readResults = docMgr.read(docId);
      } catch (Exception ex) {
        exceptionThrown = true;
      }

      System.out.println("Done");
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (transaction != null) {
        transaction.rollback();
        transaction = null;
      }
    }
  }

  @Test
  // Test Period Range Query using ALN_CONTAINS. We use a single axis during
  // query
  @Disabled("Started failing on October 18th, 2024 on MarkLogic 10, 11, and 12, suggesting that the test is " +
	  "brittle and likely affected by some date/time constraint.")
  public void testPeriodRangeQuerySingleAxisBasedOnALNContains()
      throws Exception {
    System.out.println("Inside testPeriodRangeQuerySingleAxisBasedOnALNContains");

    // Read documents based on document URI and ALN Contains. We are just
    // looking for count of documents to be correct

    String docId = "javaSingleJSONDoc.json";

    insertJSONSingleDocument(temporalCollectionName, docId, null);
    waitForPropertyPropagate();

    updateJSONSingleDocument(temporalCollectionName, docId);
    waitForPropertyPropagate();

    // Fetch documents associated with a search term (such as XML) in Address
    // element
    QueryManager queryMgr = readerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

    StructuredQueryDefinition termQuery = sqb.collection(docId);

    StructuredQueryBuilder.Axis validAxis = sqb.axis(axisValidName);
    Calendar start1 = DatatypeConverter.parseDateTime("2001-01-01T00:00:01");
    Calendar end1 = DatatypeConverter.parseDateTime("2011-12-31T23:59:58");
    StructuredQueryBuilder.Period period1 = sqb.period(start1, end1);
    StructuredQueryDefinition periodQuery = sqb
        .and(termQuery, sqb.temporalPeriodRange(validAxis,
            TemporalOperator.ALN_CONTAINS, period1));

    long start = 1;
    System.out.println("Query is " + periodQuery.serialize() );
    JSONDocumentManager docMgr = readerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
    DocumentPage termQueryResults = docMgr.search(periodQuery, start);
    Thread.sleep(2000);

    long count = 0;
    while (termQueryResults.hasNext()) {
      ++count;
      DocumentRecord record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);
      Iterator<String> resCollections = metadataHandle.getCollections()
          .iterator();
      while (resCollections.hasNext()) {
        System.out.println("Collection = " + resCollections.next());
      }

      if (record.getFormat() == Format.XML) {
        DOMHandle recordHandle = new DOMHandle();
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());
      } else {
        JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        Map<String, Object> docObject = mapper.readValue(
            recordHandle.toString(), typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> systemNode = (HashMap<String, Object>) (docObject
            .get(systemNodeName));

        String systemStartDate = (String) systemNode.get(systemStartERIName);
        String systemEndDate = (String) systemNode.get(systemEndERIName);
        System.out.println("systemStartDate = " + systemStartDate);
        System.out.println("systemEndDate = " + systemEndDate);

        @SuppressWarnings("unchecked")
        Map<String, Object> validNode = (HashMap<String, Object>) (docObject
            .get(validNodeName));

        String validStartDate = (String) validNode.get(validStartERIName);
        String validEndDate = (String) validNode.get(validEndERIName);
        System.out.println("validStartDate = " + validStartDate);
        System.out.println("validEndDate = " + validEndDate);

        assertTrue(
            (validStartDate.equals("2001-01-01T00:00:00") && validEndDate
                .equals("2011-12-31T23:59:59")));
      }
    }

    System.out.println("Number of results using SQB = " + count);
    assertEquals(1, count);
  }

  @Test
  // Test Period Range Query usig ALN_CONTAINS. We use 2 axes during query
  // Note that the query will be done for every axis across every period. And
  // the results will be an OR of the result of each of the query done for every
  // axis
  // across every period
  @Disabled("Started failing on October 18th, 2024 on MarkLogic 10, 11, and 12, suggesting that the test is " +
	  "brittle and likely affected by some date/time constraint.")
  public void testPeriodRangeQueryMultiplesAxesBasedOnALNContains()
      throws Exception {
    System.out.println("Inside testPeriodRangeQueryMultiplesAxesBasedOnALNContains");

    // Read documents based on document URI and ALN_OVERLAPS. We are just
    // looking
    // for count of documents to be correct

    String docId = "javaSingleJSONDoc.json";

    insertJSONSingleDocument(temporalCollectionName, docId, null);
    updateJSONSingleDocument(temporalCollectionName, docId);

    // Fetch documents associated with a search term (such as XML) in Address
    // element
    QueryManager queryMgr = readerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

    StructuredQueryDefinition termQuery = sqb.collection(docId);

    StructuredQueryBuilder.Axis validAxis1 = sqb.axis(axisValidName);
    Calendar start1 = DatatypeConverter.parseDateTime("2001-01-01T00:00:01");
    Calendar end1 = DatatypeConverter.parseDateTime("2011-12-31T23:59:58");
    StructuredQueryBuilder.Period period1 = sqb.period(start1, end1);

    StructuredQueryBuilder.Axis validAxis2 = sqb.axis(axisValidName);
    Calendar start2 = DatatypeConverter.parseDateTime("2003-01-01T00:00:01");
    Calendar end2 = DatatypeConverter.parseDateTime("2008-12-31T23:59:58");
    StructuredQueryBuilder.Period period2 = sqb.period(start2, end2);

    StructuredQueryBuilder.Axis[] axes = new StructuredQueryBuilder.Axis[] {
        validAxis1, validAxis2 };
    StructuredQueryBuilder.Period[] periods = new StructuredQueryBuilder.Period[] {
        period1, period2 };

    StructuredQueryDefinition periodQuery = sqb.and(termQuery,
        sqb.temporalPeriodRange(axes, TemporalOperator.ALN_CONTAINS, periods));

    // Note that the query will be done for every axis across every period. And
    // the results will be an OR of the result of each of the query done for
    // every axis
    // across every period
    long start = 1;
    JSONDocumentManager docMgr = readerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
    DocumentPage termQueryResults = docMgr.search(periodQuery, start);

    long count = 0;
    while (termQueryResults.hasNext()) {
      ++count;
      DocumentRecord record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);
      Iterator<String> resCollections = metadataHandle.getCollections()
          .iterator();
      while (resCollections.hasNext()) {
        System.out.println("Collection = " + resCollections.next());
      }

      if (record.getFormat() != Format.JSON) {
        fail("Invalid document format: " + record.getFormat());
      } else {
        JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        Map<String, Object> docObject = mapper.readValue(
            recordHandle.toString(), typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> validNode = (HashMap<String, Object>) (docObject
            .get(validNodeName));

        String validStartDate = (String) validNode.get(validStartERIName);
        String validEndDate = (String) validNode.get(validEndERIName);
        System.out.println("validStartDate = " + validStartDate);
        System.out.println("validEndDate = " + validEndDate);

        assertTrue(
            (validStartDate.equals("2001-01-01T00:00:00") || validStartDate
                .equals("2003-01-01T00:00:00")));
        assertTrue(
            (validEndDate.equals("2011-12-31T23:59:59") || validEndDate
                .equals("2008-12-31T23:59:59")));
      }
    }

    System.out.println("Number of results using SQB = " + count);
    assertEquals(2, count);
  }

  @Test
  // Test Period Compare Query using ALN_CONTAINS as the operator
  public void testPeriodCompareQueryBasedOnALNContains()
      throws Exception {
    System.out.println("Inside testPeriodCompareQueryBasedOnALNContains");

    // Read documents based on document URI and ALN Contains. We are just
    // looking for count of documents to be correct
    String docId = "javaSingleJSONDoc.json";
    ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName,
        temporalLsqtCollectionName, true);

    Calendar insertTime = DatatypeConverter
        .parseDateTime("2005-01-01T00:00:01");
    insertJSONSingleDocument(temporalLsqtCollectionName, docId, null, null,
        insertTime);

    Calendar updateTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:01");
    updateJSONSingleDocument(temporalLsqtCollectionName, docId, null,
        updateTime);

    // Fetch documents associated with a search term (such as XML) in Address
    // element
    QueryManager queryMgr = readerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

    StructuredQueryBuilder.Axis validAxis = sqb.axis(axisValidName);
    StructuredQueryBuilder.Axis systemAxis = sqb.axis(axisSystemName);

    StructuredQueryDefinition termQuery = sqb.collection(docId);
    StructuredQueryDefinition periodQuery = sqb.and(termQuery, sqb
        .temporalPeriodCompare(validAxis, TemporalOperator.ALN_CONTAINS,
            systemAxis));

    long start = 1;
    JSONDocumentManager docMgr = readerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
    DocumentPage termQueryResults = docMgr.search(periodQuery, start);

    long count = 0;
    while (termQueryResults.hasNext()) {
      ++count;
      DocumentRecord record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);
      Iterator<String> resCollections = metadataHandle.getCollections()
          .iterator();
      while (resCollections.hasNext()) {
        System.out.println("Collection = " + resCollections.next());
      }

      if (record.getFormat() == Format.XML) {
        DOMHandle recordHandle = new DOMHandle();
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());
      } else {
        JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(
            ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        Map<String, Object> docObject = mapper.readValue(
            recordHandle.toString(), typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> systemNode = (HashMap<String, Object>) (docObject
            .get(systemNodeName));

        String systemStartDate = (String) systemNode.get(systemStartERIName);
        String systemEndDate = (String) systemNode.get(systemEndERIName);
        System.out.println("systemStartDate = " + systemStartDate);
        System.out.println("systemEndDate = " + systemEndDate);

        @SuppressWarnings("unchecked")
        Map<String, Object> validNode = (HashMap<String, Object>) (docObject
            .get(validNodeName));

        String validStartDate = (String) validNode.get(validStartERIName);
        String validEndDate = (String) validNode.get(validEndERIName);
        System.out.println("validStartDate = " + validStartDate);
        System.out.println("validEndDate = " + validEndDate);

        assertTrue(
            (validStartDate.contains("2001-01-01T00:00:00") && validEndDate
                .contains("2011-12-31T23:59:59")));

        assertTrue(
            (systemStartDate.contains("2005-01-01T00:00:01") && systemEndDate
                .contains("2010-01-01T00:00:01")));

      }
    }

    System.out.println("Number of results using SQB = " + count);
    assertEquals(1, count);
  }

  @Test
  // Test LSQT Query using temporalLsqtQuery. Do the query as REST reader
  public void testLsqtQuery() throws Exception {

    ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName,
        temporalLsqtCollectionName, true);

    // Read documents based on document URI and ALN Contains. We are just
    // looking for count of documents to be correct
    String docId = "javaSingleJSONDoc.json";

    Calendar insertTime = DatatypeConverter
        .parseDateTime("2005-01-01T00:00:01");
    insertJSONSingleDocument(temporalLsqtCollectionName, docId, null, null,
        insertTime);

    Calendar updateTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:01");
    updateJSONSingleDocument(temporalLsqtCollectionName, docId, null,
        updateTime);

    Thread.sleep(2000);

    validateLSQTQueryData(readerClient);
  }

  @Test
  // Test LSQT Query using temporalLsqtQuery. Do the query as REST admin
  public void testLsqtQueryAsAdmin() throws Exception {

    ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName,
        temporalLsqtCollectionName, true);

    // Read documents based on document URI and ALN Contains. We are just
    // looking for count of documents to be correct
    String docId = "javaSingleJSONDoc.json";

    Calendar insertTime = DatatypeConverter
        .parseDateTime("2005-01-01T00:00:01");

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        docId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);
    docMgr.write(docId, mh, handle, null, null, temporalLsqtCollectionName,
        insertTime);

    Calendar updateTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:01");
    docMgr.write(docId, mh, handle, null, null, temporalLsqtCollectionName,
        updateTime);

    Thread.sleep(2000);

    validateLSQTQueryData(writerClient);
  }

  @Test
  // Test inserting a temporal document and transform it using server-side
  // Javascript
  public void testJSTransforms() throws Exception {
    // Now insert a JSON document
    System.out.println("In testJSONTransforms .. testing JSON transforms");
    String jsonDocId = "javaSingleJSONDoc.json";

    insertJSONSingleDocument(temporalCollectionName, jsonDocId,
        "timestampTransform");

    System.out.println("Out testJSONTransforms .. testing JSON transforms");
  }

  @Test
  // Negative test
  // Test inserting a JSON temporal document by specifying XML as the extension
  public void testInsertJSONDocumentUsingXMLExtension() throws Exception {
    // Now insert a JSON document
    String jsonDocId = "javaSingleJSONDoc.xml";

    boolean exceptionThrown = false;
    try {
      insertJSONSingleDocument(temporalCollectionName, jsonDocId, null);
    } catch (com.marklogic.client.FailedRequestException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("XDMP-DOCROOTTEXT"));
      assertTrue((statusCode == 400));
    }

    assertTrue(exceptionThrown);
  }

  @Test
  // Negative test
  // Test inserting a temporal document into a non-existing temporal document
  public void testInsertJSONDocumentUsingNonExistingTemporalCollection()
      throws Exception {
    // Now insert a JSON document
    String jsonDocId = "javaSingleJSONDoc.json";

    boolean exceptionThrown = false;

    System.out.println("Inside testInsertJSONDocumentUsingNonExistingTemporalCollection");

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        jsonDocId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);

    try {
      docMgr.write(jsonDocId, mh, handle, null, null, "invalidCollection");
    } catch (com.marklogic.client.FailedRequestException ex) {

      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("TEMPORAL-COLLECTIONNOTFOUND"));
      assertTrue((statusCode == 400));
    }

    assertTrue(exceptionThrown, "Exception not thrown for invalid temporal collection");
  }

  @Test
  // Negative test
  // Test inserting a temporal document into the "latest" collection. Operation
  // should fail
  public void testDocumentUsingCollectionNamedLatest() throws Exception {
    // Now insert a JSON document
    String jsonDocId = "javaSingleJSONDoc.json";

    boolean exceptionThrown = false;

    System.out.println("Inside testDocumentUsingCollectionNamedLatest");

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        jsonDocId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);

    try {
      docMgr.write(jsonDocId, mh, handle, null, null, latestCollectionName);
    } catch (com.marklogic.client.FailedRequestException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("TEMPORAL-COLLECTIONLATEST"));
      assertTrue((statusCode == 400));
    }

    assertTrue(exceptionThrown, "Exception not thrown for invalid temporal collection");
  }

  @Test
  // Negative test
  // Test inserting a temporal document as REST reader who does not have the
  // privilege for the
  // operation
  public void testInsertJSONDocumentUsingAsRESTReader() throws Exception {
    // Now insert a JSON document
    String jsonDocId = "javaSingleJSONDoc.json";

    boolean exceptionThrown = false;

    System.out
        .println("Inside testInsertJSONDocumentUsingNonExistingTemporalCollection");

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        jsonDocId);

    JSONDocumentManager docMgr = readerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);

    try {
      docMgr.write(jsonDocId, mh, handle, null, null, temporalCollectionName);
    } catch (com.marklogic.client.ForbiddenUserException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("SEC-PRIV"));
      assertTrue((statusCode == 403));
    }

    assertTrue(exceptionThrown, "Exception not thrown for invalid temporal collection");
  }

  @Test
  // Negative test.
  // Insert a temporal document whose Doc URI Id is the same as the temporal
  // collection name
  public void testInsertDocumentUsingDocumentURIAsCollectionName()
      throws Exception {
    // Now insert a JSON document
    String jsonDocId = "javaSingleJSONDoc.json";

    System.out
        .println("Inside testInserDocumentUsingDocumentURIAsCollectionName");

    // First Create collection a collection with same name as doci URI
    ConnectedRESTQA.addElementRangeIndexTemporalCollection(dbName, jsonDocId,
        axisSystemName, axisValidName);

    // Insert a document called as insertJSONSingleDocument
    insertJSONSingleDocument(temporalCollectionName, jsonDocId, null);

    JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
        "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
        jsonDocId);

    JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();
    docMgr.setMetadataCategories(Metadata.ALL);

    // put meta-data
    DocumentMetadataHandle mh = setMetadata(false);

    boolean exceptionThrown = false;
    try {
      docMgr.write(jsonDocId, mh, handle, null, null, jsonDocId);
    } catch (com.marklogic.client.FailedRequestException ex) {
      String message = ex.getFailedRequest().getMessageCode();
      int statusCode = ex.getFailedRequest().getStatusCode();

      exceptionThrown = true;

      System.out.println(message);
      System.out.println(statusCode);

      assertTrue(message.equals("TEMPORAL-CANNOT-URI"));
      assertTrue((statusCode == 400));
    }

    ConnectedRESTQA.deleteElementRangeIndexTemporalCollection("Documents",
        jsonDocId);

    assertTrue(exceptionThrown, "Exception not thrown for invalid temporal collection");
  }

  @Test
  // Test bitemporal create, update and delete works with a JSON document while
  // passing
  // system time. The temporal collection needs to be enabled for lsqt and we
  // have enabled
  // automation for lsqt (lsqt will be advanced every second and system time
  // will be set with
  // a lag of 1 second)
  public void testSystemTime() throws Exception {

    System.out.println("Inside testSystemTime");
    ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName,
        temporalLsqtCollectionName, true);

    String docId = "javaSingleJSONDoc.json";

    Calendar firstInsertTime = DatatypeConverter
        .parseDateTime("2010-01-01T00:00:01");
    insertJSONSingleDocument(temporalLsqtCollectionName, docId, null, null,
        firstInsertTime);

    // Verify that the document was inserted
    JSONDocumentManager docMgr = readerClient.newJSONDocumentManager();
    JacksonDatabindHandle<ObjectNode> recordHandle = new JacksonDatabindHandle<>(
        ObjectNode.class);
    DocumentMetadataHandle metadataHandle = new DocumentMetadataHandle();
    docMgr.read(docId, metadataHandle, recordHandle);
    DocumentPage readResults = docMgr.read(docId);

    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    DocumentRecord record = readResults.next();
    System.out.println("URI after insert = " + record.getUri());
    assertEquals( docId, record.getUri());
    System.out.println("Content = " + recordHandle.toString());

    // Make sure System start time was what was set ("2010-01-01T00:00:01")
    if (record.getFormat() != Format.JSON) {
      fail("Invalid document format: " + record.getFormat());
    } else {
      JsonFactory factory = new JsonFactory();
      ObjectMapper mapper = new ObjectMapper(factory);
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
      };

      Map<String, Object> docObject = mapper.readValue(
          recordHandle.toString(), typeRef);

      @SuppressWarnings("unchecked")
      Map<String, Object> validNode = (HashMap<String, Object>) (docObject
          .get(systemNodeName));

      String systemStartDate = (String) validNode.get(systemStartERIName);
      String systemEndDate = (String) validNode.get(systemEndERIName);
      System.out.println("systemStartDate = " + systemStartDate);
      System.out.println("systemEndDate = " + systemEndDate);

      assertTrue(
          (systemStartDate.contains("2010-01-01T00:00:01")));
      assertTrue(
          (systemEndDate.contains("9999-12-31T11:59:59")));

      // Validate collections
      Iterator<String> resCollections = metadataHandle.getCollections()
          .iterator();
      while (resCollections.hasNext()) {
        String collection = resCollections.next();
        System.out.println("Collection = " + collection);

        if (!collection.equals(docId)
            && !collection.equals(insertCollectionName)
            && !collection.equals(temporalLsqtCollectionName)
            && !collection.equals(latestCollectionName)) {
          fail("Collection not what is expected: " + collection);
        }
      }

      // Validate permissions
      DocumentPermissions permissions = metadataHandle.getPermissions();
      System.out.println("Permissions: " + permissions);

      String actualPermissions = getDocumentPermissionsString(permissions);
      System.out.println("actualPermissions: " + actualPermissions);

      assertTrue(
          actualPermissions.contains("size:7"));

      assertTrue(
          actualPermissions.contains("rest-reader:[READ]"));
      assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
      assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));
      // Split up rest-writer:[READ, EXECUTE, UPDATE] string
      String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

      assertTrue(
          writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
      assertTrue(
          writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
      assertTrue(
          writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
      // Split up app-user app-user:[UPDATE, EXECUTE, READ] string
      String[] appUserPerms = actualPermissions.split("app\\-user:\\[")[1].split("\\]")[0].split(",");

      assertTrue(
          appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
      assertTrue(
          appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
      assertTrue(
          appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));

      // Split up temporal-admin=[READ, UPDATE] string
      String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

      assertTrue(
          temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
      assertTrue(
          temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

      // Validate quality
      int quality = metadataHandle.getQuality();
      System.out.println("Quality: " + quality);
      assertEquals(quality, 11);

      validateMetadata(metadataHandle);
    }

    // =============================================================================
    // Check update works
    // =============================================================================
    Calendar updateTime = DatatypeConverter
        .parseDateTime("2011-01-01T00:00:01");
    updateJSONSingleDocument(temporalLsqtCollectionName, docId, null,
        updateTime);

    // Verify that the document was updated
    // Make sure there is 1 document in latest collection
    QueryManager queryMgr = readerClient.newQueryManager();
    StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();
    StructuredQueryDefinition termQuery = sqb.collection(latestCollectionName);
    long start = 1;
    DocumentPage termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(1, termQueryResults.getTotalSize());

    // Document URIs in latest collection must be the same as the one as the
    // original document
    while (termQueryResults.hasNext()) {
      record = termQueryResults.next();

      String uri = record.getUri();
      System.out.println("URI = " + uri);

      if (!uri.equals(docId)) {
        fail("URIs are not what is expected");
      }
    }

    // Make sure there are 4 documents in jsonDocId collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(docId);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in temporal collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(temporalLsqtCollectionName);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in total. Use string search for this
    queryMgr = readerClient.newQueryManager();
    StringQueryDefinition stringQD = queryMgr.newStringDefinition();
    stringQD.setCriteria("Skyway Park");

    start = 1;
    docMgr.setMetadataCategories(Metadata.ALL);
    termQueryResults = docMgr.search(stringQD, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    while (termQueryResults.hasNext()) {
      record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);

      if (record.getFormat() != Format.JSON) {
        fail("Format is not JSON: " + Format.JSON);
      } else {
        // Make sure that system and valid times are what is expected
        recordHandle = new JacksonDatabindHandle<>(ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        Map<String, Object> docObject = mapper.readValue(
            recordHandle.toString(), typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> systemNode = (HashMap<String, Object>) (docObject
            .get(systemNodeName));

        String systemStartDate = (String) systemNode.get(systemStartERIName);
        String systemEndDate = (String) systemNode.get(systemEndERIName);
        System.out.println("systemStartDate = " + systemStartDate);
        System.out.println("systemEndDate = " + systemEndDate);

        @SuppressWarnings("unchecked")
        Map<String, Object> validNode = (HashMap<String, Object>) (docObject
            .get(validNodeName));

        String validStartDate = (String) validNode.get(validStartERIName);
        String validEndDate = (String) validNode.get(validEndERIName);
        System.out.println("validStartDate = " + validStartDate);
        System.out.println("validEndDate = " + validEndDate);

        // Permissions
        DocumentPermissions permissions = metadataHandle.getPermissions();
        System.out.println("Permissions: " + permissions);

        String actualPermissions = getDocumentPermissionsString(permissions);
        System.out.println("actualPermissions: " + actualPermissions);

        int quality = metadataHandle.getQuality();
        System.out.println("Quality: " + quality);

        if (validStartDate.contains("2003-01-01T00:00:00")
            && validEndDate.contains("2008-12-31T23:59:59")) {
          assertTrue(
              (systemStartDate.contains("2011-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("9999-12-31T11:59:59")));

          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(updateCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }
          assertTrue(metadataHandle
              .getProperties().isEmpty());

          assertTrue(
              actualPermissions.contains("size:7"));

          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));
          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[UPDATE, READ] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertFalse(
              actualPermissions.split("app-user:\\[")[1].split("\\]")[0].contains("EXECUTE"));

          assertEquals(quality, 99);
        }

        if (validStartDate.contains("2001-01-01T00:00:00")
            && validEndDate.contains("2003-01-01T00:00:00")) {
          assertTrue(
              (systemStartDate.contains("2011-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("9999-12-31T11:59:59")));

          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(insertCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(metadataHandle
              .getProperties().isEmpty());

          assertTrue(
              actualPermissions.contains("size:7"));

          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));
          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE, EXECUTE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
          assertTrue(
              appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertEquals(quality, 11);
        }

        if (validStartDate.contains("2008-12-31T23:59:59")
            && validEndDate.contains("2011-12-31T23:59:59")) {
          // This is the latest document
          assertTrue(
              (systemStartDate.contains("2011-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("9999-12-31T11:59:59")));
          assertTrue(record.getUri()
              .equals(docId));

          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(insertCollectionName)
                && !collection.equals(temporalLsqtCollectionName)
                && !collection.equals(latestCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(
              actualPermissions.contains("size:7"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));

          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));
          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE, EXECUTE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
          assertTrue(
              appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertEquals(quality, 11);

          validateMetadata(metadataHandle);
        }

        if (validStartDate.contains("2001-01-01T00:00:00")
            && validEndDate.contains("2011-12-31T23:59:59")) {
          assertTrue(
              (systemStartDate.contains("2010-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("2011-01-01T00:00:01")));

          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(insertCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(metadataHandle
              .getProperties().isEmpty());

          assertTrue(
              actualPermissions.contains("size:7"));
          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));
          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE, EXECUTE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
          assertTrue(
              appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertEquals(quality, 11);
        }
      }
    }

    // =============================================================================
    // Check delete works
    // =============================================================================
    // Delete one of the document
    Calendar deleteTime = DatatypeConverter
        .parseDateTime("2012-01-01T00:00:01");
    deleteJSONSingleDocument(temporalLsqtCollectionName, docId, null,
        deleteTime);

    // Make sure there are still 4 documents in docId collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(docId);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    // Make sure there is one document with docId uri
    docMgr = readerClient.newJSONDocumentManager();
    readResults = docMgr.read(docId);

    System.out.println("Number of results = " + readResults.size());
    assertEquals(1, readResults.size());

    // Make sure there are no documents in latest collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(latestCollectionName);

    start = 1;
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(0, termQueryResults.getTotalSize());

    // Make sure there are 4 documents in temporal collection
    queryMgr = readerClient.newQueryManager();
    sqb = queryMgr.newStructuredQueryBuilder();
    termQuery = sqb.collection(temporalLsqtCollectionName);

    start = 1;
    docMgr.setMetadataCategories(Metadata.ALL);
    termQueryResults = docMgr.search(termQuery, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());

    while (termQueryResults.hasNext()) {
      record = termQueryResults.next();
      System.out.println("URI = " + record.getUri());

      metadataHandle = new DocumentMetadataHandle();
      record.getMetadata(metadataHandle);

      if (record.getFormat() != Format.JSON) {
        fail("Format is not JSON: " + Format.JSON);
      } else {
        // Make sure that system and valid times are what is expected
        recordHandle = new JacksonDatabindHandle<>(ObjectNode.class);
        record.getContent(recordHandle);
        System.out.println("Content = " + recordHandle.toString());

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
        };

        Map<String, Object> docObject = mapper.readValue(
            recordHandle.toString(), typeRef);

        @SuppressWarnings("unchecked")
        Map<String, Object> systemNode = (HashMap<String, Object>) (docObject
            .get(systemNodeName));

        String systemStartDate = (String) systemNode.get(systemStartERIName);
        String systemEndDate = (String) systemNode.get(systemEndERIName);
        System.out.println("systemStartDate = " + systemStartDate);
        System.out.println("systemEndDate = " + systemEndDate);

        @SuppressWarnings("unchecked")
        Map<String, Object> validNode = (HashMap<String, Object>) (docObject
            .get(validNodeName));

        String validStartDate = (String) validNode.get(validStartERIName);
        String validEndDate = (String) validNode.get(validEndERIName);
        System.out.println("validStartDate = " + validStartDate);
        System.out.println("validEndDate = " + validEndDate);

        // Permissions
        DocumentPermissions permissions = metadataHandle.getPermissions();
        System.out.println("Permissions: " + permissions);

        String actualPermissions = getDocumentPermissionsString(permissions);
        System.out.println("actualPermissions: " + actualPermissions);

        int quality = metadataHandle.getQuality();
        System.out.println("Quality: " + quality);

        if (validStartDate.contains("2003-01-01T00:00:00")
            && validEndDate.contains("2008-12-31T23:59:59")) {
          assertTrue(
              (systemStartDate.contains("2011-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("2012-01-01T00:00:01")));

          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(updateCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(metadataHandle
              .getProperties().isEmpty());

          assertTrue(
              actualPermissions.contains("size:7"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));

          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));

          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));
          assertEquals(quality, 99);
        }

        if (validStartDate.contains("2001-01-01T00:00:00")
            && validEndDate.contains("2003-01-01T00:00:00")) {
          assertTrue(
              (systemStartDate.contains("2011-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("2012-01-01T00:00:01")));

          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(insertCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(metadataHandle
              .getProperties().isEmpty());

          assertTrue(
              actualPermissions.contains("size:7"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));

          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));

          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE, EXECUTE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
          assertTrue(
              appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertEquals(quality, 11);
        }

        if (validStartDate.contains("2008-12-31T23:59:59")
            && validEndDate.contains("2011-12-31T23:59:59")) {
          assertTrue(
              (systemStartDate.contains("2011-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("2012-01-01T00:00:01")));

          assertTrue(record.getUri()
              .equals(docId));

          // Document should not be in latest collection
          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(insertCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(
              actualPermissions.contains("size:7"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));

          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));

          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE, EXECUTE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
          assertTrue(
              appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertEquals(quality, 11);

          validateMetadata(metadataHandle);
        }

        if (validStartDate.contains("2001-01-01T00:00:00")
            && validEndDate.contains("2011-12-31T23:59:59")) {
          assertTrue(
              (systemStartDate.contains("2010-01-01T00:00:01")));
          assertTrue(
              (systemEndDate.contains("2011-01-01T00:00:01")));
          Iterator<String> resCollections = metadataHandle.getCollections()
              .iterator();
          while (resCollections.hasNext()) {
            String collection = resCollections.next();
            System.out.println("Collection = " + collection);

            if (!collection.equals(docId)
                && !collection.equals(insertCollectionName)
                && !collection.equals(temporalLsqtCollectionName)) {
              fail("Collection not what is expected: " + collection);
            }
          }

          assertTrue(metadataHandle
              .getProperties().isEmpty());

          assertTrue(
              actualPermissions.contains("size:7"));
          assertTrue(actualPermissions.contains("harmonized-updater:[UPDATE]"));
          assertTrue(actualPermissions.contains("harmonized-reader:[READ]"));

          assertTrue(
              actualPermissions.contains("rest-reader:[READ]"));

          // Split up rest-writer:[READ, EXECUTE, UPDATE] string
          String[] writerPerms = actualPermissions.split("rest-writer:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              writerPerms[0].contains("UPDATE") || writerPerms[1].contains("UPDATE") || writerPerms[2].contains("UPDATE"));
          assertTrue(
              writerPerms[0].contains("EXECUTE") || writerPerms[1].contains("EXECUTE") || writerPerms[2].contains("EXECUTE"));
          assertTrue(
              writerPerms[0].contains("READ") || writerPerms[1].contains("READ") || writerPerms[2].contains("READ"));
          // Split up app-user app-user:[READ, UPDATE, EXECUTE] string
          String[] appUserPerms = actualPermissions.split("app-user:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              appUserPerms[0].contains("UPDATE") || appUserPerms[1].contains("UPDATE") || appUserPerms[2].contains("UPDATE"));
          assertTrue(
              appUserPerms[0].contains("READ") || appUserPerms[1].contains("READ") || appUserPerms[2].contains("READ"));
          assertTrue(
              appUserPerms[0].contains("EXECUTE") || appUserPerms[1].contains("EXECUTE") || appUserPerms[2].contains("EXECUTE"));
          // Split up temporal-admin=[READ, UPDATE] string
          String[] temporalAdminPerms = actualPermissions.split("temporal-admin:\\[")[1].split("\\]")[0].split(",");

          assertTrue(
              temporalAdminPerms[0].contains("UPDATE") || temporalAdminPerms[1].contains("UPDATE"));
          assertTrue(
              temporalAdminPerms[0].contains("READ") || temporalAdminPerms[1].contains("READ"));

          assertEquals(quality, 11);
        }
      }
    }

    // Make sure there are 4 documents in total. Use string search for this
    queryMgr = readerClient.newQueryManager();

    start = 1;
    termQueryResults = docMgr.search(stringQD, start);
    System.out
        .println("Number of results = " + termQueryResults.getTotalSize());
    assertEquals(4, termQueryResults.getTotalSize());
  }

  /*
   * Insert multiple temporal documents to test bulk write of temporal
   * documents. Similar to testBulkWritReadWithTransaction, but uses CtsQueryBuilder.
   */
  @Test
  public void testBulkWRTransactionCtsQueryBldr() throws Exception {

    boolean tstatus = false;
    DocumentPage termQueryResults = null;

    Transaction tx = writerClient.openTransaction();
    try {
      XMLDocumentManager docMgr = writerClient.newXMLDocumentManager();

      DocumentWriteSet writeset = docMgr.newWriteSet();
      String[] docId = new String[4];
      docId[0] = "1.xml";
      docId[1] = "2.xml";
      docId[2] = "3.xml";
      docId[3] = "4.xml";

      DOMHandle handle1 = getXMLDocumentHandle("2001-01-01T00:00:00",
              "2011-12-31T23:59:56", "999 Skyway Park - XML", docId[0]);
      DOMHandle handle2 = getXMLDocumentHandle("2001-01-02T00:00:00",
              "2011-12-31T23:59:57", "999 Skyway Park - XML", docId[1]);
      DOMHandle handle3 = getXMLDocumentHandle("2001-01-03T00:00:00",
              "2011-12-31T23:59:58", "999 Skyway Park - XML", docId[2]);
      DOMHandle handle4 = getXMLDocumentHandle("2001-01-04T00:00:00",
              "2011-12-31T23:59:59", "999 Skyway Park - XML", docId[3]);
      DocumentMetadataHandle mh = setMetadata(false);

      writeset.add(docId[0], mh, handle1);
      writeset.add(docId[1], mh, handle2);
      writeset.add(docId[2], mh, handle3);
      writeset.add(docId[3], mh, handle4);
      Map<String, DOMHandle> map = new TreeMap<String, DOMHandle>();
      map.put(docId[0], handle1);
      map.put(docId[1], handle2);
      map.put(docId[2], handle3);
      map.put(docId[3], handle4);

      docMgr.write(writeset, null, null, bulktemporalCollectionName);

      QueryManager queryMgr = readerClient.newQueryManager();
      StructuredQueryBuilder sqb = queryMgr.newStructuredQueryBuilder();

      CtsQueryBuilder ctsQueryBuilder = queryMgr.newCtsSearchBuilder();

      XsStringSeqVal collectionSeq = ctsQueryBuilder.xs.stringSeq(latestCollectionName, bulktemporalCollectionName, "insertCollection" );
      CtsQueryExpr collExpr = ctsQueryBuilder.cts.collectionQuery(collectionSeq);
      CtsQueryDefinition termQuery = ctsQueryBuilder.newCtsQueryDefinition(collExpr);

      long start = 1;
      docMgr = readerClient.newXMLDocumentManager();
      docMgr.setMetadataCategories(Metadata.ALL); // Get all metadata
      termQueryResults = docMgr.search(termQuery, start);
      assertEquals(4, termQueryResults.size());
      // Verify the Document Record content with map contents for each record.
      while (termQueryResults.hasNext()) {

        DocumentRecord record = termQueryResults.next();

        DOMHandle recordHandle = new DOMHandle();
        record.getContent(recordHandle);

        String recordContent = recordHandle.toString();

        System.out.println("Record URI = " + record.getUri());
        System.out.println("Record content is = " + recordContent);

        DOMHandle readDOMHandle = map.get(record.getUri());
        String mapContent = readDOMHandle.evaluateXPath("/root/Address/text()", String.class);
        System.out.println("Map Content is " + mapContent);
        assertTrue(recordContent.contains(mapContent));

        readDOMHandle = null;
        mapContent = null;
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
      tstatus = true;
      throw e;
    } finally {
      if (tstatus) {
        if (termQueryResults != null)
          termQueryResults.close();
      }
      tx.rollback();
      tx = null;
    }
  }

  /* Test LSQT advance manually. Should have automation set to false on the temporal collection used.
  Similar to testAdvancingLSQT - using CtsQueryBuilder.
   */
  @Test
  public void testAdvancingLSQTWithCtsQueryBuilder() throws Exception {
      try {
        System.out.println("Inside testAdvancingLSQTWithCtsQueryBuilder");
        ConnectedRESTQA.disableAutomationOnTemporalCollection(dbName, temporalLsqtCollectionName, true);

        String docId = "javaSingleJSONDoc.json";
        String afterLSQTAdvance = null;

        Calendar firstInsertTime = DatatypeConverter.parseDateTime("2010-01-01T00:00:01");
        JSONDocumentManager docMgr = writerClient.newJSONDocumentManager();

        JacksonDatabindHandle<ObjectNode> handle = getJSONDocumentHandle(
                "2001-01-01T00:00:00", "2011-12-31T23:59:59", "999 Skyway Park - JSON",
                docId);
        TemporalDescriptor desc = docMgr.write(docId, null, handle, null, null, temporalLsqtCollectionName, firstInsertTime);
        // Verify permissions for LSQT advance
        String permnExceptMsg = "User is not allowed to advanceLsqt resource at temporal/collections/" + temporalLsqtCollectionName;
        String extMsg = null;
        try {
          JSONDocumentManager docMgr1 = readerClient.newJSONDocumentManager();
          docMgr1.advanceLsqt(temporalLsqtCollectionName);
        } catch (ForbiddenUserException ex) {
          extMsg = ex.getMessage();
          System.out.println("Permissions exception message for LSQT advance is " + extMsg);
        }
        assertTrue(extMsg.contains(permnExceptMsg));

        QueryManager queryMgrLSQT = adminClient.newQueryManager();

        CtsQueryBuilder sqbLSQT = queryMgrLSQT.newCtsSearchBuilder();

        XsStringVal collName = sqbLSQT.xs.string(temporalLsqtCollectionName);
        XsStringSeqVal options = sqbLSQT.xs.stringSeq("", "");
        Calendar calTimeLSQT = DatatypeConverter.parseDateTime("2007-01-01T00:00:01");

        XsDateTimeVal queryTimeLSQT = sqbLSQT.xs.dateTime(calTimeLSQT);
        XsDoubleVal weight = sqbLSQT.xs.doubleVal(0.0);

        CtsQueryExpr ctsQueryExpr = sqbLSQT.cts.lsqtQuery(collName, queryTimeLSQT);
        CtsQueryDefinition periodQueryLSQT = sqbLSQT.newCtsQueryDefinition(ctsQueryExpr);

        long startLSQT = 1;
        JSONDocumentManager docMgrQy = adminClient.newJSONDocumentManager();
        String WithoutAdvaceSetExceptMsg = "Timestamp 2007-01-01T00:00:01-08:00 provided is greater than LSQT 1601-01-01T00:00:00Z";
        String actualNoAdvanceMsg = null;
        DocumentPage termQueryResultsLSQT = null;
        try {
          termQueryResultsLSQT = docMgrQy.search(periodQueryLSQT, startLSQT);
        } catch (Exception ex) {
          actualNoAdvanceMsg = ex.getMessage();
          System.out.println("Exception message for LSQT without advance set is " + actualNoAdvanceMsg);
        }
        assertTrue(actualNoAdvanceMsg.contains(WithoutAdvaceSetExceptMsg));

        // Set the Advance manually.
        docMgr.advanceLsqt(temporalLsqtCollectionName);
        termQueryResultsLSQT = docMgrQy.search(periodQueryLSQT, startLSQT);

        assertTrue(termQueryResultsLSQT.getTotalPages() == 0);
        assertTrue(termQueryResultsLSQT.size() == 0);

        // After Advance of the LSQT, query again with new query time greater than LSQT

        afterLSQTAdvance = desc.getTemporalSystemTime();
        Calendar calTimeLSQT2 = DatatypeConverter.parseDateTime(afterLSQTAdvance);
        calTimeLSQT2.add(Calendar.YEAR, 10);
        XsDateTimeVal queryTimeLSQT2 = sqbLSQT.xs.dateTime(calTimeLSQT2);
        docMgrQy = adminClient.newJSONDocumentManager();
        docMgrQy.setMetadataCategories(Metadata.ALL); // Get all meta-data

        CtsQueryExpr ctsQueryExpr2 = sqbLSQT.cts.lsqtQuery(collName, queryTimeLSQT2);
        CtsQueryDefinition periodQueryLSQT2 = sqbLSQT.newCtsQueryDefinition(ctsQueryExpr2);

        String excepMsgGrtr = "Timestamp 2020-01-01T00:00:01-08:00 provided is greater than LSQT 2010-01-01T08:00:01Z";
        String actGrMsg = null;
        DocumentPage termQueryResultsLSQT2 = null;
        try {
          termQueryResultsLSQT2 = docMgrQy.search(periodQueryLSQT2, startLSQT);
        } catch (Exception ex) {
          actGrMsg = ex.getMessage();
        }
        assertTrue(actGrMsg.contains(excepMsgGrtr));

        // Query again with query time less than LSQT. 10 minutes less than the LSQT
        Calendar callessTime = DatatypeConverter.parseDateTime("2009-01-01T00:00:01");
        XsDateTimeVal lessTime = sqbLSQT.xs.dateTime(callessTime);

        CtsQueryExpr ctsQueryExprLess = sqbLSQT.cts.lsqtQuery(collName, lessTime/*, options, weight*/);
        CtsQueryDefinition lessDef = sqbLSQT.newCtsQueryDefinition(ctsQueryExprLess);
        termQueryResultsLSQT2 = docMgrQy.search(lessDef, startLSQT);

        System.out.println("LSQT Query results (Total Pages) after advance " + termQueryResultsLSQT2.getTotalPages());
        System.out.println("LSQT Query results (Size) after advance " + termQueryResultsLSQT2.size());
        assertTrue(termQueryResultsLSQT2.getTotalPages() == 0);
        assertTrue(termQueryResultsLSQT2.size() == 0);
      } catch (Exception ex) {
        System.out.println("Exception thrown from testAdvacingLSQT method " + ex.getMessage());
      } finally {
        ConnectedRESTQA.updateTemporalCollectionForLSQT(dbName, temporalLsqtCollectionName, true);
      }
    }
  }
