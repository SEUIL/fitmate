package com.fitmate.app.ui

internal data class NoticeData(val id: String, val title: String, val body: String, val status: String, val version: Long, val publishedAt: String?)
internal data class NoticePageData(val items: List<NoticeData>, val nextCursor: String?)
internal data class CategoryData(val id: String, val code: String, val name: String, val active: Boolean)
internal data class CategoryCatalogData(val version: Long, val items: List<CategoryData>, val otherSports: List<Pair<String, Long>>)
