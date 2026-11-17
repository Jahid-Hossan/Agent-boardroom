package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.AgentEntity
import com.example.data.InAppBrowserStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

class InAppWebViewManager(private val appContext: Context) {

    private val webViews = mutableMapOf<String, WebView>()
    private val _statuses = MutableStateFlow<Map<String, InAppBrowserStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, InAppBrowserStatus>> = _statuses.asStateFlow()

    private val knownAuthHosts = listOf(
        "muse.ai",
        "gemini.google.com",
        "chatgpt.com",
        "chat.openai.com",
        "grok.com",
        "x.com",
        "claude.ai",
        "meta.ai"
    )

    fun isKnownAuthUrl(rawUrl: String): Boolean {
        val host = extractHostname(rawUrl)
        if (host.isEmpty()) return false
        return knownAuthHosts.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun ensureWebView(agent: AgentEntity, forceNavigateUrl: String? = null): WebView =
        withContext(Dispatchers.Main) {
            val existing = webViews[agent.id]
            val desiredUrl = normalizeHttpUrl(forceNavigateUrl ?: agent.customUrl.ifBlank { "https://muse.ai" })

            if (existing != null) {
                if (!forceNavigateUrl.isNullOrBlank()) {
                    existing.loadUrl(desiredUrl)
                }
                return@withContext existing
            }

            val wv = WebView(appContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        updateStatus(agent.id) {
                            it.copy(title = title.orEmpty(), open = true)
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        updateStatus(agent.id) {
                            it.copy(
                                open = true,
                                url = url ?: it.url,
                                isLoading = true
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        CookieManager.getInstance().flush()
                        updateStatus(agent.id) {
                            it.copy(
                                open = true,
                                url = url ?: it.url,
                                title = view?.title ?: it.title,
                                isLoading = false
                            )
                        }
                        view?.let { inspectDomState(agent.id, it) }
                    }
                }
            }

            webViews[agent.id] = wv
            updateStatus(agent.id) {
                it.copy(
                    open = true,
                    url = desiredUrl,
                    title = agent.name,
                    isLoading = true
                )
            }
            if (desiredUrl.isNotEmpty()) {
                wv.loadUrl(desiredUrl)
            }
            wv
        }

    private fun updateStatus(agentId: String, transform: (InAppBrowserStatus) -> InAppBrowserStatus) {
        _statuses.update { map ->
            val current = map[agentId] ?: InAppBrowserStatus(agentId = agentId)
            map + (agentId to transform(current))
        }
    }

    suspend fun refreshStatus(agentId: String): InAppBrowserStatus? = withContext(Dispatchers.Main) {
        val wv = webViews[agentId] ?: return@withContext _statuses.value[agentId]
        inspectDomStateSuspend(agentId, wv)
    }

    private fun inspectDomState(agentId: String, wv: WebView) {
        wv.evaluateJavascript(INSPECT_STATE_JS) { rawJson ->
            parseAndApplyInspectResult(agentId, rawJson)
        }
    }

    private suspend fun inspectDomStateSuspend(agentId: String, wv: WebView): InAppBrowserStatus =
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(INSPECT_STATE_JS) { rawJson ->
                val updated = parseAndApplyInspectResult(agentId, rawJson)
                if (cont.isActive) cont.resume(updated)
            }
        }

    private fun parseAndApplyInspectResult(agentId: String, rawJson: String?): InAppBrowserStatus {
        val current = _statuses.value[agentId] ?: InAppBrowserStatus(agentId = agentId, open = true)
        val updated = try {
            val unquoted = unquoteJsResult(rawJson)
            if (unquoted.isBlank() || unquoted == "null") {
                current
            } else {
                val json = JSONObject(unquoted)
                val ready = json.optBoolean("ready", false)
                val needsLogin = json.optBoolean("needsLogin", false)
                current.copy(
                    open = true,
                    url = json.optString("url").ifBlank { current.url },
                    title = json.optString("title").ifBlank { current.title },
                    ready = ready,
                    needsLogin = needsLogin,
                    hasSavedSession = current.hasSavedSession || (ready && !needsLogin),
                    isLoading = false
                )
            }
        } catch (_: Exception) {
            current
        }
        _statuses.update { it + (agentId to updated) }
        return updated
    }

    suspend fun navigate(agent: AgentEntity, url: String) = withContext(Dispatchers.Main) {
        val normalized = normalizeHttpUrl(url)
        val wv = ensureWebView(agent)
        if (normalized.isNotEmpty()) {
            wv.loadUrl(normalized)
        }
    }

    suspend fun goBack(agentId: String) = withContext(Dispatchers.Main) {
        val wv = webViews[agentId] ?: return@withContext
        if (wv.canGoBack()) wv.goBack()
    }

    suspend fun reload(agentId: String) = withContext(Dispatchers.Main) {
        webViews[agentId]?.reload()
    }

    suspend fun closeSession(agentId: String) = withContext(Dispatchers.Main) {
        val wv = webViews.remove(agentId)
        wv?.stopLoading()
        wv?.destroy()
        CookieManager.getInstance().flush()
        updateStatus(agentId) {
            it.copy(open = false, ready = false, needsLogin = false, isLoading = false)
        }
    }

    suspend fun injectQuickText(agentId: String, text: String) = withContext(Dispatchers.Main) {
        val wv = webViews[agentId] ?: return@withContext
        val escaped = JSONObject.quote(text)
        val js = """
            (function(val) {
              var el = document.activeElement;
              if (!el || el === document.body) {
                el = document.querySelector('input:not([type="hidden"]), textarea, [contenteditable="true"]');
              }
              if (!el) return;
              el.focus();
              var tag = el.tagName ? el.tagName.toLowerCase() : '';
              if (tag === 'input' || tag === 'textarea') {
                var proto = tag === 'textarea' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                var setter = Object.getOwnPropertyDescriptor(proto, 'value');
                if (setter && setter.set) setter.set.call(el, val);
                else el.value = val;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
              } else {
                document.execCommand('insertText', false, val);
              }
            })($escaped);
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    suspend fun pressEnter(agentId: String) = withContext(Dispatchers.Main) {
        val wv = webViews[agentId] ?: return@withContext
        val js = """
            (function() {
              var el = document.activeElement || document.body;
              ['keydown','keypress','keyup'].forEach(function(type) {
                el.dispatchEvent(new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
              });
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    suspend fun sendPrompt(agent: AgentEntity, promptText: String): String {
        val status = _statuses.value[agent.id]
        if ((status == null || !status.open) && isKnownAuthUrl(agent.customUrl)) {
            return "**${agent.name} is not signed in yet.** Tap **Browser** on **${agent.name}** to open and sign into `${extractHostname(agent.customUrl).ifEmpty { agent.customUrl }}` inside the app."
        }

        return withContext(Dispatchers.Main) {
            val wv = ensureWebView(agent)
            val escapedPrompt = JSONObject.quote(promptText)
            val startJs = buildRunPromptScript(escapedPrompt)

            evaluateSuspend(wv, startJs)

            val deadline = System.currentTimeMillis() + 27_000L
            while (System.currentTimeMillis() < deadline) {
                delay(280)
                val pollRaw = evaluateSuspend(
                    wv,
                    "(function(){ return window.__mbPromptDone ? JSON.stringify(window.__mbPromptResult) : ''; })()"
                )
                val unquoted = unquoteJsResult(pollRaw)
                if (unquoted.isNotEmpty() && unquoted != "null") {
                    val obj = runCatching { JSONObject(unquoted) }.getOrNull()
                    if (obj != null) {
                        inspectDomState(agent.id, wv)
                        return@withContext if (obj.optBoolean("success", false)) {
                            obj.optString("reply").ifBlank { "No reply captured." }
                        } else {
                            val err = obj.optString("error").ifBlank {
                                "Open Browser on ${agent.name} and verify you are signed in."
                            }
                            "**${agent.name} needs login in the In-App Browser.**\n\n$err"
                        }
                    }
                }
            }

            "**${agent.name} timed out.** Verify the session is signed in inside the **In-App Browser**."
        }
    }

    private suspend fun evaluateSuspend(wv: WebView, script: String): String =
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(script) { result ->
                if (cont.isActive) cont.resume(result ?: "")
            }
        }

    fun destroyAll() {
        webViews.values.forEach { wv ->
            runCatching {
                wv.stopLoading()
                wv.destroy()
            }
        }
        webViews.clear()
    }

    private fun unquoteJsResult(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        val trimmed = raw.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return try {
                JSONObject("{\"v\":$trimmed}").getString("v")
            } catch (_: Exception) {
                trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    companion object {
        private val INSPECT_STATE_JS = """
            (function() {
              try {
                function isVisible(el) {
                  if (!el) return false;
                  var rect = el.getBoundingClientRect();
                  var style = getComputedStyle(el);
                  return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
                }
                var inputSelectors = [
                  'rich-textarea .ql-editor[contenteditable="true"]',
                  '#prompt-textarea',
                  'div.ProseMirror[contenteditable="true"]',
                  'textarea:not([disabled]):not([readonly])',
                  '[contenteditable="true"][role="textbox"]',
                  '[contenteditable="true"]',
                  'div[role="textbox"]'
                ];
                var hasChatInput = inputSelectors.some(function(sel) {
                  return Array.from(document.querySelectorAll(sel)).some(isVisible);
                });
                var hasPasswordOrOtp = Array.from(
                  document.querySelectorAll('input[type="password"], input[autocomplete="one-time-code"], input[name*="otp" i]')
                ).some(isVisible);
                var hasLoginBtn = Array.from(document.querySelectorAll('button, a')).some(function(el) {
                  var t = (el.innerText || '').trim().toLowerCase();
                  return isVisible(el) && (t === 'log in' || t === 'sign in' || t === 'sign in to gemini' || t === 'continue with google');
                });
                var hrefLower = location.href.toLowerCase();
                var isLoginUrl = hrefLower.includes('login') || hrefLower.includes('signin') || hrefLower.includes('accounts.google.com') || hrefLower.includes('auth');
                return JSON.stringify({
                  ready: hasChatInput && !hasPasswordOrOtp,
                  needsLogin: hasPasswordOrOtp || (!hasChatInput && (hasLoginBtn || isLoginUrl)),
                  url: location.href,
                  title: document.title || location.hostname
                });
              } catch (e) {
                return JSON.stringify({ ready: false, needsLogin: false, url: location.href, title: document.title || '' });
              }
            })();
        """.trimIndent()

        private fun buildRunPromptScript(quotedPrompt: String): String = """
            (function(promptText) {
              window.__mbPromptDone = false;
              window.__mbPromptResult = null;
              var sleep = function(ms) { return new Promise(function(r) { setTimeout(r, ms); }); };
              var host = location.hostname.toLowerCase();

              function isVisible(el) {
                if (!el) return false;
                var rect = el.getBoundingClientRect();
                var style = getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
              }

              function findInputElement() {
                var selectorsBySite = [];
                if (host.includes('gemini.google.com')) {
                  selectorsBySite.push('rich-textarea .ql-editor[contenteditable="true"]', 'rich-textarea [contenteditable="true"]', 'div[role="textbox"]');
                } else if (host.includes('chatgpt.com') || host.includes('openai.com')) {
                  selectorsBySite.push('#prompt-textarea', 'div.ProseMirror[contenteditable="true"]', 'textarea[name="prompt-textarea"]');
                } else if (host.includes('grok.com') || host.includes('x.com')) {
                  selectorsBySite.push('textarea[aria-label*="Ask" i]', 'div.ProseMirror[contenteditable="true"]', 'textarea');
                } else if (host.includes('claude.ai')) {
                  selectorsBySite.push('div[contenteditable="true"].ProseMirror', 'fieldset [contenteditable="true"]');
                } else if (host.includes('muse.ai') || host.includes('meta.ai')) {
                  selectorsBySite.push('textarea', '[contenteditable="true"][role="textbox"]', 'div[role="textbox"]');
                }
                var generic = [
                  'textarea:not([disabled]):not([readonly])',
                  '[contenteditable="true"][role="textbox"]',
                  '[contenteditable="true"]',
                  'div[role="textbox"]',
                  'input[type="text"]:not([type="hidden"])'
                ];
                var all = selectorsBySite.concat(generic);
                for (var i = 0; i < all.length; i++) {
                  var list = Array.from(document.querySelectorAll(all[i]));
                  var vis = list.find(isVisible);
                  if (vis) return vis;
                }
                return null;
              }

              function isStillGenerating() {
                var stopSelectors = [
                  'button[data-testid="stop-button"]',
                  'button[aria-label*="Stop" i]',
                  'button[title*="Stop" i]'
                ];
                return stopSelectors.some(function(sel) { return Array.from(document.querySelectorAll(sel)).some(isVisible); });
              }

              function getResponseTexts() {
                var selectors = [];
                if (host.includes('gemini.google.com')) {
                  selectors = ['model-response message-content', 'message-content .markdown', '.model-response-text'];
                } else if (host.includes('chatgpt.com') || host.includes('openai.com')) {
                  selectors = ['[data-message-author-role="assistant"] .markdown', '[data-message-author-role="assistant"]'];
                } else if (host.includes('grok.com') || host.includes('x.com')) {
                  selectors = ['.message-bubble .markdown', '[data-testid*="assistant" i]', '.response-content-markdown', '.markdown', 'article'];
                } else if (host.includes('claude.ai')) {
                  selectors = ['[data-testid="assistant-message"]', '.font-claude-response', '[class*="assistant"] .markdown'];
                } else {
                  selectors = [
                    '[data-message-author-role="assistant"]',
                    '[class*="assistant" i]:not(footer *):not(nav *):not(header *)',
                    '[class*="response" i]:not(footer *):not(nav *):not(button)',
                    '[class*="message" i]:not(footer *):not(nav *):not([class*="footer" i] *)',
                    '[class*="bubble" i]',
                    '[data-testid*="message" i]',
                    '[role="log"] > div',
                    'article'
                  ];
                }
                var noise = ['© meta', 'privacy policy', 'terms of service', 'cookie policy', 'log in', 'sign in', 'sign up'];
                var results = [];
                for (var s = 0; s < selectors.length; s++) {
                  var nodes = document.querySelectorAll(selectors[s]);
                  if (nodes.length > 0) {
                    for (var n = 0; n < nodes.length; n++) {
                      var txt = (nodes[n].innerText || '').trim();
                      if (!txt || txt === promptText) continue;
                      var lower = txt.toLowerCase();
                      if (txt.length < 48 && noise.some(function(w) { return lower.includes(w); })) continue;
                      results.push(txt);
                    }
                    if (results.length > 0) break;
                  }
                }
                return results;
              }

              function setInputValue(el, text) {
                el.focus();
                var tag = el.tagName.toLowerCase();
                if (tag === 'textarea' || tag === 'input') {
                  var proto = tag === 'textarea' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                  var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (desc && desc.set) desc.set.call(el, text);
                  else el.value = text;
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                } else {
                  var sel = window.getSelection();
                  var range = document.createRange();
                  range.selectNodeContents(el);
                  sel.removeAllRanges();
                  sel.addRange(range);
                  var inserted = document.execCommand('insertText', false, text);
                  if (!inserted || !el.innerText.includes(text.slice(0, 12))) {
                    el.textContent = text;
                  }
                  el.dispatchEvent(new InputEvent('input', { bubbles: true, data: text, inputType: 'insertText' }));
                }
              }

              function clickSendButton(inputEl) {
                var buttonSelectors = [
                  'button[data-testid="send-button"]',
                  'button[aria-label*="Send" i]',
                  'button[data-testid*="send" i]',
                  'button[title*="Send" i]',
                  'button.send-button',
                  'button[type="submit"]'
                ];
                for (var i = 0; i < buttonSelectors.length; i++) {
                  var btn = Array.from(document.querySelectorAll(buttonSelectors[i])).find(function(b) {
                    return isVisible(b) && !b.disabled && b.getAttribute('aria-disabled') !== 'true';
                  });
                  if (btn) {
                    btn.click();
                    return true;
                  }
                }
                ['keydown', 'keypress', 'keyup'].forEach(function(type) {
                  inputEl.dispatchEvent(new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                });
                return false;
              }

              function hasLoginWallNow() {
                var hasPassOrOtp = Array.from(
                  document.querySelectorAll('input[type="password"], input[autocomplete="one-time-code"], input[name*="otp" i]')
                ).some(isVisible);
                if (hasPassOrOtp) return true;
                var hrefLower = location.href.toLowerCase();
                if (hrefLower.includes('accounts.google.com') || hrefLower.includes('/login') || hrefLower.includes('/signin')) {
                  return true;
                }
                return Array.from(document.querySelectorAll('[role="dialog"]')).some(function(dlg) {
                  if (!isVisible(dlg)) return false;
                  var t = (dlg.innerText || '').toLowerCase();
                  return t.includes('log in') || t.includes('sign in') || t.includes('create an account');
                });
              }

              (async function() {
                try {
                  if (hasLoginWallNow()) {
                    window.__mbPromptResult = { success: false, needsLogin: true, error: 'Not signed in yet on ' + location.hostname + '.' };
                    window.__mbPromptDone = true;
                    return;
                  }
                  var inputEl = findInputElement();
                  if (!inputEl) {
                    window.__mbPromptResult = { success: false, needsLogin: true, error: 'Chat input not found on ' + location.hostname + '. Please sign in first.' };
                    window.__mbPromptDone = true;
                    return;
                  }
                  var beforeTexts = getResponseTexts();
                  var beforeCount = beforeTexts.length;
                  var beforeLast = beforeCount > 0 ? beforeTexts[beforeCount - 1] : '';

                  setInputValue(inputEl, promptText);
                  await sleep(80);
                  clickSendButton(inputEl);

                  var latestText = '';
                  var stableRounds = 0;
                  var start = Date.now();
                  while (Date.now() - start < 24000) {
                    await sleep(220);
                    var current = getResponseTexts();
                    if (current.length === 0) {
                      if (Date.now() - start > 3500 && hasLoginWallNow()) {
                        window.__mbPromptResult = { success: false, needsLogin: true, error: location.hostname + ' requires sign-in.' };
                        window.__mbPromptDone = true;
                        return;
                      }
                      continue;
                    }
                    var candidate = current[current.length - 1];
                    var hasNew = current.length > beforeCount || (candidate && candidate !== beforeLast && candidate !== promptText);
                    if (!hasNew) continue;
                    if (candidate === latestText && candidate.length > 0 && !isStillGenerating()) {
                      stableRounds += 1;
                      if (stableRounds >= 2) {
                        window.__mbPromptResult = { success: true, reply: candidate };
                        window.__mbPromptDone = true;
                        return;
                      }
                    } else {
                      latestText = candidate;
                      stableRounds = 0;
                    }
                  }
                  if (latestText) {
                    window.__mbPromptResult = { success: true, reply: latestText };
                  } else {
                    window.__mbPromptResult = { success: false, error: 'Timed out waiting for a reply from ' + location.hostname + '.' };
                  }
                } catch (err) {
                  window.__mbPromptResult = { success: false, error: String(err) };
                } finally {
                  window.__mbPromptDone = true;
                }
              })();
            })($quotedPrompt);
        """.trimIndent()
    }
}
