package com.gpproject.smartpetitiongenerator.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.gpproject.smartpetitiongenerator.domain.TemplateEngine
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel
import com.gpproject.smartpetitiongenerator.ui.viewmodel.PetitionViewModel.PreviewOrigin
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    navController: NavController,
    viewModel: PetitionViewModel,
    petitionId: Int?,
    openShareOnLaunch: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Current preview HTML for newly generated or manually created petitions.
    val previewHtml by viewModel.currentPreviewHtml

    // Controls whether the current preview can be saved as a reusable template.
    val canSaveAsTemplate by viewModel.canSaveCurrentPreviewAsTemplate

    // Tells where the current preview came from, such as AI assistant or blank editor.
    val previewOrigin by viewModel.currentPreviewOrigin

    // Used to force reloading a saved petition after editing it.
    var petitionRefreshTick by remember { mutableIntStateOf(0) }

    // Loads the HTML either from local history by ID or from the current preview state.
    val petitionState by produceState<String?>(
        initialValue = null,
        key1 = petitionId,
        key2 = previewHtml,
        key3 = petitionRefreshTick
    ) {
        value = if (petitionId != null && petitionId > 0) {
            val petition = viewModel.getPetitionById(petitionId)
            petition?.finalHtmlContent
        } else {
            previewHtml
        }
    }

    // Keeps a reference to the WebView so toolbar buttons can execute JavaScript commands.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Editor and save-related UI state.
    var isEditMode by remember { mutableStateOf(false) }
    var hasUnsavedEdits by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf(TextFieldValue("")) }

    // Template save dialog state.
    var showTemplateSaveDialog by remember { mutableStateOf(false) }
    var templateTitle by remember { mutableStateOf(TextFieldValue("")) }
    var isTemplateSaveInProgress by remember { mutableStateOf(false) }

    // Prevents automatic share/print from running more than once.
    var hasAutoShared by remember(petitionId, openShareOnLaunch) {
        mutableStateOf(false)
    }

    // Prevents unnecessary WebView reloads.
    var lastLoadedHtml by remember { mutableStateOf<String?>(null) }

    // Typography controls used in edit mode.
    var editorFontSizePt by remember { mutableStateOf(12f) }
    var editorLineHeight by remember { mutableStateOf(1.35f) }

    // Creates a blank A4 editor page if this is a new preview with no content.
    LaunchedEffect(petitionId, previewHtml) {
        if ((petitionId == null || petitionId <= 0) && previewHtml.isNullOrBlank()) {
            val blankEditorHtml = TemplateEngine.wrapContentInA4("<p><br/></p>")
            viewModel.updateCurrentPreviewHtml(blankEditorHtml)
        }
    }

    // Enables WebView HTML editing and keeps editing inside A4/page limits.
    val enableBoundedEditScript = """
        (function() {
            document.body.contentEditable = 'true';
            document.designMode = 'on';

            const page = document.querySelector('.a4-page') || document.body;
            const scroller = document.scrollingElement || document.documentElement || document.body;

            // Make the page scrollable while editing.
            document.documentElement.style.height = '100%';
            document.documentElement.style.overflowY = 'auto';
            document.documentElement.style.overflowX = 'auto';
            document.body.style.height = 'auto';
            document.body.style.minHeight = '100%';
            document.body.style.overflowY = 'auto';
            document.body.style.overflowX = 'auto';
            document.body.style.webkitOverflowScrolling = 'touch';
            page.style.overflow = 'visible';

            page.style.zoom = '1.0';
            page.style.transformOrigin = 'top center';

            // Input types that can increase document size.
            const canGrowInput = new Set([
                'insertText',
                'insertParagraph',
                'insertLineBreak',
                'insertFromPaste',
                'insertCompositionText'
            ]);

            // Checks whether the edited content exceeds the A4 page height.
            const isOverflowing = function() {
                return page.scrollHeight > page.clientHeight + 1;
            };

            // Prevents extremely large HTML content.
            const hardLimitReached = function() {
                const htmlSize = (page.innerHTML || '').length;
                return htmlSize >= 45000;
            };

            // Rolls back the latest input if it causes overflow or reaches the hard limit.
            let rollingBack = false;
            const rollbackIfNeeded = function() {
                if (rollingBack) return;
                if (!isOverflowing() && !hardLimitReached()) return;

                rollingBack = true;
                try { document.execCommand('undo'); } catch (e) {}
                rollingBack = false;
            };

            let caretActivatedByUser = false;
            let slightZoomApplied = false;
            let lastTapTs = 0;
            let lastTapX = null;
            let lastTapY = null;
            const rapidTapMs = 280;
            const rapidTapMaxDistancePx = 28;

            // Returns the current caret rectangle if available.
            const getCaretRect = function() {
                const sel = window.getSelection && window.getSelection();
                if (!sel || sel.rangeCount === 0) return null;

                const range = sel.getRangeAt(0).cloneRange();
                let rect = range.getBoundingClientRect();

                if (rect && (rect.width > 0 || rect.height > 0)) {
                    return rect;
                }

                const node = sel.anchorNode;
                const el = node && node.nodeType === 1
                    ? node
                    : (node && node.parentElement ? node.parentElement : null);

                return el && el.getBoundingClientRect ? el.getBoundingClientRect() : null;
            };

            // Keeps the caret visible when the keyboard opens or while the user edits text.
            const keepCaretVisible = function() {
                try {
                    if (!caretActivatedByUser) return;
                    if (shouldSkipCaretAutoScroll()) return;

                    const rect = getCaretRect();
                    if (!rect) return;

                    const vv = window.visualViewport;
                    const viewportTop = vv ? vv.offsetTop : 0;
                    const viewportH = vv ? vv.height : window.innerHeight;

                    const safeTop = viewportTop + 56;
                    const safeBottom = viewportTop + viewportH - 4;
                    const targetY = viewportTop + (viewportH * 0.42);
                    const caretCenterY = rect.top + (rect.height / 2);

                    let desiredDeltaY = 0;

                    if (rect.bottom > safeBottom) {
                        desiredDeltaY = rect.bottom - safeBottom;
                    } else if (rect.top < safeTop) {
                        desiredDeltaY = rect.top - safeTop;
                    } else {
                        const centeredDelta = caretCenterY - targetY;
                        if (Math.abs(centeredDelta) > 60) {
                            desiredDeltaY = centeredDelta;
                        }
                    }

                    if (Math.abs(desiredDeltaY) < 8) return;
                    scrollByWithinBounds(desiredDeltaY);
                } catch (e) {}
            };

            // Applies a small zoom around the caret for easier mobile editing.
            const applySlightZoom = function() {
                const rect = getCaretRect();

                if (rect) {
                    const originX = rect.left + (rect.width / 2) + (window.scrollX || 0);
                    const originY = rect.top + (rect.height / 2) + (window.scrollY || 0);
                    page.style.transformOrigin = originX + 'px ' + originY + 'px';
                }

                slightZoomApplied = true;
                page.style.zoom = '1.03';
            };

            // Resets the small editing zoom.
            const resetSlightZoom = function() {
                slightZoomApplied = false;
                page.style.zoom = '1.0';
                page.style.transformOrigin = 'top center';
            };

            const toggleRapidTapZoom = function() {
                if (slightZoomApplied) {
                    resetSlightZoom();
                } else {
                    applySlightZoom();
                }
            };

            // Detects a rapid tap at almost the same screen position.
            const isRapidTapAtSameSpot = function(e) {
                const now = Date.now();
                const x = e && typeof e.clientX === 'number' ? e.clientX : null;
                const y = e && typeof e.clientY === 'number' ? e.clientY : null;

                let sameSpot = false;

                if (x !== null && y !== null && lastTapX !== null && lastTapY !== null) {
                    const dx = x - lastTapX;
                    const dy = y - lastTapY;
                    sameSpot = ((dx * dx) + (dy * dy)) <=
                        (rapidTapMaxDistancePx * rapidTapMaxDistancePx);
                }

                const rapid = (now - lastTapTs) <= rapidTapMs;

                lastTapTs = now;
                if (x !== null && y !== null) {
                    lastTapX = x;
                    lastTapY = y;
                } else {
                    lastTapX = null;
                    lastTapY = null;
                }

                return rapid && sameSpot;
            };

            // Notifies Android that the document content has changed.
            const markDirty = function() {
                if (window.AndroidEditBridge && window.AndroidEditBridge.onContentChanged) {
                    window.AndroidEditBridge.onContentChanged();
                }
            };

            // Adds bottom padding while the keyboard is visible.
            const updateKeyboardInset = function(vv, keyboardVisible) {
                if (keyboardVisible) {
                    const estimated = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
                    const extraBottom = Math.max(estimated + 320, 420);

                    document.body.style.paddingBottom = extraBottom + 'px';
                    document.documentElement.style.paddingBottom = extraBottom + 'px';
                } else {
                    document.body.style.removeProperty('padding-bottom');
                    document.documentElement.style.removeProperty('padding-bottom');
                }
            };

            const getScroller = function() {
                return document.scrollingElement || document.documentElement || document.body;
            };

            // Scrolls only within valid document bounds.
            const scrollByWithinBounds = function(deltaY) {
                if (Math.abs(deltaY) < 1) return;

                const scroller = getScroller();
                const vv = window.visualViewport;
                const visibleHeight = vv ? vv.height : window.innerHeight;

                const currentY = scroller.scrollTop;
                const maxY = Math.max(0, scroller.scrollHeight - visibleHeight);
                const targetY = Math.max(0, Math.min(maxY, currentY + deltaY));

                if (Math.abs(targetY - currentY) < 1) return;
                scroller.scrollTo({ top: targetY, behavior: 'auto' });
            };

            const clampPageIntoVisibleRange = function() {
                // Intentionally left empty to avoid forcing the user's manual scroll.
            };

            let keyboardWasVisible = false;
            let userIsManuallyScrolling = false;
            let suppressCaretUntilTs = 0;

            const shouldSkipCaretAutoScroll = function() {
                return userIsManuallyScrolling || Date.now() < suppressCaretUntilTs;
            };

            // Handles viewport changes caused by the soft keyboard.
            const viewportResizeHandler = function() {
                try {
                    const vv = window.visualViewport;
                    if (!vv) return;

                    const keyboardVisible = vv.height < window.innerHeight - 120;
                    updateKeyboardInset(vv, keyboardVisible);

                    if (keyboardVisible && !keyboardWasVisible) {
                        setTimeout(keepCaretVisible, 0);
                        setTimeout(keepCaretVisible, 60);
                        setTimeout(keepCaretVisible, 140);
                    }

                    setTimeout(clampPageIntoVisibleRange, 0);

                    keyboardWasVisible = keyboardVisible;
                } catch (e) {}
            };

            // Touch handlers prevent auto-scroll from fighting with manual scrolling.
            const onTouchStart = function() {
                userIsManuallyScrolling = false;
            };

            const onTouchMove = function() {
                userIsManuallyScrolling = true;
                suppressCaretUntilTs = Date.now() + 500;
            };

            const onTouchEnd = function() {
                userIsManuallyScrolling = false;
                suppressCaretUntilTs = Date.now() + 350;
            };

            document.addEventListener('touchstart', onTouchStart, { passive: true });
            document.addEventListener('touchmove', onTouchMove, { passive: true });
            document.addEventListener('touchend', onTouchEnd, { passive: true });
            document.addEventListener('touchcancel', onTouchEnd, { passive: true });

            // Prevents new input when the content already overflows.
            document.onbeforeinput = function(e) {
                if (!e || !canGrowInput.has(e.inputType)) return;

                if (isOverflowing() || hardLimitReached()) {
                    e.preventDefault();
                }
            };

            // Finds the nearest editable block element around the caret.
            const findEditableBlock = function(node) {
                let block = node && node.nodeType === 1
                    ? node
                    : (node ? node.parentElement : null);

                while (block && block !== document.body) {
                    const tag = (block.tagName || '').toUpperCase();

                    if (['P', 'DIV', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(tag)) {
                        return block;
                    }

                    block = block.parentElement;
                }

                return null;
            };

            // Checks whether the caret is at the beginning of the current block.
            const isCaretAtBlockStart = function(sel, block) {
                if (!sel || !block || sel.rangeCount === 0 || !sel.isCollapsed) return false;

                const caretRange = sel.getRangeAt(0).cloneRange();
                const preRange = caretRange.cloneRange();

                preRange.selectNodeContents(block);
                preRange.setEnd(caretRange.startContainer, caretRange.startOffset);

                const beforeText = (preRange.toString() || '').replace(/\u00A0/g, ' ');
                return beforeText.length === 0;
            };

            // Removes one leading space from the current block.
            const trimOneLeadingSpace = function(block) {
                if (!block) return false;

                const html = block.innerHTML || '';
                const next = html
                    .replace(/^(&nbsp;|\u00A0| )/, '')
                    .replace(/^(<br\s*\/?>)(?:&nbsp;|\u00A0| )/, '$1');

                if (next !== html) {
                    block.innerHTML = next;
                    return true;
                }

                return false;
            };

            // Handles special keyboard behavior for Backspace and Enter.
            document.onkeydown = function(e) {
                if (!e) return;

                if (e.key === 'Backspace' || e.keyCode === 8) {
                    const sel = window.getSelection && window.getSelection();
                    const range = sel && sel.rangeCount > 0 ? sel.getRangeAt(0) : null;
                    const block = range ? findEditableBlock(range.startContainer) : null;

                    if (sel && block && isCaretAtBlockStart(sel, block)) {
                        const currentIndent = parseFloat(block.style.textIndent || '0');

                        if (isFinite(currentIndent) && currentIndent > 0) {
                            e.preventDefault();

                            const nextIndent = Math.max(0, currentIndent - 1);
                            block.style.textIndent = nextIndent > 0 ? (nextIndent + 'em') : '0';

                            markDirty();
                            setTimeout(keepCaretVisible, 0);
                            return;
                        }

                        if (trimOneLeadingSpace(block)) {
                            e.preventDefault();
                            markDirty();
                            setTimeout(keepCaretVisible, 0);
                            return;
                        }
                    }
                }

                if ((e.key === 'Enter' || e.keyCode === 13) &&
                    (isOverflowing() || hardLimitReached())) {
                    e.preventDefault();
                    return;
                }

                if (e.key === 'Enter' || e.keyCode === 13) {
                    e.preventDefault();

                    try {
                        document.execCommand('insertParagraph', false, null);
                    } catch (err) {
                        document.execCommand('insertLineBreak', false, null);
                    }

                    const selAfterEnter = window.getSelection && window.getSelection();
                    const rangeAfterEnter = selAfterEnter && selAfterEnter.rangeCount > 0
                        ? selAfterEnter.getRangeAt(0)
                        : null;

                    const blockAfterEnter = rangeAfterEnter
                        ? findEditableBlock(rangeAfterEnter.startContainer)
                        : null;

                    if (blockAfterEnter) {
                        blockAfterEnter.style.textIndent = '0';
                    }
                }

                setTimeout(keepCaretVisible, 0);
            };

            // Marks the content as dirty after each input.
            document.oninput = function() {
                rollbackIfNeeded();
                markDirty();
                setTimeout(keepCaretVisible, 0);
            };

            // Checks pasted content after it is inserted.
            document.onpaste = function() {
                setTimeout(function() {
                    rollbackIfNeeded();
                    keepCaretVisible();
                }, 0);
            };

            document.onselectionchange = function() {
                caretActivatedByUser = true;
                suppressCaretUntilTs = Date.now() + 120;
                setTimeout(keepCaretVisible, 0);
            };

            document.onfocusin = function() {
                caretActivatedByUser = true;
                setTimeout(keepCaretVisible, 0);
                setTimeout(keepCaretVisible, 80);
            };

            document.onclick = function(e) {
                caretActivatedByUser = true;
                suppressCaretUntilTs = Date.now() + 120;

                if (isRapidTapAtSameSpot(e)) {
                    toggleRapidTapZoom();
                    setTimeout(keepCaretVisible, 0);
                }
            };

            // Store handlers globally so they can be removed when edit mode is disabled.
            window.__keepCaretVisible = keepCaretVisible;
            window.__viewportResizeHandler = viewportResizeHandler;
            window.__touchStartHandler = onTouchStart;
            window.__touchMoveHandler = onTouchMove;
            window.__touchEndHandler = onTouchEnd;
            window.__scrollBoundsClamp = clampPageIntoVisibleRange;

            // Cleanup helper called before leaving edit mode.
            window.__editModeCleanup = function() {
                page.style.zoom = '1.0';
                page.style.transformOrigin = 'top center';
                window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
            };

            if (window.visualViewport) {
                window.visualViewport.addEventListener('resize', viewportResizeHandler);
            }
        })();
    """.trimIndent()

    // Disables WebView editing and returns the edited inner HTML to Kotlin.
    val disableBoundedEditScript = """
        (function() {
            const page = document.querySelector('.a4-page') || document.body;

            // Remove event handlers registered in edit mode.
            document.onbeforeinput = null;
            document.onkeydown = null;
            document.oninput = null;
            document.onpaste = null;
            document.onselectionchange = null;
            document.onfocusin = null;
            document.onclick = null;

            if (window.__touchStartHandler) {
                document.removeEventListener('touchstart', window.__touchStartHandler);
            }

            if (window.__touchMoveHandler) {
                document.removeEventListener('touchmove', window.__touchMoveHandler);
            }

            if (window.__touchEndHandler) {
                document.removeEventListener('touchend', window.__touchEndHandler);
                document.removeEventListener('touchcancel', window.__touchEndHandler);
            }

            if (window.visualViewport && window.__viewportResizeHandler) {
                window.visualViewport.removeEventListener('resize', window.__viewportResizeHandler);
            }

            // Clear global helper references.
            window.__keepCaretVisible = null;
            window.__viewportResizeHandler = null;
            window.__touchStartHandler = null;
            window.__touchMoveHandler = null;
            window.__touchEndHandler = null;
            window.__scrollBoundsClamp = null;

            // Reset temporary editing styles.
            page.style.zoom = '1.0';
            page.style.transformOrigin = 'top center';
            page.style.removeProperty('overflow');

            document.body.style.removeProperty('padding-bottom');
            document.documentElement.style.removeProperty('padding-bottom');

            document.documentElement.style.removeProperty('height');
            document.documentElement.style.removeProperty('overflow-y');
            document.documentElement.style.removeProperty('overflow-x');

            document.body.style.removeProperty('height');
            document.body.style.removeProperty('min-height');
            document.body.style.removeProperty('overflow-y');
            document.body.style.removeProperty('overflow-x');
            document.body.style.removeProperty('-webkit-overflow-scrolling');

            // Disable edit mode.
            document.body.contentEditable = 'false';
            document.designMode = 'off';

            // Return only the A4 page content.
            const cleanContent = page ? page.innerHTML : document.body.innerHTML;
            return cleanContent;
        })();
    """.trimIndent()

    // Opens the Android print dialog and generates PDF from the WebView content.
    fun printAsPdf(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function(){
                try {
                    const page = document.querySelector('.a4-page') || document.body;
                    page.style.removeProperty('zoom');
                    page.style.removeProperty('overflow');
                    document.body.style.removeProperty('overflow');
                    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
                } catch(e) {}
            })();
            """.trimIndent(),
            null
        )

        webView.postDelayed({
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Dilekce_Belgesi")
            val jobName = "Dilekce_${System.currentTimeMillis()}"

            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(jobName, printAdapter, attrs)
        }, 40)
    }

    // Forces WebView to recalculate layout after page load.
    fun forceReflow(webView: WebView) {
        webView.post {
            webView.requestLayout()
            webView.invalidate()

            webView.evaluateJavascript(
                """
                (function(){
                    try {
                        void document.body.offsetHeight;
                        window.dispatchEvent(new Event('resize'));
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun undoLastEdit() {
        if (!isEditMode) return

        webViewRef?.evaluateJavascript(
            """
        (function() {
            try {
                document.execCommand('undo', false, null);

                if (window.AndroidEditBridge && window.AndroidEditBridge.onContentChanged) {
                    window.AndroidEditBridge.onContentChanged();
                }

                if (window.__keepCaretVisible) {
                    setTimeout(window.__keepCaretVisible, 0);
                }

                return "OK";
            } catch (e) {
                return "ERROR";
            }
        })();
        """.trimIndent(),
            null
        )
    }

    // Calculates a safe initial zoom percentage for the A4 WebView preview.
    fun calculateInitialScalePercent(webView: WebView): Int {
        val widthPx = if (webView.width > 0) {
            webView.width
        } else {
            webView.resources.displayMetrics.widthPixels
        }

        val widthDp = widthPx / density.density
        val viewportWidth = 834f
        val horizontalPadding = 20f
        val target = ((widthDp - horizontalPadding) / viewportWidth) * 100f

        return target.toInt().coerceIn(25, 100)
    }

    // Runs a formatting command inside the WebView editor.
    fun runEditorCommand(command: String) {
        if (!isEditMode) return

        webViewRef?.evaluateJavascript(
            """
            (function() {
                try {
                    document.execCommand('$command', false, null);

                    const sel = window.getSelection && window.getSelection();

                    // If left alignment is applied, reset paragraph indentation.
                    // NOTE: use '$command' here, not '${'$'}command'.
                    if ('$command' === 'justifyLeft' && sel && sel.rangeCount > 0) {
                        const rangeForIndentReset = sel.getRangeAt(0);
                        const startNode = rangeForIndentReset.startContainer;

                        let block = startNode && startNode.nodeType === 1
                            ? startNode
                            : (startNode ? startNode.parentElement : null);

                        while (block && block !== document.body) {
                            const tag = (block.tagName || '').toUpperCase();

                            if (['P', 'DIV', 'LI', 'BLOCKQUOTE', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6'].includes(tag)) {
                                break;
                            }

                            block = block.parentElement;
                        }

                        if (block && block !== document.body) {
                            block.style.textIndent = '0';
                        }
                    }

                    // Collapse the selection to the end after formatting.
                    if (sel && sel.rangeCount > 0) {
                        const range = sel.getRangeAt(0);
                        range.collapse(false);
                        sel.removeAllRanges();
                        sel.addRange(range);
                    }

                    // Notify Android that content changed.
                    if (window.AndroidEditBridge && window.AndroidEditBridge.onContentChanged) {
                        window.AndroidEditBridge.onContentChanged();
                    }
                } catch (e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    // Inserts a tab-like indentation at the current caret position.
    fun autoIndentCurrentLine() {
        if (!isEditMode) return

        webViewRef?.evaluateJavascript(
            """
            (function() {
                try {
                    const sel = window.getSelection && window.getSelection();
                    if (!sel || sel.rangeCount === 0) return;

                    const inserted = document.execCommand('insertText', false, '\t');

                    if (!inserted) {
                        document.execCommand('insertHTML', false, '&nbsp;&nbsp;&nbsp;&nbsp;');
                    }

                    if (window.AndroidEditBridge && window.AndroidEditBridge.onContentChanged) {
                        window.AndroidEditBridge.onContentChanged();
                    }
                } catch (e) {}
            })();
            """.trimIndent(),
            null
        )
    }

    // Reads current font size and line height from the WebView editor.
    fun syncEditorTypographyState() {
        webViewRef?.evaluateJavascript(
            """
            (function() {
                const page = document.querySelector('.a4-page') || document.body;
                const styles = window.getComputedStyle(page);

                const fontSizePx = parseFloat(styles.fontSize || '16');
                const lineHeightRaw = styles.lineHeight || '';

                let lineHeight = parseFloat(lineHeightRaw);

                if (!isFinite(lineHeight)) {
                    const fallback = parseFloat(styles.fontSize || '16');
                    lineHeight = fallback > 0 ? (fontSizePx / fallback) : 1.35;
                } else {
                    lineHeight = fontSizePx > 0 ? (lineHeight / fontSizePx) : 1.35;
                }

                const fontSizePt = fontSizePx * 0.75;

                return JSON.stringify({
                    fontSizePt: Number(fontSizePt.toFixed(2)),
                    lineHeight: Number(lineHeight.toFixed(2))
                });
            })();
            """.trimIndent()
        ) { typographyJson ->
            val parsed = runCatching {
                Gson().fromJson(typographyJson, String::class.java)
            }.getOrNull()

            if (!parsed.isNullOrBlank()) {
                val obj = runCatching {
                    Gson().fromJson(parsed, Map::class.java)
                }.getOrNull()

                val font = (obj?.get("fontSizePt") as? Number)?.toFloat()
                val line = (obj?.get("lineHeight") as? Number)?.toFloat()

                if (font != null) editorFontSizePt = font.coerceIn(8f, 24f)
                if (line != null) editorLineHeight = line.coerceIn(1f, 2.5f)
            }
        }
    }

    // Applies font size and line height to the A4 page and OCR flow content.
    fun applyEditorTypography(fontSizePt: Float = editorFontSizePt, lineHeight: Float = editorLineHeight) {
        if (!isEditMode) return

        val oldFont = editorFontSizePt
        val oldLine = editorLineHeight

        val clampedFont = fontSizePt.coerceIn(8f, 24f)
        val clampedLine = lineHeight.coerceIn(1f, 2.5f)

        webViewRef?.evaluateJavascript(
            """
        (function() {
            try {
                const page = document.querySelector('.a4-page') || document.body;
                const ocrFlow = page.querySelector('.ocr-flow');

                const oldPageFont = page.style.fontSize;
                const oldPageLine = page.style.lineHeight;

                const fontValue = '${"%.2f".format(java.util.Locale.US, clampedFont)}pt';
                const lineValue = '${"%.2f".format(java.util.Locale.US, clampedLine)}';

                page.style.fontSize = fontValue;
                page.style.lineHeight = lineValue;

                if (ocrFlow) {
                    ocrFlow.style.fontSize = fontValue;
                    ocrFlow.style.lineHeight = lineValue;
                    ocrFlow.style.fontFamily = 'inherit';

                    const lines = ocrFlow.querySelectorAll('.ocr-line');
                    lines.forEach(function(line) {
                        line.style.fontSize = 'inherit';
                        line.style.lineHeight = 'inherit';
                        line.style.fontFamily = 'inherit';
                    });
                }

                const overflowing = page.scrollHeight > page.clientHeight + 1;

                if (overflowing) {
                    page.style.fontSize = oldPageFont;
                    page.style.lineHeight = oldPageLine;
                    return "OVERFLOW";
                }

                if (window.AndroidEditBridge && window.AndroidEditBridge.onContentChanged) {
                    window.AndroidEditBridge.onContentChanged();
                }

                return "OK";
            } catch (e) {
                return "ERROR";
            }
        })();
        """.trimIndent()
        ) { result ->
            if (result.contains("OK")) {
                editorFontSizePt = clampedFont
                editorLineHeight = clampedLine
            } else {
                editorFontSizePt = oldFont
                editorLineHeight = oldLine
            }
        }
    }

    fun finishEditingAndSave() {
        val webView = webViewRef ?: return

        webView.evaluateJavascript(disableBoundedEditScript) { htmlResult ->
            val cleanInnerHtml = try {
                Gson().fromJson(htmlResult, String::class.java)
            } catch (_: Exception) {
                htmlResult
            }

            val finalWrappedHtml = TemplateEngine.wrapContentInA4(cleanInnerHtml)

            if (petitionId != null && petitionId > 0) {
                viewModel.saveEditedPetition(petitionId, finalWrappedHtml) {
                    petitionRefreshTick++
                    lastLoadedHtml = finalWrappedHtml
                    hasUnsavedEdits = false
                    isEditMode = false
                }
            } else {
                viewModel.updateCurrentPreviewHtml(finalWrappedHtml)
                lastLoadedHtml = finalWrappedHtml
                hasUnsavedEdits = false
                isEditMode = false
            }
        }
    }

    // Handles Android back button from this screen.
    BackHandler {
        if (!navController.popBackStack()) {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Automatically opens the PDF/share flow when this screen is launched in share mode.
    LaunchedEffect(openShareOnLaunch, petitionState, webViewRef) {
        if (openShareOnLaunch && !hasAutoShared && petitionState != null && webViewRef != null) {
            printAsPdf(webViewRef!!)
            hasAutoShared = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Dilekçe Önizleme")
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!navController.popBackStack()) {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                        }
                    },
                    actions = {
                        // Shows template-save action only for AI-generated new previews.
                        if (
                            canSaveAsTemplate &&
                            petitionId == null &&
                            previewOrigin == PreviewOrigin.AI_ASSISTANT
                        ) {
                            IconButton(
                                onClick = {
                                    isTemplateSaveInProgress = false
                                    showTemplateSaveDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = "Şablon olarak kaydet"
                                )
                            }
                        }

                        // New previews can be saved to local history.
                        if (petitionId == null || petitionId <= 0) {
                            TextButton(
                                onClick = {
                                    showSaveDialog = true
                                }
                            ) {
                                Text("KAYDET")
                            }
                        }

                        // Navigates to the home screen.
                        IconButton(
                            onClick = {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Ana sayfa")
                        }
                    }
                )

                // Editor toolbar shown only while edit mode is active.
                if (isEditMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(onClick = { runEditorCommand("bold") }) {
                                Icon(Icons.Default.FormatBold, contentDescription = "Kalın")
                            }

                            FilledTonalIconButton(onClick = { runEditorCommand("justifyLeft") }) {
                                Icon(Icons.Default.FormatAlignLeft, contentDescription = "Sola yasla")
                            }

                            FilledTonalIconButton(onClick = { runEditorCommand("justifyCenter") }) {
                                Icon(Icons.Default.FormatAlignCenter, contentDescription = "Ortala")
                            }

                            FilledTonalIconButton(onClick = { runEditorCommand("justifyRight") }) {
                                Icon(Icons.Default.FormatAlignRight, contentDescription = "Sağa yasla")
                            }

                            FilledTonalIconButton(onClick = { runEditorCommand("justifyFull") }) {
                                Icon(Icons.Default.FormatAlignJustify, contentDescription = "İki yana yasla")
                            }

                            FilledTonalIconButton(onClick = { autoIndentCurrentLine() }) {
                                Icon(
                                    Icons.Default.FormatIndentIncrease,
                                    contentDescription = "İmleç satırını otomatik girintile"
                                )
                            }

                            FilledTonalIconButton(onClick = { undoLastEdit() }) {
                                Icon(Icons.Default.Undo, contentDescription = "Geri al")
                            }
                        }

                        // Typography controls.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Yazı: ${"%.1f".format(editorFontSizePt)}pt")

                                FilledTonalIconButton(
                                    onClick = {
                                        applyEditorTypography(
                                            editorFontSizePt - 0.5f,
                                            editorLineHeight
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Yazı boyutunu azalt")
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        applyEditorTypography(
                                            editorFontSizePt + 0.5f,
                                            editorLineHeight
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Yazı boyutunu arttır")
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("Satır: ${"%.2f".format(editorLineHeight)}")

                                FilledTonalIconButton(
                                    onClick = {
                                        applyEditorTypography(
                                            editorFontSizePt,
                                            editorLineHeight - 0.05f
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Satır aralığını azalt")
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        applyEditorTypography(
                                            editorFontSizePt,
                                            editorLineHeight + 0.05f
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Satır aralığını arttır")
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Toggles editor mode. When leaving edit mode, saves edited HTML.
                    Button(
                        onClick = {
                            if (!isEditMode) {
                                isEditMode = true
                                hasUnsavedEdits = true
                                webViewRef?.evaluateJavascript(enableBoundedEditScript, null)
                                syncEditorTypographyState()
                            } else {
                                finishEditingAndSave()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEditMode) Color(0xFF4CAF50) else Color(0xFF1565C0)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEditMode) "DÜZENLEMEYİ KAYDET" else "DÜZENLE")
                    }

                    // Opens Android print/share PDF flow.
                    Button(
                        onClick = {
                            webViewRef?.let { printAsPdf(it) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        modifier = Modifier.weight(1f),
                        enabled = !hasUnsavedEdits
                    ) {
                        Text("PDF / PAYLAŞ")
                    }
                }
            }
        }
    ) { padding ->

        // Dialog for saving a new preview to local history.
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSaveDialog = false
                },
                title = {
                    Text("Geçmişe Kaydet")
                },
                text = {
                    OutlinedTextField(
                        value = saveTitle,
                        onValueChange = { saveTitle = it },
                        label = { Text("Kayıt adı") },
                        placeholder = { Text("Örn: Trafik Cezası İtirazı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val entered = saveTitle.text

                            viewModel.saveCurrentPreviewToHistory(entered) { savedId ->
                                if (savedId != null) {
                                    viewModel.clearCurrentPreview()
                                    showSaveDialog = false

                                    navController.navigate("preview_screen/$savedId") {
                                        popUpTo("preview_screen/new") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Kaydet")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSaveDialog = false
                        }
                    ) {
                        Text("İptal")
                    }
                }
            )
        }

        // Dialog for saving an AI-generated preview as a reusable template.
        if (showTemplateSaveDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isTemplateSaveInProgress) {
                        showTemplateSaveDialog = false
                    }
                },
                title = {
                    Text("Şablon Olarak Kaydet")
                },
                text = {
                    OutlinedTextField(
                        value = templateTitle,
                        onValueChange = { templateTitle = it },
                        label = { Text("Şablon adı") },
                        placeholder = { Text("Örn: Trafik İtirazı - Genel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !isTemplateSaveInProgress,
                        onClick = {
                            if (isTemplateSaveInProgress) return@TextButton

                            isTemplateSaveInProgress = true
                            showTemplateSaveDialog = false

                            val entered = templateTitle.text

                            viewModel.saveCurrentPreviewAsTemplate(
                                templateName = entered,
                                currentPreviewHtml = petitionState,
                                preferTemplateSource = petitionId == null || petitionId <= 0
                            ) { saved ->
                                isTemplateSaveInProgress = false

                                if (!saved) {
                                    showTemplateSaveDialog = true
                                }
                            }
                        }
                    ) {
                        Text("Kaydet")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isTemplateSaveInProgress,
                        onClick = {
                            showTemplateSaveDialog = false
                        }
                    ) {
                        Text("İptal")
                    }
                }
            )
        }

        // Shows the petition HTML inside a WebView.
        if (petitionState != null) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        // JavaScript is required for editing and toolbar commands.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        isHorizontalScrollBarEnabled = true

                        // Required for A4 layout scaling.
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        // Prevent Android system font scaling from breaking the A4 layout.
                        settings.textZoom = 100

                        // Enable pinch zoom without showing zoom control buttons.
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.setSupportZoom(true)

                        // Bridge used by JavaScript to notify Compose about unsaved edits.
                        addJavascriptInterface(
                            object {
                                @android.webkit.JavascriptInterface
                                fun onContentChanged() {
                                    this@apply.post {
                                        hasUnsavedEdits = true
                                    }
                                }
                            },
                            "AndroidEditBridge"
                        )

                        // Matches the preview background used by TemplateEngine.
                        setBackgroundColor(android.graphics.Color.parseColor("#525659"))

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)

                                // Fixes initial layout/centering issues after the page loads.
                                forceReflow(view)
                            }
                        }

                        webViewRef = this

                        val initialScale = calculateInitialScalePercent(this)
                        setInitialScale(initialScale)

                        // Load the generated or saved petition HTML.
                        loadDataWithBaseURL(
                            null,
                            petitionState!!,
                            "text/html",
                            "UTF-8",
                            null
                        )

                        lastLoadedHtml = petitionState
                    }
                },
                update = { wv ->
                    if (wv != webViewRef) {
                        webViewRef = wv
                    }

                    // Reload only when the HTML content actually changes.
                    if (petitionState != null && petitionState != lastLoadedHtml) {
                        wv.loadDataWithBaseURL(
                            null,
                            petitionState!!,
                            "text/html",
                            "UTF-8",
                            null
                        )

                        lastLoadedHtml = petitionState
                    }
                },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            // Loading state while the petition HTML is being prepared.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}