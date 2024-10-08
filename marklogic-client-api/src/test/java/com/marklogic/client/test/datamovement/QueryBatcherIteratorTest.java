/*
 * Copyright © 2024 MarkLogic Corporation. All Rights Reserved.
 */
package com.marklogic.client.test.datamovement;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.datamovement.*;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.SearchHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.query.DeleteQueryDefinition;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StructuredQueryBuilder;
import com.marklogic.client.query.StructuredQueryDefinition;
import com.marklogic.client.test.Common;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.marklogic.client.io.Format.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class QueryBatcherIteratorTest {
  Logger logger = LoggerFactory.getLogger(QueryBatcherIteratorTest.class);
  private static int numDocs = 500;
  private static DatabaseClient client = Common.connect();
  private static DataMovementManager moveMgr = client.newDataMovementManager();
  private static String collection = "QueryBatcherIteratorTest";
  private static String qhbTestCollection = "QueryBatcherIteratorTest_" +
    new Random().nextInt(10000);

  @BeforeAll
  public static void beforeClass() throws Exception {
    setup();
  }

  @AfterAll
  public static void afterClass() {
    QueryManager queryMgr = client.newQueryManager();
    DeleteQueryDefinition deleteQuery = queryMgr.newDeleteDefinition();
    deleteQuery.setCollections(collection);
    queryMgr.delete(deleteQuery);
  }

  public static void setup() throws Exception {

    assertEquals(
      null, client.newDocumentManager().exists(collection + "/doc_1.json") );

    WriteBatcher writeBatcher = moveMgr.newWriteBatcher()
      .withBatchSize(100);
    moveMgr.startJob(writeBatcher);
    // a collection so we're only looking at docs related to this test
    DocumentMetadataHandle meta = new DocumentMetadataHandle()
      .withCollections(collection, qhbTestCollection);
    for ( int i=1; i <= numDocs; i++ ) {
      writeBatcher.addAs(collection + "/doc_" + i + ".json", meta,
        new StringHandle("{name:\"John Doe\",dept:\"HR\"}").withFormat(JSON));
    }
    writeBatcher.flushAsync();
    writeBatcher.awaitCompletion();
  }

  @Test
  public void test_A_OnDiskIterator() throws Exception {
    File tempFile = File.createTempFile(qhbTestCollection, ".txt");
    logger.info("tempFile =[" + tempFile  + "]");
    AtomicInteger successDocs1;
    StringBuilder failures;
    try (FileWriter writer = new FileWriter(tempFile)) {
      StructuredQueryBuilder sqb = new StructuredQueryBuilder();
      StructuredQueryDefinition query = sqb.value(sqb.jsonProperty("dept"), "HR");
      query.setCollections(qhbTestCollection);
      successDocs1 = new AtomicInteger(0);
      failures = new StringBuilder();
      QueryBatcher getUris = moveMgr.newQueryBatcher(query)
        .withThreadCount(5)
        .withBatchSize(100)
        .onUrisReady( new UrisToWriterListener(writer) )
        .onUrisReady(batch -> successDocs1.addAndGet(batch.getItems().length))
        .onQueryFailure( throwable -> {
          throwable.printStackTrace();
          failures.append("ERROR:[" + throwable + "]\n");
        });
      moveMgr.startJob(getUris);
      getUris.awaitCompletion();
      writer.flush();
    }
    if ( failures.length() > 0 ) fail(failures.toString());

    assertEquals(numDocs, successDocs1.get());

    // now we have the uris, let's step through them and do nothing with them
    AtomicInteger successDocs2 = new AtomicInteger(0);
    StringBuffer failures2 = new StringBuffer();
    try ( FileReader fileReader = new FileReader(tempFile);
          BufferedReader reader = new BufferedReader(fileReader); )
    {

      QueryBatcher doNothing = moveMgr.newQueryBatcher(reader.lines().iterator())
        .withThreadCount(6)
        .withBatchSize(19)
        .onUrisReady(batch -> successDocs2.addAndGet(batch.getItems().length))
        .onQueryFailure( throwable -> {
          throwable.printStackTrace();
          failures2.append("ERROR:[" + throwable + "]\n");
        });
      moveMgr.startJob(doNothing);
      doNothing.awaitCompletion();
      moveMgr.stopJob(doNothing);
    }

    if ( failures2.length() > 0 ) fail(failures2.toString());
    assertEquals(numDocs, successDocs2.get());
    if(tempFile.exists()) tempFile.delete();
  }

  @Test
  public void test_B_InMemoryIterator() throws Exception {
    StructuredQueryBuilder sqb = new StructuredQueryBuilder();
    StructuredQueryDefinition query = sqb.value(sqb.jsonProperty("dept"), "HR");
    query.setCollections(qhbTestCollection);
    Set<String> uris = Collections.synchronizedSet(new HashSet<>());
    StringBuilder failures = new StringBuilder();
    QueryBatcher getUris = moveMgr.newQueryBatcher(query)
      .withThreadCount(6)
      .withBatchSize(5000)
      .onUrisReady( batch -> uris.addAll(Arrays.asList(batch.getItems())) )
      .onQueryFailure( throwable -> {
        throwable.printStackTrace();
        failures.append("ERROR:[" + throwable + "]\n");
      });
    moveMgr.startJob(getUris);
    getUris.awaitCompletion();
    if ( failures.length() > 0 ) fail(failures.toString());

    assertEquals(numDocs, uris.size());

    // now we have the uris, let's step through them
    AtomicInteger successDocs = new AtomicInteger();
    Set<String> uris2 = Collections.synchronizedSet(new HashSet<>());
    StringBuilder failures2 = new StringBuilder();
    QueryBatcher performDelete = moveMgr.newQueryBatcher(uris.iterator())
      .withThreadCount(2)
      .withBatchSize(99)
      .onUrisReady(new DeleteListener())
      .onUrisReady(batch -> successDocs.addAndGet(batch.getItems().length))
      .onUrisReady(batch -> uris2.addAll(Arrays.asList(batch.getItems())))
      .onQueryFailure( throwable -> {
        throwable.printStackTrace();
        failures2.append("ERROR:[" + throwable + "]\n");
      });
    moveMgr.startJob(performDelete);
    performDelete.awaitCompletion();

    if ( failures2.length() > 0 ) fail(failures2.toString());

    assertEquals(numDocs, uris2.size());
    assertEquals(numDocs, successDocs.get());

    SearchHandle results = client.newQueryManager().search(query, new SearchHandle());
    assertEquals(0, results.getTotalResults());
  }
}
