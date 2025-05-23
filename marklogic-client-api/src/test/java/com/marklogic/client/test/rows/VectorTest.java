package com.marklogic.client.test.rows;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.expression.PlanBuilder;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.row.RawQueryDSLPlan;
import com.marklogic.client.row.RowRecord;
import com.marklogic.client.test.junit5.RequiresML12;
import com.marklogic.client.type.ServerExpression;
import com.marklogic.client.type.XsDoubleSeqVal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(RequiresML12.class)
class VectorTest extends AbstractOpticUpdateTest {

	XsDoubleSeqVal sampleVector;
	XsDoubleSeqVal twoDimensionalVector;
	ServerExpression invalidVector;

	@BeforeEach
	void beforeEach() {
		sampleVector = op.xs.doubleSeq(1.2, 3.4, 5.6);
		twoDimensionalVector = op.xs.doubleSeq(1.2, 3.4);
		invalidVector = op.xs.string("InvalidVector");
		rowManager.withUpdate(false);
	}

	@Test
	void vectorFunctionsHappyPath() {
		PlanBuilder.ModifyPlan plan =
			op.fromView("vectors", "persons")
				.bind(op.as("sampleVector", op.vec.vector(sampleVector)))
				.bind(op.as("cosineSimilarity", op.vec.cosineSimilarity(op.col("embedding"), op.col("sampleVector"))))
				.bind(op.as("dotProduct", op.vec.dotProduct(op.col("embedding"), op.col("sampleVector"))))
				.bind(op.as("euclideanDistance", op.vec.euclideanDistance(op.col("embedding"), op.col("sampleVector"))))
				.bind(op.as("dimension", op.vec.dimension(op.col("sampleVector"))))
				.bind(op.as("normalize", op.vec.normalize(op.col("sampleVector"))))
				.bind(op.as("magnitude", op.vec.magnitude(op.col("sampleVector"))))
				.bind(op.as("get", op.vec.get(op.col("sampleVector"), op.xs.integer(2))))
				.bind(op.as("add", op.vec.add(op.col("embedding"), op.col("sampleVector"))))
				.bind(op.as("subtract", op.vec.subtract(op.col("embedding"), op.col("sampleVector"))))
				.bind(op.as("base64Encode", op.vec.base64Encode(op.col("sampleVector"))))
				.bind(op.as("base64Decode", op.vec.base64Decode(op.col("base64Encode"))))
				.bind(op.as("subVector", op.vec.subvector(op.col("sampleVector"), op.xs.integer(1), op.xs.integer(1))))
				.bind(op.as("vectorScore", op.vec.vectorScore(op.xs.unsignedInt(1), op.xs.doubleVal(0.5))))
				.select(
					op.col("cosineSimilarity"), op.col("dotProduct"), op.col("euclideanDistance"),
					op.col("name"), op.col("dimension"), op.col("normalize"),
					op.col("magnitude"), op.col("get"), op.col("add"), op.col("subtract"),
					op.col("base64Encode"), op.col("base64Decode"), op.col("subVector"), op.col("vectorScore")
				)
				.limit(5);
		List<RowRecord> rows = resultRows(plan);
		assertEquals(2, rows.size());

		rows.forEach(row -> {
//			 Simple a sanity checks to verify that the functions ran. Very little concern about the actual return values.
			double cosineSimilarity = row.getDouble("cosineSimilarity");
			assertTrue((cosineSimilarity > 0) && (cosineSimilarity < 1), "Unexpected value: " + cosineSimilarity);
			double dotProduct = row.getDouble("dotProduct");
			Assertions.assertTrue(dotProduct > 0, "Unexpected value: " + dotProduct);
			double euclideanDistance = row.getDouble("euclideanDistance");
			Assertions.assertTrue(euclideanDistance > 0, "Unexpected value: " + euclideanDistance);
			assertEquals(3, row.getInt("dimension"));
			assertEquals(3, ((ArrayNode) row.get("normalize")).size());
			double magnitude = row.getDouble("magnitude");
			assertTrue(magnitude > 0, "Unexpected value: " + magnitude);
			assertEquals(3, ((ArrayNode) row.get("add")).size());
			assertEquals(3, ((ArrayNode) row.get("subtract")).size());
			assertFalse(row.getString("base64Encode").isEmpty());
			assertEquals(3, ((ArrayNode) row.get("base64Decode")).size());
			assertEquals(5.6, row.getDouble("get"));
			assertEquals(1, ((ArrayNode) row.get("subVector")).size());
			double vectorScore = row.getDouble("vectorScore");
			assertTrue(vectorScore > 0, "Unexpected value: " + vectorScore);
		});
	}

