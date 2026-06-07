package com.vnshop.invoiceservice.application.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * TKHDon — root element of a Vietnamese GDT e-invoice (Decree 123/2020, Circular 78/2021).
 */
@XmlRootElement(name = "TKHDon")
@XmlAccessorType(XmlAccessType.FIELD)
public class TKHDon {

    @XmlAttribute(name = "phienBan")
    private String phienBan = "2.0.1";

    /** DLHDon — invoice data block */
    @XmlElement(name = "DLHDon", required = true)
    private DLHDon dlhdon;

    /** DSCKS — digital signature placeholder (populated by GDT API integration, Track 7.3) */
    @XmlElement(name = "DSCKS")
    private String dscks = "";

    public TKHDon() {}

    public TKHDon(DLHDon dlhdon) {
        this.dlhdon = dlhdon;
    }

    public String getPhienBan() { return phienBan; }
    public void setPhienBan(String phienBan) { this.phienBan = phienBan; }

    public DLHDon getDlhdon() { return dlhdon; }
    public void setDlhdon(DLHDon dlhdon) { this.dlhdon = dlhdon; }

    public String getDscks() { return dscks; }
    public void setDscks(String dscks) { this.dscks = dscks; }
}
