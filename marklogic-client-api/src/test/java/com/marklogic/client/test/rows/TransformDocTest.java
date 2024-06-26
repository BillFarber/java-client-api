package com.marklogic.client.test.rows;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.expression.PlanBuilder.ModifyPlan;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.io.marker.AbstractWriteHandle;
import com.marklogic.client.row.RowRecord;
import com.marklogic.client.test.Common;
import com.marklogic.client.test.junit5.RequiresML11;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(RequiresML11.class)
public class TransformDocTest extends AbstractOpticUpdateTest {

    @Test
    public void mjsTransformWithParam() {
        ArrayNode rows = mapper.createArrayNode();
        rows.addObject().putObject("doc").put("some", "content");

        ModifyPlan plan = op
            .fromParam("myDocs", "", op.colTypes(op.colType("doc", "none")))
            .transformDoc(op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.mjs")
                    .withParam("myParam", "my value"));

        List<RowRecord> results = resultRows(plan.bindParam("myDocs", new JacksonHandle(rows)));
        assertEquals(1, results.size());

        ObjectNode transformedDoc = results.get(0).getContentAs("doc", ObjectNode.class);
        assertEquals("world", transformedDoc.get("hello").asText());
        assertEquals("my value", transformedDoc.get("yourParam").asText());
        assertEquals("content", transformedDoc.get("theDoc").get("some").asText(),
			"The transform is expected to receive the incoming doc via the 'doc' param and then toss it into the " +
				"response under the key 'thedoc'");
    }

    @Test
    public void mjsTransformWithColParam() {
        ModifyPlan plan = op
            .fromDocUris(op.cts.documentQuery("/optic/test/musician1.json"))
            .joinDoc(op.col("doc"), op.col("uri"))
            .transformDoc(
                op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.mjs")
                    .withParam("myColumnParam", op.col("uri"))
                    .withParam("myParam", "test value")
            );

        List<RowRecord> rows = resultRows(plan);
        assertEquals(1, rows.size());

        ObjectNode doc = rows.get(0).getContentAs("doc", ObjectNode.class);
        assertEquals("/optic/test/musician1.json", doc.get("yourColumnValue").asText());
        assertEquals("test value", doc.get("yourParam").asText());
        assertEquals("world", doc.get("hello").asText());
        assertEquals("Armstrong", doc.get("theDoc").get("musician").get("lastName").asText());
    }

    @Test
    public void mjsTransformWithQualifiedColParam() {
        final String qualifier = "source";
        ModifyPlan plan = op
            .fromDocUris(op.cts.documentQuery("/optic/test/musician1.json"), qualifier)
            .joinDoc(op.col("doc"), op.viewCol(qualifier, "uri"))
            .transformDoc(
                op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.mjs").withParam("myParam", op.viewCol(qualifier, "uri"))
            );

        List<RowRecord> rows = resultRows(plan);
        assertEquals(1, rows.size());

        ObjectNode doc = rows.get(0).getContentAs("doc", ObjectNode.class);
        assertEquals("/optic/test/musician1.json", doc.get("yourParam").asText());
        assertEquals("world", doc.get("hello").asText());
        assertEquals("Armstrong", doc.get("theDoc").get("musician").get("lastName").asText());
    }

