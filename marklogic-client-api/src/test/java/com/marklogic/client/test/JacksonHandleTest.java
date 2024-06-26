/*
 * Copyright (c) 2023 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.query.QueryDefinition;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StructuredQueryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JacksonHandleTest {
  @BeforeAll
  public static void beforeClass() {
    Common.connect();
  }
  @AfterAll
  public static void afterClass() {
  }

  @Test
  public void testReadWrite() {
    // create an identifier for the database document
    String docId = "/example/jackson-test.json";

    // create a manager for JSON documents
    JSONDocumentManager docMgr = Common.client.newJSONDocumentManager();

    // construct a Jackson JSON structure
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode childObj = mapper.createObjectNode();
    childObj.put("key", "value");
    ArrayNode childArray = mapper.createArrayNode();
    childArray.add("item1");
    childArray.add("item2");
    ObjectNode writeRoot = mapper.createObjectNode();
    writeRoot.put("object", childObj);
    writeRoot.put("array",  childArray);

    // create a handle for the JSON structure
    JacksonHandle writeHandle = new JacksonHandle(writeRoot);

    // write the document to the database
    docMgr.write(docId, writeHandle);

    // create a handle to receive the database content as a Jackson structure
    JacksonHandle readHandle = new JacksonHandle();

    // read the document content from the database as a Jackson structure
    docMgr.read(docId, readHandle);

    // access the document content
    JsonNode readRoot = readHandle.get();
    assertNotNull(readRoot);
    assertTrue(readRoot.equals(writeRoot));

    // delete the document
    docMgr.delete(docId);
  }

  @Test
  public void test105Searches() throws IOException {
    QueryManager queryMgr = Common.client.newQueryManager();
    StructuredQueryBuilder qb = queryMgr.newStructuredQueryBuilder();

    for (int i=0;i<105;i++) {
      for (QueryDefinition t : new QueryDefinition[] { qb.term("leaf3"),
        qb.build(qb.value(qb.element("leaf"), "leaf3")) }) {
        JacksonHandle results = queryMgr.search(t, new JacksonHandle());
        assertNotNull(results);
        JsonNode jsonResults =results.get();
        @SuppressWarnings("unused")
        String resultString = results.getMapper().writeValueAsString(jsonResults);
        // ignore.
      }
    }
  }

}

