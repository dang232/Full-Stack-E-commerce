package com.vnshop.invoiceservice.application.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

/**
 * DLHDon — invoice data block containing header, seller, buyer, line items, and totals.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class DLHDon {

    /** TTChung — general invoice info (template, symbol, number, date, currency) */
    @XmlElement(name = "TTChung", required = true)
    private TTChung ttchung;

    /** NDHDon — invoice body (seller, buyer, items, totals) */
    @XmlElement(name = "NDHDon", required = true)
    private NDHDon ndhdon;

    public DLHDon() {}

    public DLHDon(TTChung ttchung, NDHDon ndhdon) {
        this.ttchung = ttchung;
        this.ndhdon = ndhdon;
    }

    public TTChung getTtchung() { return ttchung; }
    public void setTtchung(TTChung ttchung) { this.ttchung = ttchung; }

    public NDHDon getNdhdon() { return ndhdon; }
    public void setNdhdon(NDHDon ndhdon) { this.ndhdon = ndhdon; }

    // ---------------------------------------------------------------------------
    // TTChung inner class — general invoice header
    // ---------------------------------------------------------------------------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TTChung {

        /** Invoice template code, e.g. "1" */
        @XmlElement(name = "MauSo", required = true)
        private String mauSo;

        /** Invoice symbol (ký hiệu), e.g. "C26T" */
        @XmlElement(name = "KyHieu", required = true)
        private String kyHieu;

        /** Sequential invoice number, e.g. "0001" */
        @XmlElement(name = "SoHD", required = true)
        private String soHD;

        /** Full GDT invoice number e.g. "1C26T-001/0001" */
        @XmlElement(name = "SoHDDayDu")
        private String soHDDayDu;

        /** Invoice date (dd/MM/yyyy) */
        @XmlElement(name = "NLap", required = true)
        private String nLap;

        /** Currency code, e.g. "VND" */
        @XmlElement(name = "DVTTe", required = true)
        private String dvtte;

        /** Exchange rate (1 for VND) */
        @XmlElement(name = "TGia")
        private String tGia = "1";

        /** Invoice type: 1 = value-added invoice */
        @XmlElement(name = "HTTToan", required = true)
        private String htttoan;

        public TTChung() {}

        public String getMauSo() { return mauSo; }
        public void setMauSo(String mauSo) { this.mauSo = mauSo; }

        public String getKyHieu() { return kyHieu; }
        public void setKyHieu(String kyHieu) { this.kyHieu = kyHieu; }

        public String getSoHD() { return soHD; }
        public void setSoHD(String soHD) { this.soHD = soHD; }

        public String getSoHDDayDu() { return soHDDayDu; }
        public void setSoHDDayDu(String soHDDayDu) { this.soHDDayDu = soHDDayDu; }

        public String getNLap() { return nLap; }
        public void setNLap(String nLap) { this.nLap = nLap; }

        public String getDvtte() { return dvtte; }
        public void setDvtte(String dvtte) { this.dvtte = dvtte; }

        public String getTGia() { return tGia; }
        public void setTGia(String tGia) { this.tGia = tGia; }

        public String getHtttoan() { return htttoan; }
        public void setHtttoan(String htttoan) { this.htttoan = htttoan; }
    }
}
