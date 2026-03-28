package com.gpproject.smartpetitiongenerator.domain

object TemplateEngine {

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

        /* A4 gerçek ölçü */
        .a4-page {
            width: 210mm !important;
            height: 297mm !important;
            background: #fff;
            color: #000;
            padding: 25mm !important; /* Tüm akışlarda aynı kenar boşluğu */
            box-shadow: 0 4px 14px rgba(0,0,0,0.45);
            display: flex;
            flex-direction: column;
            overflow: hidden;

            font-family: "Times New Roman", Times, serif;
            font-size: 12pt;
            line-height: 1.35;
            text-align: justify;
        }
        
        /* Tüm alt elemanlar üst kapsayıcıdan aynı tipografiyi miras alsın */
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
            margin-top: 2.2em; /* dilekçe bitiminden yaklaşık 2 satır sonra */
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
            margin-top: 1.6em; /* adres bloğundan sonra 1 satır boşluk */
        }

        .attachments-left .contact-footer {
            margin-top: 0;
            font-size: 10.5pt;
            line-height: 1.5;
            text-align: left;
        }

        /* PRINT: Önizleme ile birebir aynı olsun */
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

    fun wrapContentInA4(rawHtmlContent: String): String {
        val hasA4 = rawHtmlContent.contains("class='a4-page'") ||
                rawHtmlContent.contains("class=\"a4-page\"")

        val bodyContent = if (hasA4) rawHtmlContent else "<div class=\"a4-page\">$rawHtmlContent</div>"

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