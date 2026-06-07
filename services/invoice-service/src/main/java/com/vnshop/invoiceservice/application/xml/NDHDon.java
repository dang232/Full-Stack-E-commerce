package com.vnshop.invoiceservice.application.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

/**
 * NDHDon — invoice body: seller (NBan), buyer (NMua), line items (DSHHDVu), totals (TToan).
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class NDHDon {

    @XmlElement(name = "NBan", required = true)
    private NBanHDon nban;

    @XmlElement(name = "NMua", required = true)
    private NMHDon nmua;

    @XmlElement(name = "DSHHDVu", required = true)
    private DSHHDVu dshhd;

    @XmlElement(name = "TToan", required = true)
    private TToan ttoan;

    public NDHDon() {}

    public NDHDon(NBanHDon nban, NMHDon nmua, DSHHDVu dshhd, TToan ttoan) {
        this.nban = nban;
        this.nmua = nmua;
        this.dshhd = dshhd;
        this.ttoan = ttoan;
    }

    public NBanHDon getNban() { return nban; }
    public void setNban(NBanHDon nban) { this.nban = nban; }

    public NMHDon getNmua() { return nmua; }
    public void setNmua(NMHDon nmua) { this.nmua = nmua; }

    public DSHHDVu getDshhd() { return dshhd; }
    public void setDshhd(DSHHDVu dshhd) { this.dshhd = dshhd; }

    public TToan getTtoan() { return ttoan; }
    public void setTtoan(TToan ttoan) { this.ttoan = ttoan; }

    // ---------------------------------------------------------------------------
    // NBanHDon — seller info
    // ---------------------------------------------------------------------------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NBanHDon {

        @XmlElement(name = "Ten", required = true)
        private String ten;

        @XmlElement(name = "MST", required = true)
        private String mst;

        @XmlElement(name = "DChi", required = true)
        private String dchi;

        @XmlElement(name = "SDThoai")
        private String sdthoai;

        @XmlElement(name = "DCTDTu")
        private String dctdtu;

        public NBanHDon() {}

        public String getTen() { return ten; }
        public void setTen(String ten) { this.ten = ten; }

        public String getMst() { return mst; }
        public void setMst(String mst) { this.mst = mst; }

        public String getDchi() { return dchi; }
        public void setDchi(String dchi) { this.dchi = dchi; }

        public String getSdthoai() { return sdthoai; }
        public void setSdthoai(String sdthoai) { this.sdthoai = sdthoai; }

        public String getDctdtu() { return dctdtu; }
        public void setDctdtu(String dctdtu) { this.dctdtu = dctdtu; }
    }

    // ---------------------------------------------------------------------------
    // DSHHDVu — list of goods/services
    // ---------------------------------------------------------------------------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DSHHDVu {

        @XmlElement(name = "HHDVu")
        private List<CTHDon> items = new ArrayList<>();

        public DSHHDVu() {}

        public DSHHDVu(List<CTHDon> items) {
            this.items = items;
        }

        public List<CTHDon> getItems() { return items; }
        public void setItems(List<CTHDon> items) { this.items = items; }
    }

    // ---------------------------------------------------------------------------
    // TToan — invoice totals
    // ---------------------------------------------------------------------------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TToan {

        /** Total before VAT */
        @XmlElement(name = "TgTCThue", required = true)
        private String tgtcthue;

        /** Total VAT amount */
        @XmlElement(name = "TgTThue", required = true)
        private String tgtthue;

        /** Grand total (including VAT) */
        @XmlElement(name = "TgTTTBSo", required = true)
        private String tgtttbso;

        /** Grand total in words */
        @XmlElement(name = "TgTTTBChu")
        private String tgtttbchu;

        /** VAT breakdown list */
        @XmlElement(name = "DSThue")
        private DSThue dsThue;

        public TToan() {}

        public String getTgtcthue() { return tgtcthue; }
        public void setTgtcthue(String tgtcthue) { this.tgtcthue = tgtcthue; }

        public String getTgtthue() { return tgtthue; }
        public void setTgtthue(String tgtthue) { this.tgtthue = tgtthue; }

        public String getTgtttbso() { return tgtttbso; }
        public void setTgtttbso(String tgtttbso) { this.tgtttbso = tgtttbso; }

        public String getTgtttbchu() { return tgtttbchu; }
        public void setTgtttbchu(String tgtttbchu) { this.tgtttbchu = tgtttbchu; }

        public DSThue getDsThue() { return dsThue; }
        public void setDsThue(DSThue dsThue) { this.dsThue = dsThue; }
    }

    // ---------------------------------------------------------------------------
    // DSThue — VAT breakdown by rate
    // ---------------------------------------------------------------------------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DSThue {

        @XmlElement(name = "Thue")
        private List<ThueEntry> thueList = new ArrayList<>();

        public DSThue() {}

        public DSThue(List<ThueEntry> thueList) {
            this.thueList = thueList;
        }

        public List<ThueEntry> getThueList() { return thueList; }
        public void setThueList(List<ThueEntry> thueList) { this.thueList = thueList; }
    }

    // ---------------------------------------------------------------------------
    // ThueEntry — one VAT rate bucket
    // ---------------------------------------------------------------------------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ThueEntry {

        /** VAT rate, e.g. "10", "8", "5", "0" */
        @XmlElement(name = "TSuat", required = true)
        private String tsuat;

        /** Pre-tax subtotal for this rate bucket */
        @XmlElement(name = "TgTCThue", required = true)
        private String tgtcthue;

        /** VAT amount for this rate bucket */
        @XmlElement(name = "TgTThue", required = true)
        private String tgtthue;

        public ThueEntry() {}

        public ThueEntry(String tsuat, String tgtcthue, String tgtthue) {
            this.tsuat = tsuat;
            this.tgtcthue = tgtcthue;
            this.tgtthue = tgtthue;
        }

        public String getTsuat() { return tsuat; }
        public void setTsuat(String tsuat) { this.tsuat = tsuat; }

        public String getTgtcthue() { return tgtcthue; }
        public void setTgtcthue(String tgtcthue) { this.tgtcthue = tgtcthue; }

        public String getTgtthue() { return tgtthue; }
        public void setTgtthue(String tgtthue) { this.tgtthue = tgtthue; }
    }
}
