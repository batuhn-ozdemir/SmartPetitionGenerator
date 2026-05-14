package com.gpproject.smartpetitiongenerator.domain

object TemplateEngine {

    // Shared CSS used to render petition content as an A4 document.
    private const val CSS_STYLE = """
        <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }

        html, body { width: 100%; height: 100%; }

        body {
            background: #525659;
            font-family: "Times New Roman", Times, serif;
            display: flex;
            justify-content: center;
            align-items: flex-start;
            padding: 16px;
        }

        /* Real A4 page size */
        .a4-page {
            width: 210mm !important;
            height: 297mm !important;
            background: #fff;
            color: #000;
            padding: 25mm !important;
            box-shadow: 0 4px 14px rgba(0,0,0,0.45);
            display: flex;
            flex-direction: column;
            overflow: hidden;

            font-family: "Times New Roman", Times, serif;
            font-size: 12pt;
            line-height: 1.35;
            text-align: justify;
        }
        
        /* Keep typography consistent inside the A4 page */
        .a4-page * {
            font-family: inherit;
            font-size: inherit;
            line-height: inherit;
        }

        .institution-name {
            text-align: center;
            font-weight: 700;
            letter-spacing: 0.3px;
            margin-bottom: 1.5mm;
        }

        .recipient-header {
            text-align: center;
            font-weight: 700;
            margin-bottom: 6mm;
        }

        .subject-line {
            margin-bottom: 5mm;
        }

        .section-title {
            font-weight: 700;
            text-transform: none;
            margin-top: 4mm;
            margin-bottom: 1.5mm;
            text-align: left;
        }

        .body-content p {
            text-indent: 12mm;
            margin-bottom: 4mm;
        }

        .footer-wrapper {
            margin-top: 2.2em;
            padding-top: 0;
        }

        .signature-container {
            width: 100%;
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 12mm;
        }

        .signature-meta-right {
            margin-left: auto;
            text-align: right;
            flex: 0 0 auto;
            white-space: nowrap;
        }

        .signature-contact-left {
            text-align: left;
            flex: 1 1 auto;
            min-width: 0;
        }

        .date-text { margin-bottom: 6mm; }
        .name-text { font-weight: 700; }
        .clear { clear: both; }

        .attachments-left {
            margin-top: 1.6em;
        }

        .attachments-left .contact-footer {
            margin-top: 0;
            font-size: 10.5pt;
            line-height: 1.5;
            text-align: left;
        }

        /* Print/PDF settings should match the preview layout */
        @page {
            size: A4;
            margin: 0;
        }

        @media print {
            body {
                background: #fff !important;
                padding: 0 !important;
                margin: 0 !important;
                display: block !important;
            }

            .a4-page {
                box-shadow: none !important;
                width: 210mm !important;
                height: 297mm !important;
                overflow: hidden !important;
                margin: 0 !important;
            }
        }
        </style>
    """

    // Wraps raw petition HTML inside a complete A4 HTML document.
    fun wrapContentInA4(rawHtmlContent: String): String {
        // Avoid wrapping the content twice if it already contains an A4 container.
        val hasA4 = rawHtmlContent.contains("class='a4-page'") ||
                rawHtmlContent.contains("class=\"a4-page\"")

        val bodyContent = if (hasA4) {
            rawHtmlContent
        } else {
            "<div class=\"a4-page\">$rawHtmlContent</div>"
        }

        // Return a full HTML document that can be rendered in WebView or converted to PDF.
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=834, initial-scale=1.0, user-scalable=yes">
                $CSS_STYLE
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }
}