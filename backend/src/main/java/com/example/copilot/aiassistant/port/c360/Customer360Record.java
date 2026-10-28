package com.example.copilot.aiassistant.port.c360;

/** Customer-360 payload as the platform returns it; field names mirror the platform DTO. */
public record Customer360Record(
        String dataDate,
        ProfileCard customerProfile,
        ManagementCard customerManagement,
        AssetBlock aum,
        LiabilityBlock lum,
        MemberRankData memberRank,
        RadarView wealthPotential,
        ProductMix productMix,
        RadarView assetProduct,
        RadarView liabilitiesAndFx,
        RadarView accountRelationshipDepth,
        RadarView consumptionBehavior,
        RadarView channelBehavior,
        RadarView digitalBehavior) {}
