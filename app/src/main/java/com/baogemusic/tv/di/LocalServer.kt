package com.baogemusic.tv.di

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.Locale

/**
 * Tiny in-app HTTP server that exposes mobile-friendly forms over the LAN so the user's phone
 * can push input into the TV (search keyword, custom-source script URL, or uploaded .js file).
 * The TV side advertises the URL via a QR dialog.
 *
 * Routes (GET = HTML form, POST = receive payload):
 *   GET  /search             - search input form
 *   POST /api/search         - field "q"    -> events.qrSearchKeyword
 *   GET  /script             - script-import form (URL + file upload)
 *   POST /api/script-url     - field "url"  -> events.qrScriptUrl
 *   POST /api/script-file    - multipart field "file" -> events.qrScriptText
 *   GET  /playlist-name      - playlist name input form (Chinese-friendly via phone IME)
 *   POST /api/playlist-name  - field "name" -> events.qrPlaylistName
 *   GET  /songlist-url       - songlist URL paste form (avoids TV-side IME entirely)
 *   POST /api/songlist-url   - field "url"  -> events.qrSonglistUrl
 *   GET  /font               - font upload form (.ttf/.otf/.woff2)
 *   POST /api/font-file      - multipart field "file" -> staged file -> events.qrFontFile
 */
