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

@DisplayName("Nori Analyzer: 한국어 형태소 분석 검증")
class NoriAnalyzerKoreanTest {

    // analysis-nori 플러그인을 설치한 후 ES를 기동하도록 entrypoint 오버라이드
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withEntrypoint("/bin/sh", "-c");
                cmd.withCmd(
                        "/usr/share/elasticsearch/bin/elasticsearch-plugin install --batch analysis-nori && " +
                        "exec /usr/local/bin/docker-entrypoint.sh eswrapper"
                );
            });

    static ElasticsearchClient client;
    static RestClient restClient;

    private static final String INDEX = "auctions_nori_test";

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
                  "settings": {
                    "analysis": {
                      "analyzer": {
                        "nori_analyzer": {
                          "type": "custom",
                          "tokenizer": "nori_tokenizer",
                          "filter": ["nori_pos_filter", "lowercase"]
                        }
                      },
                      "filter": {
                        "nori_pos_filter": {
                          "type": "nori_part_of_speech",
                          "stoptags": ["E", "J", "SC", "SE", "SF"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "properties": {
                      "title": { "type": "text", "analyzer": "nori_analyzer" },
                      "description": { "type": "text", "analyzer": "nori_analyzer" }
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
    @DisplayName("붙여쓴 '캠핑의자'로 검색 시 띄어쓴 '캠핑 의자'도 함께 검색된다")
    void search_compoundWord_findsSpacedVariant() throws IOException {
        List<String> titles = searchTitles("캠핑의자");

        assertThat(titles).contains("캠핑의자 접이식 경량");
        assertThat(titles).contains("캠핑 의자 릴렉스체어");
    }

    @Test
    @DisplayName("조사가 붙은 '캠핑의자를'로 검색해도 조사가 제거되어 결과가 반환된다")
    void search_withParticle_stripsParticleAndFindsResults() throws IOException {
        List<String> titles = searchTitles("캠핑의자를");

        assertThat(titles).isNotEmpty();
        assertThat(titles).contains("캠핑의자 접이식 경량");
    }

    @Test
    @DisplayName("붙여쓴 '블루투스이어폰'으로 검색 시 띄어쓴 '무선 블루투스 이어폰'도 검색된다")
    void search_bluetoothEarphone_findsSpacedVariant() throws IOException {
        List<String> titles = searchTitles("블루투스이어폰");

        assertThat(titles).contains("블루투스이어폰 노이즈캔슬링");
        assertThat(titles).contains("무선 블루투스 이어폰");
    }

    @Test
    @DisplayName("붙여쓴 '기계식키보드'로 검색 시 띄어쓴 '기계식 키보드 청축'도 검색된다")
    void search_mechanicalKeyboard_findsSpacedVariant() throws IOException {
        List<String> titles = searchTitles("기계식키보드");

        assertThat(titles).contains("기계식키보드 적축");
        assertThat(titles).contains("기계식 키보드 청축");
    }

    @Test
    @DisplayName("조사가 붙은 '키보드로'로 검색해도 조사가 제거되어 키보드 관련 결과가 반환된다")
    void search_keyboardWithParticle_stripsParticleAndFindsResults() throws IOException {
        List<String> titles = searchTitles("키보드로");

        assertThat(titles).isNotEmpty();
        assertThat(titles).anyMatch(t -> t.contains("키보드"));
    }

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