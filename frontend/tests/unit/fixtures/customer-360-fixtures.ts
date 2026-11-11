// Fixtures copied from the backend mock data (customer-360.json); the frontend does not import the backend file.
import type { CustomerDto, RealTimeCustomerDto } from '@/beans/customer-bean'

export const WANG_CUSTOMER: CustomerDto = {
  "dataDate": "2026/10/27",
  "customerProfile": {
    "name": "王小明",
    "age": "58",
    "segement": "R01-退休樂活客群",
    "primaryBranch": "B101 信義分行",
    "financialCounsultant": "16888 李大發"
  },
  "customerManagement": {
    "campaignList": [
      {
        "receiptNo": "CP202610260001",
        "campaignName": "退休金規劃講座",
        "campaignDuration": "2026/10/26",
        "campaignSummary": "說明退休現金流規劃與月配息配置，顧客已出席並詢問月領 3 萬的做法。",
        "campaignEnabled": "N",
        "attachment": [
          "退休金規劃講座簡章.pdf"
        ]
      },
      {
        "receiptNo": "CP202610010002",
        "campaignName": "月配息基金專案",
        "campaignDuration": "2026/10/01-2026/11/30",
        "campaignSummary": "月配息基金申購優惠，適合有每月現金流需求的顧客。",
        "campaignEnabled": "Y",
        "attachment": [
          "月配息基金專案說明.pdf"
        ]
      },
      {
        "receiptNo": "CP202610010003",
        "campaignName": "菁英年度健檢",
        "campaignDuration": "2026/10/01-2026/12/31",
        "campaignSummary": "提供資產配置健檢與菁英會員升等試算。",
        "campaignEnabled": "N",
        "attachment": []
      }
    ],
    "recommendedProducts": [
      "月配息平衡型基金",
      "美元海外債"
    ],
    "insight": "退休現金流需求明確，希望每月現金流約 3 萬；存款占比高於同客群；定存到期可轉配置；客服與電話回應率最高，適合電話經營。"
  },
  "aum": {
    "auMData": {
      "demandDeposit": {
        "ratioList": [
          "14.6",
          "22.0",
          "10.0"
        ],
        "amountList": [
          1200000,
          1430000,
          1200000
        ]
      },
      "timeDeposit": {
        "ratioList": [
          "36.6",
          "20.0",
          "15.0"
        ],
        "amountList": [
          3000000,
          1300000,
          1800000
        ]
      },
      "fund": {
        "ratioList": [
          "30.5",
          "25.0",
          "30.0"
        ],
        "amountList": [
          2500000,
          1625000,
          3600000
        ]
      },
      "financialProduct": {
        "ratioList": [
          "9.8",
          "15.0",
          "25.0"
        ],
        "amountList": [
          800000,
          975000,
          3000000
        ]
      },
      "insurance": {
        "ratioList": [
          "6.1",
          "13.0",
          "12.0"
        ],
        "amountList": [
          500000,
          845000,
          1440000
        ]
      },
      "financialAsset": {
        "ratioList": [
          "2.4",
          "5.0",
          "8.0"
        ],
        "amountList": [
          200000,
          325000,
          960000
        ]
      }
    },
    "customerTotal": 8200000,
    "segmentTotal": 6500000,
    "topTenTotal": 12000000
  },
  "lum": {
    "luMData": {
      "mtgPurchase": {
        "ratioList": [
          "76.2",
          "60.0",
          "70.0"
        ],
        "amountList": [
          5200000,
          2700000,
          6300000
        ]
      },
      "mtgRevolve": {
        "ratioList": [
          "8.8",
          "10.0",
          "8.0"
        ],
        "amountList": [
          600000,
          450000,
          720000
        ]
      },
      "collateral": {
        "ratioList": [
          "5.9",
          "6.0",
          "5.0"
        ],
        "amountList": [
          400000,
          270000,
          450000
        ]
      },
      "creditLoan": {
        "ratioList": [
          "5.1",
          "12.0",
          "9.0"
        ],
        "amountList": [
          350000,
          540000,
          810000
        ]
      },
      "finAssetLoan": {
        "ratioList": [
          "2.2",
          "6.0",
          "4.0"
        ],
        "amountList": [
          150000,
          270000,
          360000
        ]
      },
      "ccBalance": {
        "ratioList": [
          "1.8",
          "6.0",
          "4.0"
        ],
        "amountList": [
          120000,
          270000,
          360000
        ]
      }
    },
    "customerTotal": 6820000,
    "segmentTotal": 4500000,
    "topTenTotal": 9000000
  },
  "memberRank": {
    "memberRankCode": "P",
    "memberRankName": "新富",
    "progressPercent": "68.5",
    "segementProgressPercent": "52.0",
    "upgradeOrDownGradeAlert": "距菁英尚差 AuM 180 萬",
    "customerAum": "8200000",
    "segmentAverageAum": "6500000"
  },
  "wealthPotential": {
    "labels": [
      "年收入",
      "綜所稅",
      "信用卡額度",
      "年度刷卡金額",
      "不動產估值",
      "股利"
    ],
    "customerData": [
      {
        "rank": 72,
        "info": "180 萬"
      },
      {
        "rank": 65,
        "info": "12 萬"
      },
      {
        "rank": 55,
        "info": "30 萬"
      },
      {
        "rank": 40,
        "info": "36 萬"
      },
      {
        "rank": 85,
        "info": "2,400 萬"
      },
      {
        "rank": 60,
        "info": "8 萬"
      }
    ],
    "segmentData": [
      {
        "rank": 55
      },
      {
        "rank": 50
      },
      {
        "rank": 50
      },
      {
        "rank": 50
      },
      {
        "rank": 60
      },
      {
        "rank": 45
      }
    ]
  },
  "productMix": {
    "aumYield": "1.86",
    "customerData": {
      "ldr": 62,
      "casaAndWealthAumRatio": [
        29,
        49
      ]
    },
    "segmentData": {
      "ldr": 48,
      "casaAndWealthAumRatio": [
        45,
        38
      ]
    }
  },
  "assetProduct": {
    "labels": [
      "債券型基金",
      "股票型基金",
      "平衡型基金",
      "儲蓄型保險",
      "保障型保險",
      "海外債"
    ],
    "customerData": [
      {
        "rank": 62,
        "highlight": false
      },
      {
        "rank": 28,
        "highlight": false
      },
      {
        "rank": 55,
        "highlight": false
      },
      {
        "rank": 48,
        "highlight": false
      },
      {
        "rank": 35,
        "highlight": false
      },
      {
        "rank": 81,
        "highlight": true
      }
    ],
    "segmentData": [
      {
        "rank": 50
      },
      {
        "rank": 30
      },
      {
        "rank": 45
      },
      {
        "rank": 40
      },
      {
        "rank": 35
      },
      {
        "rank": 40
      }
    ]
  },
  "liabilitiesAndFx": {
    "labels": [
      "外匯・美元",
      "外匯・日圓",
      "信貸",
      "房貸周轉金",
      "SB",
      "信用卡分期"
    ],
    "customerData": [
      {
        "rank": 70,
        "highlight": true
      },
      {
        "rank": 42,
        "highlight": false
      },
      {
        "rank": 18,
        "highlight": false
      },
      {
        "rank": 55,
        "highlight": false
      },
      {
        "rank": 22,
        "highlight": false
      },
      {
        "rank": 30,
        "highlight": false
      }
    ],
    "segmentData": [
      {
        "rank": 40
      },
      {
        "rank": 35
      },
      {
        "rank": 20
      },
      {
        "rank": 40
      },
      {
        "rank": 25
      },
      {
        "rank": 30
      }
    ]
  },
  "accountRelationshipDepth": {
    "labels": [
      "大額淨流出入",
      "房貸自扣與還款",
      "證券自扣與交易",
      "公共事業費自扣",
      "信貸自扣與還款"
    ],
    "customerData": [
      {
        "rank": 40
      },
      {
        "rank": 90
      },
      {
        "rank": 65
      },
      {
        "rank": 80
      },
      {
        "rank": 30
      }
    ],
    "segmentData": [
      {
        "rank": 50
      },
      {
        "rank": 60
      },
      {
        "rank": 45
      },
      {
        "rank": 55
      },
      {
        "rank": 35
      }
    ]
  },
  "consumptionBehavior": {
    "labels": [
      "生活繳費",
      "美食餐飲",
      "網購",
      "3C電信",
      "保險投資",
      "App及小額支付",
      "休閒旅遊",
      "百貨購物",
      "其他"
    ],
    "customerData": [
      {
        "rank": 70
      },
      {
        "rank": 55
      },
      {
        "rank": 25
      },
      {
        "rank": 30
      },
      {
        "rank": 75
      },
      {
        "rank": 15
      },
      {
        "rank": 60
      },
      {
        "rank": 45
      },
      {
        "rank": 20
      }
    ],
    "segmentData": [
      {
        "rank": 60
      },
      {
        "rank": 50
      },
      {
        "rank": 40
      },
      {
        "rank": 40
      },
      {
        "rank": 45
      },
      {
        "rank": 35
      },
      {
        "rank": 45
      },
      {
        "rank": 45
      },
      {
        "rank": 25
      }
    ],
    "tagList": [
      "穩健居家型",
      "保障規劃族",
      "旅遊休閒"
    ]
  },
  "channelBehavior": {
    "labels": [
      "分行",
      "ATM",
      "行銀",
      "網銀",
      "Wallet",
      "客服",
      "Line"
    ],
    "customerData": [
      {
        "rank": 85
      },
      {
        "rank": 40
      },
      {
        "rank": 20
      },
      {
        "rank": 5
      },
      {
        "rank": 10
      },
      {
        "rank": 70
      },
      {
        "rank": 35
      }
    ],
    "segmentData": [
      {
        "rank": 50
      },
      {
        "rank": 45
      },
      {
        "rank": 30
      },
      {
        "rank": 20
      },
      {
        "rank": 20
      },
      {
        "rank": 40
      },
      {
        "rank": 25
      }
    ]
  },
  "digitalBehavior": {
    "labels": [
      "數位活動響應率",
      "線上登入次數",
      "線上交易次數",
      "數位收入"
    ],
    "customerData": [
      {
        "rank": 25,
        "info": "1/4 檔"
      },
      {
        "rank": 20,
        "info": "2 次/月"
      },
      {
        "rank": 15,
        "info": "1 筆/月"
      },
      {
        "rank": 10,
        "info": "0.2 萬/年"
      }
    ],
    "segmentData": [
      {
        "rank": 50
      },
      {
        "rank": 60
      },
      {
        "rank": 45
      },
      {
        "rank": 40
      }
    ]
  }
}