class LocalServer private constructor(
    private val events: AppEvents,
    // Returns hot-search columns as a JSON string: [{"source":"kw","name":"酷我","words":[...]}].
    // Supplied by AppContainer; called on NanoHTTPD's worker thread (blocking is fine here).
    private val hotSearchJson: () -> String,
    // Where uploaded fonts are staged. NanoHTTPD deletes its multipart temp files as soon as the
    // response is written, so we copy the bytes out before replying.
    private val uploadDir: File,
    // 生成调试信息报告文件（设备信息 + 设置 + 崩溃日志 + logcat），供 /debug 扫码下载。
    private val debugReport: () -> File?,
    port: Int,
) : NanoHTTPD(port) {

    /** Port the server is bound to. */
    val boundPort: Int get() = listeningPort

    private fun tryStart(): Boolean = runCatching {
        start(SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "LocalServer up on port $boundPort")
        true
    }.getOrElse {
        Log.w(TAG, "port $listeningPort failed: ${it.message}")
        false
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.GET -> handleGet(session)
                Method.POST -> handlePost(session)
                else -> notFound()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "serve error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    private fun handleGet(session: IHTTPSession): Response = when (session.uri.trimEnd('/')) {
        "/api/hotsearch" -> json(runCatching { hotSearchJson() }.getOrDefault("[]"))
        "/search" -> html(SEARCH_HTML)
        "/script" -> html(SCRIPT_HTML)
        "/playlist-name" -> html(PLAYLIST_NAME_HTML)
        "/songlist-url" -> html(SONGLIST_URL_HTML)
        "/font" -> html(FONT_HTML)
        // 调试信息报告：下载为附件，手机扫码即可保存后回传，方便排查闪退。
        "/debug" -> {
            val file = runCatching { debugReport() }.getOrNull()
            if (file != null && file.exists()) download(file, "baoting-debug.txt")
            else newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "调试信息生成失败，请在电视上重试",
            )
        }
        "" -> html(INDEX_HTML)
        else -> notFound()
    }

    private fun handlePost(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files) // populates params for url-encoded; stores tmp paths for multipart
        val params = session.parameters
        return when (session.uri.trimEnd('/')) {
            "/api/search" -> {
                val q = params["q"]?.firstOrNull().orEmpty().trim()
                if (q.isNotEmpty()) events.postQrSearchKeyword(q)
                html(donePage("已发送到电视：$q"))
            }
            "/api/script-url" -> {
                val url = params["url"]?.firstOrNull().orEmpty().trim()
                if (url.isNotEmpty()) events.postQrScriptUrl(url)
                html(donePage("URL 已发送：$url"))
            }
            "/api/script-file" -> {
                val tmpPath = files["file"]
                val text = tmpPath?.let { runCatching { File(it).readText() }.getOrNull() }
                if (!text.isNullOrEmpty()) {
                    events.postQrScriptText(text)
                    html(donePage("已上传 ${text.length} 字符的脚本"))
                } else {
                    html(donePage("未收到文件"))
                }
            }
            "/api/playlist-name" -> {
                val name = params["name"]?.firstOrNull().orEmpty().trim()
                if (name.isNotEmpty()) events.postQrPlaylistName(name)
                html(donePage("已发送：$name"))
            }
            "/api/songlist-url" -> {
                val url = params["url"]?.firstOrNull().orEmpty().trim()
                if (url.isNotEmpty()) events.postQrSonglistUrl(url)
                html(donePage("已发送歌单链接"))
            }
            "/api/font-file" -> {
                val tmpPath = files["file"]
                val wanted = params["name"]?.firstOrNull()?.trim().orEmpty()
                val staged = tmpPath?.let { stageUpload(File(it), wanted) }
                if (staged != null) {
                    events.postQrFontFile(staged.absolutePath)
                    html(donePage("已上传字体：${staged.nameWithoutExtension}"))
                } else {
                    html(donePage("未收到字体文件"))
                }
            }
            else -> notFound()
        }
    }

    /**
     * Copy an uploaded font out of NanoHTTPD's temp file into [uploadDir] before the response is
     * written. Returns null when the file is missing/empty or fails the extension + size gate, so
     * the TV never even sees a hopeless file.
     */
    private fun stageUpload(tmp: File, wantedName: String): File? {
        if (!tmp.exists() || tmp.length() <= 0) return null
        val ext = wantedName.substringAfterLast('.', "").lowercase(Locale.US)
            .takeIf { it in FONT_EXTENSIONS } ?: "ttf"
        val base = wantedName.substringBeforeLast('.').ifBlank { "font" }
        val safe = base.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "font" }
        val target = File(uploadDir, "${System.currentTimeMillis()}-$safe.$ext")
        return runCatching {
            uploadDir.mkdirs()
            tmp.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > FONT_MAX_BYTES) {
                target.delete()
                return null
            }
            target
        }.getOrNull()
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    /** 以「附件下载」形式返回本地文件（手机点开链接直接保存）。 */
    private fun download(file: File, fileName: String): Response {
        val res = newFixedLengthResponse(
            Response.Status.OK,
            "text/plain; charset=utf-8",
            file.inputStream(),
            file.length(),
        )
        res.addHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
        res.addHeader("Cache-Control", "no-store")
        return res
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")

    companion object {
        private const val TAG = "LocalServer"

        /** 字体上传的白名单与大小上限，与 FontStore.FontImportRules 保持一致。 */
        private val FONT_EXTENSIONS = listOf("ttf", "otf", "woff2")
        private const val FONT_MAX_BYTES = 30L * 1024 * 1024

        /** Start the local server, trying a small range of ports in case 8765 is busy. */
        fun start(
            events: AppEvents,
            hotSearchJson: () -> String = { "[]" },
            uploadDir: File,
            debugReport: () -> File? = { null },
        ): LocalServer? {
            for (port in listOf(8765, 8766, 8767, 8768)) {
                val server = LocalServer(events, hotSearchJson, uploadDir, debugReport, port)
                if (server.tryStart()) return server
            }
            return null
        }

        private const val PAGE_CSS = """
            <style>
              body{margin:0;padding:24px;font-family:-apple-system,system-ui,sans-serif;
                   background:#0A0D14;color:#fff;}
              h2{margin:0 0 16px;font-weight:700;color:#4ADE80;}
              p{color:#a0a0b0;margin:0 0 18px;line-height:1.5;font-size:14px;}
              .card{background:#14171F;border-radius:12px;padding:18px;margin-bottom:14px;}
              input,textarea{width:100%;padding:13px;font-size:16px;border-radius:8px;
                             border:1px solid #2A2D38;background:#0A0D14;color:#fff;
                             box-sizing:border-box;margin-bottom:10px;}
              input[type=file]{padding:8px;}
              button{width:100%;padding:14px;background:#4ADE80;color:#0A0D14;border:0;
                     border-radius:8px;font-size:16px;font-weight:700;cursor:pointer;}
              .ok{color:#4ADE80;font-size:18px;font-weight:700;}
              a{color:#4ADE80;text-decoration:none;}
            </style>
        """

        private val INDEX_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>宝听 - 手机助手</title>$PAGE_CSS</head>
            <body>
              <h2>宝听</h2>
              <p>选择要在电视上做的事：</p>
              <div class="card"><a href="/search"><h2>🔍 搜索歌曲</h2></a></div>
              <div class="card"><a href="/script"><h2>📜 导入自定义音源</h2></a></div>
              <div class="card"><a href="/playlist-name"><h2>✏️ 输入歌单名</h2></a></div>
              <div class="card"><a href="/songlist-url"><h2>🔗 推送歌单链接</h2></a></div>
              <div class="card"><a href="/font"><h2>🔤 上传歌词字体</h2></a></div>
              <div class="card"><a href="/debug"><h2>🐞 下载调试信息</h2></a></div>
            </body></html>
        """.trimIndent()

        private val SEARCH_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>搜索 · 宝听</title>$PAGE_CSS
            <style>
              .toast{position:fixed;left:50%;top:24px;transform:translateX(-50%);
                     background:#4ADE80;color:#0A0D14;font-weight:700;font-size:15px;
                     padding:12px 22px;border-radius:24px;opacity:0;transition:opacity .25s;
                     pointer-events:none;z-index:9;box-shadow:0 6px 20px rgba(0,0,0,.4);}
              .toast.show{opacity:1;}
              .hotgrid{display:grid;grid-template-columns:1fr 1fr;gap:6px 16px;}
              .hotgrp{min-width:0;margin-top:10px;}
              .hottitle{font-weight:700;font-size:14px;margin:0 0 6px;}
              .hotword{display:flex;align-items:center;gap:8px;padding:8px 4px;
                       cursor:pointer;font-size:14px;color:#fff;border-radius:8px;}
              .hotword:active{background:#1c2029;}
              .hotword .w{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
              .rank{width:16px;flex:none;text-align:center;font-weight:700;font-size:12px;color:#666;}
              .rank.top{color:inherit;}
            </style></head>
            <body>
              <div id="toast" class="toast"></div>
              <h2>搜索歌曲</h2>
              <p>输入关键词点发送，电视会自动开始搜索，发送后可继续搜下一个。也可点下方热搜词。</p>
              <div class="card">
                <input id="q" placeholder="歌曲名 / 歌手 / 歌单" autofocus>
                <button onclick="send()">发送到电视</button>
              </div>
              <div id="hot"></div>
              <script>
                var TINT={kw:'#F26D4D',kg:'#1AB0F0',tx:'#33CC8C',wy:'#F24552'};
                function toast(msg){
                  var t=document.getElementById('toast');
                  t.textContent=msg; t.className='toast show';
                  setTimeout(function(){t.className='toast';},1600);
                }
                function send(){
                  var q=document.getElementById('q').value.trim();
                  if(!q) return;
                  var body='q='+encodeURIComponent(q);
                  fetch('/api/search',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body})
                    .then(function(){toast('已发送到电视：'+q);})
                    .catch(function(){toast('发送失败，请重试');});
                }
                function pick(w){
                  document.getElementById('q').value=w;
                  send();
                }
                document.getElementById('q').addEventListener('keydown',function(e){
                  if(e.key==='Enter') send();
                });
                fetch('/api/hotsearch').then(function(r){return r.json();}).then(function(cols){
                  var wrap=document.getElementById('hot');
                  if(!cols||!cols.length) return;
                  var h='<div class="card"><h2 style="margin-bottom:6px">热门搜索</h2><div class="hotgrid">';
                  cols.forEach(function(c){
                    if(!c.words||!c.words.length) return;
                    var tint=TINT[c.source]||'#4ADE80';
                    h+='<div class="hotgrp"><div class="hottitle" style="color:'+tint+'">'+c.name+'</div>';
                    c.words.forEach(function(w,i){
                      var rankStyle=i<3?'color:'+tint:'';
                      h+='<div class="hotword" onclick="pick(this.dataset.w)" data-w="'+w.replace(/"/g,'&quot;')+'">'
                        +'<span class="rank'+(i<3?' top':'')+'" style="'+rankStyle+'">'+(i+1)+'</span>'
                        +'<span class="w">'+w+'</span></div>';
                    });
                    h+='</div>';
                  });
                  h+='</div></div>';
                  wrap.innerHTML=h;
                });
              </script>
            </body></html>
        """.trimIndent()

        private val PLAYLIST_NAME_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>歌单名 · 宝听</title>$PAGE_CSS</head>
            <body>
              <h2>给歌单起个名字</h2>
              <p>用手机输入法（中英文都行），提交后名字会出现在电视的对话框里。</p>
              <form class="card" method="POST" action="/api/playlist-name">
                <input name="name" placeholder="例如：开车听的歌" maxlength="24" autofocus>
                <button>发送到电视</button>
              </form>
            </body></html>
        """.trimIndent()

        private val SONGLIST_URL_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>推送歌单 · 宝听</title>$PAGE_CSS</head>
            <body>
              <h2>推送歌单链接</h2>
              <p>从酷我 / 酷狗 / QQ 音乐 / 网易云分享一个歌单链接（或纯数字 ID），粘进来发送给电视，电视会自动识别并导入。</p>
              <form class="card" method="POST" action="/api/songlist-url">
                <textarea name="url" placeholder="例如 https://music.163.com/playlist?id=2034742057" rows="3" autofocus></textarea>
                <button>发送到电视</button>
              </form>
            </body></html>
        """.trimIndent()

        private val SCRIPT_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>自定义音源 · 宝听</title>$PAGE_CSS</head>
            <body>
              <h2>导入自定义音源</h2>
              <p>支持两种方式：粘贴脚本 URL，或直接上传 .js 文件。提交后电视会自动加载。</p>
              <form class="card" method="POST" action="/api/script-url">
                <input name="url" placeholder="脚本 URL（例如 https://.../source.js）">
                <button>发送 URL</button>
              </form>
              <form class="card" method="POST" action="/api/script-file" enctype="multipart/form-data">
                <input type="file" name="file">
                <button>上传脚本文件</button>
              </form>
            </body></html>
        """.trimIndent()

        private val FONT_HTML = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>上传字体 · 宝听</title>$PAGE_CSS</head>
            <body>
              <h2>上传歌词字体</h2>
              <p>选择手机里的字体文件发给电视，字幕会立刻换成这个字体。支持 .ttf / .otf / .woff2，单个不超过 30MB。
                 （Android 不能直接加载 woff2，如遇提示请先转换成 ttf / otf）</p>
              <form class="card" id="f" method="POST" action="/api/font-file" enctype="multipart/form-data">
                <input type="file" id="file" name="file" accept=".ttf,.otf,.woff2,font/ttf,font/otf,font/woff2">
                <input name="name" id="name" placeholder="字体名称（选文件后自动填）">
                <button>上传到电视</button>
              </form>
              <script>
                var f=document.getElementById('file'), n=document.getElementById('name');
                f.addEventListener('change',function(){
                  var v=(f.files&&f.files[0])?f.files[0].name:'';
                  var ok=/\.(ttf|otf|woff2)${'$'}/i.test(v);
                  if(!ok&&v){ alert('只支持 ttf / otf / woff2 字体文件'); f.value=''; n.value=''; return; }
                  n.value=v;
                });
                document.getElementById('f').addEventListener('submit',function(e){
                  if(!f.files||!f.files[0]){ alert('请先选择字体文件'); e.preventDefault(); }
                });
              </script>
            </body></html>
        """.trimIndent()

        private fun donePage(message: String): String = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>已发送 · 宝听</title>$PAGE_CSS</head>
            <body>
              <div class="card" style="text-align:center;padding:32px 18px;">
                <div class="ok">✓ ${escapeHtml(message)}</div>
                <p style="margin-top:18px;">可以回到电视查看结果。</p>
                <p><a href="/">返回首页</a></p>
              </div>
            </body></html>
        """.trimIndent()

        private fun escapeHtml(s: String): String = s
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
    }
}
