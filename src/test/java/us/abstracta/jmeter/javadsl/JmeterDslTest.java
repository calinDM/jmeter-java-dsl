package us.abstracta.jmeter.javadsl;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static us.abstracta.jmeter.javadsl.JmeterDsl.httpSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.testPlan;
import static us.abstracta.jmeter.javadsl.JmeterDsl.threadGroup;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;
import ru.lanwen.wiremock.ext.WiremockUriResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver.WiremockUri;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

@ExtendWith({
    WiremockResolver.class,
    WiremockUriResolver.class
})
public class JmeterDslTest {

  private static final int TEST_ITERATIONS = 3;
  private static final String HEADER_NAME_1 = "name1";
  private static final String HEADER_VALUE_1 = "value1";
  private static final String HEADER_NAME_2 = "name2";
  private static final String HEADER_VALUE_2 = "value2";
  private static final String SAMPLE_1_LABEL = "sample1";
  private static final String SAMPLE_2_LABEL = "sample2";
  private static final String OVERALL_STATS_LABEL = "overall";

  private String wiremockUri;
  private WireMockServer wiremockServer;

  @BeforeEach
  public void setup(@Wiremock WireMockServer server, @WiremockUri String uri) {
    server.stubFor(any(anyUrl()));
    wiremockServer = server;
    wiremockUri = uri;
  }

