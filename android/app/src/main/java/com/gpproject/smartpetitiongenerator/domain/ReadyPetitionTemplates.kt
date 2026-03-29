package com.gpproject.smartpetitiongenerator.domain

import com.gpproject.smartpetitiongenerator.data.remote.InputField

data class ReadyPetitionTemplate(
    val id: String,
    val title: String,
    val category: String,
    val templateHtml: String,
    val requiredFields: List<InputField>,
    val givenParams: Map<String, String> = emptyMap(),
    val isAiGenerated: Boolean = false
)

object ReadyPetitionTemplates {

    private fun field(key: String, label: String, type: String = "text") =
        InputField(key = key, label = label, type = type)

    private fun template(
        id: String,
        title: String,
        category: String,
        templateHtml: String,
        requiredFields: List<InputField>,
        givenParams: Map<String, String> = emptyMap(),
        isAiGenerated: Boolean = false
    ) = ReadyPetitionTemplate(
        id = id,
        title = title,
        category = category,
        templateHtml = templateHtml,
        requiredFields = requiredFields,
        givenParams = givenParams,
        isAiGenerated = isAiGenerated
    )


    private fun footerBlock() = """

        <div class="footer-wrapper">
            <div class="signature-container">
                <div class="signature-contact-left">
                    <div>T.C. Kimlik No: {{TCKN}}</div>
                    <div>Telefon: {{TELEFON}}</div>
                    <div>Adres: {{ADRES}}</div>
                    <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                </div>
                <div class="signature-meta-right">
                    <div class="date-text">{{BUGUN_TARIH}}</div>
                    <div class="name-text">{{AD_SOYAD}}</div>
                </div>
            </div>
            <div class="clear"></div>
        </div>
    """.trimIndent()

    val templates: List<ReadyPetitionTemplate> = listOf(
        // A4 Hazır Şablon (3)
        template(
            id = "huk_06",
            title = "Trafik İdari Para Cezasına İtiraz",
            category = "Hukuk/İdari",
            templateHtml = """
                <div class="recipient-header">{{MAKAMIN_ADI}} NÖBETÇİ SULH CEZA HAKİMLİĞİ'NE</div>

                <div><b>İTİRAZ EDEN:</b> {{AD_SOYAD}} - T.C. Kimlik No: {{TCKN}} - Adres: {{ADRES}}</div>
                <div style="margin-top:2.5mm;"><b>KARŞI TARAF:</b> {{KARSI_TARAF}}</div>
                <div style="margin-top:2.5mm;"><b>TUTANAK TARİHİ / NO:</b> {{TUTANAK_TARIHI}} / {{TUTANAK_NO}}</div>
                <div style="margin-top:2.5mm;"><b>TEBLİĞ TARİHİ:</b> {{TEBLIGAT_TARIHI}}</div>

                <div class="subject-line" style="margin-top:4mm;"><b>KONU:</b> {{KONU_KISA_OZETI}}</div>

                <div class="section-title">AÇIKLAMALAR:</div>
                <div class="body-content">
                    <p>{{PLAKA}} plakalı araca, 2918 sayılı Kanun'un {{KANUN_MADDELERI}} maddesi kapsamında {{TUTANAK_TARIHI}} tarihinde {{CEZA_TUTARI}} TL idari para cezası uygulanmıştır.</p>
                    <p>{{OLAY_ACIKLAMASI}}</p>
                    <p>{{EK_GEREKCE}}</p>
                    <p>Belirtilen nedenlerle ceza işlemi hukuka ve usule aykırıdır.</p>
                </div>

                <p>
                    <span class="section-title">HUKUKİ DELİLLER: </span>
                    {{TUTANAK_TARIHI}} Tarihi {{TUTANAK_NO}} Numaralı Trafik Cezası Tutanağı, Bilirkişi İncelemesi ve sair hukuki deliller
                </p>
                </br>
                <p>
                    <span class="section-title">HUKUKİ SEBEPLER: </span>
                    Karayolları Trafik Kanunu ve sair hukuki sebepler
                </p>
                
                <div class="section-title">SONUÇ VE TALEP</div>
                <div class="body-content">
                    <p>Açıklanan nedenlerle, tarafıma uygulanan trafik idari para cezasının iptaline karar verilmesini saygıyla arz ve talep ederim. ({{DILEKCE_TARIHI}})</p>
                </div>

                <div class="footer-wrapper" style="margin-top:6mm;">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right" style="text-align:center;">
                            <div class="name-text">{{AD_SOYAD}}</div>
                            <div style="height:8mm;"></div>
                        </div>
                    </div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("MAKAMIN_ADI", "... NÖBETÇİ SULH CEZA HAKİMLİĞİ’NE"),
                field("KARSI_TARAF", "Karşı taraf kurum ve adresi"),
                field("TUTANAK_TARIHI", "Tutanak tarihi", "date"),
                field("TUTANAK_NO", "Tutanak numarası"),
                field("TEBLIGAT_TARIHI", "Tebliğ tarihi", "date"),
                field("KONU_KISA_OZETI", "Konu satırı"),
                field("PLAKA", "Araç plakası"),
                field("KANUN_MADDELERI", "İlgili kanun maddeleri (örn: 47/1-A ve 47/1-B)"),
                field("CEZA_TUTARI", "Ceza tutarı (TL)"),
                field("OLAY_ACIKLAMASI", "Somut olay açıklaması"),
                field("EK_GEREKCE", "Ek gerekçe paragrafı"),
                field("DELIL_VE_BELGELER", "Hukuki deliller"),
                field("HUKUKI_DAYANAK", "Hukuki sebepler"),
                field("DILEKCE_TARIHI", "Dilekçe tarihi", "date"),
                field("EKLER_LISTESI", "Ekler (her satıra bir ek)")
            )
        )
    )
}