export const WANG_REAL_TIME: RealTimeCustomerDto = {
  "attentionCustomer": false,
  "specialCareCustomer": false,
  "profilePhoto": {
    "tags": [
      "退休規劃",
      "穩健配息",
      "電話偏好"
    ]
  },
  "customerFlaggingList": [],
  "keyAlerts": {
    "items": [
      {
        "type": "IDLE_FUNDS",
        "title": "定存 300 萬將於 2026/11/15 到期",
        "detail": "建議提前聯繫續約或資產配置",
        "date": "2026/11/15"
      },
      {
        "type": "OTHER",
        "title": "10 月下旬面談尚未排定",
        "detail": "2026/10/02 電話中約定",
        "date": "2026/10/02"
      },
      {
        "type": "OTHER",
        "title": "KYC 將於 2026/12 到期",
        "detail": "需重新評估風險屬性",
        "date": "2026/12/31"
      }
    ]
  }
}

export const HUANG_CUSTOMER: CustomerDto = {
  "dataDate": "2026/10/27",
  "customerProfile": {
    "name": "黃俊豪",
    "age": "36",
    "segement": "D04-數位新世代客群",
    "primaryBranch": "B201 台中分行",
    "financialCounsultant": "17015 張書豪"
  },
  "customerManagement": {
    "campaignList": [
      {
        "receiptNo": "CP202608250008",
        "campaignName": "數位帳戶新戶禮",
        "campaignDuration": "2026/08/25",
        "campaignSummary": "數位帳戶新戶開戶禮，顧客已點擊簡訊連結。",
        "campaignEnabled": "N",
        "attachment": []
      },
      {
        "receiptNo": "CP202610100009",
        "campaignName": "信用卡分期回饋活動",
        "campaignDuration": "2026/10/01-2026/12/31",
        "campaignSummary": "信用卡分期消費享回饋，屬推廣型活動。",
        "campaignEnabled": "Y",
        "attachment": [
          "信用卡分期回饋活動辦法.pdf"
        ]
      }
    ],
    "recommendedProducts": [
      "數位帳戶加值方案",
      "信用卡分期專案"
    ],
    "insight": "數位原生，以行銀與 Wallet 為主要往來；信用卡分期與信貸使用多；目前為不宜行銷顧客，僅適合服務類聯繫。"
  },
  "aum": {
    "auMData": {
      "demandDeposit": {
        "ratioList": [
          "56.3",
          "40.0",
          "20.0"
        ],
        "amountList": [
          450000,
          240000,
          600000
        ]
      },
      "timeDeposit": {
        "ratioList": [
          "12.5",
          "15.0",
          "15.0"
        ],
        "amountList": [
          100000,
          90000,
          450000
        ]
      },
      "fund": {
        "ratioList": [
          "18.8",
          "20.0",
          "30.0"
        ],
        "amountList": [
          150000,
          120000,
          900000
        ]
      },
      "financialProduct": {
        "ratioList": [
          "6.3",
          "10.0",
          "20.0"
        ],
        "amountList": [
          50000,
          60000,
          600000
        ]
      },
      "insurance": {
        "ratioList": [
          "3.8",
          "10.0",
          "10.0"
        ],
        "amountList": [
          30000,
          60000,
          300000
        ]
      },
      "financialAsset": {
        "ratioList": [
          "2.5",
          "5.0",
          "5.0"
        ],
        "amountList": [
          20000,
          30000,
          150000
        ]
      }
    },
    "customerTotal": 800000,
    "segmentTotal": 600000,
    "topTenTotal": 3000000
  },
  "lum": {
    "luMData": {
      "mtgPurchase": {
        "ratioList": [
          "0.0",
          "30.0",
          "45.0"
        ],
        "amountList": [
          0,
          210000,
          1125000
        ]
      },
      "mtgRevolve": {
        "ratioList": [
          "0.0",
          "5.0",
          "8.0"
        ],
        "amountList": [
          0,
          35000,
          200000
        ]
      },
      "collateral": {
        "ratioList": [
          "0.0",
          "3.0",
          "4.0"
        ],
        "amountList": [
          0,
          21000,
          100000
        ]
      },
      "creditLoan": {
        "ratioList": [
          "69.8",
          "40.0",
          "28.0"
        ],
        "amountList": [
          300000,
          280000,
          700000
        ]
      },
      "finAssetLoan": {
        "ratioList": [
          "11.6",
          "10.0",
          "8.0"
        ],
        "amountList": [
          50000,
          70000,
          200000
        ]
      },
      "ccBalance": {
        "ratioList": [
          "18.6",
          "12.0",
          "7.0"
        ],
        "amountList": [
          80000,
          84000,
          175000
        ]
      }
    },
    "customerTotal": 430000,
    "segmentTotal": 700000,
    "topTenTotal": 2500000
  },
  "memberRank": {
    "memberRankCode": "0",
    "memberRankName": "非理財會員",
    "progressPercent": "26.7",
    "segementProgressPercent": "35.0",
    "upgradeOrDownGradeAlert": null,
    "customerAum": "800000",
    "segmentAverageAum": "600000"
  },
  "wealthPotential": {
    "labels": [
      "年收入",
      "綜所稅",
      "信用卡額度",
      "年度刷卡金額",
      "不動產估值",
      "股利"
    ],
    "customerData": [
      {
        "rank": 45,
        "info": "72 萬"
      },
      {
        "rank": 35,
        "info": "3 萬"
      },
      {
        "rank": 40,
        "info": "20 萬"
      },
      {
        "rank": 50,
        "info": "30 萬"
      },
      {
        "rank": 15,
        "info": "0 萬"
      },
      {
        "rank": 10,
        "info": "0.5 萬"
      }
    ],
    "segmentData": [
      {
        "rank": 50
      },
      {
        "rank": 45
      },
      {
        "rank": 45
      },
      {
        "rank": 45
      },
      {
        "rank": 40
      },
      {
        "rank": 30
      }
    ]
  },
  "productMix": {
    "aumYield": "0.55",
    "customerData": {
      "ldr": 55,
      "casaAndWealthAumRatio": [
        56,
        25
      ]
    },
    "segmentData": {
      "ldr": 40,
      "casaAndWealthAumRatio": [
        48,
        26
      ]
    }
  },
  "assetProduct": {
    "labels": [
      "債券型基金",
      "股票型基金",
      "平衡型基金",
      "儲蓄型保險",
      "保障型保險",
      "海外債"
    ],
    "customerData": [
      {
        "rank": 10,
        "highlight": false
      },
      {
        "rank": 35,
        "highlight": true
      },
      {
        "rank": 15,
        "highlight": false
      },
      {
        "rank": 5,
        "highlight": false
      },
      {
        "rank": 10,
        "highlight": false
      },
      {
        "rank": 5,
        "highlight": false
      }
    ],
    "segmentData": [
      {
        "rank": 30
      },
      {
        "rank": 30
      },
      {
        "rank": 25
      },
      {
        "rank": 25
      },
      {
        "rank": 25
      },
      {
        "rank": 20
      }
    ]
  },
  "liabilitiesAndFx": {
    "labels": [
      "外匯・美元",
      "外匯・日圓",
      "信貸",
      "房貸周轉金",
      "SB",
      "信用卡分期"
    ],
    "customerData": [
      {
        "rank": 10,
        "highlight": false
      },
      {
        "rank": 5,
        "highlight": false
      },
      {
        "rank": 60,
        "highlight": false
      },
      {
        "rank": 5,
        "highlight": false
      },
      {
        "rank": 5,
        "highlight": false
      },
      {
        "rank": 80,
        "highlight": true
      }
    ],
    "segmentData": [
      {
        "rank": 20
      },
      {
        "rank": 10
      },
      {
        "rank": 40
      },
      {
        "rank": 15
      },
      {
        "rank": 10
      },
      {
        "rank": 45
      }
    ]
  },
  "accountRelationshipDepth": {
    "labels": [
      "大額淨流出入",
      "房貸自扣與還款",
      "證券自扣與交易",
      "公共事業費自扣",
      "信貸自扣與還款"
    ],
    "customerData": [
      {
        "rank": 20
      },
      {
        "rank": 10
      },
      {
        "rank": 45
      },
      {
        "rank": 35
      },
      {
        "rank": 40
      }
    ],
    "segmentData": [
      {
        "rank": 30
      },
      {
        "rank": 25
      },
      {
        "rank": 35
      },
      {
        "rank": 40
      },
      {
        "rank": 35
      }
    ]
  },
  "consumptionBehavior": {
    "labels": [
      "生活繳費",
      "美食餐飲",
      "網購",
      "3C電信",
      "保險投資",
      "App及小額支付",
      "休閒旅遊",
      "百貨購物",
      "其他"
    ],
    "customerData": [
      {
        "rank": 45
      },
      {
        "rank": 75
      },
      {
        "rank": 85
      },
      {
        "rank": 70
      },
      {
        "rank": 15
      },
      {
        "rank": 90
      },
      {
        "rank": 50
      },
      {
        "rank": 40
      },
      {
        "rank": 20
      }
    ],
    "segmentData": [
      {
        "rank": 55
      },
      {
        "rank": 55
      },
      {
        "rank": 55
      },
      {
        "rank": 50
      },
      {
        "rank": 35
      },
      {
        "rank": 50
      },
      {
        "rank": 45
      },
      {
        "rank": 40
      },
      {
        "rank": 25
      }
    ],
    "tagList": [
      "數位原生",
      "行動支付",
      "美食網購"
    ]
  },
  "channelBehavior": {
    "labels": [
      "分行",
      "ATM",
      "行銀",
      "網銀",
      "Wallet",
      "客服",
      "Line"
    ],
    "customerData": [
      {
        "rank": 10
      },
      {
        "rank": 35
      },
      {
        "rank": 85
      },
      {
        "rank": 40
      },
      {
        "rank": 90
      },
      {
        "rank": 20
      },
      {
        "rank": 30
      }
    ],
    "segmentData": [
      {
        "rank": 30
      },
      {
        "rank": 40
      },
      {
        "rank": 50
      },
      {
        "rank": 40
      },
      {
        "rank": 45
      },
      {
        "rank": 25
      },
      {
        "rank": 30
      }
    ]
  },
  "digitalBehavior": {
    "labels": [
      "數位活動響應率",
      "線上登入次數",
      "線上交易次數",
      "數位收入"
    ],
    "customerData": [
      {
        "rank": 90,
        "info": "5/5 檔"
      },
      {
        "rank": 95,
        "info": "30 次/月"
      },
      {
        "rank": 85,
        "info": "20 筆/月"
      },
      {
        "rank": 40,
        "info": "0.4 萬/年"
      }
    ],
    "segmentData": [
      {
        "rank": 60
      },
      {
        "rank": 70
      },
      {
        "rank": 55
      },
      {
        "rank": 40
      }
    ]
  }
}

export const HUANG_REAL_TIME: RealTimeCustomerDto = {
  "attentionCustomer": false,
  "specialCareCustomer": false,
  "profilePhoto": {
    "tags": [
      "數位原生",
      "ETF 新手",
      "行動支付"
    ]
  },
  "customerFlaggingList": [
    {
      "type": "NO_MARKETING",
      "label": "不宜行銷",
      "expiryDate": "2027/03/31"
    }
  ],
  "keyAlerts": {
    "items": [
      {
        "type": "OTHER",
        "title": "數位帳戶身分驗證即將到期",
        "detail": "請提醒顧客完成重新驗證",
        "date": "2026/11/30"
      }
    ]
  }
}

/** 王小明 with a relationship-account rank code, for the no-progress-axis case. */
export const RELATIONSHIP_CUSTOMER: CustomerDto = {
  ...WANG_CUSTOMER,
  memberRank: { ...WANG_CUSTOMER.memberRank, memberRankCode: 'R', memberRankName: '關係戶', upgradeOrDownGradeAlert: null },
}