  @Test
  public void shouldSendRequestsToServerWhenSimpleHttpTestPlan() throws IOException {
    testPlan(
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri)
        )
    ).run();
    verifyRequestsSentToServer(TEST_ITERATIONS);
  }

  private void verifyRequestsSentToServer(int testIterations) {
    wiremockServer.verify(testIterations, getRequestedFor(anyUrl()));
  }

  @Test
  public void shouldSendDoubleRequestsToServerWhenTwoSamplersTestPlan() throws IOException {
    testPlan(
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri),
            JmeterDsl.httpSampler(wiremockUri)
        )
    ).run();
    verifyRequestsSentToServer(TEST_ITERATIONS * 2);
  }

  @Test
  public void shouldGetLabeledAndOverallRequestsCountWhenRunPlanWithMultipleSamplers()
      throws IOException {
    TestPlanStats stats = testPlan(
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(SAMPLE_1_LABEL, wiremockUri),
            JmeterDsl.httpSampler(SAMPLE_2_LABEL, wiremockUri)
        )
    ).run();
    assertEquals(buildExpectedTotalCounts(), extractTotalCounts(stats));
  }

  private Map<String, Long> extractTotalCounts(TestPlanStats stats) {
    Map<String, Long> actualStats = new HashMap<>();
    actualStats.put(OVERALL_STATS_LABEL, stats.overall().samplesCount());
    for (String label : stats.labels()) {
      actualStats.put(label, stats.byLabel(label).samplesCount());
    }
    return actualStats;
  }

  private Map<String, Long> buildExpectedTotalCounts() {
    Map<String, Long> expectedStats = new HashMap<>();
    expectedStats.put(OVERALL_STATS_LABEL, (long) 2 * TEST_ITERATIONS);
    expectedStats.put(SAMPLE_1_LABEL, (long) TEST_ITERATIONS);
    expectedStats.put(SAMPLE_2_LABEL, (long) TEST_ITERATIONS);
    return expectedStats;
  }

  @Test
  public void shouldWriteAllThreadGroupsResultsToFileWhenJtlWriterAtTestPlan(@TempDir Path tempDir)
      throws IOException {
    Path resultsFilePath = tempDir.resolve("results.jtl");
    JmeterDsl.testPlan(
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri),
            JmeterDsl.httpSampler(wiremockUri)
        ),
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri)
        ),
        JmeterDsl.jtlWriter(resultsFilePath.toString())
    ).run();
    assertResultsFileResultsCount(resultsFilePath, TEST_ITERATIONS * 3);
  }

  private void assertResultsFileResultsCount(Path resultsFilePath, int resultsCount)
      throws IOException {
    // we add one more expected line due to headers line
    assertEquals(resultsCount + 1, Files.readAllLines(resultsFilePath).size());
  }

  @Test
  public void shouldWriteContainingThreadGroupResultsToFileWhenJtlWriterAtThreadGroup(
      @TempDir Path tempDir) throws IOException {
    Path resultsFilePath = tempDir.resolve("results.jtl");
    JmeterDsl.testPlan(
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri),
            JmeterDsl.httpSampler(wiremockUri),
            JmeterDsl.jtlWriter(resultsFilePath.toString())
        ),
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri)
        )
    ).run();
    assertResultsFileResultsCount(resultsFilePath, TEST_ITERATIONS * 2);
  }

  @Test
  public void shouldWriteContainingSamplerResultsToFileWhenJtlWriterAtSampler(
      @TempDir Path tempDir) throws IOException {
    Path resultsFilePath = tempDir.resolve("results.jtl");
    testPlan(
        threadGroup(1, TEST_ITERATIONS,
            JmeterDsl.httpSampler(wiremockUri)
                .children(
                    JmeterDsl.jtlWriter(resultsFilePath.toString())
                ),
            JmeterDsl.httpSampler(wiremockUri)
        )
    ).run();
    assertResultsFileResultsCount(resultsFilePath, TEST_ITERATIONS);
  }

  @Test
  public void shouldSendPostWithContentTypeToServerWhenHttpSamplerWithPost() throws Exception {
    String jsonBody = "{\"var\":\"val\"}";
    Type contentType = Type.APPLICATION_JSON;
    testPlan(
        threadGroup(1, 1,
            JmeterDsl.httpSampler(wiremockUri).post(jsonBody, contentType)
        )
    ).run();
    wiremockServer.verify(postRequestedFor(anyUrl())
        .withHeader(HttpHeader.CONTENT_TYPE.toString(), equalTo(contentType.toString()))
        .withRequestBody(equalToJson(jsonBody)));
  }

  @Test
  public void shouldSendHeadersWhenHttpSamplerWithHeaders() throws Exception {
    testPlan(
        threadGroup(1, 1,
            JmeterDsl.httpSampler(wiremockUri)
                .method(HttpMethod.POST)
                .header(HEADER_NAME_1, HEADER_VALUE_1)
                .header(HEADER_NAME_2, HEADER_VALUE_2)
        )
    ).run();
    verifyHeadersSentToServer();
  }

  private void verifyHeadersSentToServer() {
    wiremockServer.verify(postRequestedFor(anyUrl())
        .withHeader(HEADER_NAME_1, equalTo(HEADER_VALUE_1))
        .withHeader(HEADER_NAME_2, equalTo(HEADER_VALUE_2)));
  }

  @Test
  public void shouldSendHeadersWhenHttpSamplerWithChildHeaders() throws Exception {
    testPlan(
        threadGroup(1, 1,
            JmeterDsl.httpSampler(wiremockUri)
                .method(HttpMethod.POST)
                .children(
                    JmeterDsl.httpHeaders()
                        .header(HEADER_NAME_1, HEADER_VALUE_1)
                        .header(HEADER_NAME_2, HEADER_VALUE_2)
                )
        )
    ).run();
    verifyHeadersSentToServer();
  }

  @Test
  public void shouldSendHeadersWhenHttpSamplerAndHeadersAtThreadGroup() throws Exception {
    testPlan(
        threadGroup(1, 1,
            JmeterDsl.httpHeaders()
                .header(HEADER_NAME_1, HEADER_VALUE_1)
                .header(HEADER_NAME_2, HEADER_VALUE_2),
            JmeterDsl.httpSampler(wiremockUri)
                .method(HttpMethod.POST)
        )
    ).run();
    verifyHeadersSentToServer();
  }

}