    @Test
    public void mjsTransformWithoutParam() {
        ArrayNode rows = mapper.createArrayNode();
        rows.addObject().putObject("doc").put("some", "content");

        ModifyPlan plan = op
            .fromParam("myDocs", "", op.colTypes(op.colType("doc", "none")))
            .transformDoc(op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.mjs").withKind("mjs"));

        List<RowRecord> results = resultRows(plan.bindParam("myDocs", new JacksonHandle(rows)));
        assertEquals(1, results.size());

        ObjectNode transformedDoc = results.get(0).getContentAs("doc", ObjectNode.class);
        assertEquals("world", transformedDoc.get("hello").asText());
        assertFalse(transformedDoc.has("yourParam"), "myParam was not specified, so yourParam should not exist");
        assertEquals("content", transformedDoc.get("theDoc").get("some").asText());
    }

    @Test
    public void mjsTransformReturnsMultipleRows() {
        ModifyPlan plan = op
            .fromDocDescriptors(
                op.docDescriptor(newWriteOp("will-be-replaced", mapper.createObjectNode().put("hello", "there")))
            )
            .transformDoc(op.col("doc"), op.transformDef("/etc/optic/test/transformDoc-multipleRows.mjs"))
            .bind(op.as("uri", op.fn.concat(
                op.xs.string("/acme/"),
                op.xdmp.random(),
                op.xs.string(".json"))
            ))
            .write();

        List<RowRecord> rows = resultRows(plan);
        assertEquals(2, rows.size(),
			"Two docs should have been written because the transform returns an array of 2 objects");
        rows.forEach(row -> {
            final String uri = row.getString("uri");
            final ObjectNode returnedDoc = row.getContentAs("doc", ObjectNode.class);
            verifyJsonDoc(uri, doc -> {
                assertEquals(returnedDoc.get("number").asInt(), doc.get("number").asInt());
                assertEquals("there", doc.get("theDoc").get("hello").asText());
            });
        });
    }

    @Test
    public void transformThrowsError() {
        ModifyPlan plan = op
            .fromDocUris("/optic/test/musician1.json")
            .joinDoc(op.col("doc"), op.col("uri"))
            .transformDoc(op.col("doc"), op.transformDef("/etc/optic/test/transformDoc-throwsError.mjs"));

        FailedRequestException ex = assertThrows(FailedRequestException.class, () -> rowManager.execute(plan));
        assertTrue(ex.getMessage().contains("throw Error(\"This is intentional\")"),
			"Unexpected message: " + ex.getMessage());
    }

    @Test
    public void multipleJsonDocs() {
        ModifyPlan plan = op
            .fromDocUris("/optic/test/musician1.json", "/optic/test/musician2.json")
            .joinDoc(op.col("doc"), op.col("uri"))
            .transformDoc(op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.mjs").withParam("myParam",
                    "my value"));

        List<RowRecord> results = resultRows(plan);
        assertEquals(2, results.size());

        ObjectNode transformedDoc = results.get(0).getContentAs("doc", ObjectNode.class);
        assertEquals("world", transformedDoc.get("hello").asText());
        assertEquals("my value", transformedDoc.get("yourParam").asText());
        assertEquals("Armstrong", transformedDoc.get("theDoc").get("musician").get("lastName").asText());

        transformedDoc = results.get(1).getContentAs("doc", ObjectNode.class);
        assertEquals("world", transformedDoc.get("hello").asText());
        assertEquals("my value", transformedDoc.get("yourParam").asText());
        assertEquals("Byron", transformedDoc.get("theDoc").get("musician").get("lastName").asText());
    }

    @Test
    public void xsltTransformWithParam() {
        ArrayNode rows = mapper.createArrayNode();
        rows.addObject().put("rowId", 1).put("doc", "doc1.xml");
        Map<String, AbstractWriteHandle> attachments = new HashMap<>();
        attachments.put("doc1.xml", new StringHandle("<doc>1</doc>").withFormat(Format.XML));

        ModifyPlan plan = op
            .fromParam("myDocs", "", op.colTypes(op.colType("rowId", "integer"), op.colType("doc", "none")))
            .transformDoc(op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.xslt")
                    .withKind("xslt")
                    .withParam("myParam", "my value"));

        List<RowRecord> results = resultRows(
            plan.bindParam("myDocs", new JacksonHandle(rows), Collections.singletonMap("doc", attachments)));
        assertEquals(1, results.size());

        String xml = getRowContentWithoutXmlDeclaration(results.get(0), "doc");
        String message = "Unexpected XML doc: " + xml;
        // TODO marklogic-junit would make this much easier/nicer once we change this project
        // to use JUnit 5
        assertTrue(xml.startsWith("<result>"), message);
        assertTrue(xml.contains("<doc>1</doc>"), message);
        assertTrue(xml.contains("<hello>world</hello>"), message);
        assertTrue(xml.contains("<yourParam>my value</yourParam"), message);
        assertTrue(xml.endsWith("</result>"), message);
    }

    @Test
    public void xsltTransformWithoutParam() {
        ArrayNode rows = mapper.createArrayNode();
        rows.addObject().put("doc", "doc1.xml");
        Map<String, AbstractWriteHandle> attachments = new HashMap<>();
        attachments.put("doc1.xml", new StringHandle("<doc>1</doc>").withFormat(Format.XML));

        ModifyPlan plan = op
            .fromParam("myDocs", "", op.colTypes(op.colType("doc", "none")))
            .transformDoc(op.col("doc"),
                op.transformDef("/etc/optic/test/transformDoc-test.xslt").withKind("xslt"));

        List<RowRecord> results = resultRows(
            plan.bindParam("myDocs", new JacksonHandle(rows), Collections.singletonMap("doc", attachments)));
        assertEquals(1, results.size());

        String xml = getRowContentWithoutXmlDeclaration(results.get(0), "doc");
        String message = "Unexpected XML doc: " + xml;
        assertTrue(xml.startsWith("<result>"), message);
        assertTrue(xml.contains("<hello>world</hello>"), message);
        assertTrue(xml.contains("<yourParam/>"), message);
        assertTrue(xml.endsWith("</result>"), message);
    }
}
