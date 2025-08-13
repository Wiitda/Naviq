package com.example.nav.data.model

import com.google.gson.annotations.SerializedName

data class SafetyHeader(
    @SerializedName("resultMsg") val resultMsg: String?,
    @SerializedName("resultCode") val resultCode: String?,
    @SerializedName("errorMsg") val errorMsg: String?
)

data class SafetyResponse(
    @SerializedName("header") val header: SafetyHeader?,
    @SerializedName("numOfRows") val numOfRows: Int? = null,
    @SerializedName("pageNo") val pageNo: Int? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("body") val body: List<SafetyItem>? // ← 핵심: items가 아니라 body
)

data class SafetyItem(
    @SerializedName("SN") val sn: Long?,
    @SerializedName("CRT_DT") val createdAt: String?,          // 예: 2023/09/16 11:09:49
    @SerializedName("MSG_CN") val msg: String?,
    @SerializedName("RCPTN_RGN_NM") val regionName: String?,
    @SerializedName("EMRG_STEP_NM") val emergencyStep: String?,
    @SerializedName("DST_SE_NM") val disasterType: String?,
    @SerializedName("REG_YMD") val regYmd: String?,
    @SerializedName("MDFCN_YMD") val modYmd: String?
)
