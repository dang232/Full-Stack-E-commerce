package com.vnshop.invoiceservice.application.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * CTHDon — a single line item (goods or service) on the invoice.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class CTHDon {

    /** Line number (STT) */
    @XmlElement(name = "STT", required = true)
    private int stt;

    /** Item description */
    @XmlElement(name = "TenHHDV", required = true)
    private String tenHHDV;

    /** Unit of measure */
    @XmlElement(name = "DVTinh")
    private String dvtinh;

    /** Quantity */
    @XmlElement(name = "SLuong", required = true)
    private String sluong;

    /** Unit price (pre-VAT) */
    @XmlElement(name = "DGia", required = true)
    private String dgia;

    /** Line total before VAT */
    @XmlElement(name = "TgTien", required = true)
    private String tgtien;

    /** VAT rate percentage string, e.g. "10", "8", "5", "0" */
    @XmlElement(name = "TSuat", required = true)
    private String tsuat;

    /** VAT amount for this line */
    @XmlElement(name = "TgTThue", required = true)
    private String tgtthue;

    /** Line total including VAT */
    @XmlElement(name = "TgTTTien", required = true)
    private String tgttTien;

    public CTHDon() {}

    public int getStt() { return stt; }
    public void setStt(int stt) { this.stt = stt; }

    public String getTenHHDV() { return tenHHDV; }
    public void setTenHHDV(String tenHHDV) { this.tenHHDV = tenHHDV; }

    public String getDvtinh() { return dvtinh; }
    public void setDvtinh(String dvtinh) { this.dvtinh = dvtinh; }

    public String getSluong() { return sluong; }
    public void setSluong(String sluong) { this.sluong = sluong; }

    public String getDgia() { return dgia; }
    public void setDgia(String dgia) { this.dgia = dgia; }

    public String getTgtien() { return tgtien; }
    public void setTgtien(String tgtien) { this.tgtien = tgtien; }

    public String getTsuat() { return tsuat; }
    public void setTsuat(String tsuat) { this.tsuat = tsuat; }

    public String getTgtthue() { return tgtthue; }
    public void setTgtthue(String tgtthue) { this.tgtthue = tgtthue; }

    public String getTgttTien() { return tgttTien; }
    public void setTgttTien(String tgttTien) { this.tgttTien = tgttTien; }
}