	@Test
	void cosineSimilarity_DimensionMismatch() {
		PlanBuilder.ModifyPlan plan =
			op.fromView("vectors", "persons")
				.bind(op.as("sampleVector", op.vec.vector(twoDimensionalVector)))
				.bind(op.as("cosineSimilarity", op.vec.cosineSimilarity(op.col("embedding"), op.col("sampleVector"))))
				.select(op.col("name"), op.col("summary"), op.col("cosineSimilarity"));
		Exception exception = assertThrows(FailedRequestException.class, () -> resultRows(plan));
		String actualMessage = exception.getMessage();
		assertTrue(actualMessage.contains("Server Message: VEC-DIMMISMATCH"), "Unexpected message: " + actualMessage);
		assertTrue(actualMessage.contains("Mismatched dimension"), "Unexpected message: " + actualMessage);
	}

	@Test
	void cosineSimilarity_InvalidVector() {
		PlanBuilder.ModifyPlan plan =
			op.fromView("vectors", "persons")
				.bind(op.as("sampleVector", invalidVector))
				.bind(op.as("cosineSimilarity", op.vec.cosineSimilarity(op.col("embedding"), op.col("sampleVector"))))
				.select(op.col("name"), op.col("summary"), op.col("cosineSimilarity"));
		Exception exception = assertThrows(FailedRequestException.class, () -> resultRows(plan));
		String actualMessage = exception.getMessage();
		assertTrue(actualMessage.contains("Server Message: XDMP-ARGTYPE"), "Unexpected message: " + actualMessage);
		assertTrue(actualMessage.contains("arg2 is not of type vec:vector"), "Unexpected message: " + actualMessage);
	}

	@Test
		// As of 07/26/24, this test will fail with the ML12 develop branch.
		// However, it will succeed with the 12ea1 build.
		// See https://progresssoftware.atlassian.net/browse/MLE-15707
	void bindVectorFromDocs() {
		PlanBuilder.ModifyPlan plan =
			op.fromSearchDocs(
					op.cts.andQuery(
						op.cts.documentQuery("/optic/vectors/alice.json"),
						op.cts.elementQuery(
							"person",
							op.cts.trueQuery()
						)
					))
				.bind(op.as("embedding", op.vec.vector(op.xpath("doc", "/person/embedding"))));
		List<RowRecord> rows = resultRows(plan);
		assertEquals(1, rows.size());
	}

	// This is passing locally when running 12-nightly on Docker, but has been failing on Jenkins since it was
	// introduced on Nov 5th. Created https://progresssoftware.atlassian.net/browse/MLE-17964 to track it.
	@Test
	void vecVectorWithCol() {
		String query = "op.fromView('vectors', 'persons').limit(2).bind(op.as('summaryCosineSim', op.vec.vector(op.col('embedding'))))";
		RawQueryDSLPlan plan = rowManager.newRawQueryDSLPlan(new StringHandle(query));
		List<RowRecord> rows = resultRows(plan);
		assertEquals(2, rows.size());
	}

	@Test
	void annTopK() {
		PlanBuilder.ModifyPlan plan = op.fromView("vectors", "persons")
			.annTopK(10, op.col("embedding"), op.vec.vector(sampleVector), op.col("distance"), 0.5f);

		List<RowRecord> rows = resultRows(plan);
		assertEquals(2, rows.size(), "Verifying that annTopK worked and returned both rows from the view.");

		rows.forEach(row -> {
			float distance = row.getFloat("distance");
			assertTrue(distance > 0, "Just verifying that annTopK both worked and put a valid value into the 'distance' column.");
		});
	}

	@Test
	void dslAnnTopK() {
		String query = "const qualityVector = vec.vector([ 1.1, 2.2, 3.3 ]);\n" +
			"op.fromView('vectors', 'persons')\n" +
			"  .bind(op.as('myVector', op.vec.vector(op.col('embedding'))))\n" +
			"  .annTopK(2, op.col('myVector'), qualityVector, op.col('distance'), 0.5)";

		RawQueryDSLPlan plan = rowManager.newRawQueryDSLPlan(new StringHandle(query));
		List<RowRecord> rows = resultRows(plan);
		assertEquals(2, rows.size(), "Just verifying that 'annTopK' works via the DSL and v1/rows.");
	}
}
