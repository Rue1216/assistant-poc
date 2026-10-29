package com.example.copilot.aiassistant.tool.customer.sections;

import com.example.copilot.aiassistant.port.c360.AssetBlock;
import com.example.copilot.aiassistant.port.c360.AumItems;
import com.example.copilot.aiassistant.port.c360.CategoryData;
import com.example.copilot.aiassistant.port.c360.Customer360Record;
import com.example.copilot.aiassistant.port.c360.LiabilityBlock;
import com.example.copilot.aiassistant.port.c360.LumItems;
import com.example.copilot.aiassistant.port.c360.ProductMix;
import com.example.copilot.aiassistant.port.c360.ProductMixData;
import com.example.copilot.aiassistant.tool.customer.DisplayFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Section {@code assetLiability}: assets, liabilities, liability-to-asset ratio and product mix. */
public final class AssetLiabilitySection {

    /** A category as output fields, with the customer ratio it is sorted by. */
    private record Row(Map<String, Object> fields, BigDecimal customerRatio) {
    }

    private AssetLiabilitySection() {
    }

    /** Null when the platform delivered none of aum, lum and productMix. */
    public static Map<String, Object> render(Customer360Record c) {
        if (c.aum() == null && c.lum() == null && c.productMix() == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.aum() != null && c.aum().auMData() != null) {
            data.put("assets", block(c.aum().customerTotal(), c.aum().segmentTotal(), c.aum().topTenTotal(),
                    assetRows(c.aum().auMData())));
        } else {
            SectionSupport.missing("aum");
        }
        if (c.lum() != null && c.lum().luMData() != null) {
            data.put("liabilities", block(c.lum().customerTotal(), c.lum().segmentTotal(), c.lum().topTenTotal(),
                    liabilityRows(c.lum().luMData())));
        } else {
            SectionSupport.missing("lum");
        }
        liabilityToAssetRatio(c.aum(), c.lum()).ifPresent(ratio -> data.put("liabilityToAssetRatio", ratio));
        if (c.productMix() != null) {
            data.put("productMix", productMix(c.productMix()));
        } else {
            SectionSupport.missing("productMix");
        }
        return data;
    }

    private static Map<String, Object> block(long total, long segmentTotal, long topTenTotal, List<Row> rows) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("total", DisplayFormat.amount(total));
        block.put("segmentTotal", DisplayFormat.amount(segmentTotal));
        block.put("topTenTotal", DisplayFormat.amount(topTenTotal));
        // sorted() is stable: equal ratios keep the platform order.
        block.put("items", rows.stream().sorted(Comparator.comparing(Row::customerRatio).reversed())
                .map(Row::fields).toList());
        return block;
    }

    private static List<Row> assetRows(AumItems a) {
        List<Row> rows = new ArrayList<>();
        addRow(rows, "活存", a.demandDeposit());
        addRow(rows, "定存", a.timeDeposit());
        addRow(rows, "基金", a.fund());
        addRow(rows, "金融商品", a.financialProduct());
        addRow(rows, "保險", a.insurance());
        addRow(rows, "財金商品", a.financialAsset());
        return rows;
    }

    private static List<Row> liabilityRows(LumItems l) {
        List<Row> rows = new ArrayList<>();
        addRow(rows, "房貸（購置）", l.mtgPurchase());
        addRow(rows, "房貸（週轉金）", l.mtgRevolve());
        addRow(rows, "副擔保", l.collateral());
        addRow(rows, "信貸", l.creditLoan());
        addRow(rows, "金融資產融資", l.finAssetLoan());
        addRow(rows, "信用卡已使用額度", l.ccBalance());
        return rows;
    }

    /** Both lists are [customer, segment average, segment top 10]; a missing or malformed category is skipped. */
    private static void addRow(List<Row> rows, String item, CategoryData category) {
        if (category == null || category.ratioList() == null || category.ratioList().size() < 3
                || category.amountList() == null || category.amountList().isEmpty()) {
            SectionSupport.missing("category " + item);
            return;
        }
        BigDecimal customer = SectionSupport.decimal(category.ratioList().get(0), item);
        BigDecimal segment = SectionSupport.decimal(category.ratioList().get(1), item);
        BigDecimal topTen = SectionSupport.decimal(category.ratioList().get(2), item);
        if (customer == null || segment == null || topTen == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("item", item);
        fields.put("amount", DisplayFormat.amount(category.amountList().get(0)));
        fields.put("ratio", DisplayFormat.percent(customer));
        fields.put("segmentRatio", DisplayFormat.percent(segment));
        fields.put("topTenRatio", DisplayFormat.percent(topTen));
        fields.put("vsSegment", DisplayFormat.percentPoints(customer.subtract(segment)));
        rows.add(new Row(fields, customer));
    }

    /** Liabilities over assets; empty when either block is missing or the assets are zero. */
    private static Optional<String> liabilityToAssetRatio(AssetBlock aum, LiabilityBlock lum) {
        if (aum == null || lum == null || aum.customerTotal() == 0) {
            return Optional.empty();
        }
        BigDecimal ratio = BigDecimal.valueOf(lum.customerTotal())
                .divide(BigDecimal.valueOf(aum.customerTotal()), 6, RoundingMode.HALF_UP);
        return Optional.of(DisplayFormat.ratio(ratio));
    }

    private static Map<String, Object> productMix(ProductMix mix) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal yield = SectionSupport.decimal(mix.aumYield(), "aumYield");
        if (yield != null) {
            out.put("aumYield", DisplayFormat.percent(yield));
        }
        ProductMixData customer = mix.customerData();
        ProductMixData segment = mix.segmentData();
        if (customer != null && segment != null) {
            out.put("ldr", comparison(customer.ldr(), segment.ldr()));
            if (hasTwo(customer) && hasTwo(segment)) {
                out.put("casaRatio", comparison(customer.casaAndWealthAumRatio().get(0),
                        segment.casaAndWealthAumRatio().get(0)));
                out.put("wealthAumRatio", comparison(customer.casaAndWealthAumRatio().get(1),
                        segment.casaAndWealthAumRatio().get(1)));
            }
        }
        return out;
    }

    private static boolean hasTwo(ProductMixData data) {
        return data.casaAndWealthAumRatio() != null && data.casaAndWealthAumRatio().size() >= 2;
    }

    private static Map<String, Object> comparison(double customer, double segment) {
        BigDecimal c = BigDecimal.valueOf(customer);
        BigDecimal s = BigDecimal.valueOf(segment);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customer", DisplayFormat.percent(c));
        out.put("segment", DisplayFormat.percent(s));
        out.put("vsSegment", DisplayFormat.percentPoints(c.subtract(s)));
        return out;
    }
}
