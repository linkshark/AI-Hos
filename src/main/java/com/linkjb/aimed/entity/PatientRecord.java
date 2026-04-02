package com.linkjb.aimed.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 机构患者信息PR_PATIRECORD
 *
 * @author shark
 * @date 2026-04-02
 */
@Data
@Schema(description = "机构患者信息实体")
public class PatientRecord {

    @Schema(description = "id")
    private Integer id;

    @Schema(description = "组织机构代码")
    private String orgCode;

    @Schema(description = "患者序号")
    private String patiMediId;

    @Schema(description = "病案号")
    private String mediRecordNo;

    @Schema(description = "患者姓名")
    private String patiName;

    @Schema(description = "证件号码")
    private String patiIdCardNum;

    @Schema(description = "证件类型代码CV02.01.101")
    private String patiIdCardType;

    @Schema(description = "出生日期")
    private Date patiBirthday;

    @Schema(description = "性别代码")
    private String patiSexCode;

    @Schema(description = "性别名称")
    private String patiSexName;

    @Schema(description = "民族代码")
    private String nationCode;

    @Schema(description = "民族名称")
    private String nationName;

    @Schema(description = "手机号码")
    private String patiTel;

    @Schema(description = "工作单位电话号码")
    private String companyTel;

    @Schema(description = "删除标志")
    private BigDecimal cancelFlag;

    @Schema(description = "采集时间")
    private Date collectTime;

    @Schema(description = "采集流水号")
    private Integer collectSerialNo;

    @Schema(description = "籍贯")
    private String birthAddr;

    @Schema(description = "固定电话")
    private String fixedPhone;

    @Schema(description = "工作单位邮编")
    private String companyZipcode;

    @Schema(description = "工作单位地址")
    private String companyAddr;

    @Schema(description = "户口地址邮编")
    private String householdAddrZipcode;

    @Schema(description = "电子邮件地址")
    private String patiEmailAddr;

    @Schema(description = "调阅级别CVX_SECRET")
    private BigDecimal secretFlag;

    @Schema(description = "建档日期时间")
    private Date recordCreateTime;

    @Schema(description = "建档者姓名")
    private String recordCreatorName;

    @Schema(description = "建档者ID")
    private String recordCreatorId;

    @Schema(description = "联系人姓名")
    private String contactsName;

    @Schema(description = "联系人关系")
    private String contactsRelation;

    @Schema(description = "联系人电话")
    private String contactsTel;

    @Schema(description = "联系人地址")
    private String contactsAddrName;

    @Schema(description = "居住地址代码")
    private String liveAddrCode;

    @Schema(description = "居住地址名称")
    private String liveAddrName;

    @Schema(description = "居住详细地址")
    private String liveAddrDetail;

    @Schema(description = "居住地邮政编码")
    private String livePostCode;

    @Schema(description = "户口地址代码")
    private String homeAddrCode;

    @Schema(description = "户口地址名称")
    private String homeAddrName;

    @Schema(description = "户口详细地址")
    private String homeAddrDetail;

    @Schema(description = "职业代码")
    private String professionCode;

    @Schema(description = "职业名称")
    private String professionName;

    @Schema(description = "汉子输入码1")
    private String inputCode1;

    @Schema(description = "汉子输入码2")
    private String inputCode2;

    @Schema(description = "国家代码")
    private String countryCode;

    @Schema(description = "国家名称")
    private String countryName;

    @Schema(description = "婚姻代码")
    private String marriageCode;

    @Schema(description = "婚姻名称")
    private String marriageName;

    @Schema(description = "文化程度代码")
    private String culturalCode;

    @Schema(description = "文化程度名称")
    private String culturalName;

    @Schema(description = "工作单位名称")
    private String companyName;

    @Schema(description = "居民健康卡号")
    private String healthCardNo;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改时间")
    private Date updateTime;

    @Schema(description = "公司电话")
    private String companyPhone;

    @Schema(description = "来源id")
    private String originId;

    @Schema(description = "来源名")
    private String originName;
}