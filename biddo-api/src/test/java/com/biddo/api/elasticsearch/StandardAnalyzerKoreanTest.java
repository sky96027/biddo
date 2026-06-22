package com.biddo.api.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Standard Analyzer: 한국어 검색 한계 검증")
class StandardAnalyzerKoreanTest {

    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    static ElasticsearchClient client;
    static RestClient restClient;

    private static final String INDEX = "auctions_standard_test";

    @BeforeAll
    static void setUp() throws IOException {
        elasticsearch.start();

        restClient = RestClient.builder(
                HttpHost.create(elasticsearch.getHttpHostAddress())
        ).build();

        client = new ElasticsearchClient(
                new RestClientTransport(restClient, new JacksonJsonpMapper()));

        createIndexAndSeedData();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
        elasticsearch.stop();
    }

    static void createIndexAndSeedData() throws IOException {
        String settings = """
                {
                  "mappings": {
                    "properties": {
                      "title": { "type": "text", "analyzer": "standard" },
                      "description": { "type": "text", "analyzer": "standard" }
                    }
                  }
                }
                """;

        client.indices().create(c -> c
                .index(INDEX)
                .withJson(new StringReader(settings)));

        BulkRequest.Builder bulk = new BulkRequest.Builder();

        indexDoc(bulk, "1", "캠핑의자 접이식 경량", "가벼운 캠핑의자입니다");
        indexDoc(bulk, "2", "캠핑 의자 릴렉스체어", "편안한 캠핑 의자");
        indexDoc(bulk, "3", "무선 블루투스 이어폰", "고음질 무선 이어폰");
        indexDoc(bulk, "4", "블루투스이어폰 노이즈캔슬링", "노이즈캔슬링 기능");
        indexDoc(bulk, "5", "기계식키보드 적축", "체리 적축 기계식 키보드");
        indexDoc(bulk, "6", "기계식 키보드 청축", "청축 키보드");

        client.bulk(bulk.build());
        client.indices().refresh(r -> r.index(INDEX));
    }

    static void indexDoc(BulkRequest.Builder bulk, String id, String title, String description) {
        bulk.operations(op -> op.index(i -> i
                .index(INDEX)
                .id(id)
                .document(new Doc(title, description))));
    }

    @Test
    @DisplayName("붙여쓴 '캠핑의자'로 검색 시 띄어쓴 '캠핑 의자'가 누락된다")
    void search_compoundWord_missesSpacedVariant() throws IOException {
        // given
        String keyword = "캠핑의자";

        // when
        List<String> titles = searchTitles(keyword);

        // then
        assertThat(titles).contains("캠핑의자 접이식 경량");
        assertThat(titles).doesNotContain("캠핑 의자 릴렉스체어");
    }

    @Test
    @DisplayName("띄어쓴 '캠핑 의자'로 검색 시 붙여쓴 '캠핑의자'가 누락된다")
    void search_spacedWord_missesCompoundVariant() throws IOException {
        // given
        String keyword = "캠핑 의자";

        // when
        List<String> titles = searchTitles(keyword);

        // then
        assertThat(titles).contains("캠핑 의자 릴렉스체어");
        assertThat(titles).doesNotContain("캠핑의자 접이식 경량");
    }

    @Test
    @DisplayName("'블루투스이어폰'으로 검색 시 '무선 블루투스 이어폰'이 누락된다")
    void search_bluetoothEarphone_missesSpacedVariant() throws IOException {
        // given
        String keyword = "블루투스이어폰";

        // when
        List<String> titles = searchTitles(keyword);

        // then
        assertThat(titles).contains("블루투스이어폰 노이즈캔슬링");
        assertThat(titles).doesNotContain("무선 블루투스 이어폰");
    }

    @Test
    @DisplayName("조사가 붙은 '캠핑의자를'로 검색 시 결과가 없다")
    void search_withParticle_noResults() throws IOException {
        // given
        String keyword = "캠핑의자를";

        // when
        List<String> titles = searchTitles(keyword);

        // then
        assertThat(titles).isEmpty();
    }

    @Test
    @DisplayName("'기계식키보드'로 검색 시 '기계식 키보드 청축'이 누락된다")
    void search_mechanicalKeyboard_missesSpacedVariant() throws IOException {
        // given
        String keyword = "기계식키보드";

        // when
        List<String> titles = searchTitles(keyword);

        // then
        assertThat(titles).contains("기계식키보드 적축");
        assertThat(titles).doesNotContain("기계식 키보드 청축");
    }

    // Nori 분석기 적용 시 기대 결과 (TODO)
    // Nori 분석기를 적용하면 위 테스트들의 결과가 달라져야 한다:
    // - "캠핑의자" → "캠핑" + "의자"로 분리되어 띄어쓰기 무관하게 검색
    // - "캠핑의자를" → 조사 "를" 제거 후 "캠핑" + "의자"로 검색
    // - "블루투스이어폰" → "블루투스" + "이어폰"으로 분리
    // - "기계식키보드" → "기계식" + "키보드"로 분리
    //
    // Nori 적용 후 검증 테스트는 별도 클래스(NoriAnalyzerKoreanTest)로 작성 예정.
    // ES 이미지에 analysis-nori 플러그인 설치 필요:
    // new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
    //     .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
    //     .withCommand("sh", "-c",
    //         "bin/elasticsearch-plugin install analysis-nori && exec /usr/local/bin/docker-entrypoint.sh");

    private List<String> searchTitles(String keyword) throws IOException {
        SearchResponse<ObjectNode> response = client.search(s -> s
                        .index(INDEX)
                        .query(q -> q.multiMatch(mm -> mm
                                .query(keyword)
                                .fields("title^2", "description"))),
                ObjectNode.class);

        return response.hits().hits().stream()
                .map(Hit::source)
                .map(node -> node.get("title").asText())
                .toList();
    }

    record Doc(String title, String description) {}
}