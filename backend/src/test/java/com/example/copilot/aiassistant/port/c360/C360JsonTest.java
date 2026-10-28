package com.example.copilot.aiassistant.port.c360;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class C360JsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void platformSpellingsSurviveRoundTrip() throws Exception {
        String json = """
                {"dataDate":"2026/10/27",
                 "customerProfile":{"name":"王小明","age":"45","segement":"A1","primaryBranch":"信義","financialCounsultant":"李"},
                 "aum":{"auMData":{"demandDeposit":{"ratioList":["36.6"],"amountList":[1]}},
                        "customerTotal":1,"segmentTotal":2,"topTenTotal":3},
                 "lum":{"luMData":{"mtgPurchase":{"ratioList":["1"],"amountList":[2]}},
                        "customerTotal":1,"segmentTotal":2,"topTenTotal":3},
                 "memberRank":{"memberRankCode":"P","memberRankName":"新富","progressPercent":"50",
                               "segementProgressPercent":"40","upgradeOrDownGradeAlert":"x",
                               "customerAum":"1","segmentAverageAum":"2"}}
                """;
        Customer360Record rec = mapper.readValue(json, Customer360Record.class);
        assertThat(rec.customerProfile().segment()).isEqualTo("A1");
        assertThat(rec.customerProfile().financialConsultant()).isEqualTo("李");
        assertThat(rec.memberRank().segmentProgressPercent()).isEqualTo("40");
        assertThat(rec.aum().auMData().demandDeposit().amountList()).containsExactly(1L);
        assertThat(rec.lum().luMData().mtgPurchase().ratioList()).containsExactly("1");

        JsonNode out = mapper.readTree(mapper.writeValueAsString(rec));
        assertThat(out.get("customerProfile").has("segement")).isTrue();
        assertThat(out.get("customerProfile").has("segment")).isFalse();
        assertThat(out.get("customerProfile").has("financialCounsultant")).isTrue();
        assertThat(out.get("memberRank").has("segementProgressPercent")).isTrue();
        assertThat(out.get("aum").has("auMData")).isTrue();
        assertThat(out.get("lum").has("luMData")).isTrue();
    }

    @Test
    void radarViewOmitsNullTagList() throws Exception {
        RadarView view = new RadarView(java.util.List.of("a"), java.util.List.of(new RadarPoint(1, "i", null)),
                java.util.List.of(), null);
        JsonNode out = mapper.readTree(mapper.writeValueAsString(view));
        assertThat(out.has("tagList")).isFalse();
        assertThat(out.get("customerData").get(0).has("highlight")).isFalse();
    }
}
