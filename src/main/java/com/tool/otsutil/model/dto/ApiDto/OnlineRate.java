package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
public class OnlineRate {
    //"indexId": null,
    //            "rtuId": null,
    //            "id": null,
    //            "rtuIdSet": null,
    //            "areaId": "3096224861257729",
    //            "alisaName": "南平市",
    //            "areaName": null,
    //            "parentAreaId": "3096224861257729",
    //            "rtuKind": 0,
    //            "rtuModel": 0,
    //            "updateTime": null,
    //            "time": "2024-12-31",
    //            "totalTime": 5.702112E9,
    //            "onlineTime": 5.601767387E9,
    //            "onlineNum": null,
    //            "totalNum": 9445,
    //            "rate": 0.9851086,
    //            "timeDimension": 0,
    //            "molecule": 0.0,
    //            "denominator": 0.0,
    //            "unlineNum": 81,
    //            "subControlAreaName": null,
    //            "companyId": null,
    //            "subControlAreaId": null,
    //            "companyName": null

    private String indexId;
    private String rtuId;
    private String rtuIdSet;
    private String areaId;
    private String alisaName;
    private BigDecimal totalTime;
    private BigDecimal onlineTime;
    private Integer totalNum;
    private BigDecimal rate;
    private Integer unlineNum;
    private String time;


    private String id;
    private String areaName;
    private String parentAreaId;
    private int rtuKind;
    private int rtuModel;
    private String updateTime;
    private Integer onlineNum;
    private int timeDimension;
    private double molecule;
    private double denominator;
    private String subControlAreaName;
    private String companyId;
    private String subControlAreaId;
    private String companyName;
}
