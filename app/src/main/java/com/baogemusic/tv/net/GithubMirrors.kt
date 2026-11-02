package com.baogemusic.tv.net

/**
 * GitHub 文件镜像。大陆直连 `raw.githubusercontent.com` / `github.com` 基本不通，
 * 但 jsDelivr 这类 CDN 是通的，所以这里把一条 GitHub 链接展开成多个候选地址依次尝试。
 *
 * 两类镜像：
 *  1. **jsDelivr**（`https://cdn.jsdelivr.net/gh/owner/repo@ref/path`）—— CDN 改写路径，
 *     速度最稳，实测 7/7 源全部 200，优先级最高。
 *  2. **前缀代理**（`https://gh-proxy.com/https://github.com/...`）—— 原样拼前缀，
 *     覆盖 jsDelivr 不认的写法（比如带查询串、release 附件）。
 *
 * 只处理指向 **仓库内文件** 的链接；release 附件（`releases/download/...`）不走 jsDelivr，
 * 因为 jsDelivr 只服务仓库里已提交的文件。
 */
object GithubMirrors {

    /** jsDelivr 主站，也是我们要加的通用镜像源。 */
    const val JSDELIVR = "https://cdn.jsdelivr.net"

    /** 前缀代理，按可用性排序（取不到就落到下一个）。 */
    val PREFIX_PROXIES = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.net/",
        "https://gh.ddlc.top/",
    )

    /**
     * 把一条 GitHub 链接展开成按优先级排序的候选地址（原地址永远排第一，
     * 万一哪天直连又通了不用改代码）。
     */
    fun candidates(rawUrl: String): List<String> {
        val url = rawUrl.trim()
        if (url.isEmpty()) return emptyList()

        val out = linkedSetOf(url)

        jsDelivrFor(url)?.let { out += it }

        // 前缀代理：只对 github / raw.githubusercontent 的链接有意义
        if (url.contains("github.com") || url.contains("raw.githubusercontent.com")) {
            PREFIX_PROXIES.forEach { out += it + url }
        }
        return out.toList()
    }

    /**
     * GitHub 仓库内文件 → jsDelivr 的 `gh` 路径。识别不出来（release 附件、
     * 别的域名）返回 null。
     */
    fun jsDelivrFor(url: String): String? {
        val patterns = listOf(
            // https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}
            Regex("""^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$"""),
            // https://github.com/{owner}/{repo}/raw/{ref}/{path}
            Regex("""^https?://github\.com/([^/]+)/([^/]+)/raw/([^/]+)/(.+)$"""),
            // https://github.com/{owner}/{repo}/blob/{ref}/{path}
            Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$"""),
        )
        for (p in patterns) {
            val m = p.find(url) ?: continue
            val (owner, repo, ref, path) = m.destructured
            // jsDelivr 路径里的文件名不能带查询串/锚点
            val cleanPath = path.substringBefore('?').substringBefore('#')
            if (cleanPath.isBlank()) continue
            return "$JSDELIVR/gh/$owner/$repo@$ref/$cleanPath"
        }
        return null
    }
}
