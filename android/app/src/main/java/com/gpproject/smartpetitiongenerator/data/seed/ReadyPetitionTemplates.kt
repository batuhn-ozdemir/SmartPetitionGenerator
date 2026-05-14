package com.gpproject.smartpetitiongenerator.data.seed

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
        ),
        template(
            id = "ogr_05",
            title = "Üniversite Mazeret Sınavı Başvurusu",
            category = "Eğitim",
            templateHtml = """
                <div class="institution-name">T.C.</div>
                <div class="institution-name">{{KURUM_ADI}}</div>
                <div class="recipient-header">{{MAKAM_ADI}}'NA</div>
                <div class="subject-line"><b>Konu:</b> Mazeret sınavı talebi</div>

                <div class="body-content">
                    <p>{{OGRENCI_NO}} numaralı, {{BOLUM_SINIF}} öğrencisi olarak, {{DERS_ADI}} dersinin {{SINAV_TURU}} sınavına, {{MAZERET_NEDENI}} nedeniyle katılamadım.</p>
                    <p>Mazeretimi gösterir belgeler dilekçem ekinde sunulmuştur. İlgili mevzuat çerçevesinde mazeretimin değerlendirilerek, tarafıma mazeret sınavına girme hakkı verilmesini arz ederim.</p>
                    <p>Gereğini bilgilerinize saygılarımla sunarım.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("KURUM_ADI", "Üniversite/Okul adı"),
                field("MAKAM_ADI", "Verilecek makam adı"),
                field("BOLUM_SINIF", "Bölüm / Sınıf"),
                field("DERS_ADI", "Dersin adı"),
                field("SINAV_TURU", "Sınav türü ve tarihi (vize/final/ara sınav)"),
                field("OGRENCI_NO", "Öğrenci No", "number"),
                field("MAZERET_NEDENI", "Mazeret nedeni (sağlık/ailevi/resmi görev vb.)"),
                field("EKLER_LISTESI", "Ekler (her satıra bir ek)")
            )
        ),
        template(
            id = "fin_08",
            title = "Banka Masraf/Komisyon İadesi Talebi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="recipient-header">{{BANKA_ADI}} GENEL MÜDÜRLÜĞÜ’NE / {{SUBE_ADI}} ŞUBESİ’NE</div>
                <div class="subject-line"><b>Konu:</b> Banka masraf/komisyon bedelinin iadesi talebi</div>

                <div class="body-content">
                    <p>Bankanız nezdinde bulunan {{HESAP_BILGISI}} ile ilişkili hesabımdan/kartımdan, {{ISLEM_TARIHI}} tarihinde “{{KESILEN_UCRET_ADI}}” açıklamasıyla {{TUTAR}} TL masraf/komisyon tahsil edilmiştir.</p>
                    <p>Söz konusu tahsilatın tarafıma açık, yeterli ve anlaşılabilir şekilde bildirilmediğini ve ilgili kesintinin hukuki dayanağının tarafıma sunulmadığını düşünmekteyim. Bu nedenle, hesabımdan/kartımdan tahsil edilen {{TUTAR}} TL bedelin incelenerek tarafıma iade edilmesini talep ediyorum.</p>
                    <p>Gerekmesi halinde, kesintiye ilişkin işlem dekontu ve hesap hareketleri tarafımca sunulacaktır. Yapılacak inceleme sonucunun yazılı olarak veya aşağıda belirttiğim iletişim bilgilerim üzerinden tarafıma bildirilmesini arz ederim.</p>
                    <p>Saygılarımla.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div>Telefon: {{TELEFON}}</div>
                            <div>E-posta: {{EPOSTA}}</div>
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
            """.trimIndent(),
            requiredFields = listOf(
                field("BANKA_ADI", "Banka adı"),
                field("SUBE_ADI", "Şube adı"),
                field("HESAP_BILGISI", "Hesap no / müşteri no / kart son 4 hane"),
                field("ISLEM_TARIHI", "Kesinti tarihi", "date"),
                field("KESILEN_UCRET_ADI", "Kesilen ücretin adı"),
                field("TUTAR", "Kesilen tutar (TL)", "number"),
                field("EPOSTA", "E-posta"),
                field("EKLER_LISTESI", "Ekler (her satıra bir ek)")
            )
        ),

        // Eğitim (4)
        template(
            id = "ogr_01",
            title = "Transkript Belgesi Talebi",
            category = "Eğitim",
            templateHtml = """
                <div class="institution-name">{{UNIVERSITE_ADI}} REKTÖRLÜĞÜ’NE</div>
                <div class="recipient-header">(Öğrenci İşleri Daire Başkanlığı’na)</div>

                <div class="body-content">
                    <p>Üniversiteniz {{FAKULTE_BIRIM_ADI}} Fakültesi / Meslek Yüksekokulu / Enstitüsü, {{BOLUM_PROGRAM_ADI}} bölümü, {{OGRENCI_NO}} numaralı öğrencisiyim.</p>
                    <p>Eğitim hayatım süresince almış olduğum dersleri ve not durumumu gösteren resmî transkript belgesinin tarafıma verilmesini arz ederim.</p>
                    <p>Gereğini saygılarımla bilgilerinize sunarım.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div>Öğrenci No: {{OGRENCI_NO}}</div>
                            <div>Bölüm / Program: {{BOLUM_PROGRAM_ADI}}</div>
                            <div>İletişim Numarası: {{TELEFON}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("UNIVERSITE_ADI", "Üniversite adı"),
                field("FAKULTE_BIRIM_ADI", "... Fakültesi / MYO / Enstitü adı"),
                field("BOLUM_PROGRAM_ADI", "Bölüm / Program adı"),
                field("OGRENCI_NO", "Öğrenci No", "number"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "ogr_02",
            title = "Öğrenci Belgesi Talebi",
            category = "Eğitim",
            templateHtml = """
                <div class="institution-name">{{UNIVERSITE_ADI}} REKTÖRLÜĞÜ’NE</div>
                <div class="recipient-header">(Öğrenci İşleri Daire Başkanlığı’na)</div>

                <div class="body-content">
                    <p>Üniversiteniz {{FAKULTE_BIRIM_ADI}} Fakültesi / Meslek Yüksekokulu / Enstitüsü, {{BOLUM_PROGRAM_ADI}} bölümü, {{OGRENCI_NO}} numaralı öğrencisiyim.</p>
                    <p>Hâlen öğrenciniz olduğuma dair öğrenci belgesinin tarafıma verilmesini arz ederim.</p>
                    <p>Gereğini saygılarımla bilgilerinize sunarım.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div>Öğrenci No: {{OGRENCI_NO}}</div>
                            <div>Bölüm / Program: {{BOLUM_PROGRAM_ADI}}</div>
                            <div>İletişim Numarası: {{TELEFON}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("UNIVERSITE_ADI", "Üniversite adı"),
                field("FAKULTE_BIRIM_ADI", "... Fakültesi / MYO / Enstitü adı"),
                field("BOLUM_PROGRAM_ADI", "Bölüm / Program adı"),
                field("OGRENCI_NO", "Öğrenci No", "number"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "ogr_03",
            title = "Kayıt Dondurma Talebi",
            category = "Eğitim",
            templateHtml = """
                <div class="institution-name">{{UNIVERSITE_ADI}} REKTÖRLÜĞÜ’NE</div>
                <div class="recipient-header">{{MAKAM_ADI}}’na</div>

                <div class="body-content">
                    <p>Üniversiteniz {{FAKULTE_BIRIM_ADI}} Fakültesi / Meslek Yüksekokulu / Enstitüsü, {{BOLUM_PROGRAM_ADI}} bölümü, {{OGRENCI_NO}} numaralı öğrencisiyim.</p>
                    <p>{{MAZERET_NEDENI}} nedeniyle, {{EGITIM_OGRETIM_YILI}} eğitim-öğretim yılı {{EGITIM_OGRETIM_YARIYILI}} yarıyılı / {{EGITIM_OGRETIM_YILI_BITIS}} eğitim-öğretim yılı boyunca kaydımın dondurulması hususunda gereğini arz ederim.</p>
                    <p>Gereğini saygılarımla bilgilerinize sunarım.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div>Öğrenci No: {{OGRENCI_NO}}</div>
                            <div>Bölüm / Program: {{BOLUM_PROGRAM_ADI}}</div>
                            <div>İletişim Numarası: {{TELEFON}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("UNIVERSITE_ADI", "Üniversite adı"),
                field("MAKAM_ADI", "Makam adı"),
                field("FAKULTE_BIRIM_ADI", "... Fakültesi / MYO / Enstitü adı"),
                field("BOLUM_PROGRAM_ADI", "Bölüm / Program adı"),
                field("MAZERET_NEDENI", "Bölüm / Program adı"),
                field("EGITIM_OGRETIM_YILI", "../.. yılından"),
                field("EGITIM_OGRETIM_YARIYILI", "... yaryılından"),
                field("EGITIM_OGRETIM_YILI_BITIS", "Bitiş yılı"),
                field("OGRENCI_NO", "Öğrenci No", "number"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "ogr_04",
            title = "Yurttan Kayıt Sildirme ve Güvence Bedeli Talebi",
            category = "Eğitim",
            templateHtml = """
                
                <div class="date-text" style="text-align:right; margin-top:2mm;">{{BUGUN_TARIH}}</div>
                <div class="recipient-header" style="margin-top:3mm;">{{YURT_ADI}} MÜDÜRLÜĞÜ’NE</div>
                <div class="subject-line"><b>KONU:</b> Yurttan Kayıt Sildirme ve Güvence Bedeli (Depozito) İadesi.</div>

                <div class="body-content">
                    <p>Yurdunuzun {{BLOK_NO}} Blok, {{ODA_NO}} numaralı odasında barınmakta olan öğrencisiyim.</p>
                    <p>{{AYRILIS_TARIHI}} tarihi itibarıyla {{NEDEN}} nedeniyle yurttan ayrılıyorum. Yurtla ilişiğimin kesilmesini; odayı eksiksiz ve hasarsız teslim ettiğimi beyanla, girişte ödemiş olduğum “Güvence Bedeli”nin (Depozitonun) aşağıda bilgileri verilen banka hesabıma iade edilmesini arz ederim.</p>
                </div>

                <div class="body-content" style="margin-top:4mm;">
                    <p style="text-align:left !important; text-indent:0;"><b>T.C. Kimlik No:</b> {{TCKN}}</p>
                    <p style="text-align:left !important; text-indent:0;"><b>Tel:</b> {{TELEFON}}</p>
                    <p style="text-align:left !important; text-indent:0;"><b>BANKA HESAP BİLGİLERİ (İade İçin):</b></p>
                    <p style="text-align:left !important; text-indent:0;"><b>Banka Adı:</b> {{BANKA_ADI}}</p>
                    <p style="text-align:left !important; text-indent:0;"><b>IBAN:</b> {{IBAN}}</p>
                    <p style="text-align:left !important; text-indent:0;"><b>Hesap Sahibi:</b> {{HESAP_SAHIBI}}</p>
                </div>

                <div class="footer-wrapper" style="margin-top:8mm;">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("YURT_ADI", "Yurt adı"),
                field("BLOK_NO", "Blok no"),
                field("ODA_NO", "Oda no"),
                field("AYRILIS_TARIHI", "Ayrılış tarihi", "date"),
                field("NEDEN", "Ayrılış nedeni"),
                field("BANKA_ADI", "Banka adı"),
                field("IBAN", "IBAN"),
                field("HESAP_SAHIBI", "Hesap sahibi"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),

        // Nüfus/Belediye (5)
        template(
            id = "nuf_01",
            title = "Adres Kayıt Düzeltme Talebi",
            category = "Nüfus/Belediye",
            templateHtml = """
                <div class="institution-name">{{KAYMAKAMLIK_ADI}}</div>
                <div class="recipient-header">{{NUFUS_MUDURLUK_ADI}} İlçe Nüfus Müdürlüğü’ne</div>
                <div class="subject-line"><b>Konu:</b> Adres kayıt bilgisinin düzeltilmesi / güncellenmesi talebi</div>

                <div class="body-content">
                    <p>Kurumunuz kayıtlarında tarafıma ait adres bilgisinin düzeltilmesi/güncellenmesi gerekmektedir.</p>
                    <p>Bu kapsamda, mevcut kaydın aşağıda belirttiğim doğru bilgi doğrultusunda düzeltilmesini arz ederim.</p>
                    <p>Gereğini saygılarımla bilgilerinize sunarım.</p>
                    <br>
                </div>
                <div class="subject-line">
                    <p>Eski Adres Bilgisi:</p>
                    <p>{{ESKI_ADRES}}</p>
                    <p>Yeni Adres Bilgisi:</p>
                    <p>{{ADRES}}</p>
                </div>
                

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div>İletişim Numarası: {{TELEFON}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("KAYMAKAMLIK_ADI", "Kaymakamlık adı"),
                field("NUFUS_MUDURLUK_ADI", "... İlçe Nüfus Müdürlüğü'ne"),
                field("ESKI_ADRES", "Eski adres bilgisi"),
                field("EKLER_LISTESI", "Ekler (ikametgah, fatura vb.)")
            )
        ),
        template(
            id = "nuf_02",
            title = "Yol ve Kaldırım Onarım Talebi",
            category = "Nüfus/Belediye",
            templateHtml = """
                <div class="institution-name">{{BELEDIYE_ADI}} BELEDİYE BAŞKANLIĞI’NA</div>
                <div class="recipient-header">(Fen İşleri Müdürlüğü’ne)</div>

                <div class="body-content">
                    <p>İlçeniz sınırları içerisinde bulunan {{ONARIM_ADRES}} adresi üzerindeki yol / kaldırımda oluşan bozulmalar ve hasarlar, vatandaşların ulaşımını güçleştirmekte ve can-mal güvenliği açısından risk oluşturmaktadır.</p>
                    <p>Söz konusu yol / kaldırımın gerekli incelemeler yapılarak onarılması ve uygun hale getirilmesi hususunda gereğini saygılarımla arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("BELEDIYE_ADI", "... BELEDİYE BAŞKANLIĞI'NA"),
                field("ONARIM_ADRES", "Sorunun bulunduğu adres"),
                field("EKLER_LISTESI", "Ekler (fotoğraf vb.)")
            )
        ),
        template(
            id = "nuf_03",
            title = "Gürültü Şikâyeti Dilekçesi",
            category = "Nüfus/Belediye",
            templateHtml = """
                <div class="institution-name">{{BELEDIYE_ADI}} BELEDİYE BAŞKANLIĞI’NA</div>
                <div class="recipient-header">(Zabıta Müdürlüğü’ne / Çevre Koruma ve Kontrol Müdürlüğü’ne)</div>

                <div class="body-content">
                    <p>{{ADRES}} adresinde ikamet etmekteyim.</p>
                    <p>İkamet ettiğim bölgede / binada / çevrede bulunan {{SES_KAYNAK}} kaynaklı olarak, özellikle {{SES_SAATLERI}} saatleri arasında meydana gelen yüksek ses ve gürültü, günlük yaşamı olumsuz etkilemekte ve huzursuzluğa neden olmaktadır.</p>
                    <p>Söz konusu durumun incelenerek gerekli denetimlerin yapılması ve mevzuat kapsamında gereken işlemlerin uygulanması hususunda gereğini saygılarımla arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("BELEDIYE_ADI", "... BELEDİYE BAŞKANLIĞI'NA"),
                field("SES_KAYNAK", "Sesin kaynağı"),
                field("SES_SAATLERI", "../.. saatleri arasında"),
                field("EKLER_LISTESI", "Ekler (fotoğraf vb.)")
            )
        ),
        template(
            id = "nuf_04",
            title = "Emlak Vergisi Muafiyet Dilekçesi",
            category = "Nüfus/Belediye",
            templateHtml = """
             
                <div class="institution-name">T.C. {{BELEDIYE_ADI}} BAŞKANLIĞI</div>
                <div class="recipient-header">MALİ HİZMETLER MÜDÜRLÜĞÜ’NE</div>
                <div class="subject-line"><b>KONU:</b> Emlak Vergisi Muafiyeti (İndirimli Bina Vergisi Uygulaması) Hakkında.</div>

                <div class="body-content">
                    <p>1319 sayılı Emlak Vergisi Kanunu’nun 8. maddesi uyarınca Türkiye sınırları içinde hisseli veya tam mülkiyet kapsamında, brüt yüzölçümü 200 m²’yi geçmeyen tek meskenim dışında başka bir meskenim bulunmamaktadır.</p>
                    <p>Gelirim, münhasıran Sosyal Güvenlik Kurumundan aldığım aylıktan ibarettir. Sahip olduğum meskeni muayyen zamanlarda dinlenme amacıyla (yazlık vb.) değil, daimi ikametgâh olarak kullanmaktayım.</p>
                    <p>Bu nedenle, aşağıda bilgileri bulunan gayrimenkulüm için indirimli bina vergisi (sıfır oranlı) uygulanmasını, aksi durumun tespiti halinde uğranılan vergi ziyaı ve cezalarını ödemeyi kabul ettiğimi beyan ve arz ederim.</p>
                </div>

                <div class="section-title">MÜKELLEFİN (SİZİN):</div>
                <div>Ad Soyadı&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: {{AD_SOYAD}}</div>
                <div>T.C. Kimlik No : {{TCKN}}</div>
                <div>Adres&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: {{ADRES}}</div>
                <div>Telefon&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: {{TELEFON}}</div>

                <div class="section-title">GAYRİMENKULÜN BİLGİLERİ:</div>
                <div>Adres Durumu&nbsp;&nbsp;&nbsp;&nbsp;: Yukarıda belirttiğim ikamet adresim ile aynıdır.</div>
                <div>Belediye Sicil No : {{BELEDIYE_SICIL_NO}}</div>

                <div class="section-title">TAPU BİLGİLERİ:</div>
                <div>Pafta No&nbsp;&nbsp;&nbsp;: {{PAFTA_NO}}</div>
                <div>Ada No&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;: {{ADA_NO}}</div>
                <div>Parsel No&nbsp;: {{PARSEL_NO}}</div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("BELEDIYE_ADI", "Belediye adı"),
                field("BELEDIYE_SICIL_NO", "Belediye sicil no"),
                field("PAFTA_NO", "Pafta no"),
                field("ADA_NO", "Ada no"),
                field("PARSEL_NO", "Parsel no"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "nuf_05",
            title = "Kayıp Kimlik/Ehliyet Bildirim Dilekçesi",
            category = "Nüfus/Belediye",
            templateHtml = """
                <div class="institution-name">T.C. {{KAYMAKAMLIK_ADI}} KAYMAKAMLIĞI</div>
                <div class="recipient-header">İLÇE NÜFUS MÜDÜRLÜĞÜ’NE</div>
                <div class="subject-line"><b>KONU :</b> {{BELGE_TURU}} Kayıp Bildirimi ve Yenileme Talebi.</div>

                <div class="body-content">
                    <p>Müdürlüğünüz kayıtlarında yer alan Türkiye Cumhuriyeti {{BELGE_TURU}} belgesini, {{KAYBETME_TARIHI}} tarihinde kaybettim (veya çaldırdım).</p>
                    <p>Kaybolan kimlik kartımın / belgemin geçersiz sayılarak iptal edilmesini, kayıp tarihinden itibaren söz konusu belge ile yapılabilecek hiçbir işlemden sorumluluğumun bulunmadığını beyan ederim.</p>
                    <p>Gerekli yasal işlemlerin yapılarak, adıma yeni {{BELGE_TURU}} düzenlenmesi hususunda gereğini bilgilerinize arz ederim.</p>
                </div>

                <div class="section-title">BİLDİRİMDE BULUNAN:</div>
                <div>Ad Soyad : {{AD_SOYAD}}</div>
                <div>T.C. Kimlik No : {{TCKN}}</div>
                <div>Adres : {{ADRES}}</div>
                <div>Tel : {{TELEFON}}</div>

                <div class="footer-wrapper" style="margin-top:6mm;">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right" style="text-align:center;">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                            <div style="height:8mm;"></div>
                        </div>
                    </div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("BELGE_TURU", "Belge türü (Kimlik Kartı / Sürücü Belgesi)"),
                field("KAYMAKAMLIK_ADI", "Kaymakamlık adı"),
                field("KAYBETME_TARIHI", "Kaybetme tarihi", "date"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),

        // Hukuk/İdari (5)
        template(
            id = "huk_01",
            title = "Bilgi Edinme Başvurusu",
            category = "Hukuk/İdari",
            templateHtml = """
                <div class="institution-name">{{KURUM_ADI}} KURUMU / MÜDÜRLÜĞÜ’NE</div>
                <br>
                <div class="body-content">
                    <p>4982 sayılı Bilgi Edinme Hakkı Kanunu kapsamında aşağıda belirtilen hususlara ilişkin bilgi ve belgelerin tarafıma verilmesini talep ederim.</p>
                    <p>Talep konusu bilgi / belge:: {{TALEP_METNI}}</p>
                    <p>Talep ettiğim bilgi / belgenin, Kanun kapsamında değerlendirilerek tarafıma {{TESLIM_TERCIHI}} yoluyla bildirilmesini saygılarımla arz ederim.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div>İletişim Numarası: {{TELEFON}}</div>
                            <div>E-posta: {{E_POSTA}}</div>
                            <div>Adres: {{ADRES}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("KURUM_ADI", "İlgili Kamu Kurumu"),
                field("TALEP_METNI", "Talep edilen bilgi/belgeler"),
                field("TESLIM_TERCIHI", "Cevap teslim tercihi"),
                field("E_POSTA", "E-posta adresi"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "huk_02",
            title = "İdari İşleme İtiraz Dilekçesi",
            category = "Hukuk/İdari",
            templateHtml = """
                <div class="institution-name">T.C.</div>
                <div class="recipient-header">{{KURUM_ADI}} MÜDÜRLÜĞÜ’NE / BAŞKANLIĞI’NA / REKTÖRLÜĞÜ’NE</div>
                <div class="subject-line"><b>Konu:</b> {{ISLEM_TARIHI}} tarihli ve {{ISLEM_NO}} sayılı idari işleme itirazımın sunulmasından ibarettir.</div>

                <div class="body-content">
                    <p>Tarafıma bildirilen / tarafım hakkında tesis edilen {{ISLEM_TARIHI}} tarihli ve {{ISLEM_NO}} sayılı işlem ile {{IDARI_ISLEM}} yönünde idari işlem tesis edilmiştir.</p>
                    <p>Söz konusu işlem; hukuka, usule, hakkaniyete aykırı nitelik taşımaktadır.</p>
                    <p>Şöyle ki;</p>
                    <p>{{OLAY_ACIKLAMASI}}</p>
                    <p>Bu nedenlerle, hukuka aykırı olduğunu düşündüğüm {{ISLEM_TARIHI}} tarihli ve {{ISLEM_NO}} sayılı işlemin kaldırılması / geri alınması / düzeltilmesi / yeniden değerlendirilmesi hususunda gereğini arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("KURUM_ADI", "İtiraz Mercii (MÜDÜRLÜĞÜ’NE / BAŞKANLIĞI’NA / REKTÖRLÜĞÜ’NE)"),
                field("ISLEM_TARIHI", "İşlem tarihi"),
                field("ISLEM_NO", "İşlem / karar no"),
                field("OLAY_ACIKLAMASI", "İtiraz gerekçeleri"),
                field("EKLER_LISTESI", "Ekler")
            )
        ),
        template(
            id = "huk_03",
            title = "CİMER Başvuru Dilekçesi",
            category = "Hukuk/İdari",
            templateHtml = """
                <div class="institution-name">T.C. CUMHURBAŞKANLIĞI İLETİŞİM MERKEZİNE</div>
                <div class="recipient-header">Cumhurbaşkanlığı Külliyesi 06550 Beştepe / ANKARA</div>
                <br>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;"><strong>Başvuran:</strong> {{AD_SOYAD}}</p>
                    <p style="text-align:left !important; text-indent:0;"><strong>TCKN:</strong> {{TCKN}}</p>
                    <p style="text-align:left !important; text-indent:0;"><strong>Adres:</strong> {{ADRES}}</p>
                    <p style="text-align:left !important; text-indent:0;"><strong>İletişim Bilgileri:</strong> {{TELEFON}}</p>
        
                    <p style="text-align:left !important; text-indent:0;"><strong>Talep Konusu:</strong> {{TALEP_KONUSU_KISA_OZETI}}</p>
        
                    <p style="text-align:left !important; text-indent:0;"><strong>Açıklamalar:</strong></p>
                    <p>{{OLAY_ACIKLAMASI}}</p>
        
                    <p style="text-align:left !important; text-indent:0;">Yaşanan durum nedeniyle ortaya çıkan mağduriyet: {{MAGDURIYET_ETKISI}}</p>
        
                    <p>Yukarıda arz ve izah edilen hususlar çerçevesinde, talebimin değerlendirilerek gereğinin yapılmasını ve sonucundan tarafıma bilgi verilmesini arz ederim.</p>
                </div>
                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("TALEP_KONUSU_KISA_OZETI", "CİMER başvuru konusu"),
                field("OLAY_ACIKLAMASI", "Olayın özeti (- koyarak madde madde yaz)"),
                field("MAGDURIYET_ETKISI", "Mağduriyet etkisi"),
                field("EKLER_LISTESI", "Ekler")
            )
        ),
        template(
            id = "huk_04",
            title = "Savcılık Şikayet Dilekçesi",
            category = "Hukuk/İdari",
            templateHtml = """
                <div class="recipient-header">{{BASSAVCILIK}} CUMHURİYET BAŞSAVCILIĞI’NA</div>
                <br>

                <p style="text-align:left !important; text-indent:0;"><strong>MÜŞTEKİ (MAĞDUR):</strong> {{AD_SOYAD}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>T.C. Kimlik No:</strong> {{TCKN}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>Adres:</strong> {{ADRES}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>Telefon:</strong> {{TELEFON}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>ŞÜPHELİ:</strong> {{SUPHELI}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>SUÇ:</strong> {{SUC}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>SUÇ TARİHİ:</strong> {{SUC_TARIHI}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>KONU:</strong> Şüpheli hakkında gerekli soruşturmanın yapılarak kamu davası açılması ve cezalandırılması talebidir.</p>

                <div class="section-title">AÇIKLAMALAR:</div>
                <div class="body-content">
                    <p>1. {{SUC_TARIHI}} tarihinde, {{OLAY_YERI}} konumunda şüpheli tarafından mağdur edildim. {{OLAY_OZETI}}</p>
                    <p>2. Yaşanan bu olay nedeniyle şüpheliden şikayetçiyim.</p>
                    <p>3. Şüphelinin eylemi suç teşkil ettiğinden, gerekli soruşturmanın yapılarak hakkında kamu davası açılmasını talep etme zorunluluğu doğmuştur.</p>
                </div>

                <p style="text-align:left !important; text-indent:0;"><strong>HUKUKİ DELİLLER:</strong> {{DELILLER}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>SONUÇ VE İSTEM:</strong> Yukarıda arz ve izah edilen nedenlerle; şüpheli hakkında gerekli soruşturmanın yapılarak, TCK'nın ilgili maddeleri uyarınca cezalandırılması için kamu davası açılmasına karar verilmesini saygılarımla arz ve talep ederim.</p>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-meta-right">
                            <div class="date-text">Tarih: {{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("BASSAVCILIK", "Cumhuriyet Başsavcılığı (örn: Ankara)"),
                field("SUPHELI", "Şüpheli ad-soyad/bilgileri"),
                field("SUC", "Suç türü (örn: Dolandırıcılık)"),
                field("SUC_TARIHI", "Suç tarihi", "date"),
                field("OLAY_YERI", "Olay yeri"),
                field("OLAY_OZETI", "Olay özeti (1-2 cümle)"),
                field("DELILLER", "Deliller (tanık, mesaj, kamera kaydı vb.)"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "huk_05",
            title = "Veraset İlamı Talep Dilekçesi",
            category = "Hukuk/İdari",
            templateHtml = """
                <div class="date-text" style="text-align:right;">{{BUGUN_TARIH}}</div>
                <div class="recipient-header">{{MAHKEME_ADI}} NÖBETÇİ SULH HUKUK MAHKEMESİ’NE</div>
                <br>

                <p style="text-align:left !important; text-indent:0;"><strong>DAVACI (SİZ):</strong></p>
                <p style="text-align:left !important; text-indent:0;"><strong>Ad Soyadı:</strong> {{AD_SOYAD}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>T.C. Kimlik No:</strong> {{TCKN}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>Tel:</strong> {{TELEFON}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>DAVALI:</strong> Hasımsız.</p>
                <p style="text-align:left !important; text-indent:0;"><strong>MURİS (VEFAT EDEN):</strong></p>
                <p style="text-align:left !important; text-indent:0;"><strong>Ad Soyadı:</strong> {{MURIS_AD_SOYAD}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>T.C. Kimlik No:</strong> {{MURIS_TC_NO}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>Vefat Tarihi:</strong> {{OLUM_TARIHI}}</p>
                <p style="text-align:left !important; text-indent:0;"><strong>KONU:</strong> Mirasçılık belgesi (Veraset İlamı) verilmesi talebidir.</p>

                <div class="section-title">AÇIKLAMALAR:</div>
                <div class="body-content">
                    <p>1. Yukarıda kimlik bilgileri yazılı muris {{OLUM_TARIHI}} tarihinde vefat etmiştir.</p>
                    <p>2. Murisin vefatı ile geriye yasal mirasçıları olarak; ben (ve varsa diğer kardeşlerim/mirasçılar) kalmış bulunmaktayız.</p>
                    <p>3. Muristen intikal eden banka hesapları, taşınmazlar ve diğer resmi kurumlardaki işlemleri (intikal işlemlerini) yapabilmemiz için tarafımıza mirasçılık belgesi verilmesi zorunluluğu doğmuştur.</p>
                </div>

                <div class="section-title">HUKUKİ DELİLLER:</div>
                <div class="body-content">
                    <p>Nüfus kayıtları, ölüm belgesi ve her türlü yasal delil.</p>
                </div>

                <div class="section-title">SONUÇ VE İSTEM:</div>
                <div class="body-content">
                    <p>Yukarıda açıklanan nedenlerle; muris {{MURIS_AD_SOYAD}}'e ait mirasçıları ve miras paylarını gösterir veraset ilamının tarafımıza verilmesini saygılarımla arz ve talep ederim.</p>
                </div>
                
                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("MAHKEME_ADI", "Mahkeme adı"),
                field("MURIS_AD_SOYAD", "Muris ad soyadı"),
                field("MURIS_TC_NO", "Muris T.C. kimlik no"),
                field("OLUM_TARIHI", "Ölüm tarihi", "date"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),


        // Çalışma Hayatı (4)
        template(
            id = "cal_01",
            title = "Yıllık İzin Dilekçesi",
            category = "Çalışma Hayatı",
            templateHtml = """
                <div class="recipient-header">{{SIRKET_KURUM_ADI}} MÜDÜRLÜĞÜ’NE</div>
              
                <div class="body-content">
                    <p>Kurumunuza/Şirketinize bağlı {{GOREV}} görevi ile çalışmaktayım. Yıllık iznimden kalan hakkımı kullanmak üzere, {{IZIN_BASLANGIC_TARIHI}} ile {{IZIN_BITIS_TARIHI}} tarihleri arasında toplam {{TOPLAM_IS_GUNU}} iş günü yıllık izin kullanmak istiyorum.</p>
                    <p>Gereğini bilgilerinize arz ederim.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>Adres: {{ADRES}}</div>
                            <div>Tel: {{TELEFON}}</div>
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("SIRKET_KURUM_ADI", "Şirket/Kurum adı"),
                field("GOREV", "Görev"),
                field("IZIN_BASLANGIC_TARIHI", "İzin başlangıç tarihi", "date"),
                field("IZIN_BITIS_TARIHI", "İzin bitiş tarihi", "date"),
                field("TOPLAM_IS_GUNU", "Toplam iş günü", "number"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "cal_04",
            title = "İstifa Dilekçesi",
            category = "Çalışma Hayatı",
            templateHtml = """
                <div class="recipient-header">{{SIRKET_ADI}} İNSAN KAYNAKLARI MÜDÜRLÜĞÜ’NE</div>

                <div class="body-content">
                    <p>{{ISE_BASLANGIC_TARIHI}} tarihinden beri çalışmakta olduğum işyerinizden, {{ISTEN_AYRILMA_TARIHI}} tarihinden itibaren geçerli olmak üzere kendi isteğimle ayrılmak istiyorum.</p>
                    <p>Gerekli işlemlerin yapılmasını arz ederim.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>Adres: {{ADRES}}</div>
                            <div>Tel: {{TELEFON}}</div>
                            <div>T.C. Kimlik No: {{TCKN}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("SIRKET_ADI", "Şirket adı"),
                field("ISE_BASLANGIC_TARIHI", "İşe başlangıç tarihi", "date"),
                field("ISTEN_AYRILMA_TARIHI", "İşten ayrılma tarihi", "date"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "cal_02",
            title = "Fazla Mesai Alacağı Talebi",
            category = "Çalışma Hayatı",
            templateHtml = """
                <div class="institution-name">T.C.</div>
                <div class="recipient-header">{{MAKAMIN_ADI}}</div>
                <div class="subject-line"><b>Konu:</b> {{KONU_KISA_OZETI}}</div>

                <div class="body-content">
                    <p>Çalışma sürem boyunca yaptığım fazla çalışmaların karşılığı olan ücretlerin tam ve eksiksiz ödenmediğini tespit etmiş bulunmaktayım.</p>
                    <p>Talep metni: {{TALEP_METNI}}</p>
                    <p>Fazla mesai alacağımın yasal haklarım çerçevesinde hesaplanarak tarafıma ödenmesini arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("MAKAMIN_ADI", "İşyeri / İşveren Yetkilisi"),
                field("KONU_KISA_OZETI", "Fazla mesai alacağı talebi"),
                field("TALEP_METNI", "Dönem ve alacak açıklaması"),
                field("EKLER_LISTESI", "Ekler (puantaj, bordro vb.)")
            )
        ),
        template(
            id = "cal_03",
            title = "Maaş Hesaplama Hatası Düzeltme Talebi",
            category = "Çalışma Hayatı",
            templateHtml = """
                <div class="institution-name">T.C.</div>
                <div class="recipient-header">{{MAKAMIN_ADI}}</div>
                <div class="subject-line"><b>Konu:</b> {{KONU_KISA_OZETI}}</div>

                <div class="body-content">
                    <p>{{ISLEM_NO}} referans numaralı bordro / maaş kaydında hesaplama hatası bulunduğu değerlendirilmiştir.</p>
                    <p>Söz konusu hata: {{OLAY_ACIKLAMASI}}</p>
                    <p>Bu nedenle, ilgili dönem ücret ve bordro hesaplamalarının yeniden incelenerek gerekli düzeltmenin yapılmasını, oluşan fark bulunması hâlinde tarafıma yansıtılmasını arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("MAKAMIN_ADI", "Muhasebe / İnsan Kaynakları"),
                field("KONU_KISA_OZETI", "Ücret hesaplama hatasının düzeltilmesi talebi"),
                field("ISLEM_NO", "Bordro / işlem numarası"),
                field("OLAY_ACIKLAMASI", "Tespit edilen hata ve dayanakları"),
                field("EKLER_LISTESI", "Ekler (bordro, puantaj, hesap dökümü vb.)")
            )
        ),

        // Sağlık (1)
        template(
            id = "sag_01",
            title = "Aile Hekimi Değişikliği Dilekçesi",
            category = "Sağlık",
            templateHtml = """
                <div class="date-text" style="text-align:right;">{{BUGUN_TARIH}}</div>
                <div class="institution-name">T.C. {{KAYMAKAMLIK_ADI}} KAYMAKAMLIĞI</div>
                <div class="recipient-header">İLÇE SAĞLIK MÜDÜRLÜĞÜ’NE</div>
                <div class="recipient-header">(Sunulmak üzere)</div>
                <div class="recipient-header">{{ASM_ADI}} AİLE SAĞLIĞI MERKEZİ’NE</div>

                <div class="subject-line"><b>KONU :</b> Aile Hekimi Değişiklik Talebi.</div>

                <div class="body-content">
                    <p>Halen {{ESKI_ASM_ADI}} Aile Sağlığı Merkezi'nde, {{ESKI_ASM_DOKTORU}} isimli Aile Hekimine kayıtlı bir hastayım.</p>
                    <p>İkamet adresimin değişmesi {{MAZERET}} nedeniyle aile hekimliği hizmetimi bundan sonra Merkezinizde (Sağlık Ocağınızda) görev yapmakta olan Dr. {{YENI_ASM_DOKTORU}}'den almak istiyorum.</p>
                    <p>Kaydımın, talep ettiğim hekimin listesine alınması hususunda gereğini bilgilerinize arz ederim.</p>
                </div>

                <div class="section-title">ADRES ve İLETİŞİM:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">Adres: {{ADRES}}</p>
                    <p style="text-align:left !important; text-indent:0;">Tel: {{TELEFON}}</p>
                    <p style="text-align:left !important; text-indent:0;">T.C. Kimlik No: {{TCKN}}</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("KAYMAKAMLIK_ADI", "Kaymakamlık adı"),
                field("ASM_ADI", "Başvurulan ASM adı"),
                field("ESKI_ASM_ADI", "Eski ASM adı"),
                field("ESKI_ASM_DOKTORU", "Eski ASM doktoru"),
                field("MAZERET", "Mazeret"),
                field("YENI_ASM_DOKTORU", "Yeni ASM doktoru"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),

        // Finans/Tüketici (7)
        template(
            id = "fin_01",
            title = "Hatalı Ürün İadesi Dilekçesi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div style="margin-top:4mm;" class="recipient-header">{{IL_ADI}} TÜKETİCİ İL HAKEM HEYETİ BAŞKANLIĞI’NA</div>
                    
                <div style="margin-bottom:2mm;"><b>ŞİKAYET EDEN (SİZ)</b></div>
                <div>Ad Soyadı : {{AD_SOYAD}}</div>
                <div>T.C. Kimlik No : {{TCKN}}</div>
                <div>Adres : {{ADRES}}</div>
                <div>Telefon : {{TELEFON}}</div>

                <div style="margin-top:4mm;"><b>ŞİKAYET EDİLEN (FİRMA)</b> : {{SIKAYET_EDILEN_FIRMA}}</div>
                <div><b>UYUŞMAZLIK BEDELİ</b> : {{UYUSMAZLIK_BEDELI}} TL</div>
                <div><b>KONU</b> : Ayıplı ürünün iadesi ve ödenen bedelin tarafıma geri ödenmesi talebidir.</div>    

                <div class="section-title">AÇIKLAMALAR:</div>
                <div class="body-content">
                    <p>1. Şikayet edilen firmadan {{URUN_ALIM_TARIHI}} tarihinde, {{UYUSMAZLIK_BEDELI}} TL bedel ile {{URUN_MARKA_MODEL}} satın aldım. Faturası ektedir.</p>
                    <p>2. Satın aldığım ürün, kullanım süresi içerisinde arızalanmış/ayıplı çıkmıştır. {{ARIZA_DETAY}}</p>
                    <p>3. Firmaya başvurmama rağmen değişim veya iade talebim reddedilmiştir.</p>
                    <p>4. 6502 Sayılı Tüketicinin Korunması Hakkında Kanun'un "Ayıplı Mal" hükümleri gereğince; ürünün iade alınmasını ve ödediğim {{UYUSMAZLIK_BEDELI}} TL bedelin tarafıma yasal faiziyle birlikte iadesini talep ediyorum.</p>
                </div>
                
                <div class="section-title">SONUÇ VE İSTEM:</div>
                <div class="body-content">
                    <p>Yukarıda açıklanan nedenlerle; ayıplı ürünün firma tarafından geri alınmasına ve ödediğim ücretin tarafıma iadesine karar verilmesini saygılarımla arz ederim.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("IL_ADI", "... TÜKETİCİ İL HAKEM HEYETİ BAŞKANLIĞI’NA"),
                field("SIKAYET_EDILEN_FIRMA", "Şikayet edilen firma"),
                field("UYUSMAZLIK_BEDELI", "Uyuşmazlık bedeli (TL)", "number"),
                field("URUN_ALIM_TARIHI", "Ürün alım tarihi", "date"),
                field("URUN_MARKA_MODEL", "Ürünün markası ve modeli"),
                field("ARIZA_DETAY", "Arıza detayı (1-2 cümle)"),
                field("EKLER_LISTESI", "Ekler (fatura, servis formu vb.)")
            )
        ),
        template(
            id = "fin_02",
            title = "Kredi Kartı Aidat İadesi Talebi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="institution-name">T.C.</div>
                <div class="recipient-header">{{MAKAMIN_ADI}}</div>
                <div class="subject-line"><b>Konu:</b> {{KONU_KISA_OZETI}}</div>

                <div class="body-content">
                    <p>Kredi kartı hesabımdan tahsil edilen yıllık kart aidatının yasal dayanağı tarafıma açık şekilde bildirilmemiştir.</p>
                    <p>İşlem bilgisi: {{ISLEM_NO}}</p>
                    <p>Tahsil edilen aidat bedelinin tarafıma iadesini arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("MAKAMIN_ADI", "Banka Müşteri İlişkileri"),
                field("KONU_KISA_OZETI", "Kart aidatı iadesi"),
                field("ISLEM_NO", "Kart/ekstre işlem no"),
                field("EKLER_LISTESI", "Ekler")
            )
        ),
        template(
            id = "fin_03",
            title = "Hatalı Fatura Bedeline İtiraz",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="institution-name">T.C.</div>
                <div class="recipient-header">{{MAKAMIN_ADI}}</div>
                <div class="subject-line"><b>Konu:</b> {{KONU_KISA_OZETI}}</div>

                <div class="body-content">
                    <p>{{ISLEM_NO}} numaralı faturada yer alan bedelin fiili kullanım verileriyle uyumsuz olduğu kanaatindeyim.</p>
                    <p>İtiraz gerekçem: {{OLAY_ACIKLAMASI}}</p>
                    <p>Faturanın yeniden incelenmesini, hatalı tahakkuk varsa düzeltilmesini arz ederim.</p>
                </div>

                ${footerBlock()}
            """.trimIndent(),
            requiredFields = listOf(
                field("MAKAMIN_ADI", "İlgili Hizmet Sağlayıcı Kurum"),
                field("KONU_KISA_OZETI", "Hatalı fatura itirazı"),
                field("ISLEM_NO", "Fatura/abonelik no"),
                field("OLAY_ACIKLAMASI", "İtiraz detayları"),
                field("EKLER_LISTESI", "Ekler")
            )
        ),
        template(
            id = "fin_04",
            title = "Merkezi Sistem Yakıt Giderine İtiraz Dilekçesi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="recipient-header" style="margin-top:2mm;">{{APARTMAN_SITE_ADI}} APARTMANI/SİTESİ YÖNETİCİSİ’NE</div>
                <div class="subject-line"><b>KONU:</b> {{ITIRAZ_EDILEN_AY}} Ayı Yakıt Gider Paylaşımı Hesaplamasına İtiraz.</div>

                <div class="body-content">
                    <p>Siteniz/Apartmanınız {{BLOK_NO}} Blok, {{DAIRE_NO}} numaralı dairede ikamet etmekteyim. Tarafıma tebliğ edilen {{ITIRAZ_EDILEN_AY}} ayına ait, {{TUTAR}} TL tutarındaki ısınma/yakıt gideri borç bildirimi; fiili kullanımım ve dairemdeki ısı pay ölçer (veya kalorimetre) endeks değerleri ile örtüşmemektedir.</p>
                    <p>"Merkezi Isıtma ve Sıhhi Sıcak Su Sistemlerinde Isınma ve Sıhhi Sıcak Su Giderlerinin Paylaştırılmasına İlişkin Yönetmelik" hükümleri gereğince; hesaplamanın hatalı yapıldığını ve/veya dağılım cetvellerinde yanlışlık olduğunu düşünmekteyim.</p>
                    <p>Bu nedenle;</p>
                    <p>1. İlgili döneme ait binanın toplam doğalgaz faturasının,</p>
                    <p>2. Daireme ait ilk ve son endeks okuma tutanaklarının,</p>
                    <p>3. Tüm bağımsız bölümlerin paylarını gösteren detaylı dağılım (icmal) listesinin tarafıma ibraz edilmesini,</p>
                    <p>Maddi hatanın ivedilikle düzeltilerek faturanın yeniden hesaplanmasını arz ve rica ederim.</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div>Blok No: {{BLOK_NO}}</div>
                            <div>Daire No: {{DAIRE_NO}}</div>
                            <div>Tel: {{TELEFON}}</div>
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                             <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("APARTMAN_SITE_ADI", "Apartman/Site adı"),
                field("ITIRAZ_EDILEN_AY", "İtiraz edilen ay (örn: Ocak 2026)"),
                field("BLOK_NO", "Blok no"),
                field("DAIRE_NO", "Daire no"),
                field("TUTAR", "İtiraz edilen tutar (TL)", "number"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "fin_05",
            title = "Kredi Kartı İptal Dilekçesi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="recipient-header">{{BANKA_ADI}} A.Ş.</div>
                <div class="recipient-header">{{BANKA_SUBE}} ŞUBESİ MÜDÜRLÜĞÜ’NE</div>

                <div class="body-content">
                    <p>Bankanızın {{TCKN}} T.C. Kimlik numaralı müşterisiyim. Bankanız nezdinde kullandığım, aşağıda bilgileri yazılı olan kredi kartımın (ve varsa bu karta bağlı tüm ek kartların) hiçbir gerekçe ileri sürülmeksizin kullanıma kapatılmasını ve iptal edilmesini talep ediyorum.</p>
                    <p>Varsa mevcut taksitli borçlarımı, hesap özetleri (ekstre) tarafıma gönderildikçe son ödeme tarihlerinde ödemeye devam edeceğim. Bankacılık mevzuatı gereği, kartın iptal edilmesi için borcun tamamen bitmesi gerekmediğini biliyorum.</p>
                    <p>Bu dilekçenin tarafınıza ulaştığı tarihten itibaren, 5464 sayılı Banka Kartları ve Kredi Kartları Kanunu gereğince en geç 7 (yedi) gün içinde kartın iptal işleminin gerçekleştirilmesini, tarafıma yazılı veya SMS yoluyla bilgi verilmesini rica ederim.</p>
                </div>

                <div class="section-title">MÜŞTERİ BİLGİLERİ:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">Ad Soyadı: {{AD_SOYAD}}</p>
                    <p style="text-align:left !important; text-indent:0;">T.C. Kimlik No: {{TCKN}}</p>
                    <p style="text-align:left !important; text-indent:0;">Adres: {{ADRES}}</p>
                    <p style="text-align:left !important; text-indent:0;">Telefon: {{TELEFON}}</p>
                    <p style="text-align:left !important; text-indent:0;">E-posta: {{E-POSTA}}</p>
                </div>

                <div class="section-title">İPTAL EDİLECEK KART BİLGİLERİ:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">Kart Sahibi: {{AD_SOYAD}}</p>
                    <p style="text-align:left !important; text-indent:0;">Kart Numarası: {{KART_NO}}</p>
                </div>
                
                 <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("BANKA_ADI", "Banka adı"),
                field("BANKA_SUBE", "Banka şubesi"),
                field("KART_NO", "Kart numarası"),
                field("E-POSTA", "E-posta adresi"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "fin_06",
            title = "Poliçe İptali ve Prim İadesi Talep Dilekçesi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="recipient-header">{{ACENTE_ADI}} A.Ş.</div>
                <div class="recipient-header">GENEL MÜDÜRLÜĞÜ’NE</div>
                <div class="subject-line"><b>KONU :</b> Araç Satışı Nedeniyle Poliçe İptali ve Prim İadesi Talebi.</div>

                <div class="body-content">
                    <p>Şirketiniz nezdinde {{POLICE_NO}} poliçe numarası ile sigortalı bulunan, adıma kayıtlı {{PLAKA}} plakalı aracımı, {{SATIS_TARIHI}} tarihinde noter satışı ile üçüncü şahsa devretmiş/satmış bulunmaktayım.</p>
                    <p>Söz konusu aracın satış işlemi gerçekleştiğinden; ilgili Trafik / Kasko poliçemin satış tarihi itibarıyla iptal edilmesini talep ediyorum.</p>
                    <p>İptal işlemi sonrası hesaplanacak kalan günlere ait prim tutarının (iade bedelinin), aşağıda belirttiğim şahsıma ait banka hesabına (IBAN'a) yatırılması hususunda gereğini bilgilerinize arz ederim.</p>
                </div>

                <div class="section-title">SİGORTALI BİLGİLERİ:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">Ad Soyadı: {{AD_SOYAD}}</p>
                    <p style="text-align:left !important; text-indent:0;">T.C. Kimlik No: {{TCKN}}</p>
                    <p style="text-align:left !important; text-indent:0;">Tel: {{TELEFON}}</p>
                </div>

                <div class="section-title">İADE İÇİN BANKA BİLGİLERİ:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">Banka Adı: {{BANKA_ADI}}</p>
                    <p style="text-align:left !important; text-indent:0;">IBAN: {{IBAN_NO}}</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("ACENTE_ADI", "Acente adı"),
                field("POLICE_NO", "Poliçe numarası"),
                field("PLAKA", "Araç plakası"),
                field("SATIS_TARIHI", "Satış tarihi", "date"),
                field("BANKA_ADI", "Banka adı"),
                field("IBAN_NO", "IBAN numarası"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        ),
        template(
            id = "fin_07",
            title = "Kredi Kartı Harcama İtirazı Dilekçesi",
            category = "Finans/Tüketici",
            templateHtml = """
                <div class="recipient-header">{{BANKA_ADI}} A.Ş.</div>
                <div class="recipient-header">(Harcama İtiraz Birimine)</div>
                <div class="subject-line"><b>KONU :</b> Kredi Kartı Harcama İtirazı (Chargeback) Talebi.</div>

                <div class="body-content">
                    <p>Bankanızın {{KART_NO}} numaralı kredi kartı hamiliyim. Aşağıda detaylarını belirttiğim harcama işlemi/işlemleri, bilgim ve rızam dışında yapılmıştır.</p>
                    <p>Uluslararası kart kuruluşlarının (Visa/Mastercard) kuralları ve 5464 sayılı Kanun gereğince; söz konusu işlem için “Ters İbraz (Chargeback)” sürecinin başlatılmasını ve tutarın kredi kartıma iade edilmesini talep ediyorum.</p>
                    <p>Gereğini bilgilerinize arz ederim.</p>
                </div>

                <div class="section-title">İTİRAZ EDİLEN İŞLEM BİLGİLERİ:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">(Ekstrede göründüğü şekliyle)</p>
                    <p style="text-align:left !important; text-indent:0;">İşyeri Adı: {{ISYERI_ADI}}</p>
                    <p style="text-align:left !important; text-indent:0;">Tarih: {{ISLEM_TARIHI}}</p>
                    <p style="text-align:left !important; text-indent:0;">Tutar: {{TUTAR}} TL</p>
                </div>

                <div class="section-title">MÜŞTERİ BİLGİLERİ:</div>
                <div class="body-content">
                    <p style="text-align:left !important; text-indent:0;">Adres: {{ADRES}}</p>
                    <p style="text-align:left !important; text-indent:0;">Tel: {{TELEFON}}</p>
                    <p style="text-align:left !important; text-indent:0;">T.C. Kimlik No: {{TCKN}}</p>
                </div>

                <div class="footer-wrapper">
                    <div class="signature-container">
                        <div class="signature-contact-left">
                            <div class="attachments-left">{{EKLER_BOLUMU}}</div>
                        </div>
                        <div class="signature-meta-right">
                            <div class="date-text">{{BUGUN_TARIH}}</div>
                            <div class="name-text">{{AD_SOYAD}}</div>
                        </div>
                    </div>
                    <div class="clear"></div>
                </div>
            """.trimIndent(),
            requiredFields = listOf(
                field("BANKA_ADI", "Banka adı"),
                field("KART_NO", "Kart numarası"),
                field("ISYERI_ADI", "İşyeri adı"),
                field("ISLEM_TARIHI", "İşlem tarihi", "date"),
                field("TUTAR", "Tutar (TL)", "number"),
                field("EKLER_LISTESI", "Ekler (varsa)")
            )
        )
    )
}