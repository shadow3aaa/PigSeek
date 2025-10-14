package com.shadow3aaa.pigseek

/**
 * 计算两个字符串之间的 Jaro-Winkler 相似度。
 * 返回值在 0.0 (完全不同) 到 1.0 (完全相同) 之间。
 */
fun jaroWinklerSimilarity(s1: String, s2: String, p: Double = 0.1): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0

    val s1Len = s1.length
    val s2Len = s2.length
    val matchDistance = maxOf(s1Len, s2Len) / 2 - 1

    val s1Matches = BooleanArray(s1Len)
    val s2Matches = BooleanArray(s2Len)

    var matches = 0
    for (i in 0 until s1Len) {
        val start = maxOf(0, i - matchDistance)
        val end = minOf(i + matchDistance + 1, s2Len)

        for (j in start until end) {
            if (!s2Matches[j] && s1[i] == s2[j]) {
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
    }

    if (matches == 0) return 0.0

    var t = 0.0
    var k = 0
    for (i in 0 until s1Len) {
        if (s1Matches[i]) {
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) t++
            k++
        }
    }

    val transpositions = t / 2.0
    val jaroSimilarity = (matches.toDouble() / s1Len + matches.toDouble() / s2Len + (matches - transpositions) / matches) / 3.0

    // Winkler 修正（对共同前缀加分）
    var l = 0
    val limit = minOf(4, minOf(s1Len, s2Len))
    while (l < limit && s1[l] == s2[l]) {
        l++
    }

    return jaroSimilarity + l * p * (1 - jaroSimilarity)
}
